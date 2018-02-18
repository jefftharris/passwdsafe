/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import java.io.File;

import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

/**
 * Provides common Provider operations for local file management
 */
public abstract class AbstractProvider implements Provider
{
    private final SyncResults itsSyncResults = new SyncResults();

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
