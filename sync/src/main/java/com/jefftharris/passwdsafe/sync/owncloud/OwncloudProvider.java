/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.Preferences;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;

/**
 *  Implements a provider for the ownCloud service
 */
public class OwncloudProvider extends AbstractSyncTimerProvider
{
    private static final String PREF_AUTH_ACCOUNT = "owncloudAccount";

    private static final String TAG = "OwncloudProvider";

    private String itsAccountName = null;

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

        if (dbProvider != null) {
            SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
            int numNotify = prefs.getInt(Preferences.PREF_OWNCLOUD_DISABLED, 0);
            if (numNotify < 3) {
                NotifUtils.showNotif(NotifUtils.Type.OWNCLOUD_DISABLED,
                                     getContext());
            }

        }
    }

    @Override
    public void startAccountLink(FragmentActivity activity, int requestCode)
    {
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
        return null;
    }

    @Override
    public void unlinkAccount()
    {
        clearAuthData();
        updateOwncloudAcct();
        AccountManager acctMgr = AccountManager.get(getContext());
        acctMgr.invalidateAuthToken(
                SyncDb.OWNCLOUD_ACCOUNT_TYPE,
                // From old AccountTypeUtils.getAuthTokenTypePass...
                SyncDb.OWNCLOUD_ACCOUNT_TYPE + ".password");
    }

    @Override
    public boolean isAccountAuthorized()
    {
        return false;
    }

    @Override
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.OWNCLOUD_ACCOUNT_TYPE);
    }

    @Override
    public void checkProviderAdd(SQLiteDatabase db) throws Exception
    {
        throw new Exception("New ownCloud accounts not allowed");
    }

    @Override
    public void cleanupOnDelete()
    {
        unlinkAccount();
    }

    @Override
    public void requestSync(boolean manual)
    {
    }

    @Nullable
    @Override
    public SyncConnectivityResult checkSyncConnectivity(Account acct)
    {
        return null;
    }

    @Override
    public void sync(Account acct,
                     final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec)
    {
    }

    @Override
    protected String getAccountUserId()
    {
        return itsAccountName;
    }

    /** Update the ownCloud account client based on availability of
     *  authentication information. */
    private synchronized void updateOwncloudAcct()
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());

        itsAccountName = prefs.getString(PREF_AUTH_ACCOUNT, null);
        PasswdSafeUtil.dbginfo(TAG, "updateOwncloudAcct token %b",
                               itsAccountName);
        updateSyncFreq(null, 0);
    }

    /** Save or clear the ownCloud authentication data */
    private void clearAuthData()
    {
        synchronized (OwncloudProvider.class) {
            PasswdSafeUtil.dbginfo(TAG, "clearAuthData");
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(PREF_AUTH_ACCOUNT);
            editor.apply();
        }
    }
}
