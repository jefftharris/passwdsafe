/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.dropbox;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.android.AuthActivity;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.oauth.DbxOAuthException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.BuildConfig;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  The DropboxCoreProvider class encapsulates Dropbox
 */
public class DropboxCoreProvider extends AbstractSyncTimerProvider
{
    private static final String DROPBOX_SYNC_APP_KEY =
            BuildConfig.DROPBOX_SYNC_APP_KEY;
    private static final String PREF_MIGRATE_TOKEN = "dropboxMigrateToken";
    private static final String PREF_OAUTH2_TOKEN = "dropboxOAuth2Token";
    private static final String PREF_OATH_KEY = "dropboxOAuthKey";
    private static final String PREF_OATH_SECRET = "dropboxOAuthSecret";
    private static final String PREF_PKCE_TOKEN = "dropboxPkceToken";
    private static final String PREF_USER_ID = "dropboxUserId";

    private static final DbxRequestConfig REQUEST_CONFIG =
            new DbxRequestConfig("PasswdSafe");

    private static final String TAG = "DropboxCoreProvider";

    private DbxClientV2 itsClient;
    private DbxCredential itsClientCred;
    private String itsUserId = null;
    private final ArrayList<TokenRevokeTask> itsRevokeTasks = new ArrayList<>();

    /** Constructor */
    public DropboxCoreProvider(Context ctx)
    {
        super(ProviderType.DROPBOX, ctx, TAG);
    }


