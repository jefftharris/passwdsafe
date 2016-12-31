/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.R;

/**
 *  Base attributes and methods for the sync operation for a provider
 */
public abstract class ProviderSyncer<ProviderClientT>
{
    protected final ProviderClientT itsProviderClient;
    protected final Context itsContext;
    private final DbProvider itsProvider;
    private final SyncConnectivityResult itsConnResult;
    private final SQLiteDatabase itsDb;
    private final SyncLogRecord itsLogrec;
    private final String itsTag;
    private long itsDbUpdateCount = SyncDb.INVALID_UPDATE_COUNT;

    /**
     * Interface for a user of the database
     */
    private interface DbUser
    {
        /**
         * Use the database
         */
        void useDb(boolean dbOk) throws Exception;
    }

    /** Constructor */
    protected ProviderSyncer(ProviderClientT providerClient,
                             DbProvider provider,
                             SyncConnectivityResult connResult,
                             SQLiteDatabase db,
                             SyncLogRecord logrec, Context ctx,
                             String tag)
    {
        itsProviderClient = providerClient;
        itsProvider = provider;
        itsConnResult = connResult;
        itsDb = db;
        itsLogrec = logrec;
        itsContext = ctx;
        itsTag = tag;
    }


    /** Sync the provider */
    public final void sync()
            throws Exception
    {
        final ObjectHolder<List<SyncOper<ProviderClientT>>> opers =
                new ObjectHolder<>();

        try {
            final ObjectHolder<List<DbFile>> dbfiles = new ObjectHolder<>();
            try {
                useDb(new CheckedDbUser()
                {
                    @Override
                    public void useDb() throws Exception
                    {
                        syncDisplayName();
                        dbfiles.set(SyncDb.getFiles(itsProvider.itsId, itsDb));
                    }
                });

                final SyncRemoteFiles remoteFiles =
                        getSyncRemoteFiles(dbfiles.get());
                useDb(new CheckedDbUser()
                {
                    @Override
                    public void useDb() throws Exception
                    {
                        if (remoteFiles != null) {
                            updateDbFiles(remoteFiles);
                            opers.set(resolveSyncOpers());
                        }
                    }
                });
            } catch (Exception e) {
                throw updateSyncException(e);
            }

            if (opers.get() != null) {
                for (final SyncOper<ProviderClientT> oper: opers.get()) {
                    if (oper == null) {
                        continue;
                    }
                    try {
                        itsLogrec.checkSyncInterrupted();
                        itsLogrec.addEntry(oper.getDescription(itsContext));
                        oper.doOper(itsProviderClient, itsContext);
                        useDb(new DbUser()
                        {
                            @Override
                            public void useDb(boolean dbOk)
                                    throws Exception
                            {
                                oper.doPostOperUpdate(dbOk, itsDb, itsContext);
                            }
                        });
                    } catch (Exception e) {
                        e = updateSyncException(e);
                        Log.e(itsTag, "Sync error for file " + oper.getFile(),
                              e);
                        itsLogrec.addFailure(e);
                    } finally {
                        oper.finish();
                    }
                }
            }
        } finally {
            itsContext.getContentResolver().notifyChange(
                    PasswdSafeContract.CONTENT_URI, null, false);
        }
    }


    /** Get the remote files to sync */
    protected abstract @Nullable SyncRemoteFiles getSyncRemoteFiles(
            List<DbFile> dbfiles)
            throws Exception;


    /** Create an operation to sync local to remote */
    protected abstract AbstractLocalToRemoteSyncOper<ProviderClientT>
    createLocalToRemoteOper(DbFile dbfile);


    /** Create an operation to sync remote to local */
    protected abstract AbstractRemoteToLocalSyncOper<ProviderClientT>
    createRemoteToLocalOper(DbFile dbfile);


    /** Create an operation to remove a file */
    protected abstract AbstractRmSyncOper<ProviderClientT>
    createRmFileOper(DbFile dbfile);


    /** Update an exception thrown during syncing */
    protected Exception updateSyncException(Exception e)
    {
        return e;
    }


