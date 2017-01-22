/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * Database helper for the PasswdSafe database
 */
public final class PasswdSafeDb extends SQLiteOpenHelper
{
    public static final String DB_TABLE_FILES = "files";
    public static final String DB_COL_FILES_ID = BaseColumns._ID;
    public static final String DB_COL_FILES_TITLE = "title";
    public static final String DB_COL_FILES_URI = "uri";
    public static final String DB_COL_FILES_DATE = "date";

    private static final String DB_NAME = "recent_files.db";
    private static final int DB_VERSION = 1;

    private static final String TAG = "PasswdSafeDb";

    /**
     * Interface for a user of the database
     */
    public interface DbUser<T>
    {
        /**
         * Use the database
         */
        T useDb(SQLiteDatabase db) throws Exception;
    }

    /**
     * Constructor
     */
    public PasswdSafeDb(Context ctx)
    {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    /**
     * Query a database
     */
    public Cursor queryDb(String table, String[] columns, String sortOrder)
            throws SQLException
    {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(table, columns, null, null, null, null, sortOrder);
    }

    /**
     * Use the database with a transaction
     */
    public <T> T useDb(DbUser<T> user) throws Exception
    {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            T rc = user.useDb(db);
            db.setTransactionSuccessful();
            return rc;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        PasswdSafeUtil.dbginfo(TAG, "Create DB");
        db.execSQL("CREATE TABLE " + DB_TABLE_FILES + " (" +
                   DB_COL_FILES_ID + " INTEGER PRIMARY KEY," +
                   DB_COL_FILES_TITLE + " TEXT NOT NULL, " +
                   DB_COL_FILES_URI + " TEXT NOT NULL, " +
                   DB_COL_FILES_DATE + " INTEGER NOT NULL" +
                   ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
    }
}
