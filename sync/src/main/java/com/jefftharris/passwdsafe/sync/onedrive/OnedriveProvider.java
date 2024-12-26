/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.microsoft.graph.core.requests.GraphClientFactory;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.identity.client.AcquireTokenSilentParameters;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.SignInParameters;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.kiota.ApiException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Interceptor;
import okhttp3.Response;


/**
 * Implements a provider for the OneDrive service
 */
public class OnedriveProvider extends AbstractSyncTimerProvider
{
    private static final String PREF_OLD_USER_ID = "onedriveUserId";
    private static final String PREF_HOME_ACCT_ID = "onedriveHomeAcctId";

    private static final boolean VERBOSE_LOGS = false;//false;

    private static final String TAG = "OnedriveProvider";

    private File itsConfigFile;
    private ISingleAccountPublicClientApplication itsClientApp = null;
    private IAccount itsAccount = null;
    private final Object itsAccountLock = new Object();
    private final ReentrantLock itsServiceLock = new ReentrantLock();
    private AcquireTokenCallback itsNewAcctCb;

    /**
     * Constructor
     */
    public OnedriveProvider(Context ctx)
    {
        super(ProviderType.ONEDRIVE, ctx, TAG);

        try {
            itsConfigFile = File.createTempFile("onedrive-config", "json");
            itsConfigFile.deleteOnExit();

            try (var writer = new PrintWriter(itsConfigFile)) {
                writer.printf("{ \"client_id\": \"%s\", ", Constants.CLIENT_ID);
                writer.printf("\"redirect_uri\": \"msal%s://auth\", ",
                              Constants.CLIENT_ID);
                writer.write("\"account_mode\": \"SINGLE\", ");
                writer.write("\"broker_redirect_uri_registered\": true, ");
                writer.write("\"logging\": { ");
                writer.write("\"pii_enabled\": false");
                writer.printf(", \"logcat_enabled\": %s",
                              VERBOSE_LOGS ? "true" : "false");
                writer.printf(", \"log_level\": \"%s\"",
                              VERBOSE_LOGS ? "INFO" : "WARNING");
                writer.write(" }");
                writer.write(" }");
            }

            PublicClientApplication.createSingleAccountPublicClientApplication(
                    ctx, itsConfigFile, new ClientCreatedListener());
        } catch (IOException ioe) {
            PasswdSafeLog.error(TAG, ioe, "Failed to create PCA config file");
        }
    }

    /**
     * Initialize the provider
     */
    @Override
    @MainThread
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
        PasswdSafeLog.debug(TAG, "init");

        // TODO: PREF_HOME_ACCT_ID is present, show notification
        //  for migration
//        Context ctx = getContext();
//        SharedPreferences prefs =
//                PreferenceManager.getDefaultSharedPreferences(ctx);
//        if (old prefs) {
//            NotifUtils.showNotif(NotifUtils.Type.ONEDRIVE_MIGRATED, ctx);
//        }