    /**
     * Use the database
     */
    private void useDb(DbUser user) throws Exception
    {
        itsLogrec.checkSyncInterrupted();
        try {
            itsDb.beginTransaction();
            user.useDb(SyncDb.checkUpdateCount(itsDbUpdateCount));
            itsDb.setTransactionSuccessful();
        } finally {
            itsDbUpdateCount = SyncDb.getUpdateCount();
            itsDb.endTransaction();
        }
    }

    /** Update database files from the remote files */
    private void updateDbFiles(@NonNull SyncRemoteFiles remoteFiles)
    {
        List<DbFile> dbfiles = SyncDb.getFiles(itsProvider.itsId, itsDb);
        if (remoteFiles.hasUpdatedRemoteIds()) {
            boolean modified = false;
            for (DbFile dbfile: dbfiles) {
                String remoteId = remoteFiles.getUpdatedRemoteId(dbfile.itsId);
                if (remoteId != null) {
                    SyncDb.updateRemoteFile(
                            dbfile.itsId, remoteId,
                            dbfile.itsRemoteTitle, dbfile.itsRemoteFolder,
                            dbfile.itsRemoteModDate, dbfile.itsRemoteHash,
                            itsDb);
                    modified = true;
                }
            }
            if (modified) {
                dbfiles = SyncDb.getFiles(itsProvider.itsId, itsDb);
            }
        }

        Set<String> processedRemoteFiles = new HashSet<>();
        for (DbFile dbfile: dbfiles) {
            if ((dbfile.itsRemoteId == null) ||
                (dbfile.itsLocalChange == DbFile.FileChange.ADDED)) {

                ProviderRemoteFile remfile =
                        remoteFiles.getRemoteFileForNew(dbfile.itsId);
                if (remfile != null) {
                    String remoteId = remfile.getRemoteId();
                    SyncDb.updateRemoteFile(
                            dbfile.itsId, remoteId, remfile.getTitle(),
                            remfile.getFolder(), remfile.getModTime(),
                            remfile.getHash(), itsDb);
                    SyncDb.updateRemoteFileChange(
                            dbfile.itsId, DbFile.FileChange.ADDED, itsDb);
                    processedRemoteFiles.add(remoteId);
                }
            } else {
                ProviderRemoteFile remfile =
                        remoteFiles.getRemoteFile(dbfile.itsRemoteId);
                if (remfile != null) {
                    checkRemoteFileChange(dbfile, remfile);
                    processedRemoteFiles.add(dbfile.itsRemoteId);
                } else {
                    PasswdSafeUtil.dbginfo(itsTag,
                                           "updateDbFiles remove remote %s",
                                           dbfile.itsRemoteId);
                    SyncDb.updateRemoteFileDeleted(dbfile.itsId, itsDb);
                }
            }
        }

        for (ProviderRemoteFile remfile: remoteFiles.getRemoteFiles()) {
            String remoteId = remfile.getRemoteId();
            if (processedRemoteFiles.contains(remoteId)) {
                continue;
            }

            PasswdSafeUtil.dbginfo(itsTag, "updateDbFiles add remote %s",
                                   remoteId);
            SyncDb.addRemoteFile(itsProvider.itsId, remoteId,
                                 remfile.getTitle(), remfile.getFolder(),
                                 remfile.getModTime(), remfile.getHash(),
                                 itsDb);
        }
    }


    /** Resolve the sync operations after the database files are updated */
    private List<SyncOper<ProviderClientT>> resolveSyncOpers()
    {
        List<SyncOper<ProviderClientT>> opers = new ArrayList<>();
        List<DbFile> dbfiles = SyncDb.getFiles(itsProvider.itsId, itsDb);
        for (DbFile dbfile: dbfiles) {
            resolveSyncOper(dbfile, opers);
        }
        return opers;
    }


    /**
     * Sync the display name of the user
     */
    private void syncDisplayName() throws SQLException
    {
        String displayName = itsConnResult.getDisplayName();
        PasswdSafeUtil.dbginfo(itsTag, "syncDisplayName %s", displayName);
        if (!TextUtils.equals(itsProvider.itsDisplayName, displayName)) {
            SyncDb.updateProviderDisplayName(itsProvider.itsId, displayName,
                                             itsDb);
        }
    }


