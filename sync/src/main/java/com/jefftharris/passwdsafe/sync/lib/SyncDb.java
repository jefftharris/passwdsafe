/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;

import java.util.ArrayList;
import java.util.List;

/**
 * The SyncDb encapsulates the synchronization database
 */
public class SyncDb
{
    private static final String TAG = "SyncDb";

    public static final String GDRIVE_ACCOUNT_TYPE = "com.google";
    public static final String DROPBOX_ACCOUNT_TYPE = "com.jefftharris.dropbox";
    public static final String BOX_ACCOUNT_TYPE = "com.jefftharris.box";
    public static final String ONEDRIVE_ACCOUNT_TYPE =
            "com.jefftharris.onedrive";
    public static final String OWNCLOUD_ACCOUNT_TYPE = "owncloud";

    public static final String DB_TABLE_PROVIDERS = "providers";
    public static final String DB_COL_PROVIDERS_ID = BaseColumns._ID;
    public static final String DB_COL_PROVIDERS_TYPE = "type";
    public static final String DB_COL_PROVIDERS_ACCT = "acct";
    private static final String DB_COL_PROVIDERS_SYNC_CHANGE = "sync_change";
    public static final String DB_COL_PROVIDERS_SYNC_FREQ = "sync_freq";
    public static final String DB_COL_PROVIDERS_DISPLAY_NAME = "display_name";
    public static final String DB_COL_PROVIDERS_SYNC_LAST_SUCCESS =
            "sync_last_success";
    public static final String DB_COL_PROVIDERS_SYNC_LAST_FAILURE =
            "sync_last_failure";
    public static final String DB_MATCH_PROVIDERS_ID =
        DB_COL_PROVIDERS_ID + " = ?";
    private static final String DB_MATCH_PROVIDERS_TYPE_ACCT =
        DB_COL_PROVIDERS_TYPE + " = ? AND " + DB_COL_PROVIDERS_ACCT + " = ?";

    public static final String DB_TABLE_FILES = "files";
    public static final String DB_COL_FILES_ID = BaseColumns._ID;
    public static final String DB_COL_FILES_PROVIDER = "provider";
    public static final String DB_COL_FILES_LOCAL_FILE = "local_file";
    public static final String DB_COL_FILES_LOCAL_TITLE = "local_title";
    public static final String DB_COL_FILES_LOCAL_MOD_DATE = "local_mod_date";
    public static final String DB_COL_FILES_LOCAL_DELETED = "local_deleted";
    public static final String DB_COL_FILES_LOCAL_FOLDER = "local_folder";
    public static final String DB_COL_FILES_LOCAL_CHANGE = "local_change";
    public static final String DB_COL_FILES_REMOTE_ID = "remote_id";
    public static final String DB_COL_FILES_REMOTE_TITLE = "remote_title";
    public static final String DB_COL_FILES_REMOTE_MOD_DATE = "remote_mod_date";
    public static final String DB_COL_FILES_REMOTE_DELETED = "remote_deleted";
    public static final String DB_COL_FILES_REMOTE_FOLDER = "remote_folder";
    public static final String DB_COL_FILES_REMOTE_CHANGE = "remote_change";
    public static final String DB_COL_FILES_REMOTE_HASH = "remote_hash";
    public static final String DB_MATCH_FILES_ID =
        DB_COL_FILES_ID + " = ?";
    public static final String DB_MATCH_FILES_PROVIDER_ID =
        DB_COL_FILES_PROVIDER + " = ?";
    private static final String DB_MATCH_FILES_PROVIDER_REMOTE_ID =
        DB_COL_FILES_PROVIDER + " = ? AND " +
        DB_COL_FILES_REMOTE_ID + " = ?";

