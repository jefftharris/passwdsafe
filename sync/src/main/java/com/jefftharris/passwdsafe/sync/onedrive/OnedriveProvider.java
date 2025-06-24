/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;
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
import com.microsoft.identity.client.SilentAuthenticationCallback;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;
import com.microsoft.kiota.ApiException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
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

    private static final boolean VERBOSE_LOGS = false;

    private static final String TAG = "OnedriveProvider";

    /**
     * Type of account check to perform
     */
    private enum AcctCheck
    {
        PRESENT,
        AUTHORIZED
    }

    private File itsConfigFile;
    private ISingleAccountPublicClientApplication itsClientApp = null;
    private IAccount itsAccount = null;
    private boolean itsAccountTokenOk = false;
    private final Object itsAccountLock = new Object();
    private final ReentrantLock itsServiceLock = new ReentrantLock();
    private AcquireTokenCallback itsNewAcctCb;
    private boolean itsIsMigrationNeeded = false;

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

        checkMigration();
        updateOnedriveAcct();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        PasswdSafeLog.debug(TAG, "onResume");
        updateOnedriveAcct();
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

        boolean isReauth = checkAccount(AcctCheck.PRESENT);
        itsNewAcctCb = new AcquireTokenCallback(isReauth);

        var params = SignInParameters
                .builder()
                .withActivity(activity)
                .withCallback(itsNewAcctCb)
                .withScopes(Arrays.asList(Constants.SCOPES))
                .build();
        if (isReauth) {
            itsClientApp.signInAgain(params);
        } else {
            itsClientApp.signIn(params);
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
        PasswdSafeLog.debug(TAG, "unlinkAccount");
        if (checkAccount(AcctCheck.PRESENT)) {
            itsClientApp.signOut(new SignOutCallback());
        } else {
            updateOnedriveAcct();
        }
        clearMigration(true);
    }

    /**
     * Is the account fully authorized
     */
    @Override
    @MainThread
    public boolean isAccountAuthorized()
    {
        return checkAccount(AcctCheck.AUTHORIZED);
    }

    /**
     * Get the account for the named provider
     */
    @Override
    @MainThread
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.ONEDRIVE_ACCOUNT_TYPE);
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
        }, null);
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
     * Use the OneDrive service with optional provided service for caching
     */
    @WorkerThread
    public void useOneDriveService(OneDriveUser user,
                                   GraphServiceClient service)
        throws Exception
    {
        if (SyncHelper.isOnUiThread()) {
            throw new Exception("Can't invoke getOnedriveService in ui thread");
        }

        if (!itsServiceLock.tryLock(15, TimeUnit.MINUTES)) {
            throw new Exception("Timeout waiting for OneDrive service");
        }

        try {
            try {
                useOneDriveServiceImpl(user, service);
            } catch (MsalUiRequiredException e) {
                PasswdSafeLog.error(TAG, e, "Auth token invalid");
                updateOnedriveAcct();
                throw e;
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
            SyncHelper.runOnUiThread(() -> {
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
            var tokenParams = createAcquireTokenParams(null);
            if (tokenParams == null) {
                throw new Exception(TAG + " useOneDriveService not authorized");
            }

            var auth = itsClientApp.acquireTokenSilent(tokenParams);

            var httpClientFactory = GraphClientFactory.create();
            if (VERBOSE_LOGS) {
                httpClientFactory.addInterceptor(new DebugHttpInterceptor());
            }
            var httpClient = httpClientFactory.build();
            service = new GraphServiceClient(
                    (request, extraAuthCtx) -> request.headers.add(
                            "Authorization", auth.getAuthorizationHeader()),
                    httpClient);
        }

        user.useOneDrive(service);
    }

    /**
     * Asynchronously update the OneDrive account client based on availability
     * of authentication information
     */
    private void updateOnedriveAcct()
    {
        if (itsClientApp == null) {
            return;
        }

        itsClientApp.getCurrentAccountAsync(new CurrentAccountCallback());
    }

    /**
     * Check whether an account exists and optionally is it authorized
     */
    private boolean checkAccount(AcctCheck check)
    {
        synchronized (itsAccountLock) {
            if ((itsAccount == null) || (itsClientApp == null)) {
                return false;
            }

            return switch (check) {
                case PRESENT -> true;
                case AUTHORIZED -> itsAccountTokenOk;
            };
        }
    }

    /**
     * Set the account to use for the client
     */
    @MainThread
    private void setAccount(IAccount newAccount)
    {
        boolean changed = false;
        String newAccountId;
        synchronized (itsAccountLock) {
            var acctId = getAccountId(itsAccount);
            newAccountId = getAccountId(newAccount);
            if (!TextUtils.equals(acctId, newAccountId)) {
                PasswdSafeLog.debug(TAG, "setAccount %s -> %s", acctId,
                                    newAccountId);
                itsAccount = newAccount;
                changed = true;
            }
        }

        if (changed) {
            if (newAccountId != null) {
                try {
                    updateProviderSyncFreq(newAccountId);
                } catch (Exception e) {
                    Log.e(TAG, "setAccount update provider failure", e);
                }
            } else {
                updateSyncFreq(null, 0);
            }
            SyncApp.get(getContext()).updateProviderState();
        }

        if (newAccountId != null) {
            var tokenParams =
                    createAcquireTokenParams(new AccountTokenCallback());
            if (tokenParams != null) {
                itsClientApp.acquireTokenSilentAsync(tokenParams);
            }
        } else {
            setAccountTokenOk(false);
        }
    }

    /**
     * Set whether the token is OK for the account
     */
    @MainThread
    private void setAccountTokenOk(boolean tokenOk)
    {
        boolean changed = false;
        synchronized (itsAccountLock) {
            if (tokenOk != itsAccountTokenOk) {
                PasswdSafeLog.debug(TAG, "setAccountTokenOk %b -> %b",
                                    itsAccountTokenOk, tokenOk);
                itsAccountTokenOk = tokenOk;
                changed = true;
            }
        }

        if (changed) {
            SyncApp.get(getContext()).updateProviderState();
            if (tokenOk) {
                clearMigration(false);
            }
        }
    }

    /**
     * Check whether a migration to reauthorize is needed
     */
    @MainThread
    private void checkMigration()
    {
        var ctx = getContext();
        if (checkMigrationPrefs(ctx) != null) {
            PasswdSafeLog.debug(TAG, "Migration needed");
            NotifUtils.showNotif(NotifUtils.Type.ONEDRIVE_MIGRATED, ctx);
            itsIsMigrationNeeded = true;
        }
    }

    /**
     * Remove the migration preferences and notification if needed
     */
    @MainThread
    private void clearMigration(boolean force)
    {
        if (!force && !itsIsMigrationNeeded) {
            return;
        }
        itsIsMigrationNeeded = false;
        var ctx = getContext();
        var prefs = checkMigrationPrefs(ctx);
        if (prefs != null) {
            PasswdSafeLog.debug(TAG, "Migration cleared");
            prefs
                    .edit()
                    .remove(PREF_OLD_USER_ID)
                    .remove(PREF_HOME_ACCT_ID)
                    .apply();
            NotifUtils.cancelNotif(NotifUtils.Type.ONEDRIVE_MIGRATED, ctx);
        }
    }

    /**
     * Check whether the migration preferences exist
     * @return The preferences if they exist; null otherwise
     */
    @Nullable
    @MainThread
    private SharedPreferences checkMigrationPrefs(Context ctx)
    {
        var prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (prefs.contains(PREF_OLD_USER_ID) ||
            prefs.contains(PREF_HOME_ACCT_ID)) {
            return prefs;
        }
        return null;
    }

    /**
     * Create the parameters to silently acquire the auth token
     */
    private @Nullable AcquireTokenSilentParameters createAcquireTokenParams(
            @Nullable SilentAuthenticationCallback authCb)
    {
        synchronized (itsAccountLock) {
            if ((itsAccount == null) || (itsClientApp == null)) {
                return null;
            }

            var builder = new AcquireTokenSilentParameters.Builder()
                    .withScopes(Arrays.asList(Constants.SCOPES))
                    .fromAuthority(itsClientApp
                                           .getConfiguration()
                                           .getDefaultAuthority()
                                           .getAuthorityURL()
                                           .toString())
                    .forAccount(itsAccount);
            if (authCb != null) {
                builder.withCallback(authCb);
            }

            return builder.build();
        }
    }

    /**
     * Get the identifier for an account
     */
    @Nullable
    private static String getAccountId(@Nullable IAccount acct)
    {
        if (acct instanceof com.microsoft.identity.client.Account) {
            var account = (com.microsoft.identity.client.Account)acct;
            return account.getHomeAccountId();
        }
        return (acct != null) ? acct.getId() : null;
    }

    /**
     * New OneDrive account task
     */
    private static class NewOneDriveTask
            extends NewAccountTask<OnedriveProvider>
    {
        private final @NonNull AcquireTokenCallback itsTokenCb;

        /**
         * Constructor
         */
        protected NewOneDriveTask(Uri currAcctUri,
                                  @NonNull AcquireTokenCallback tokenCb,
                                  OnedriveProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
            itsTokenCb = tokenCb;
        }

        @Override
        protected boolean doProviderUpdate(@NonNull OnedriveProvider provider)
                throws Exception
        {
            String acctId = null;
            IAuthenticationResult authResult = itsTokenCb.getResult();
            if (authResult != null) {
                acctId = getAccountId(authResult.getAccount());
            }

            itsNewAcct = acctId;
            provider.updateOnedriveAcct();

            // On a re-authorization, don't delete and recreate the provider
            return !itsTokenCb.isReauth();
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

            updateOnedriveAcct();
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
            updateOnedriveAcct();
        }
    }

    /**
     * Callback for acquiring the token for the client's current account
     */
    private class AccountTokenCallback implements SilentAuthenticationCallback
    {
        @Override
        public void onSuccess(IAuthenticationResult authenticationResult)
        {
            PasswdSafeLog.debug(TAG, "Acquire token success");
            finish(true);
        }

        @Override
        public void onError(MsalException e)
        {
            PasswdSafeLog.error(TAG, e, "Acquire token failed");
            finish(false);
        }

        @MainThread
        private void finish(boolean tokenOk)
        {
            try {
                setAccountTokenOk(tokenOk);
            } catch (Exception e) {
                PasswdSafeLog.error(TAG, e,
                                    "updateOnedriveAcct token finish failure");
            }
        }
    }

    /**
     * Callback for retrieving the client's current account
     */
    private class CurrentAccountCallback
            implements ISingleAccountPublicClientApplication.CurrentAccountCallback
    {
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

        @MainThread
        private void finish(IAccount newAccount)
        {
            try {
                setAccount(newAccount);
            } catch (Exception e) {
                PasswdSafeLog.error(TAG, e,
                                    "updateOnedriveAcct update finish failure");
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
