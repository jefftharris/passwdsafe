/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.db;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * Access to the backup files database
 */
@Dao
public abstract class BackupFilesDao
{
    /**
     * Get backup files
     */
    @Query("SELECT * FROM " + BackupFile.TABLE + " ORDER BY " +
           BackupFile.COL_DATE + " DESC")
    public abstract LiveData<List<BackupFile>> loadBackupFiles();

    /**
     * Insert a backup file
     */
    public void insert(@NonNull Uri fileUri, @NonNull String title,
                       ContentResolver cr) throws Exception
    {
        try {
            doInsert(fileUri, title, cr);
        } catch (RuntimeException e) {
            // Unpack a wrapped exception from doInsert
            if (e.getCause() instanceof Exception) {
                throw (Exception)e.getCause();
            }
            throw new RuntimeException("Error inserting backup", e);
        }
    }

    /**
     * Insert a backup file implementation
     */
    @Transaction
    protected void doInsert(Uri fileUri,
                            String title,
                            ContentResolver cr) throws RuntimeException
    {
        try {
            BackupFile backup = new BackupFile(fileUri, title);
            doInsert(backup);

//            try (ParcelFileDescriptor pfd = cr.openFileDescriptor(fileUri, "r")) {
//            }
        } catch (Exception e) {
            throw new RuntimeException("Error inserting backup", e);
        }
    }

    /**
     * Insert a backup file entry
     */
    @Insert
    protected abstract void doInsert(BackupFile file);
}