        updateOnedriveAcct(null);
    }

    /**
     * Start the process of linking to an account
     */
    @Override
    @MainThread
    public void startAccountLink(final FragmentActivity activity,
                                 final int requestCode)
    {
        PasswdSafeLog.debug(TAG, "startAccountLink");
        if (itsClientApp == null) {
            PasswdSafeUtil.showErrorMsg("No OneDrive client",
                                        new ActContext(activity));
            return;
        }

        Runnable loginTask = () -> {
            itsNewAcctCb = new AcquireTokenCallback();
            var params = SignInParameters
                    .builder()
                    .withActivity(activity)
                    .withCallback(itsNewAcctCb)
                    .withScopes(Arrays.asList(Constants.SCOPES))
                    .build();
            // TODO: how to re-authenticate (signin again?)
            itsClientApp.signIn(params);
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
    @MainThread
    public NewAccountTask<? extends AbstractSyncTimerProvider>
    finishAccountLink(FragmentActivity activity,
                      int activityRequestCode,
                      int activityResult,
                      Intent activityData,
                      Uri providerAcctUri)
    {
        PasswdSafeLog.debug(TAG, "finishAccountLink");
        AcquireTokenCallback tokenCb = itsNewAcctCb;
        itsNewAcctCb = null;
        return new NewOneDriveTask(providerAcctUri, tokenCb, this);
    }

    /**
     * Unlink an account
     */
    @Override
    @MainThread
    public void unlinkAccount()
    {
        unlinkAccount(null);
    }

    /**
     * Is the account fully authorized
     */
    @Override
    @MainThread
    public boolean isAccountAuthorized()
    {
        synchronized (itsAccountLock) {
            return (itsAccount != null) && (itsClientApp != null);
        }
    }

    /**
     * Get the account for the named provider
     */
    @Override
    @MainThread
    public Account getAccount(String acctName)
    {
        // TODO: Add base-class impl to use account type
        return new Account(acctName, SyncDb.ONEDRIVE_ACCOUNT_TYPE);
    }

    /**
     * Check whether a provider can be added
     */
    @Override
    @MainThread
    public void checkProviderAdd(SQLiteDatabase db)
            throws Exception
    {
        // TODO: Add base-class impl to check against type
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
    @MainThread
    public void cleanupOnDelete()
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }

    /**
     * Request a sync
     */
    @Override
    @MainThread
    public void requestSync(boolean manual)
    {
        boolean authorized = isAccountAuthorized();
        PasswdSafeLog.debug(TAG, "requestSync authorized: %b", authorized);
        if (authorized) {
            doRequestSync(manual);
        }
    }

    @Override
    @WorkerThread
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
    @WorkerThread
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
    @MainThread
    protected String getAccountUserId()
    {
        synchronized (itsAccountLock) {
            return getAccountId(itsAccount);
        }
    }

    /**
     * Interface for users of the OneDrive service
     */
    public interface OneDriveUser
    {
        /**
         * Callback to use the service
         */
        void useOneDrive(GraphServiceClient client) throws Exception;
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
    @WorkerThread
    private void useOneDriveService(OneDriveUser user,
                                    GraphServiceClient service)
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
            } catch (ApiException e) {
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
            SyncApp.runOnUiThread(() -> {
                if (!isAccountAuthorized()) {
                    SyncApp.get(getContext()).updateProviderState();
                }
            });
        }
    }

    /**
     * Implementation of using the OneDrive service which can be retried
     */
    @WorkerThread
    private void useOneDriveServiceImpl(OneDriveUser user,
                                        GraphServiceClient service)
        throws Exception
    {
        if (service == null) {
            AcquireTokenSilentParameters tokenParams;
            synchronized (itsAccountLock) {
                if ((itsAccount == null) || (itsClientApp == null)) {
                    throw new Exception(
                            TAG + " useOneDriveService not authorized");
                }

                tokenParams = new AcquireTokenSilentParameters.Builder()
                        .withScopes(Arrays.asList(Constants.SCOPES))
                        .fromAuthority(itsClientApp
                                               .getConfiguration()
                                               .getDefaultAuthority()
                                               .getAuthorityURL()
                                               .toString())
                        .forAccount(itsAccount)
                        .build();
            }

            // TODO: test unauthorized and whether to trigger an account update
            var auth = itsClientApp.acquireTokenSilent(tokenParams);

            var httpClientFactory = GraphClientFactory.create();
            if (VERBOSE_LOGS) {
                httpClientFactory.addInterceptor(new DebugHttpInterceptor());
            }
            var httpClient = httpClientFactory.build();
            service = new GraphServiceClient(
                    (request, additionalAuthenticationContext) -> request.headers.add(
                            "Authorization", auth.getAuthorizationHeader()),
                    httpClient);
        }

        user.useOneDrive(service);
    }

    /**
     * Asynchronously unlink the account
     * @param completeCb The callback to run when the unlink is complete
     */
    @MainThread
    private void unlinkAccount(final Runnable completeCb)
    {
        PasswdSafeLog.debug(TAG, "unlinkAccount");
        if (isAccountAuthorized()) {
            itsClientApp.signOut(new SignOutCallback(completeCb));
        } else {
            updateOnedriveAcct(completeCb);
        }
    }

    /**
     * Asynchronously update the OneDrive account client based on availability
     * of authentication information
     * @param completeCb The callback to run when the update is complete
     */
    @MainThread
    private void updateOnedriveAcct(final Runnable completeCb)
    {
        if (itsClientApp == null) {
            return;
        }

        itsClientApp.getCurrentAccountAsync(
                new CurrentAccountCallback(completeCb));
    }

    /**
     * Set the account to use for the client
     */
    private void setAccount(IAccount newAccount)
    {
        boolean updateProviders = false;
        synchronized (itsAccountLock) {
            var acctStr = getAccountId(itsAccount);
            var newStr = getAccountId(newAccount);
            PasswdSafeLog.debug(TAG, "setAccount %s -> %s", acctStr, newStr);

            itsAccount = newAccount;
            if (!TextUtils.equals(acctStr, newStr)) {
                updateProviders = true;
            }
        }

        if (updateProviders) {
            SyncApp.get(getContext()).updateProviderState();
        }
    }

    /**
     * Get the identifier for an account
     */
    @Nullable
    private static String getAccountId(@Nullable IAccount acct)
    {
        return (acct != null) ? acct.getId() : null;
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
        protected NewOneDriveTask(Uri currAcctUri,
                                  AcquireTokenCallback tokenCb,
                                  //boolean reauthorization,
                                  OnedriveProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
            itsTokenCb = tokenCb;
            itsIsReauth = false;//reauthorization;
        }

        @Override
        protected boolean doProviderUpdate(@NonNull OnedriveProvider provider)
                throws Exception
        {
            String acctId = null;
            IAccount newAccount = null;
            IAuthenticationResult authResult = itsTokenCb.getResult();
            if (authResult != null) {
                newAccount = authResult.getAccount();
                acctId = newAccount.getId();
            }

            itsNewAcct = acctId;
            provider.setAccount(newAccount);

            // TODO: Is reauth needed with upgrade from previous?
//            if (itsIsReauth && !TextUtils.isEmpty(itsNewAcct)) {
//                // Change account identifier
//                SyncDb.useDb(db -> {
//                    long id = PasswdSafeContract.Providers.getId(itsAccountUri);
//                    DbProvider dbprovider = SyncDb.getProvider(id, db);
//                    if ((dbprovider != null) &&
//                        !TextUtils.equals(dbprovider.itsAcct, itsNewAcct)) {
//                        SyncDb.updateProviderAccount(id, itsNewAcct, db);
//                    }
//                    return null;
//                });
//                provider.updateOnedriveAcct(null);
//            }

            return !itsIsReauth;
        }
    }

    /**
     * Listener for the creation of the PublicClientApplication instance
     */
    private class ClientCreatedListener
            implements IPublicClientApplication.ISingleAccountApplicationCreatedListener
    {
        @Override
        public void onCreated(ISingleAccountPublicClientApplication app)
        {
            PasswdSafeLog.debug(TAG, "App created");
            rmConfigFile();
            itsClientApp = app;

            updateOnedriveAcct(null);
        }

        @Override
        public void onError(MsalException e)
        {
            PasswdSafeLog.error(TAG, e, "Error creating app");
            rmConfigFile();
        }

        private void rmConfigFile()
        {
            if (!itsConfigFile.delete()) {
                PasswdSafeLog.error(TAG, "Error deleting MSAL config file");
            }
        }
    }

    /**
     * Callback for the completion of a client sign-out
     */
    private class SignOutCallback
            implements ISingleAccountPublicClientApplication.SignOutCallback
    {
        private final Runnable itsCompleteCb;

        public SignOutCallback(Runnable completeCb)
        {
            itsCompleteCb = completeCb;
        }

        @Override
        public void onSignOut()
        {
            PasswdSafeLog.debug(TAG, "signOut success");
            finish();
        }

        @Override
        public void onError(@NonNull MsalException e)
        {
            PasswdSafeLog.error(TAG, e, "signOut failed");
            finish();
        }

        private void finish()
        {
            updateOnedriveAcct(itsCompleteCb);
        }
    }

    /**
     * Callback for retrieving the client's current account
     */
    private class CurrentAccountCallback
            implements ISingleAccountPublicClientApplication.CurrentAccountCallback
    {
        private final Runnable itsCompleteCb;

        public CurrentAccountCallback(Runnable completeCb)
        {
            itsCompleteCb = completeCb;
        }

        @Override
        public void onAccountLoaded(@Nullable IAccount activeAccount)
        {
            PasswdSafeLog.debug(TAG, "Current account loaded: %s",
                                getAccountId(activeAccount));
            finish(activeAccount);
        }

        @Override
        public void onAccountChanged(@Nullable IAccount priorAccount,
                                     @Nullable IAccount currentAccount)
        {
            PasswdSafeLog.debug(TAG, "Current account changed: %s -> %s",
                                getAccountId(priorAccount),
                                getAccountId(currentAccount));
            finish(currentAccount);
        }

        @Override
        public void onError(@NonNull MsalException e)
        {
            PasswdSafeLog.error(TAG, e, "Error getting current account");
            finish(null);
        }

        private void finish(IAccount newAccount)
        {
            try {
                setAccount(newAccount);

                if (newAccount != null) {
                    try {
                        updateProviderSyncFreq(newAccount.getId());
                    } catch (Exception e) {
                        Log.e(TAG, "updateOnedriveAcct update " +
                                   "provider failure", e);
                    }
                } else {
                    updateSyncFreq(null, 0);
                }

                if (itsCompleteCb != null) {
                    itsCompleteCb.run();
                }
            } catch (Throwable e) {
                PasswdSafeLog.error(TAG, e,
                                    "updateOnedriveAcct update failure");
            }
        }
    }

    /**
     * HTTP client interceptor for debug tracing
     */
    private static class DebugHttpInterceptor implements Interceptor
    {
        @NonNull
        public Response intercept(@NonNull Chain chain) throws IOException
        {
            PasswdSafeUtil.dbginfo(TAG, "HTTP REQUEST %s %s (Content-type: %s)",
                                   chain.request().method(),
                                   chain.request().url(), chain
                                           .request()
                                           .headers()
                                           .get("Content-Type"));
            return chain.proceed(chain.request());
        }
    }
}
