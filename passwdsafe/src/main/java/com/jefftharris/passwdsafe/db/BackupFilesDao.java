/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.db;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.Utils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Access to the backup files database
 */
@Dao
public abstract class BackupFilesDao
{
    private static final String BACKUP_FILE_PFX = "backup-";
    private static final String TAG = "BackupFilesDao";

    /**
     * Get backup files
     */
    @Query("SELECT * FROM " + BackupFile.TABLE + " ORDER BY " +
           BackupFile.COL_DATE + " DESC")
    public abstract LiveData<List<BackupFile>> loadBackupFiles();

    /**
     * Get a backup file by its primary key
     */
    @Query("SELECT * FROM " + BackupFile.TABLE +
           " WHERE " + BackupFile.COL_ID + " = :backupFileId")
    public abstract BackupFile getBackupFile(long backupFileId);

    /**
     * Open an input stream for a backup file
     */
    public static InputStream openBackupFile(@NonNull BackupFile file,
                                             @NonNull Context ctx)
            throws FileNotFoundException
    {
        return ctx.openFileInput(getBackupFileName(file.id));
    }

    /**
     * Insert a backup file
     */
    public void insert(@NonNull Uri fileUri,
                       @NonNull String title,
                       Context ctx,
                       ContentResolver cr)
    {
        try {
            doInsert(fileUri, title, ctx, cr);
        } catch (SkipBackupException e) {
            // exception reverts the backup
        } catch (Exception e) {
            Log.e(TAG, "Error inserting backup for: " + fileUri, e);

            new Handler(Looper.getMainLooper()).post(
                    () -> Toast.makeText(ctx, R.string.backup_creation_failed,
                                         Toast.LENGTH_LONG).show());
        }
    }

    /**
     * Delete all of the backup files
     */
    public void deleteAll(Context ctx)
    {
        for (String fileName : ctx.fileList()) {
            if (fileName.startsWith(BACKUP_FILE_PFX)) {
                ctx.deleteFile(fileName);
            }
        }
        doDeleteAll();
    }

    /**
     * Delete a backup file by its primary key
     */
    public void delete(long backupFileId, Context ctx) {
        ctx.deleteFile(getBackupFileName(backupFileId));
        doDelete(backupFileId);
    }

    /**
     * Insert a backup file implementation
     */
    @Transaction
    protected void doInsert(Uri fileUri,
                            String title,
                            Context ctx,
                            ContentResolver cr) throws RuntimeException
    {
        try {
            BackupFile backup = new BackupFile(fileUri, title);
            long id = doInsert(backup);

            String backupFileName = getBackupFileName(id);
            try (InputStream is = Objects.requireNonNull(
                    cr.openInputStream(fileUri));
                 OutputStream os = Objects.requireNonNull(
                         ctx.openFileOutput(backupFileName,
                                            Context.MODE_PRIVATE))) {
                if (Utils.copyStream(is, os) == 0) {
                    // Skip backup on an empty file which is often a new file
                    throw new SkipBackupException();
                }
            } catch (Exception e) {
                ctx.deleteFile(backupFileName);
                throw e;
            }
        } catch (SkipBackupException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error inserting backup", e);
        }
    }

    /**
     * Insert a backup file entry implementation
     */
    @Insert
    protected abstract long doInsert(BackupFile file);

    /**
     * Delete all backup file entries implementation
     */
    @Query("DELETE FROM " + BackupFile.TABLE)
    protected abstract void doDeleteAll();

    /**
     * Delete a backup file entry implementation
     */
    @Query("DELETE FROM " + BackupFile.TABLE +
           " WHERE " + BackupFile.COL_ID + " = :backupFileId")
    protected abstract void doDelete(long backupFileId);

    /**
     * Get the name of a backup file
     */
    private static String getBackupFileName(long backupId)
    {
        return String.format(Locale.US, "%s%d", BACKUP_FILE_PFX, backupId);
    }

    /**
     * Exception to skip the backup and undo the file and database updates
     */
    private static class SkipBackupException extends RuntimeException
    {
    }
}
