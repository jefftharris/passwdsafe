/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.extensions.GraphServiceClient;
import com.microsoft.graph.extensions.IGraphServiceClient;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.Logger;
import com.microsoft.identity.client.PublicClientApplication;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Implements a provider for the OneDrive service
 */
public class OnedriveProvider extends AbstractSyncTimerProvider
{
    private static final String PREF_OLD_USER_ID = "onedriveUserId";
    private static final String PREF_HOME_ACCT_ID = "onedriveHomeAcctId";

    private static final boolean VERBOSE_LOGS = false;

    private static final String TAG = "OnedriveProvider";

    private final PublicClientApplication itsClientApp;
    private final ReentrantLock itsServiceLock = new ReentrantLock();
    private String itsHomeAccountId = null;
    private AcquireTokenCallback itsNewAcctCb;
    private boolean itsHasOldAcct = false;

    /** Constructor */
    public OnedriveProvider(Context ctx)
    {
        super(ProviderType.ONEDRIVE, ctx, TAG);
        itsClientApp = new PublicClientApplication(
                getContext().getApplicationContext(), Constants.CLIENT_ID);

        Logger.getInstance().setLogLevel(
                VERBOSE_LOGS ? Logger.LogLevel.VERBOSE : Logger.LogLevel.INFO);
    }