    public static final String DB_TABLE_SYNC_LOGS = "sync_logs";
    public static final String DB_COL_SYNC_LOGS_ID = BaseColumns._ID;
    public static final String DB_COL_SYNC_LOGS_ACCT = "acct";
    public static final String DB_COL_SYNC_LOGS_START = "start";
    public static final String DB_COL_SYNC_LOGS_END = "end";
    public static final String DB_COL_SYNC_LOGS_FLAGS = "flags";
    public static final String DB_COL_SYNC_LOGS_LOG = "log";
    public static final String DB_COL_SYNC_LOGS_STACK = "stack";
    private static final String DB_MATCH_SYNC_LOGS_START_BEFORE =
            DB_COL_SYNC_LOGS_START + " < ?";

    public static final long INVALID_UPDATE_COUNT = -1;

    private static SyncDb itsDb = null;
    private static long itsUpdateCount = 0;

    private final DbHelper itsDbHelper;

    /**
     * Interface for a user of the database
     */
    public interface DbUser<T>
    {
        /**
         * Use the database
         */
        @Nullable T useDb(SQLiteDatabase db) throws Exception;
    }

    /** Initialize the single SyncDb instance */
    public static synchronized void initializeDb(Context ctx)
    {
        if (itsDb == null) {
            itsDb = new SyncDb(ctx);
        }
    }

    /** Finalize the single SyncDb instance */
    public static synchronized void finalizeDb()
    {
        itsDb.close();
        itsDb = null;
    }

    /**
     * Use the database with a transaction
     */
    public static <T> T useDb(@NonNull DbUser<T> user) throws Exception
    {
        SyncDb syncDb = getDb();
        SQLiteDatabase db = syncDb.itsDbHelper.getWritableDatabase();
        try {
            db.beginTransaction();
            T rc = user.useDb(db);
            db.setTransactionSuccessful();
            return rc;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Query the database without a transaction
     */
    public static Cursor queryDb(@NonNull SQLiteQueryBuilder qb,
                                 String[] projection,
                                 String selection, String[] selectionArgs,
                                 String sortOrder) throws SQLException
    {
        SyncDb syncDb = getDb();
        SQLiteDatabase db = syncDb.itsDbHelper.getReadableDatabase();
        return qb.query(db, projection, selection, selectionArgs,
                        null, null, sortOrder);
    }

    /** Constructor */
    private SyncDb(Context ctx)
    {
        itsDbHelper = new DbHelper(ctx);
    }

    /** Close the DB */
    private void close()
    {
        itsDbHelper.close();
    }

    /** Add a provider */
    public static long addProvider(String name, @NonNull ProviderType type,
                                   int freq, SQLiteDatabase db)
        throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_PROVIDERS_TYPE, type.toString());
        values.put(DB_COL_PROVIDERS_ACCT, name);
        values.put(DB_COL_PROVIDERS_SYNC_CHANGE, -1);
        values.put(DB_COL_PROVIDERS_SYNC_FREQ, freq);
        return doInsert(db, DB_TABLE_PROVIDERS, values);
    }

    /** Delete a provider */
    public static void deleteProvider(long id, SQLiteDatabase db)
        throws SQLException
    {
        String[] idargs = new String[] { Long.toString(id) };
        doDelete(db, DB_TABLE_FILES, DB_MATCH_FILES_PROVIDER_ID, idargs);
        doDelete(db, DB_TABLE_PROVIDERS, DB_MATCH_PROVIDERS_ID, idargs);
    }

