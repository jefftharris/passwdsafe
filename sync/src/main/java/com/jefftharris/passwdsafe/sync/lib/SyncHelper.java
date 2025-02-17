/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;

import org.jetbrains.annotations.Contract;

/**
 * The SyncHelper class contains some helper methods for performing a sync.
 */
public class SyncHelper
{
    private static final Handler itsUIHandler =
            new Handler(Looper.getMainLooper());

    private static final String TAG = "SyncHelper";

    /** Get the filename for a local file */
    @NonNull
    @Contract(pure = true)
    public static String getLocalFileName(long fileId)
    {
        return "syncfile-" + fileId;
    }


    /** Get the DB provider for an account */
    @Nullable
    public static DbProvider getDbProviderForAcct(@NonNull Account acct,
                                                  SQLiteDatabase db)
    {
        ProviderType providerType;
        switch (acct.type) {
        case SyncDb.GDRIVE_ACCOUNT_TYPE: {
            providerType = ProviderType.GDRIVE;
            break;
        }
        case SyncDb.DROPBOX_ACCOUNT_TYPE: {
            providerType = ProviderType.DROPBOX;
            break;
        }
        case SyncDb.BOX_ACCOUNT_TYPE: {
            providerType = ProviderType.BOX;
            break;
        }
        case SyncDb.ONEDRIVE_ACCOUNT_TYPE: {
            providerType = ProviderType.ONEDRIVE;
            break;
        }
        case SyncDb.OWNCLOUD_ACCOUNT_TYPE: {
            providerType = ProviderType.OWNCLOUD;
            break;
        }
        default: {
            PasswdSafeUtil.dbginfo(TAG, "Unknown account type: %s",
                                   acct.type);
            return null;
        }
        }
        DbProvider provider = SyncDb.getProvider(acct.name, providerType, db);
        if (provider == null) {
            PasswdSafeUtil.dbginfo(TAG, "No provider for %s", acct.name);
        }
        return provider;
    }

    /**
     * Run the given task on the UI thread
     */
    public static void runOnUiThread(Runnable run)
    {
        itsUIHandler.post(run);
    }

    /**
     * Is the caller running on the UI thread
     */
    public static boolean isOnUiThread()
    {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
