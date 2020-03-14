/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.gdrive;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.UserRecoverableNotifiedException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.ActivityRequest;
import com.jefftharris.passwdsafe.sync.R;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.SyncUpdateHandler;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;

import java.io.IOException;
import java.util.Collections;

/**
 * The GDriveProvider class encapsulates Google Drive
 */
public class GDriveProvider extends AbstractSyncTimerProvider
{
    public static final String ABOUT_FIELDS = "user";
    public static final String FILE_FIELDS =
            "id,name,mimeType,trashed,fileExtension,modifiedTime," +
            "md5Checksum,parents";
    public static final String FOLDER_MIME =
            "application/vnd.google-apps.folder";

    private static final String PREF_ACCOUNT_NAME = "gdriveAccountName";
    private static final String PREF_MIGRATION = "gdriveMigration";

    private static final int MIGRATION_V3API = 1;
    private static final int MIGRATION_SERVICE = 2;

    private static final String TAG = "GDriveProvider";

    private String itsAccountName;
    private String itsPendingAccountName;

    /** Constructor */
    public GDriveProvider(Context ctx)
    {
        super(ProviderType.GDRIVE, ctx, TAG);
    }


    @Override
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
        int migration = checkMigration();
        updateAcct();
        checkMigrationWithAcct(migration);
    }


    @Override
    public void startAccountLink(FragmentActivity activity, int requestCode)
    {
        Account selAccount = null;
        boolean alwaysPrompt = true;
        if (hasAcctName()) {
            // Reauthorization, select the current account
            NotifUtils.cancelNotif(NotifUtils.Type.DRIVE_REAUTH_REQUIRED,
                                   getContext());
            selAccount = new Account(itsAccountName,
                                     GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
            alwaysPrompt = false;
        }

        itsPendingAccountName = null;
        Intent intent = AccountPicker.newChooseAccountIntent(
                selAccount, null,
                new String[] { GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE },
                alwaysPrompt, null, null, null, null);
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            String msg = getContext().getString(
                    R.string.google_acct_not_available);
            PasswdSafeUtil.showError(msg, TAG, e, new ActContext(activity));
        }
    }

    @Override
    public NewAccountTask finishAccountLink(FragmentActivity activity,
                                            int activityRequestCode,
                                            int activityResult,
                                            Intent activityData,
                                            Uri acctProviderUri)
    {
        if (activityResult != Activity.RESULT_OK) {
            return null;
        }

        switch (activityRequestCode) {
        case ActivityRequest.GDRIVE_PLAY_LINK: {
            if (activityData == null) {
                return null;
            }
            Bundle b = activityData.getExtras();
            String accountName = (b != null) ?
                    b.getString(AccountManager.KEY_ACCOUNT_NAME) : null;
            Log.i(TAG, "Selected initial account: " + accountName);
            if (TextUtils.isEmpty(accountName)) {
                return null;
            }

            itsPendingAccountName = accountName;

            try {
                Intent intent = getSigninClient(accountName, activity)
                        .getSignInIntent();
                activity.startActivityForResult(
                        intent, ActivityRequest.GDRIVE_PLAY_LINK_PERMS);
            } catch (ActivityNotFoundException e) {
                String msg = getContext().getString(
                        R.string.google_acct_not_available);
                PasswdSafeUtil.showError(msg, TAG, e, new ActContext(activity));
            }
            return null;
        }
        case ActivityRequest.GDRIVE_PLAY_LINK_PERMS: {
            String accountName = itsPendingAccountName;
            itsPendingAccountName = null;
            Log.i(TAG, "Selected account: " + accountName);
            if (TextUtils.isEmpty(accountName)) {
                return null;
            }

            // Re-authorization, don't trigger a new account
            if (TextUtils.equals(accountName, itsAccountName)) {
                requestSync(false);
                return null;
            }

            setAcctName(accountName);
            updateAcct();
            return new NewAccountTask<>(acctProviderUri, accountName,
                                        this, false, getContext(), TAG);
        }
        }

        return null;
    }

    @Override
    public void unlinkAccount()
    {
        Account acct = getAccount(itsAccountName);
        setAcctName(null);
        updateAcct();
        if (acct != null) {
            try {
                Context ctx = getContext();
                GoogleAccountCredential credential = getAcctCredential(ctx);
                String token = GoogleAuthUtil.getTokenWithNotification(
                        ctx, acct, credential.getScope(), null);
                PasswdSafeUtil.dbginfo(TAG, "Remove token for %s", acct.name);
                if (token != null) {
                    GoogleAuthUtil.clearToken(ctx, token);
                }

                GoogleSignInClient client = getSigninClient(acct.name, ctx);
                Task<Void> task = client.revokeAccess();
                task.addOnCompleteListener(
                        task1 -> PasswdSafeUtil.dbginfo(TAG,
                                                        "Revoke complete"));

            } catch (Exception e) {
                PasswdSafeUtil.dbginfo(TAG, e, "No auth token for %s",
                                       acct.name);
            }
        }
    }

    @Override
    public synchronized boolean isAccountAuthorized()
    {
        return hasAcctName() && (getAccount(itsAccountName) != null);
    }

    @Override
    public Account getAccount(String acctName)
    {
        GoogleAccountManager acctMgr = new GoogleAccountManager(getContext());
        return acctMgr.getAccountByName(acctName);
    }

    @Override
    public void checkProviderAdd(SQLiteDatabase db)
    {
    }

    @Override
    public void cleanupOnDelete()
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }

    @Override
    protected String getAccountUserId()
    {
        return itsAccountName;
    }

    @Override
    public synchronized void requestSync(boolean manual)
    {
        boolean authorized = isAccountAuthorized();
        PasswdSafeUtil.dbginfo(TAG, "requestSync authorized %b, manual %b",
                               authorized, manual);
        if (authorized) {
            doRequestSync(manual);
        } else {
            checkReauthRequired();
        }
    }

    @Override
    public SyncConnectivityResult checkSyncConnectivity(Account acct)
            throws Exception
    {
        final ObjectHolder<SyncConnectivityResult> connResult =
                new ObjectHolder<>();
        useDriveService(acct, drive -> {
            String displayName = GDriveSyncer.getDisplayName(drive);
            connResult.set(new SyncConnectivityResult(displayName));
            return SyncUpdateHandler.GDriveState.OK;
        });
        return connResult.get();
    }

    @Override
    public void sync(Account acct, final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec) throws Exception
    {
        useDriveService(acct, drive -> {
            GDriveSyncer sync = new GDriveSyncer(
                    drive, provider, connResult, logrec, getContext());
            sync.sync();
            return sync.getSyncState();
        });
    }

    /**
     * Interface for users of the Drive service
     */
    private interface DriveUser
    {
        /**
         * Callback to use the drive
         */
        SyncUpdateHandler.GDriveState useDrive(Drive drive) throws Exception;
    }

    /**
     * Use the drive service
     */
    private void useDriveService(Account acct, DriveUser user) throws Exception
    {
        Context ctx = getContext();
        Pair<Drive, String> driveService = getDriveService(acct, ctx);
        SyncUpdateHandler.GDriveState syncState =
                SyncUpdateHandler.GDriveState.OK;
        try {
            if (driveService.first != null) {
                syncState = user.useDrive(driveService.first);
            } else {
                syncState = SyncUpdateHandler.GDriveState.AUTH_REQUIRED;
            }
        } catch (UserRecoverableAuthIOException e) {
            PasswdSafeUtil.dbginfo(TAG, e, "Recoverable google auth error");
            GoogleAuthUtil.clearToken(ctx, driveService.second);
            syncState = SyncUpdateHandler.GDriveState.AUTH_REQUIRED;
        } catch (GoogleAuthIOException e) {
            Log.e(TAG, "Google auth error", e);
            GoogleAuthUtil.clearToken(ctx, driveService.second);
            throw e;
        } finally {
            SyncApp.get(ctx).updateGDriveSyncState(syncState);
        }
    }

    /**
     * Set the account name
     */
    private synchronized void setAcctName(String acctName)
    {
        PasswdSafeUtil.dbginfo(TAG, "setAcctName %s", acctName);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.edit().putString(PREF_ACCOUNT_NAME, acctName).apply();
    }

    /**
     * Is an account name set
     */
    private synchronized boolean hasAcctName()
    {
        return !TextUtils.isEmpty(itsAccountName);
    }

    /**
     * Update the account from saved authentication information
     */
    private synchronized void updateAcct()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        itsAccountName = prefs.getString(PREF_ACCOUNT_NAME, null);
        if (isAccountAuthorized()) {
            try {
                updateProviderSyncFreq(itsAccountName);
            } catch (Exception e) {
                Log.e(TAG, "updateAcct failure", e);
            }
        } else if (!checkReauthRequired()) {
            updateSyncFreq(null, 0);
        } else {
            requestSync(false);
        }
    }

    /**
     * Check for whether an account has been selected but is no longer returned
     * by the system.  If so, request a reauthorization.
     */
    private boolean checkReauthRequired()
    {
        if (!hasAcctName()) {
            return false;
        }
        Context ctx = getContext();
        NotifUtils.showNotif(NotifUtils.Type.DRIVE_REAUTH_REQUIRED, ctx);
        SyncApp.get(ctx).updateGDriveSyncState(
                SyncUpdateHandler.GDriveState.AUTH_REQUIRED);
        return true;
    }

    /**
     * Check whether any migrations are needed
     */
    private int checkMigration()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        int migration = prefs.getInt(PREF_MIGRATION, 0);

        if (migration < MIGRATION_V3API) {
            // Set the account name from the db provider
            try {
                SyncDb.useDb((SyncDb.DbUser<Void>)db -> {
                    for (DbProvider provider: SyncDb.getProviders(db)) {
                        if (provider.itsType == ProviderType.GDRIVE) {
                            setAcctName(provider.itsAcct);
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                Log.e(TAG, "Error migrating account", e);
            }

            prefs.edit().putInt(PREF_MIGRATION, MIGRATION_V3API).apply();
        }

        return migration;
    }

    /**
     * Check whether any migrations are needed after the account is available
     */
    private void checkMigrationWithAcct(int migration)
    {
        if (migration < MIGRATION_SERVICE) {
            // If there was a previous account, remove the automatic sync
            // from the sync service
            if (hasAcctName()) {
                try {
                    Account acct = new Account(
                            itsAccountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
                    ContentResolver.removePeriodicSync(
                            acct, PasswdSafeContract.AUTHORITY, new Bundle());
                    ContentResolver.setSyncAutomatically(
                            acct, PasswdSafeContract.AUTHORITY, false);
                } catch (Exception e) {
                    Log.e(TAG, "Error removing sync service", e);
                }
            }

            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putInt(PREF_MIGRATION, MIGRATION_SERVICE).apply();
        }
    }

    /**
     * Get the sign-in client
     */
    private static GoogleSignInClient getSigninClient(String accountName,
                                                      Context ctx)
    {
        GoogleSignInOptions.Builder builder =
                new GoogleSignInOptions.Builder();
        builder.requestScopes(new Scope(DriveScopes.DRIVE));
        builder.setAccountName(accountName);
        return GoogleSignIn.getClient(ctx, builder.build());
    }

    /** Get the Google account credential */
    private static GoogleAccountCredential getAcctCredential(Context ctx)
    {
        return GoogleAccountCredential.usingOAuth2(
                ctx.getApplicationContext(),
                Collections.singletonList(DriveScopes.DRIVE));
    }

    /**
     * Retrieve a authorized service object to send requests to the Google
     * Drive API. On failure to retrieve an access token, a notification is
     * sent to the user requesting that authorization be granted for the
     * {@code https://www.googleapis.com/auth/drive} scope.
     *
     * @return An authorized service object and its auth token.
     */
    private static Pair<Drive, String> getDriveService(Account acct,
                                                       Context ctx)
    {
        Drive drive = null;
        String token = null;
        try {
            GoogleAccountCredential credential = getAcctCredential(ctx);
            credential.setBackOff(new ExponentialBackOff());
            credential.setSelectedAccountName(acct.name);

            token = GoogleAuthUtil.getToken(ctx, acct, credential.getScope());

            Drive.Builder builder = new Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    JacksonFactory.getDefaultInstance(), credential);
            builder.setApplicationName(ctx.getString(R.string.app_name));
            drive = builder.build();
        } catch (UserRecoverableAuthException e) {
            PasswdSafeUtil.dbginfo(TAG, e, "Recoverable auth exception");
            NotifUtils.showNotif(NotifUtils.Type.DRIVE_REAUTH_REQUIRED, ctx);
            try {
                GoogleAuthUtil.clearToken(ctx, null);
            } catch (Exception ioe) {
                Log.e(TAG, "getDriveService clear failure", e);
            }
        } catch (UserRecoverableNotifiedException e) {
            // User notified
            PasswdSafeUtil.dbginfo(TAG, e, "User notified auth exception");
            try {
                GoogleAuthUtil.clearToken(ctx, null);
            } catch(Exception ioe) {
                Log.e(TAG, "getDriveService clear failure", e);
            }
        } catch (GoogleAuthException e) {
            // Unrecoverable
            Log.e(TAG, "Unrecoverable auth exception", e);
        }
        catch (IOException e) {
            // Transient
            PasswdSafeUtil.dbginfo(TAG, e, "Transient error");
        } catch (Exception e) {
            Log.e(TAG, "Token exception", e);
        }
        return new Pair<>(drive, token);
    }
}