    /** Check for a remote file change and update */
    private void checkRemoteFileChange(DbFile dbfile, ProviderRemoteFile remfile)
    {
        String remTitle = remfile.getTitle();
        String remFolder = remfile.getFolder();
        long remModDate = remfile.getModTime();
        String remHash = remfile.getHash();
        boolean changed = true;
        do {
            if (!TextUtils.equals(dbfile.itsRemoteTitle, remTitle) ||
                !TextUtils.equals(dbfile.itsRemoteFolder, remFolder) ||
                (dbfile.itsRemoteModDate != remModDate) ||
                !TextUtils.equals(dbfile.itsRemoteHash, remHash) ||
                TextUtils.isEmpty(dbfile.itsLocalFile)) {
                break;
            }

            java.io.File localFile =
                    itsContext.getFileStreamPath(dbfile.itsLocalFile);
            if (!localFile.exists()) {
                break;
            }

            changed = false;
        } while(false);

        if (!changed) {
            return;
        }

        PasswdSafeUtil.dbginfo(itsTag, "checkRemoteFileChange update remote %s",
                               dbfile);
        SyncDb.updateRemoteFile(dbfile.itsId, dbfile.itsRemoteId,
                                remTitle, remFolder, remModDate, remHash,
                                itsDb);
        switch (dbfile.itsRemoteChange) {
        case NO_CHANGE:
        case REMOVED: {
            SyncDb.updateRemoteFileChange(dbfile.itsId,
                                          DbFile.FileChange.MODIFIED, itsDb);
            break;
        }
        case ADDED:
        case MODIFIED: {
            break;
        }
        }
    }


    /** Resolve the sync operations for a file */
    private void resolveSyncOper(DbFile dbfile,
                                 List<SyncOper<ProviderClientT>> opers)
            throws SQLException
    {
        if ((dbfile.itsLocalChange != DbFile.FileChange.NO_CHANGE) ||
            (dbfile.itsRemoteChange != DbFile.FileChange.NO_CHANGE)) {
            PasswdSafeUtil.dbginfo(itsTag, "resolveSyncOper %s", dbfile);
        }

        switch (dbfile.itsLocalChange) {
        case ADDED: {
            switch (dbfile.itsRemoteChange) {
            case ADDED:
            case MODIFIED: {
                logConflictFile(dbfile, true);
                splitConflictedFile(dbfile, opers);
                break;
            }
            case NO_CHANGE: {
                opers.add(createLocalToRemoteOper(dbfile));
                break;
            }
            case REMOVED: {
                logConflictFile(dbfile, true);
                recreateRemoteRemovedFile(dbfile, opers);
                break;
            }
            }
            break;
        }
        case MODIFIED: {
            switch (dbfile.itsRemoteChange) {
            case ADDED:
            case MODIFIED: {
                logConflictFile(dbfile, true);
                splitConflictedFile(dbfile, opers);
                break;
            }
            case NO_CHANGE: {
                opers.add(createLocalToRemoteOper(dbfile));
                break;
            }
            case REMOVED: {
                logConflictFile(dbfile, true);
                recreateRemoteRemovedFile(dbfile, opers);
                break;
            }
            }
            break;
        }
        case NO_CHANGE: {
            switch (dbfile.itsRemoteChange) {
            case ADDED:
            case MODIFIED: {
                opers.add(createRemoteToLocalOper(dbfile));
                break;
            }
            case NO_CHANGE: {
                // Nothing
                break;
            }
            case REMOVED: {
                opers.add(createRmFileOper(dbfile));
                break;
            }
            }
            break;
        }
        case REMOVED: {
            switch (dbfile.itsRemoteChange) {
            case ADDED:
            case MODIFIED: {
                logConflictFile(dbfile, false);
                DbFile newRemfile = splitRemoteToNewFile(dbfile);
                DbFile updatedLocalFile = SyncDb.getFile(dbfile.itsId, itsDb);

                opers.add(createRemoteToLocalOper(newRemfile));
                opers.add(createRmFileOper(updatedLocalFile));
                break;
            }
            case NO_CHANGE:
            case REMOVED: {
                opers.add(createRmFileOper(dbfile));
                break;
            }
            }
            break;
        }
        }
    }


