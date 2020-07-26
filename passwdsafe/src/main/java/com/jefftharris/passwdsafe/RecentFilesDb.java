/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent files database
 */
public final class RecentFilesDb
{
    private static final String DB_COL_FILES_ID =
            PasswdSafeDb.DB_COL_FILES_ID;
    private static final String DB_COL_FILES_TITLE =
            PasswdSafeDb.DB_COL_FILES_TITLE;
    private static final String DB_COL_FILES_DATE =
            PasswdSafeDb.DB_COL_FILES_DATE;

    private static final String[] QUERY_COLUMNS = new String[] {
            DB_COL_FILES_ID, DB_COL_FILES_TITLE,
            PasswdSafeDb.DB_COL_FILES_URI, DB_COL_FILES_DATE };

    private static final int QUERY_COL_ID = 0;
    public static final int QUERY_COL_TITLE = 1;
    public static final int QUERY_COL_URI = 2;
    public static final int QUERY_COL_DATE = 3;

    private static final String WHERE_BY_ID =
            PasswdSafeDb.DB_COL_FILES_ID + " = ?";
    private static final String WHERE_BY_URI =
            PasswdSafeDb.DB_COL_FILES_URI + " = ?";
    private static final String ORDER_BY_DATE =
            PasswdSafeDb.DB_COL_FILES_DATE + " DESC";


    private static final int NUM_RECENT_FILES = 10;

    private static final String TAG = "RecentFilesDb";

    private final PasswdSafeDb itsDb;

    /** Constructor */
    public RecentFilesDb(Context context)
    {
        PasswdSafeApp app = (PasswdSafeApp)context.getApplicationContext();
        itsDb = app.getPasswdSafeDb();
    }

    /** Query files */
    public Cursor queryFiles() throws SQLException
    {
        PasswdSafeUtil.dbginfo(TAG, "load recent files");
        return itsDb.queryDb(PasswdSafeDb.DB_TABLE_FILES,
                             QUERY_COLUMNS, ORDER_BY_DATE);
    }


    /** Insert or update the entry for the file */
    public void insertOrUpdateFile(final Uri uri, final String title)
            throws Exception
    {
        itsDb.useDb((PasswdSafeDb.DbUser<Void>)db -> {
            String uristr = uri.toString();
            long uriId = -1;
            {
                Cursor cursor = db.query(PasswdSafeDb.DB_TABLE_FILES,
                                         QUERY_COLUMNS, WHERE_BY_URI,
                                         new String[]{uristr},
                                         null, null, null);
                try {
                    if (cursor.moveToFirst()) {
                        uriId = cursor.getLong(QUERY_COL_ID);
                    }
                } finally {
                    cursor.close();
                }
            }
            ContentValues values = new ContentValues();
            values.put(PasswdSafeDb.DB_COL_FILES_DATE,
                       System.currentTimeMillis());
            if (uriId != -1) {
                db.update(PasswdSafeDb.DB_TABLE_FILES, values, WHERE_BY_ID,
                          new String[] { Long.toString(uriId) });
            } else {
                values.put(PasswdSafeDb.DB_COL_FILES_TITLE, title);
                values.put(PasswdSafeDb.DB_COL_FILES_URI, uristr);
                db.insertOrThrow(PasswdSafeDb.DB_TABLE_FILES, null, values);
            }

            Cursor delCursor = db.query(PasswdSafeDb.DB_TABLE_FILES,
                                        QUERY_COLUMNS, null, null, null,
                                        null, ORDER_BY_DATE);
            try {
                if (delCursor.move(NUM_RECENT_FILES)) {
                    while (delCursor.moveToNext()) {
                        long id = delCursor.getLong(QUERY_COL_ID);
                        db.delete(PasswdSafeDb.DB_TABLE_FILES, WHERE_BY_ID,
                                  new String[] { Long.toString(id) });
                    }
                }
            } finally {
                delCursor.close();
            }
            return null;
        });
    }


    /** Touch an entry for the file */
    public void touchFile(final Uri uri) throws Exception
    {
        itsDb.useDb((PasswdSafeDb.DbUser<Void>)db -> {
            String uristr = uri.toString();
            ContentValues values = new ContentValues();
            values.put(PasswdSafeDb.DB_COL_FILES_DATE,
                       System.currentTimeMillis());
            db.update(PasswdSafeDb.DB_TABLE_FILES, values, WHERE_BY_URI,
                      new String[] {uristr});
            return null;
        });
    }


    /** Delete a recent file with the given uri */
    public void removeUri(final Uri permUri) throws Exception
    {
        itsDb.useDb((PasswdSafeDb.DbUser<Void>)db -> {
            db.delete(PasswdSafeDb.DB_TABLE_FILES, WHERE_BY_URI,
                      new String[]{ permUri.toString() });
            return null;
        });
    }


    /** Clear the recent files */
    public List<Uri> clear() throws Exception
    {
        return itsDb.useDb(db -> {
            List<Uri> uris = new ArrayList<>();
            Cursor c = db.query(PasswdSafeDb.DB_TABLE_FILES, QUERY_COLUMNS,
                                null, null, null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        uris.add(Uri.parse(c.getString(QUERY_COL_URI)));
                    }
                } finally {
                    c.close();
                }
            }
            db.delete(PasswdSafeDb.DB_TABLE_FILES, null, null);
            return uris;
        });
    }


    /** Update an opened storage access file */
    public static void updateOpenedSafFile(Uri uri, int flags, Context ctx)
    {
        ContentResolver cr = ctx.getContentResolver();
        ApiCompat.takePersistableUriPermission(cr, uri, flags);
    }

    /** Get the display name of a storage access file */
    public static String getSafDisplayName(Uri uri, Context ctx)
    {
        ContentResolver cr = ctx.getContentResolver();
        Cursor cursor = cr.query(uri, null, null, null, null);
        try {
            if ((cursor != null) && (cursor.moveToFirst())) {
                return cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