    @Override
    public synchronized void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);

        Context ctx = getContext();
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(ctx);
        String oldUserId = prefs.getString(PREF_OLD_USER_ID, null);
        if (oldUserId != null) {
            PasswdSafeUtil.dbginfo(TAG, "old acct %s", oldUserId);
            itsHasOldAcct = true;
            NotifUtils.showNotif(NotifUtils.Type.ONEDRIVE_MIGRATED, ctx);
        }

        updateOnedriveAcct(null);
    }

    /**
     * Start the process of linking to an account
     */
    @Override
    public synchronized void startAccountLink(final FragmentActivity activity,
                                              final int requestCode)
    {
        Runnable loginTask = () -> {
            itsNewAcctCb = new AcquireTokenCallback();
            itsClientApp.acquireToken(activity, Constants.SCOPES, itsNewAcctCb);
            };

        if (isAccountAuthorized()) {
            unlinkAccount(loginTask);
        } else {
            loginTask.run();
        }
    }

    /**
     * Finish the process of linking to an account
     */
    @Override
    public synchronized NewAccountTask finishAccountLink(
            int activityRequestCode,
            int activityResult,
            Intent activityData,
            Uri providerAcctUri)
    {
        try {
            itsClientApp.handleInteractiveRequestRedirect(
                    activityRequestCode, activityResult, activityData);
        } catch (NullPointerException npe) {
            itsNewAcctCb.onCancel();
        }
        AcquireTokenCallback tokenCb = itsNewAcctCb;
        itsNewAcctCb = null;
        return new NewOneDriveTask(providerAcctUri, tokenCb,
                                   itsHasOldAcct, this);
    }

    /**
     * Unlink an account
     */
    @Override
    public synchronized void unlinkAccount()
    {
        unlinkAccount(null);
    }

    /**
     * Is the account fully authorized
     */
    @Override
    public synchronized boolean isAccountAuthorized()
    {
        try {
            return (getODAccount() != null);
        } catch (Exception e) {
            Log.e(TAG, "isAccountAuthorized error", e);
            return false;
        }
    }

    /**
     * Get the account for the named provider
     */
    @Override
    public synchronized Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.ONEDRIVE_ACCOUNT_TYPE);
    }

    /**
     * Check whether a provider can be added
     */
    @Override
    public synchronized void checkProviderAdd(SQLiteDatabase db)
            throws Exception
    {
        List<DbProvider> providers = SyncDb.getProviders(db);
        for (DbProvider provider: providers) {
            if (provider.itsType == ProviderType.ONEDRIVE) {
                throw new Exception("Only one OneDrive account allowed");
            }
        }
    }

    /**
     * Cleanup a provider when deleted
     */
    @Override
    public synchronized void cleanupOnDelete(String acctName)
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }

    /**
     * Request a sync
     */
    @Override
    public synchronized void requestSync(boolean manual)
    {
        boolean authorized = isAccountAuthorized();
        PasswdSafeUtil.dbginfo(TAG, "requestSync authorized: %b", authorized);
        if (authorized) {
            doRequestSync(manual);
        }
    }

    @Override
    public SyncConnectivityResult checkSyncConnectivity(Account acct)
            throws Exception
    {
        final ObjectHolder<SyncConnectivityResult> connResult =
                new ObjectHolder<>();
        useOneDriveService(client -> {
            String displayName = OnedriveSyncer.getDisplayName(client);
            connResult.set(
                    new OnedriveSyncConnectivityResult(displayName, client));
        });
        return connResult.get();
    }

    /**
     * Sync a provider
     */
    @Override
    public void sync(Account acct,
                     final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec) throws Exception
    {
        OnedriveSyncConnectivityResult odConnResult =
                (OnedriveSyncConnectivityResult)connResult;
        useOneDriveService(
                client -> new OnedriveSyncer(client, provider, connResult,
                                             logrec, getContext()).sync(),
                odConnResult.getService());
    }

    /**
     * Get the account user identifier
     */
    @Override
    protected synchronized String getAccountUserId()
    {
        return itsHomeAccountId;
    }

    /**
     * Interface for users of the OneDrive service
     */
    public interface OneDriveUser
    {
        /**
         * Callback to use the service
         */
        void useOneDrive(IGraphServiceClient client) throws Exception;
    }

    /**
     * Use the OneDrive service
     */
    public void useOneDriveService(OneDriveUser user) throws Exception
    {
        useOneDriveService(user, null);
    }

    /**
     * Use the OneDrive service with optional provided service
     */
    private void useOneDriveService(OneDriveUser user,
                                    IGraphServiceClient service)
        throws Exception
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new Exception("Can't invoke getOnedriveService in ui thread");
        }

        if (!itsServiceLock.tryLock(15, TimeUnit.MINUTES)) {
            throw new Exception("Timeout waiting for OneDrive service");
        }

        try {
            try {
                useOneDriveServiceImpl(user, service);
            } catch (GraphServiceException e) {
                if (OnedriveSyncer.is401Error(e)) {
                    Log.i(TAG, "Unauthorized, retrying", e);
                    Thread.sleep(5000);
                    useOneDriveServiceImpl(user, null);
                } else {
                    throw e;
                }
            }
        } finally {
            itsServiceLock.unlock();
            if (!isAccountAuthorized()) {
                SyncApp.get(getContext()).updateProviderState();
            }
        }
    }

    /**
     * Implementation of using the OneDrive service which can be retried
     */
    private void useOneDriveServiceImpl(OneDriveUser user,
                                        IGraphServiceClient service)
        throws Exception
    {
        if (service == null) {
            AcquireTokenCallback tokenCb = new AcquireTokenCallback();

            synchronized (this) {
                IAccount acct = getODAccount();
                if (acct == null) {
                    throw new Exception(
                            TAG + " useOneDriveService not authorized");
                }

                itsClientApp.acquireTokenSilentAsync(Constants.SCOPES,
                                                     acct, tokenCb);
            }

            AuthenticationResult authResult = tokenCb.getResult();
            if (authResult == null) {
                throw new Exception("Not authorized");
            }

            String auth = "Bearer " + authResult.getAccessToken();
            IAuthenticationProvider authProvider =
                    request -> request.addHeader("Authorization", auth);

            final IClientConfig clientConfig = new ClientConfig(
                    authProvider,
                    VERBOSE_LOGS ? LoggerLevel.Debug : LoggerLevel.Error);
            service = new GraphServiceClient.Builder()
                    .fromConfig(clientConfig)
                    .buildClient();
        }

        user.useOneDrive(service);
    }

    /**
     * Asynchronously unlink the account
     * @param completeCb The callback to run when the unlink is complete
     */
    private void unlinkAccount(final Runnable completeCb)
    {
        IAccount acct;
        try {
            acct = getODAccount();
        } catch (Exception e) {
            Log.e(TAG, "unlinkAccount error", e);
            acct = null;
        }
        if (acct != null) {
            itsClientApp.removeAccount(acct);
        }
        setHomeAcctId(null);
        updateOnedriveAcct(completeCb);
    }

    /**
     * Asynchronously update the OneDrive account client based on availability
     * of authentication information
     * @param completeCb The callback to run when the update is complete
     */
    private synchronized void updateOnedriveAcct(final Runnable completeCb)
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());

        itsHomeAccountId = prefs.getString(PREF_HOME_ACCT_ID, null);
        if (isAccountAuthorized()) {
            try {
                updateProviderSyncFreq(itsHomeAccountId);
                requestSync(false);
            } catch (Exception e) {
                Log.e(TAG, "updateOnedriveAcct failure", e);
            }
        } else {
            itsHomeAccountId = null;
            updateSyncFreq(null, 0);
        }

        if (completeCb != null) {
            completeCb.run();
        }
    }

    /**
     * Get the OneDrive account for the active account
     */
    @Nullable
    @SuppressLint("ApplySharedPref")
    private synchronized IAccount getODAccount() throws Exception
    {
        if (TextUtils.isEmpty(itsHomeAccountId)) {
            return null;
        }
        try {
            return itsClientApp.getAccount(itsHomeAccountId);
        } catch (Throwable e) {
            // Work-around for
            // https://github.com/AzureAD/microsoft-authentication-library-for-android/issues/495
            SharedPreferences msalPrefs = getContext().getSharedPreferences(
                    "com.microsoft.identity.client.account_credential_cache",
                    Context.MODE_PRIVATE);
            msalPrefs.edit().clear().commit();
            Log.w(TAG, "getODAccount error", e);
            throw new Exception("OneDrive account retrieval error", e);
        }
    }

    /** Update the account's user ID */
    private synchronized void setHomeAcctId(String homeAcctId)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateHomeAcctId: %s", homeAcctId);
        itsHomeAccountId = homeAcctId;

        Context ctx = getContext();
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_HOME_ACCT_ID, itsHomeAccountId);
        if (itsHasOldAcct && !TextUtils.isEmpty(homeAcctId)) {
            PasswdSafeUtil.dbginfo(TAG, "Remove old acct");
            editor.remove(PREF_OLD_USER_ID);
            itsHasOldAcct = false;
            NotifUtils.cancelNotif(NotifUtils.Type.ONEDRIVE_MIGRATED, ctx);
        }
        editor.apply();
    }

    /**
     * New OneDrive account task
     */
    private static class NewOneDriveTask
            extends NewAccountTask<OnedriveProvider>
    {
        private final AcquireTokenCallback itsTokenCb;
        private final boolean itsIsReauth;

        /**
         * Constructor
         */
        public NewOneDriveTask(Uri currAcctUri,
                               AcquireTokenCallback tokenCb,
                               boolean reauthorization,
                               OnedriveProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
            itsTokenCb = tokenCb;
            itsIsReauth = reauthorization;
        }

        @Override
        protected boolean doProviderUpdate(@NonNull OnedriveProvider provider)
                throws Exception
        {
            String acctId = null;
            AuthenticationResult authResult = itsTokenCb.getResult();
            if (authResult != null) {
                IAccount acct = authResult.getAccount();
                if (acct != null) {
                    acctId = acct.getHomeAccountIdentifier().getIdentifier();
                }
            }

            itsNewAcct = acctId;
            provider.setHomeAcctId(acctId);
            if (itsIsReauth && !TextUtils.isEmpty(itsNewAcct)) {
                // Change account identifier
                SyncDb.useDb(db -> {
                    long id = PasswdSafeContract.Providers.getId(itsAccountUri);
                    DbProvider dbprovider = SyncDb.getProvider(id, db);
                    if ((dbprovider != null) &&
                        !TextUtils.equals(dbprovider.itsAcct, itsNewAcct)) {
                        SyncDb.updateProviderAccount(id, itsNewAcct, db);
                    }
                    return null;
                });
                provider.updateOnedriveAcct(null);
            }

            return !itsIsReauth;
        }
    }
}