    /** Split the file.  A new added remote file is created with the remote id,
     * and the file is updated to resemble a new local file with the same id but
     * a different name indicating a conflict
     */
    private void splitConflictedFile(DbFile dbfile,
                                     List<SyncOper<ProviderClientT>> opers)
            throws SQLException
    {
        DbFile newRemfile = splitRemoteToNewFile(dbfile);
        DbFile updatedLocalFile = updateFileAsLocallyAdded(
                dbfile, itsContext.getString(R.string.conflicted_local_copy));

        opers.add(createRemoteToLocalOper(newRemfile));
        opers.add(createLocalToRemoteOper(updatedLocalFile));
    }


    /** Recreate a remotely deleted file from local updates */
    private void recreateRemoteRemovedFile(
            DbFile dbfile,
            List<SyncOper<ProviderClientT>> opers)
            throws SQLException
    {
        resetRemoteFields(dbfile);
        DbFile updatedLocalFile = updateFileAsLocallyAdded(
                dbfile, itsContext.getString(R.string.recreated_local_copy));
        opers.add(createLocalToRemoteOper(updatedLocalFile));
    }


    /** Update a file to appear as a locally added file with a new name */
    private DbFile updateFileAsLocallyAdded(DbFile dbfile, String titlePrefix)
            throws SQLException
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH-mm-ss", Locale.US);
        String newTitle = String.format(Locale.US,
                "%s (%s) - %s",
                titlePrefix,
                dateFormat.format(System.currentTimeMillis()),
                dbfile.itsLocalTitle);
        SyncDb.updateLocalFile(
                dbfile.itsId, dbfile.itsLocalFile, newTitle,
                null, dbfile.itsLocalModDate, itsDb);
        SyncDb.updateLocalFileChange(
                dbfile.itsId, DbFile.FileChange.ADDED, itsDb);
        return SyncDb.getFile(dbfile.itsId, itsDb);
    }


    /** Split the remote information for the file into a new file.  The
     * remote fields for the existing file are reset. */
    private DbFile splitRemoteToNewFile(DbFile dbfile)
            throws SQLException
    {
        long newRemoteId = SyncDb.addRemoteFile(
                itsProvider.itsId, dbfile.itsRemoteId,
                dbfile.itsRemoteTitle, dbfile.itsRemoteFolder,
                dbfile.itsRemoteModDate, dbfile.itsRemoteHash, itsDb);
        DbFile newRemfile = SyncDb.getFile(newRemoteId, itsDb);

        resetRemoteFields(dbfile);

        return newRemfile;
    }


    /** Reset the remote fields for a file to their defaults */
    private void resetRemoteFields(DbFile dbfile)
            throws SQLException
    {
        SyncDb.updateRemoteFile(
                dbfile.itsId, null, null, null, -1, null, itsDb);
        SyncDb.updateRemoteFileChange(
                dbfile.itsId, DbFile.FileChange.NO_CHANGE, itsDb);
    }


    /** Log a conflicted file */
    private void logConflictFile(DbFile dbfile, boolean localName)
    {
        String filename = localName ? dbfile.getLocalTitleAndFolder() :
            dbfile.getRemoteTitleAndFolder();
        itsLogrec.addConflictFile(filename);

        String log = itsContext.getString(
                R.string.sync_conflict_log,
                filename, dbfile.itsLocalChange, dbfile.itsRemoteChange);
        itsLogrec.addEntry(log);
    }

    /**
     * A user of the database that checks for an ok state
     */
    private static abstract class CheckedDbUser implements DbUser
    {
        @Override
        public final void useDb(boolean dbOk) throws Exception
        {
            if (!dbOk) {
                throw new IllegalStateException("DB updated during sync");
            }
            useDb();
        }

        /**
         * Use the database
         */
        public abstract void useDb() throws Exception;
    }
}