    /** Update a provider display name */
    public static void updateProviderDisplayName(long id, String displayName,
                                                 SQLiteDatabase db)
           throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_PROVIDERS_DISPLAY_NAME, displayName);
        updateProviderFields(id, values, db);
    }

    /** Update a provider sync frequency */
    public static void updateProviderSyncFreq(long id, int freq,
                                              SQLiteDatabase db)
           throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_PROVIDERS_SYNC_FREQ, freq);
        updateProviderFields(id, values, db);
    }

    /** Update the last sync time for a provider */
    public static void updateProviderSyncTime(long id,
                                              boolean isSuccess,
                                              long syncTime,
                                              SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(isSuccess ? DB_COL_PROVIDERS_SYNC_LAST_SUCCESS :
                           DB_COL_PROVIDERS_SYNC_LAST_FAILURE,
                   syncTime);
        updateProviderFields(id, values, db);
    }

    /** Get a provider by id */
    public static DbProvider getProvider(long id, SQLiteDatabase db)
            throws SQLException
    {
        return getProvider(DB_MATCH_PROVIDERS_ID,
                           new String[] { Long.toString(id) }, db);
    }

    /** Get a provider by name and type */
    public static DbProvider getProvider(String acctName,
                                         @NonNull ProviderType type,
                                         SQLiteDatabase db)
            throws SQLException
    {
        return getProvider(
                DB_MATCH_PROVIDERS_TYPE_ACCT,
                new String[] { type.name(), acctName },
                db);
    }

    /** Get the providers */
    @NonNull
    public static List<DbProvider> getProviders(@NonNull SQLiteDatabase db)
            throws SQLException
    {
        List<DbProvider> providers = new ArrayList<>();
        try (Cursor cursor = db.query(DB_TABLE_PROVIDERS,
                                      DbProvider.QUERY_FIELDS, null, null, null,
                                      null, null)) {
            for (boolean more = cursor.moveToFirst(); more;
                 more = cursor.moveToNext()) {
                providers.add(new DbProvider(cursor));
            }
        }
        return providers;
    }

    /** Get a file by id */
    public static DbFile getFile(long id, SQLiteDatabase db)
            throws SQLException
    {
        return getFile(DB_MATCH_FILES_ID, new String[] { Long.toString(id) },
                       db);
    }


    /** Get a file by provider and remote file id */
    public static DbFile getFileByRemoteId(long provider,
                                           String remoteId,
                                           SQLiteDatabase db)
            throws SQLException
    {
        return getFile(DB_MATCH_FILES_PROVIDER_REMOTE_ID,
                       new String[] { Long.toString(provider),
                                      remoteId },
                       db);
    }


    /** Get all of the files for a provider by id */
    @NonNull
    public static List<DbFile> getFiles(long providerId,
                                        @NonNull SQLiteDatabase db)
            throws SQLException
    {
        List<DbFile> files = new ArrayList<>();
        try (Cursor cursor = db.query(DB_TABLE_FILES, DbFile.QUERY_FIELDS,
                                      DB_MATCH_FILES_PROVIDER_ID,
                                      new String[]{Long.toString(providerId)},
                                      null, null, null)) {
            for (boolean more = cursor.moveToFirst(); more;
                 more = cursor.moveToNext()) {
                files.add(new DbFile(cursor));
            }
        }

        return files;
    }


    /** Add a local file for a provider */
    public static long addLocalFile(long providerId, String title,
                                    long modDate, SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_PROVIDER, providerId);
        values.put(DB_COL_FILES_LOCAL_TITLE, title);
        values.put(DB_COL_FILES_LOCAL_MOD_DATE, modDate);
        values.put(DB_COL_FILES_LOCAL_DELETED, false);
        values.put(DB_COL_FILES_LOCAL_CHANGE,
                   DbFile.FileChange.toDbStr(DbFile.FileChange.ADDED));
        values.put(DB_COL_FILES_REMOTE_MOD_DATE, -1);
        values.put(DB_COL_FILES_REMOTE_DELETED, false);
        return doInsert(db, DB_TABLE_FILES, values);
    }


    /** Add a remote file for a provider */
    public static long addRemoteFile(long providerId,
                                     String remId, String remTitle,
                                     String remFolder, long remModDate,
                                     String remHash, SQLiteDatabase db)
        throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_PROVIDER, providerId);
        values.put(DB_COL_FILES_LOCAL_MOD_DATE, -1);
        values.put(DB_COL_FILES_LOCAL_DELETED, false);
        values.put(DB_COL_FILES_REMOTE_ID, remId);
        values.put(DB_COL_FILES_REMOTE_TITLE, remTitle);
        values.put(DB_COL_FILES_REMOTE_MOD_DATE, remModDate);
        values.put(DB_COL_FILES_REMOTE_HASH, remHash);
        values.put(DB_COL_FILES_REMOTE_DELETED, false);
        values.put(DB_COL_FILES_REMOTE_FOLDER, remFolder);
        values.put(DB_COL_FILES_REMOTE_CHANGE,
                   DbFile.FileChange.toDbStr(DbFile.FileChange.ADDED));
        return doInsert(db, DB_TABLE_FILES, values);
    }


    /** Update a local file */
    public static void updateLocalFile(long fileId, String locFile,
                                       String locTitle, String locFolder,
                                       long locModDate, SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_LOCAL_FILE, locFile);
        values.put(DB_COL_FILES_LOCAL_TITLE, locTitle);
        values.put(DB_COL_FILES_LOCAL_MOD_DATE, locModDate);
        values.put(DB_COL_FILES_LOCAL_DELETED, false);
        values.put(DB_COL_FILES_LOCAL_FOLDER, locFolder);
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Update the change for a local file */
    public static void updateLocalFileChange(long fileId,
                                             DbFile.FileChange change,
                                             SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_LOCAL_CHANGE,
                   DbFile.FileChange.toDbStr(change));
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Update a remote file */
    public static void updateRemoteFile(long fileId, String remId,
                                        String remTitle, String remFolder,
                                        long remModDate, String remHash,
                                        SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_REMOTE_ID, remId);
        values.put(DB_COL_FILES_REMOTE_TITLE, remTitle);
        values.put(DB_COL_FILES_REMOTE_MOD_DATE, remModDate);
        values.put(DB_COL_FILES_REMOTE_HASH, remHash);
        values.put(DB_COL_FILES_REMOTE_DELETED, false);
        values.put(DB_COL_FILES_REMOTE_FOLDER, remFolder);
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Update the change for a remote file */
    public static void updateRemoteFileChange(long fileId,
                                              DbFile.FileChange change,
                                              SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_REMOTE_CHANGE,
                   DbFile.FileChange.toDbStr(change));
        switch (change) {
        case ADDED:
        case MODIFIED: {
            values.put(DB_COL_FILES_REMOTE_DELETED, false);
            break;
        }
        case REMOVED: {
            values.put(DB_COL_FILES_REMOTE_DELETED, true);
            break;
        }
        case NO_CHANGE: {
            break;
        }
        }
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Update a remote file as deleted */
    public static void updateRemoteFileDeleted(long fileId, SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_REMOTE_DELETED, true);
        values.put(DB_COL_FILES_REMOTE_CHANGE,
                DbFile.FileChange.toDbStr(DbFile.FileChange.REMOVED));
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Update a local file as deleted */
    public static void updateLocalFileDeleted(long fileId, SQLiteDatabase db)
            throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_FILES_LOCAL_DELETED, true);
        values.put(DB_COL_FILES_LOCAL_CHANGE,
                   DbFile.FileChange.toDbStr(DbFile.FileChange.REMOVED));
        doUpdate(db, DB_TABLE_FILES, values,
                 DB_MATCH_FILES_ID, new String[] { Long.toString(fileId) });
    }


    /** Remove the file */
    public static void removeFile(long fileId, SQLiteDatabase db)
        throws SQLException
    {
        doDelete(db, DB_TABLE_FILES, DB_MATCH_FILES_ID,
                 new String[] { Long.toString(fileId) });
    }


    /** Add a sync log */
    public static void addSyncLog(@NonNull SyncLogRecord logrec,
                                  SQLiteDatabase db,
                                  Context ctx)
        throws SQLException
    {
        ContentValues values = new ContentValues();
        values.put(DB_COL_SYNC_LOGS_ACCT, logrec.getAccount());
        values.put(DB_COL_SYNC_LOGS_START, logrec.getStartTime());
        values.put(DB_COL_SYNC_LOGS_END, logrec.getEndTime());
        values.put(DB_COL_SYNC_LOGS_LOG, logrec.getActions(ctx));
        values.put(DB_COL_SYNC_LOGS_STACK, logrec.getStacktrace());

        int flags = 0;
        if (logrec.isManualSync()) {
            flags |= PasswdSafeContract.SyncLogs.FLAGS_IS_MANUAL;
        }
        if (logrec.isNotConnected()) {
            flags |= PasswdSafeContract.SyncLogs.FLAGS_IS_NOT_CONNECTED;
        }
        values.put(DB_COL_SYNC_LOGS_FLAGS, flags);

        doInsert(db, DB_TABLE_SYNC_LOGS, values);
    }


    /** Delete old logs */
    public static void deleteSyncLogs(long removeBefore, SQLiteDatabase db)
        throws SQLException
    {
        doDelete(db, DB_TABLE_SYNC_LOGS, DB_MATCH_SYNC_LOGS_START_BEFORE,
                 new String[] { Long.toString(removeBefore) });
    }

    /**
     * Check whether the DB update count matches the passed value
     */
    public static synchronized boolean checkUpdateCount(long updateCount)
    {
        return (updateCount == INVALID_UPDATE_COUNT) ||
               (updateCount == itsUpdateCount);
    }

    /**
     * Get the DB update count
     */
    public static synchronized long getUpdateCount()
    {
        return itsUpdateCount;
    }

    /** Get a provider */
    @Nullable
    private static DbProvider getProvider(String match, String[] matchArgs,
                                          @NonNull SQLiteDatabase db)
            throws SQLException
    {
        try (Cursor cursor = db.query(DB_TABLE_PROVIDERS,
                                      DbProvider.QUERY_FIELDS, match, matchArgs,
                                      null, null, null)) {
            if (cursor.moveToFirst()) {
                return new DbProvider(cursor);
            }
        }
        return null;
    }

    /** Update fields for a provider */
    private static void updateProviderFields(long providerId,
                                             ContentValues values,
                                             SQLiteDatabase db)
        throws SQLException
    {
        String[] idargs = new String[] { Long.toString(providerId) };
        doUpdate(db, DB_TABLE_PROVIDERS, values, DB_MATCH_PROVIDERS_ID, idargs);
    }


    /** Get a file */
    @Nullable
    private static DbFile getFile(String match, String[] matchArgs,
                                  @NonNull SQLiteDatabase db)
            throws SQLException
    {
        try (Cursor cursor = db.query(DB_TABLE_FILES, DbFile.QUERY_FIELDS,
                                      match, matchArgs, null, null, null)) {
            if (cursor.moveToFirst()) {
                return new DbFile(cursor);
            }
        }

        return null;
    }

    /**
     * Insert into the database
     */
    private static long doInsert(@NonNull SQLiteDatabase db, String table,
                                 ContentValues values) throws SQLException
    {
        long id = db.insertOrThrow(table, null, values);
        incrUpdateCount();
        return id;
    }

    /**
     * Update the database
     */
    private static void doUpdate(@NonNull SQLiteDatabase db, String table,
                                 ContentValues values,
                                 String where, String[] args)
    {
        db.update(table, values, where, args);
        incrUpdateCount();
    }

    /**
     * Delete from the database
     */
    private static void doDelete(@NonNull SQLiteDatabase db,
                                 String table,
                                 String where,
                                 String[] args)
    {
        db.delete(table, where, args);
        incrUpdateCount();
    }

    /**
     * Get the SyncDb instance
     */
    private static synchronized SyncDb getDb()
    {
        return itsDb;
    }

    /**
     * Increment the DB update count
     */
    private static synchronized void incrUpdateCount()
    {
        ++itsUpdateCount;
    }

    /** Database helper class to manage the tables */
    private static final class DbHelper extends SQLiteOpenHelper
    {
        private static final String DB_NAME = "sync.db";
        private static final int DB_VERSION = 6;

        private final Context itsContext;

        /** Constructor */
        private DbHelper(Context context)
        {
            super(context, DB_NAME, null, DB_VERSION);
            itsContext = context;
        }

        /* (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
         */
        @Override
        public void onCreate(SQLiteDatabase db)
        {
            PasswdSafeUtil.dbginfo(TAG, "Create DB");
            enableForeignKey(db);
            db.execSQL("CREATE TABLE " + DB_TABLE_PROVIDERS + " (" +
                       DB_COL_PROVIDERS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                       DB_COL_PROVIDERS_TYPE + " TEXT NOT NULL," +
                       DB_COL_PROVIDERS_ACCT + " TEXT NOT NULL," +
                       DB_COL_PROVIDERS_SYNC_CHANGE + " INTEGER NOT NULL," +
                       DB_COL_PROVIDERS_SYNC_FREQ + " INTEGER NOT NULL" +
                       ");");
            db.execSQL("CREATE TABLE " + DB_TABLE_FILES + " (" +
                       DB_COL_FILES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                       DB_COL_FILES_PROVIDER + " INTEGER REFERENCES " +
                           DB_TABLE_PROVIDERS + "(" + DB_COL_PROVIDERS_ID +
                           ") NOT NULL," +
                       DB_COL_FILES_LOCAL_FILE + " TEXT," +
                       DB_COL_FILES_LOCAL_TITLE + " TEXT," +
                       DB_COL_FILES_LOCAL_MOD_DATE + " INTEGER NOT NULL," +
                       DB_COL_FILES_LOCAL_DELETED + " INTEGER NOT NULL," +
                       DB_COL_FILES_REMOTE_ID + " TEXT," +
                       DB_COL_FILES_REMOTE_TITLE + " TEXT," +
                       DB_COL_FILES_REMOTE_MOD_DATE + " INTEGER NOT NULL," +
                       DB_COL_FILES_REMOTE_DELETED + " INTEGER NOT NULL" +
                       ");");
            db.execSQL("CREATE TABLE " + DB_TABLE_SYNC_LOGS + " (" +
                       DB_COL_SYNC_LOGS_ID + " INTEGER PRIMARY KEY," +
                       DB_COL_SYNC_LOGS_ACCT + " TEXT NOT NULL," +
                       DB_COL_SYNC_LOGS_START + " INTEGER NOT NULL, \"" +
                       DB_COL_SYNC_LOGS_END + "\" INTEGER NOT NULL," +
                       DB_COL_SYNC_LOGS_FLAGS + " INTEGER NOT NULL," +
                       DB_COL_SYNC_LOGS_LOG + " TEXT" +
                       ");");

            onUpgrade(db, 1, DB_VERSION);
        }

        /* (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            enableForeignKey(db);

            if (oldVersion < 2) {
                PasswdSafeUtil.dbginfo(TAG, "Upgrade to v2");
                db.execSQL("ALTER TABLE " + DB_TABLE_PROVIDERS +
                           " ADD COLUMN " + DB_COL_PROVIDERS_DISPLAY_NAME +
                           " TEXT;");
            }

            if (oldVersion < 3) {
                PasswdSafeUtil.dbginfo(TAG, "Upgrade to v3");
                db.execSQL("ALTER TABLE " + DB_TABLE_FILES +
                           " ADD COLUMN " + DB_COL_FILES_LOCAL_FOLDER +
                           " TEXT;");
                db.execSQL("ALTER TABLE " + DB_TABLE_FILES +
                           " ADD COLUMN " + DB_COL_FILES_REMOTE_FOLDER +
                           " TEXT;");
            }

            if (oldVersion < 4) {
                PasswdSafeUtil.dbginfo(TAG, "Upgrade to v4");
                db.execSQL("ALTER TABLE " + DB_TABLE_FILES +
                           " ADD COLUMN " + DB_COL_FILES_LOCAL_CHANGE +
                           " TEXT;");
                db.execSQL("ALTER TABLE " + DB_TABLE_FILES +
                           " ADD COLUMN " + DB_COL_FILES_REMOTE_CHANGE +
                           " TEXT;");
                db.execSQL("ALTER TABLE " + DB_TABLE_FILES +
                           " ADD COLUMN " + DB_COL_FILES_REMOTE_HASH +
                           " TEXT;");

                try (Cursor cursor = db.query(
                        DB_TABLE_PROVIDERS,
                        new String[]{SyncDb.DB_COL_PROVIDERS_ID},
                        null, null, null, null, null)) {
                    for (boolean more = cursor.moveToFirst(); more;
                         more = cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        for (DbFile file : getFiles(id, db)) {
                            onUpgradeV4File(file, db);
                        }
                    }
                }
            }

            if (oldVersion < 5) {
                PasswdSafeUtil.dbginfo(TAG, "Upgrade to v5");
                db.execSQL("ALTER TABLE " + DB_TABLE_SYNC_LOGS +
                           " ADD COLUMN " + DB_COL_SYNC_LOGS_STACK +
                           " TEXT;");
            }

            if (oldVersion < 6) {
                PasswdSafeUtil.dbginfo(TAG, "Upgrade to v6");
                db.execSQL("ALTER TABLE " + DB_TABLE_PROVIDERS +
                           " ADD COLUMN " + DB_COL_PROVIDERS_SYNC_LAST_SUCCESS +
                           " INTEGER;");
                db.execSQL("ALTER TABLE " + DB_TABLE_PROVIDERS +
                           " ADD COLUMN " + DB_COL_PROVIDERS_SYNC_LAST_FAILURE +
                           " INTEGER;");
            }
        }

        /* (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onOpen(android.database.sqlite.SQLiteDatabase)
         */
        @Override
        public void onOpen(SQLiteDatabase db)
        {
            enableForeignKey(db);
            super.onOpen(db);
        }

        /** Upgrade a file for the v4 schema */
        private void onUpgradeV4File(@NonNull DbFile file, SQLiteDatabase db)
                throws SQLException
        {
            DbFile.FileChange local = DbFile.FileChange.NO_CHANGE;
            DbFile.FileChange remote = DbFile.FileChange.NO_CHANGE;
            if (file.itsIsRemoteDeleted) {
                remote = DbFile.FileChange.REMOVED;
            } else if (TextUtils.isEmpty(file.itsLocalFile) ||
                    (file.itsLocalModDate == -1) ||
                    !itsContext.getFileStreamPath(file.itsLocalFile).exists()) {
                remote = DbFile.FileChange.ADDED;
            } else if (!TextUtils.equals(file.itsLocalFolder,
                                         file.itsRemoteFolder) ||
                    (file.itsRemoteModDate > file.itsLocalModDate)) {
                remote = DbFile.FileChange.MODIFIED;
            }

            if (file.itsIsLocalDeleted) {
                local = DbFile.FileChange.REMOVED;
            } else if (TextUtils.isEmpty(file.itsRemoteId)) {
                local = DbFile.FileChange.ADDED;
            } else if (file.itsLocalModDate > file.itsRemoteModDate) {
                local = DbFile.FileChange.MODIFIED;
            }

            if (local != DbFile.FileChange.NO_CHANGE) {
                updateLocalFileChange(file.itsId, local, db);
            }
            if (remote != DbFile.FileChange.NO_CHANGE) {
                updateRemoteFileChange(file.itsId, remote, db);
            }
        }

        /** Enable support for foreign keys on the open database connection */
        private void enableForeignKey(@NonNull SQLiteDatabase db)
            throws SQLException
        {
            if (!db.isReadOnly()) {
                db.execSQL("PRAGMA foreign_keys = \"ON\";");
            }
        }
    }
}