    @Override
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
        doMigration();
        updateDropboxAcct();
    }

    @Override
    public void fini()
    {
        super.fini();
        for (TokenRevokeTask task: itsRevokeTasks) {
            task.cancel(true);
        }
        itsRevokeTasks.clear();
    }

    @Override
    protected String getAccountUserId()
    {
        return itsUserId;
    }


    @Override
    public void startAccountLink(FragmentActivity activity, int requestCode)
    {
        if (isAccountAuthorized()) {
            unlinkAccount();
        }
        AuthActivity.result = null;
        Auth.startOAuth2PKCE(activity, DROPBOX_SYNC_APP_KEY,
                             REQUEST_CONFIG,
                             Arrays.asList("account_info.read",
                                           "files.metadata.write",
                                           "files.content.read",
                                           "files.content.write",
                                           "file_requests.write"));
    }


    @Nullable
    @Override
    public NewAccountTask<? extends AbstractSyncTimerProvider>
    finishAccountLink(FragmentActivity activity,
                      int activityRequestCode,
                      int activityResult,
                      Intent activityData,
                      Uri providerAcctUri)
    {
        var cred = Auth.getDbxCredential();
        if (cred == null) {
            PasswdSafeUtil.dbginfo(TAG, "finishAccountLink auth failed");
            return null;
        }
        saveAuthData(cred);
        updateDropboxAcct();

        // If user already exists, this is a re-authorization so don't trigger
        // a new account task
        if (itsUserId != null) {
            return null;
        }

        return new NewDropboxTask(providerAcctUri, this);
    }


    @Override
    public void unlinkAccount()
    {
        if (itsClient != null) {
            TokenRevokeTask task = new TokenRevokeTask(this);
            task.execute(itsClient);
            itsRevokeTasks.add(task);
        }
        saveAuthData(null);
        setUserId(null);
        updateDropboxAcct();
    }


    @Override
    public boolean isAccountAuthorized()
    {
        return (itsClient != null);
    }


    @Override
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.DROPBOX_ACCOUNT_TYPE);
    }


    @Override
    public void cleanupOnDelete()
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }


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
        useDropboxService(() -> {
            String displayName = DropboxCoreSyncer.getDisplayName(itsClient);
            connResult.set(new SyncConnectivityResult(displayName));
        });
        return connResult.get();
    }

    @Override
    public void sync(Account acct,
                     final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec) throws Exception
    {
        useDropboxService(
                () -> new DropboxCoreSyncer(itsClient, provider, connResult,
                                            logrec, getContext()).sync());
    }

    /** List files */
    public List<ProviderRemoteFile> listFiles(String path)
            throws Exception
    {
        List<ProviderRemoteFile> files = new ArrayList<>();
        useDropboxService(() -> {
            ListFolderResult result = itsClient.files().listFolder(path);
            do {
                for (Metadata child : result.getEntries()) {
                    files.add(new DropboxCoreProviderFile(child));
                }

                if (result.getHasMore()) {
                    result = itsClient
                            .files()
                            .listFolderContinue(result.getCursor());
                } else {
                    result = null;
                }
            } while (result != null);
        });

        return files;
    }

    /**
     * Interface for users of Dropbox
     */
    private interface DropboxUser
    {
        /**
         * Callback to use the client
         */
        void useDropbox() throws Exception;
    }

    /**
     * Use the Dropbox service
     */
    private void useDropboxService(DropboxUser user) throws Exception
    {
        boolean authorized = false;
        try {
            authorized = isAccountAuthorized();
            PasswdSafeUtil.dbginfo(TAG, "account authorized: %b", authorized);
            if (authorized) {
                synchronized (this) {
                    if ((itsClientCred != null) &&
                        itsClientCred.aboutToExpire()) {
                        PasswdSafeUtil.dbginfo(TAG, "refreshing cred");
                        itsClientCred.refresh(REQUEST_CONFIG);
                        saveAuthData(itsClientCred);
                    }
                }

                user.useDropbox();
            }
        } catch (InvalidAccessTokenException | DbxOAuthException e) {
            Log.e(TAG, "unlinked error", e);
            saveAuthData(null);
            updateDropboxAcct();
            throw e;
        } finally {
            if (authorized && !isAccountAuthorized()) {
                SyncApp.get(getContext()).updateProviderState();
            }
        }
    }

    /** Update the Dropbox account client based on availability of
     *  authentication information */
    private synchronized void updateDropboxAcct()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());

        boolean haveAuth = false;

        String credStr = prefs.getString(PREF_PKCE_TOKEN, null);
        if (credStr != null) {
            try {
                final var cred = DbxCredential.Reader.readFully(credStr);

                if (cred != null) {
                    PasswdSafeUtil.dbginfo(TAG, "PKCE auth config");
                    itsClient = new DbxClientV2(REQUEST_CONFIG, cred);
                    itsClientCred = cred;
                    haveAuth = true;
                }
            } catch (JsonReadException e) {
                PasswdSafeLog.error(TAG, e, "Credential read failure");
                saveAuthData(null);
            }
        }

        if (!haveAuth) {
            String authToken = prefs.getString(PREF_OAUTH2_TOKEN, null);
            if (authToken != null) {
                PasswdSafeUtil.dbginfo(TAG, "oauth2 auth config");
                itsClient = new DbxClientV2(REQUEST_CONFIG, authToken);
                haveAuth = true;
            }
        }

        if (haveAuth) {
            itsUserId = prefs.getString(PREF_USER_ID, null);
            if (itsUserId != null) {
                try {
                    updateProviderSyncFreq(itsUserId);
                } catch (Exception e) {
                    Log.e(TAG, "updateDropboxAcct failure", e);
                }
            } else {
                requestSync(false);
            }
        } else {
            itsUserId = null;
            updateSyncFreq(null, 0);
            itsClient = null;
            itsClientCred = null;
        }

        PasswdSafeUtil.dbginfo(TAG, "init auth %b", isAccountAuthorized());
    }


    /** Update the account's user ID */
    private synchronized void setUserId(String user)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateUserId: %s", user);
        itsUserId = user;

        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_USER_ID, itsUserId);
        editor.apply();
    }


    /** Save or clear authentication data */
    private synchronized void saveAuthData(DbxCredential cred)
    {
        PasswdSafeUtil.dbginfo(TAG, "saveAuthData: %b", cred);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = prefs.edit();
        if (cred != null) {
            editor.putString(PREF_PKCE_TOKEN,
                             DbxCredential.Writer.writeToString(cred));
        } else {
            editor.remove(PREF_PKCE_TOKEN);
        }
        editor.remove(PREF_OAUTH2_TOKEN);
        editor.remove(PREF_OATH_KEY);
        editor.remove(PREF_OATH_SECRET);
        editor.apply();
    }


    /** Migrate from previous Dropbox */
    private void doMigration()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());

        boolean migrate = prefs.getBoolean(PREF_MIGRATE_TOKEN, true);
        if (migrate) {
            PasswdSafeUtil.dbginfo(TAG, "doMigration");
            SharedPreferences.Editor editor = prefs.edit();
            boolean didMigrate = false;

            try {
                Context appctx = getContext().getApplicationContext();
                String accts =
                        appctx.getSharedPreferences("dropbox-credentials",
                                                    Context.MODE_PRIVATE)
                              .getString("accounts", null);
                if (accts != null) {
                    JSONArray jsonAccounts = new JSONArray(accts);
                    if (jsonAccounts.length() > 0) {
                        JSONObject acct = jsonAccounts.getJSONObject(0);
                        String userId = acct.getString("userId");
                        PasswdSafeUtil.dbginfo(TAG, "migrate user: %s", userId);
                        editor.putString(PREF_USER_ID, userId);
                        didMigrate = true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error migrating token", e);
            }

            editor.putBoolean(PREF_MIGRATE_TOKEN, false);
            editor.apply();

            try {
                didMigrate |= SyncDb.useDb(db -> {
                    boolean dbmigrate = false;
                    for (DbProvider provider: SyncDb.getProviders(db)) {
                        if (provider.itsType != ProviderType.DROPBOX) {
                            continue;
                        }

                        dbmigrate = true;
                        String dirpfx = "/Apps/PasswdSafe Sync";
                        for (DbFile dbfile:
                                SyncDb.getFiles(provider.itsId, db)) {
                            SyncDb.updateRemoteFile(
                                    dbfile.itsId,
                                    (dirpfx + dbfile.itsRemoteId).toLowerCase(),
                                    dbfile.itsRemoteTitle,
                                    dirpfx + dbfile.itsRemoteFolder,
                                    dbfile.itsRemoteModDate,
                                    dbfile.itsRemoteHash,
                                    db);
                        }
                    }
                    return dbmigrate;
                });
            } catch (Exception e) {
                Log.e(TAG, "Error migrating files", e);
            }

            if (didMigrate) {
                NotifUtils.showNotif(NotifUtils.Type.DROPBOX_MIGRATED,
                                     getContext());
            }
        }
    }

    /**
     * New Dropbox account task
     */
    private static class NewDropboxTask
            extends NewAccountTask<DropboxCoreProvider>
    {
        /**
         * Constructor
         */
        protected NewDropboxTask(Uri currAcctUri, DropboxCoreProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
        }

        @Override
        protected boolean doProviderUpdate(
                @NonNull DropboxCoreProvider provider)
                throws DbxException
        {
            FullAccount acct = provider.itsClient.users().getCurrentAccount();
            itsNewAcct = acct.getAccountId();
            provider.setUserId(itsNewAcct);
            return true;
        }
    }

    /**
     * Background task to revoke a token
     */
    private static class TokenRevokeTask
            extends AsyncTask<DbxClientV2, Void, Void>
    {
        private final ManagedRef<DropboxCoreProvider> itsProvider;

        /**
         * Constructor
         */
        protected TokenRevokeTask(DropboxCoreProvider provider)
        {
            itsProvider = new ManagedRef<>(provider);
        }

        @Nullable
        @Override
        protected Void doInBackground(@NonNull DbxClientV2... clients)
        {
            PasswdSafeUtil.dbginfo(TAG, "revoking auth tokens");
            for (DbxClientV2 client: clients) {
                try {
                    client.auth().tokenRevoke();
                } catch (DbxException e) {
                    Log.e(TAG, "Error revoking auth token", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid)
        {
            DropboxCoreProvider provider = itsProvider.get();
            if (provider != null) {
                provider.itsRevokeTasks.remove(this);
            }
        }
    }
}
