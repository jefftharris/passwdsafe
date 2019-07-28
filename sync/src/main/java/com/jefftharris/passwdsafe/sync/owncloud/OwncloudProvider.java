/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.Preferences;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncIOException;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.accounts.AccountTypeUtils;
import com.owncloud.android.lib.common.network.CertificateCombinedException;
import com.owncloud.android.lib.common.network.NetworkUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *  Implements a provider for the ownCloud service
 */
public class OwncloudProvider extends AbstractSyncTimerProvider
{
    private static final String PREF_AUTH_ACCOUNT = "owncloudAccount";
    private static final String PREF_CERT_ALIAS = "owncloudCertAlias";
    private static final String PREF_URL = "owncloudUrl";
    private static final String PREF_USE_HTTPS = "owncloudUseHttps";

    private static final String TAG = "OwncloudProvider";

    private String itsAccountName = null;
    private String itsUserName = null;
    private Uri itsUrl = null;
    private boolean itsIsSyncAuthError= false;

    /** Constructor */
    public OwncloudProvider(Context ctx)
    {
        super(ProviderType.OWNCLOUD, ctx, TAG);
    }

    @Override
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
        updateOwncloudAcct();

        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        int numNotify = prefs.getInt(Preferences.PREF_OWNCLOUD_SURVEY, 0);
        if (numNotify < 3) {
            NotifUtils.showNotif(NotifUtils.Type.OWNCLOUD_USAGE,
                                 "Select for a survey of users of ownCloud",
                                 getContext());
            prefs.edit()
                 .putInt(Preferences.PREF_OWNCLOUD_SURVEY, numNotify + 1)
                 .apply();
        }
    }

    // TODO: remove AccountChooserDlg?
    // TODO: remove edit dialog

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#startAccountLink(android.app.Activity, int)
     */
    @Override
    public void startAccountLink(FragmentActivity activity, int requestCode)
    {
        Intent intent = new Intent();
        intent.setClass(activity, OwncloudAccountActivity.class);
        activity.startActivity(intent);
    }

    @Override
    public NewAccountTask finishAccountLink(int activityRequestCode,
                                            int activityResult,
                                            Intent activityData,
                                            Uri providerAcctUri)
    {
        // TODO: finish account link
        String accountName = null;
        if ((activityResult == Activity.RESULT_OK) &&
            (activityData != null)) {
            Bundle b = activityData.getExtras();
            accountName = (b != null) ?
                    b.getString(AccountManager.KEY_ACCOUNT_NAME) : null;
            Log.i(TAG, "Selected account: " + accountName);
            if (TextUtils.isEmpty(accountName)) {
                accountName = null;
            }
        }

        saveAuthData(accountName, createUrlFromAccount(accountName, true));
        updateOwncloudAcct();

        if (accountName == null) {
            return null;
        }
        return new NewOwncloudTask(providerAcctUri, accountName, this);
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#unlinkAccount()
     */
    @Override
    public void unlinkAccount()
    {
        saveCertAlias(null, getContext());
        saveAuthData(null, null);
        updateOwncloudAcct();
        AccountManager acctMgr = AccountManager.get(getContext());
        acctMgr.invalidateAuthToken(
                SyncDb.OWNCLOUD_ACCOUNT_TYPE,
                AccountTypeUtils.getAuthTokenTypePass(
                        SyncDb.OWNCLOUD_ACCOUNT_TYPE));
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#isAccountAuthorized()
     */
    @Override
    public boolean isAccountAuthorized()
    {
        return (itsAccountName != null) && !itsIsSyncAuthError;
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#getAccount(java.lang.String)
     */
    @Override
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.OWNCLOUD_ACCOUNT_TYPE);
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#checkProviderAdd(android.database.sqlite.SQLiteDatabase)
     */
    @Override
    public void checkProviderAdd(SQLiteDatabase db) throws Exception
    {
        List<DbProvider> providers = SyncDb.getProviders(db);
        for (DbProvider provider: providers) {
            if (provider.itsType == ProviderType.OWNCLOUD) {
                throw new Exception("Only one ownCloud account allowed");
            }
        }
    }

    @Override
    public void cleanupOnDelete()
    {
        unlinkAccount();
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#requestSync(boolean)
     */
    @Override
    public void requestSync(boolean manual)
    {
        PasswdSafeUtil.dbginfo(TAG, "requestSync client: %b", itsAccountName);
        if (itsAccountName == null) {
            return;
        }
        doRequestSync(manual);
    }

    @Override
    public SyncConnectivityResult checkSyncConnectivity(Account acct)
            throws Exception
    {
        final ObjectHolder<SyncConnectivityResult> connResult =
                new ObjectHolder<>();
        useOwncloudService(client -> {
            String displayName;
            Context ctx = getContext();
            try {
                displayName = OwncloudSyncer.getDisplayName(client, ctx);
            } catch (SyncIOException e) {
                if (e.getCause() instanceof CertificateCombinedException) {
                    displayName = OwncloudSyncer.getDisplayName(client, ctx);
                } else {
                    throw e;
                }
            }
            connResult.set(new SyncConnectivityResult(displayName));
        });
        return connResult.get();
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#sync(android.accounts.Account, com.jefftharris.passwdsafe.sync.lib.DbProvider, android.database.sqlite.SQLiteDatabase, boolean, boolean, com.jefftharris.passwdsafe.sync.lib.SyncLogRecord)
     */
    @Override
    public void sync(Account acct,
                     final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec) throws Exception
    {
        useOwncloudService(client -> {
            PasswdSafeUtil.dbginfo(TAG, "sync client: %b", itsAccountName);
            OwncloudSyncer syncer = new OwncloudSyncer(
                    client, provider, connResult, logrec, getContext());
            try {
                syncer.sync();
            } catch (SyncIOException e) {
                if (e.isRetry()) {
                    requestSync(false);
                }
                throw e;
            } finally {
                itsIsSyncAuthError = !syncer.isAuthorized();
            }
        });
    }


    /** Create a ownCloud client to a server */
    public final OwnCloudClient getClient(Context ctx)
    {
        Account account = getAccount(itsAccountName);

        OwnCloudClient client = OwnCloudClientFactory.createOwnCloudClient(
                itsUrl, ctx, true);
        client.setFollowRedirects(true);

        client.clearCredentials();
        String authToken = getAuthToken(account, ctx, null);
        if (authToken != null) {
            client.setCredentials(
                    OwnCloudCredentialsFactory.newBasicCredentials(
                            itsUserName, authToken));
        }
        return client;
    }


    /** Get the ownCloud URL */
    public final Uri getUrl()
    {
        return itsUrl;
    }


    /** Set account settings */
    public final void setSettings(String url)
    {
        if (!TextUtils.equals(itsUrl.toString(), url)) {
            saveAuthData(itsAccountName, url);
            updateOwncloudAcct();
        }
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider#getAccountUserId()
     */
    @Override
    protected String getAccountUserId()
    {
        return itsAccountName;
    }

    /**
     * Interface for users of the ownCloud service
     */
    private interface OwncloudUser
    {
        /**
         * Callback to user the service
         */
        void useOwncloud(OwnCloudClient client) throws Exception;
    }

    /**
     * Use the ownCloud service
     */
    private void useOwncloudService(OwncloudUser user) throws Exception
    {
        if (itsAccountName == null) {
            return;
        }
        user.useOwncloud(getClient(getContext()));
        if (!isAccountAuthorized()) {
            SyncApp.get(getContext()).updateProviderState();
        }
    }

    /** Update the ownCloud account client based on availability of
     *  authentication information. */
    private synchronized void updateOwncloudAcct()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());

        itsAccountName = prefs.getString(PREF_AUTH_ACCOUNT, null);
        String urlStr = prefs.getString(PREF_URL, null);
        PasswdSafeUtil.dbginfo(TAG, "updateOwncloudAcct token %b, url %s",
                               itsAccountName, urlStr);

        String userName = null;
        Uri url = null;

        if (itsAccountName != null) {
            int pos = itsAccountName.indexOf('@');
            if (pos != -1) {
                userName = itsAccountName.substring(0, pos);

                // Check upgrade
                if ((urlStr == null) && prefs.contains(PREF_USE_HTTPS)) {
                    boolean useHttps = prefs.getBoolean(PREF_USE_HTTPS, true);
                    urlStr = createUrlFromAccount(itsAccountName, useHttps);

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.remove(PREF_USE_HTTPS);
                    editor.putString(PREF_URL, urlStr);
                    editor.apply();
                }

                if (urlStr != null) {
                    url = Uri.parse(urlStr);
                }
            } else {
                itsAccountName = null;
            }
        }

        itsUserName = userName;
        itsUrl = url;
        if (itsUrl != null) {
            try {
                updateProviderSyncFreq(itsAccountName);
                requestSync(false);
            } catch (Exception e) {
                Log.e(TAG, "updateOwncloudAcct failure", e);
            }
        } else {
            updateSyncFreq(null, 0);
        }
    }

    /**
     * Create the ownCloud URL from the account name
     */
    private static String createUrlFromAccount(String accountName,
                                               boolean useHttps)
    {
        if (accountName == null) {
            return null;
        }
        int pos = accountName.indexOf('@');
        if (pos == -1) {
            return null;
        }
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(useHttps ? "https" : "http");
        builder.authority(accountName.substring(pos + 1));
        builder.path("/owncloud");
        return builder.build().toString();
    }

    /** Save or clear the ownCloud authentication data */
    private void saveAuthData(String accountName, String url)
    {
        synchronized (OwncloudProvider.class) {
            PasswdSafeUtil.dbginfo(TAG, "saveAuthData: %b", accountName);
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor editor = prefs.edit();
            if (accountName != null) {
                editor.putString(PREF_AUTH_ACCOUNT, accountName);
                editor.putString(PREF_URL, url);
            } else {
                editor.remove(PREF_AUTH_ACCOUNT);
                editor.remove(PREF_URL);
                editor.remove(PREF_USE_HTTPS);
            }
            editor.apply();
        }
    }


    /** Save or clear the ownCloud SSL certificate */
    public static void saveCertAlias(String certAlias, Context ctx)
    {
        synchronized (OwncloudProvider.class) {
            PasswdSafeUtil.dbginfo(TAG, "saveCertAlias: %s", certAlias);
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(ctx);
            SharedPreferences.Editor editor = prefs.edit();
            if (certAlias != null) {
                editor.putString(PREF_CERT_ALIAS, certAlias);
            } else {
                String currAlias = prefs.getString(PREF_CERT_ALIAS, null);
                if (currAlias != null) {
                    try {
                        NetworkUtils.removeCertFromKnownServersStore(currAlias,
                                                                     ctx);
                    } catch (Exception e) {
                        Log.e(TAG, "Error clearing certificate: " + currAlias,
                              e);
                    }
                }
                editor.remove(PREF_CERT_ALIAS);
            }
            editor.apply();
        }
    }


    /**
     * Get the ownCloud authentication for an account. A notification may be
     * presented if authorization is required. Must be called from a background
     * thread.
     */
    @SuppressWarnings("deprecation")
    private static String getAuthToken(Account account,
                                       Context ctx,
                                       Activity activity)
    {
        String authToken = null;
        try {
            AccountManager acctMgr = AccountManager.get(ctx);
            String authType = AccountTypeUtils.getAuthTokenTypePass(
                    SyncDb.OWNCLOUD_ACCOUNT_TYPE);
            AccountManagerFuture<Bundle> fut;
            if ((activity != null) &&
                    ApiCompat.canAccountMgrGetAuthTokenWithDialog()) {
                fut = acctMgr.getAuthToken(account, authType, null,
                                           activity, null, null);
            } else {
                fut = acctMgr.getAuthToken(account, authType, true, null, null);
            }
            Bundle b = fut.getResult(60, TimeUnit.SECONDS);
            authToken = b.getString(AccountManager.KEY_AUTHTOKEN);
        } catch (Throwable e) {
            PasswdSafeUtil.dbginfo(TAG, e, "getAuthToken");
        }

        PasswdSafeUtil.dbginfo(TAG, "getAuthToken: %b", (authToken != null));
        return authToken;
    }

    /**
     * New ownCloud account task
     */
    private static class NewOwncloudTask
            extends NewAccountTask<OwncloudProvider>
    {
        /**
         * Constructor
         */
        protected NewOwncloudTask(Uri currAcctUri,
                                  String newAcct,
                                  OwncloudProvider provider)
        {
            super(currAcctUri, newAcct, provider, true, provider.getContext(),
                  TAG);
        }

        @Override
        protected boolean doProviderUpdate(@NonNull OwncloudProvider provider)
        {
            Activity act = getActivity();
            if (act == null) {
                return false;
            }
            String authToken = getAuthToken(provider.getAccount(itsNewAcct),
                                            act, act);
            return (authToken != null);
        }
    }
}
