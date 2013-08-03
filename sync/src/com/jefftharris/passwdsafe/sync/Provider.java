/*
 * Copyright (©) 2013 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.accounts.Account;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.jefftharris.passwdsafe.lib.ProviderType;

/**
 * The Provider interface encapsulates a service that provides files which are
 * synchronized
 */
public abstract class Provider
{
    /** Get the account for the named provider */
    public abstract Account getAccount(String acctName);

    /** Cleanup a provider when deleted */
    public abstract void cleanupOnDelete(String acctName);

    /** Sync a provider */
    public abstract void sync(Account acct, SyncDb.DbProvider provider,
                              SQLiteDatabase db,
              boolean manual, SyncLogRecord logrec) throws Exception;

    /** Get the provider implementation for the type */
    public static Provider getProvider(ProviderType type, Context ctx)
    {
        switch (type) {
        case GDRIVE: {
            return new GDriveProvider(ctx);
        }
        case DROPBOX: {
            return new DropboxProvider(ctx);
        }
        }
        return null;
    }
}
