/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Provides common Provider operations for local file management
 */
public abstract class AbstractProvider implements Provider
{
    private final SyncResults itsSyncResults = new SyncResults();

    @Override
    @CallSuper
    public void init(@Nullable DbProvider dbProvider)
    {
        long syncLastSuccess = SyncResults.UNKNOWN;
        long syncLastFailure = SyncResults.UNKNOWN;
        if (dbProvider != null) {
            if (dbProvider.itsSyncLastSuccess != DbProvider.UNKNOWN_SYNC_TIME) {
                syncLastSuccess = dbProvider.itsSyncLastSuccess;
            }
            if (dbProvider.itsSyncLastFailure != DbProvider.UNKNOWN_SYNC_TIME) {
                syncLastFailure = dbProvider.itsSyncLastFailure;
            }
        }

        itsSyncResults.init(syncLastSuccess, syncLastFailure);
    }

    @Override
    @CallSuper
    public void updateSyncFreq(Account acct, int freq)
    {
        itsSyncResults.setSyncFreq(freq);
    }

    @Override
    public void setLastSyncResult(boolean success, long syncEndTime)
    {
        itsSyncResults.setResult(success, syncEndTime);
    }

    @Override
    public @NonNull SyncResults getSyncResults()
    {
        return itsSyncResults;
    }

    @Override
    public long insertLocalFile(long providerId,
                                String title,
                                SQLiteDatabase db)
            throws SQLException
    {
        long id = SyncDb.addLocalFile(providerId, title,
                                      System.currentTimeMillis(), db);
        requestSync(false);
        return id;
    }

    @Override
    public void updateLocalFile(DbFile file,
                                String localFileName,
                                File localFile,
                                SQLiteDatabase db)
    {
        SyncDb.updateLocalFile(file.itsId, localFileName,
                               file.itsLocalTitle, file.itsLocalFolder,
                               localFile.lastModified(), db);
        switch (file.itsLocalChange) {
        case NO_CHANGE:
        case REMOVED: {
            SyncDb.updateLocalFileChange(file.itsId, DbFile.FileChange.MODIFIED,
                                         db);
            break;
        }
        case ADDED:
        case MODIFIED: {
            break;
        }
        }
        requestSync(false);
    }

    @Override
    public void deleteLocalFile(DbFile file, SQLiteDatabase db)
    {
        SyncDb.updateLocalFileDeleted(file.itsId, db);
        requestSync(false);
    }
}
