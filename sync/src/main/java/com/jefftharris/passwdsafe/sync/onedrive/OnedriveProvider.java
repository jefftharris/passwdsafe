/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.accounts.Account;
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
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.extensions.GraphServiceClient;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.AuthenticationResult;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.Logger;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Implements a provider for the OneDrive service
 */
public class OnedriveProvider extends AbstractSyncTimerProvider
{
    // TODO: use for upgrade?
    //private static final String PREF_USER_ID = "onedriveUserId";

    private static final String PREF_HOME_ACCT_ID = "onedriveHomeAcctId";

    private static final String TAG = "OnedriveProvider";

    // TODO: lock around itsClientApp use
    private PublicClientApplication itsClientApp = null;
    private String itsHomeAccountId = null;
    final private ReentrantLock itsServiceLock = new ReentrantLock();
    private boolean itsIsAddingAcct = false;
    private final Object itsNewAcctLock = new Object();

    /** Constructor */
    public OnedriveProvider(Context ctx)
    {
        super(ProviderType.ONEDRIVE, ctx, TAG);
    }

    @Override
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);

        itsClientApp = new PublicClientApplication(
                getContext().getApplicationContext(), Constants.CLIENT_ID);

        // TODO: nice down
        Logger.getInstance().setLogLevel(Logger.LogLevel.INFO);

        updateOnedriveAcct(null);
    }

    /**
     * Start the process of linking to an account
     */
    @Override
    public void startAccountLink(final FragmentActivity activity,
                                 final int requestCode)
    {
        Runnable loginTask = () -> {
            itsIsAddingAcct = true;
            itsClientApp.acquireToken(
                    activity, Constants.SCOPES,
                    new AuthenticationCallback()
                    {
                        @Override
                        public void onSuccess(AuthenticationResult authResult)
                        {
                            PasswdSafeUtil.dbginfo(TAG, "login ok res %s",
                                                   authResult);
                            updateAcctId(authResult.getAccount());
                        }

                        @Override
                        public void onError(MsalException exception)
                        {
                            Log.e(TAG, "Auth error", exception);
                            updateAcctId(null);
                        }

                        @Override
                        public void onCancel()
                        {
                            Log.e(TAG, "Auth canceled");
                            updateAcctId(null);
                        }

                        /**
                         * Update the account id when the authentication is
                         * finished
                         */
                        private void updateAcctId(IAccount acct)
                        {
                            synchronized (itsNewAcctLock) {
                                if (acct != null) {
                                    itsHomeAccountId =
                                            acct.getHomeAccountIdentifier()
                                                .getIdentifier();
                                } else {
                                    itsHomeAccountId = null;
                                }
                                itsIsAddingAcct = false;
                                itsNewAcctLock.notifyAll();
                            }
                        }
                    });
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
    public NewAccountTask finishAccountLink(int activityRequestCode,
                                            int activityResult,
                                            Intent activityData,
                                            Uri providerAcctUri)
    {
        itsClientApp.handleInteractiveRequestRedirect(
                activityRequestCode, activityResult, activityData);
        return new NewOneDriveTask(providerAcctUri, this);
    }

    /**
     * Unlink an account
     */
    @Override
    public void unlinkAccount()
    {
        unlinkAccount(null);
    }

    /**
     * Is the account fully authorized
     */
    @Override
    public boolean isAccountAuthorized()
    {
        return (getODAccount() != null);
    }

    /**
     * Get the account for the named provider
     */
    @Override
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.ONEDRIVE_ACCOUNT_TYPE);
    }

    /**
     * Check whether a provider can be added
     */
    @Override
    public void checkProviderAdd(SQLiteDatabase db) throws Exception
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
    public void cleanupOnDelete(String acctName)
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }

    /**
     * Request a sync
     */
    @Override
    public void requestSync(boolean manual)
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
            connResult.set(new SyncConnectivityResult(displayName));
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
        useOneDriveService(
                client -> new OnedriveSyncer(client, provider, connResult,
                                             logrec, getContext()).sync());
    }

    /**
     * Get a request builder for accessing a file path
     */
    public static IDriveItemRequestBuilder getFilePathRequest(
            IGraphServiceClient client,
            String path)
    {
        // TODO: move OD utils to separate class
        IDriveItemRequestBuilder rootRequest =
                client.getMe().getDrive().getRoot();
        if (path.length() > 1) {
            rootRequest = rootRequest.getItemWithPath(path.substring(1));
        }
        return rootRequest;
    }

    /**
     * Get the account user identifier
     */
    @Override
    protected String getAccountUserId()
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new Exception("Can't invoke getOnedriveService in ui thread");
        }

        if (!itsServiceLock.tryLock(15, TimeUnit.MINUTES)) {
            throw new Exception("Timeout waiting for OneDrive service");
        }

        try {
            IAccount acct = getODAccount();
            if (acct == null) {
                throw new Exception(TAG + " useOneDriveService not authorized");
            }

            final ObjectHolder<ObjectHolder<AuthenticationResult>> authResult =
                    new ObjectHolder<>();

            itsClientApp.acquireTokenSilentAsync(
                    Constants.SCOPES,
                    itsClientApp.getAccount(itsHomeAccountId),
                    new AuthenticationCallback()
                    {
                        @Override
                        public void onSuccess(AuthenticationResult authResult)
                        {
                            PasswdSafeUtil.dbginfo(
                                    TAG, "useOneDriveService auth ok");
                            onAuthDone(authResult);
                        }

                        @Override
                        public void onError(MsalException exception)
                        {
                            Log.e(TAG, "useOneDriveService auth error",
                                  exception);
                            onAuthDone(null);
                        }

                        @Override
                        public void onCancel()
                        {
                            Log.e(TAG, "useOneDriveService auth cancel");
                            onAuthDone(null);
                        }

                        /**
                         * Handle when authentication is finished
                         */
                        private void onAuthDone(AuthenticationResult result)
                        {
                            synchronized (authResult) {
                                ObjectHolder<AuthenticationResult> resHolder =
                                        new ObjectHolder<>();
                                resHolder.set(result);
                                authResult.set(resHolder);
                                authResult.notifyAll();
                            }
                        }
                    });

            synchronized (authResult) {
                while (authResult.get() == null) {
                    // todo: timeout
                    authResult.wait(15000);
                }
            }
            if (authResult.get().get() == null) {
                throw new Exception("Not authorized");
            }

            String auth = "Bearer " + authResult.get().get().getAccessToken();
            IAuthenticationProvider authProvider =
                    request -> request.addHeader("Authorization", auth);

            // TODO: use custom client config to nice down debug logs?
            final IClientConfig clientConfig =
                    DefaultClientConfig.createWithAuthenticationProvider(
                            authProvider);
            IGraphServiceClient service = new GraphServiceClient.Builder()
                    .fromConfig(clientConfig)
                    .buildClient();

            user.useOneDrive(service);
        } finally {
            itsServiceLock.unlock();
            if (!isAccountAuthorized()) {
                SyncApp.get(getContext()).updateProviderState();
            }
        }
    }

    /**
     * Asynchronously unlink the account
     * @param completeCb The callback to run when the unlink is complete
     */
    private void unlinkAccount(final Runnable completeCb)
    {
        IAccount acct = getODAccount();
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
    private synchronized IAccount getODAccount()
    {
        if (TextUtils.isEmpty(itsHomeAccountId)) {
            return null;
        }
        return itsClientApp.getAccount(itsHomeAccountId);
    }

    /** Update the account's user ID */
    private synchronized void setHomeAcctId(String homeAcctId)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateHomeAcctId: %s", homeAcctId);
        itsHomeAccountId = homeAcctId;

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_HOME_ACCT_ID, itsHomeAccountId);
        editor.apply();
    }

    /**
     * New OneDrive account task
     */
    private static class NewOneDriveTask
            extends NewAccountTask<OnedriveProvider>
    {
        /**
         * Constructor
         */
        public NewOneDriveTask(Uri currAcctUri, OnedriveProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
        }

        @Override
        protected boolean doProviderUpdate(@NonNull OnedriveProvider provider)
                throws Exception
        {
            synchronized (provider.itsNewAcctLock) {
                while (provider.itsIsAddingAcct) {
                    provider.itsNewAcctLock.wait(30000);
                    // TODO: check timeout
                }
            }
            // TODO: treat home account id from the acquireToken callback as
            // transient until here
            itsNewAcct = provider.itsHomeAccountId;
            provider.setHomeAcctId(provider.itsHomeAccountId);
            return true;
        }
    }
}
