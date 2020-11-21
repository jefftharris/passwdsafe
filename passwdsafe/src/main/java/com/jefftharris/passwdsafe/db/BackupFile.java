/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.db;

import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Backup file database entry
 */
@Entity(tableName = BackupFile.TABLE)
public class BackupFile
{
    public static final String TABLE = "backups";
    public static final String COL_ID = BaseColumns._ID;
    public static final String COL_TITLE = "title";
    public static final String COL_FILE_URI = "fileUri";
    public static final String COL_DATE = "date";

    /**
     * Unique id for the backup file
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COL_ID)
    public long id;

    /**
     * Title of the file
     */
    @ColumnInfo(name = COL_TITLE)
    @NonNull
    public String title;

    /**
     * The URI of the file
     */
    @ColumnInfo(name = COL_FILE_URI)
    @NonNull
    public String fileUri;

    /**
     * Backup date
     */
    @ColumnInfo(name = COL_DATE)
    public long date;

    /**
     * Constructor from database entry
     */
    BackupFile(long id,
               @NonNull String title,
               @NonNull String fileUri,
               long date)
    {
        this.id = id;
        this.title = title;
        this.fileUri = fileUri;
        this.date = date;
    }

    /**
     * Constructor for a new backup file
     */
    @Ignore
    public BackupFile(@NonNull Uri fileUri, @NonNull String title)
    {
        this.id = 0;
        this.title = title;
        this.fileUri = fileUri.toString();
        this.date = System.currentTimeMillis();
    }


    @Override
    public boolean equals(@Nullable Object obj)
    {
        if (!(obj instanceof BackupFile)) {
            return false;
        }
        BackupFile backup = (BackupFile)obj;
        return (id == backup.id) && title.equals(backup.title) &&
               fileUri.equals(backup.fileUri) && (date == backup.date);
    }
}
