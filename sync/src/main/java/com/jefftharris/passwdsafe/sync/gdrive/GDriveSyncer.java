/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.gdrive;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.SyncUpdateHandler;
import com.jefftharris.passwdsafe.sync.lib.AbstractLocalToRemoteSyncOper;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.AbstractRmSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.jefftharris.passwdsafe.sync.lib.ProviderSyncer;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.jefftharris.passwdsafe.sync.lib.SyncRemoteFiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Syncer class encapsulates a sync operation
 */
public class GDriveSyncer extends ProviderSyncer<Drive>
{
    private final FileFolders itsFileFolders;
    private SyncUpdateHandler.GDriveState itsSyncState =
            SyncUpdateHandler.GDriveState.OK;

    private static final String TAG = "GDriveSyncer";

    /** Constructor */
    public GDriveSyncer(Drive drive,
                        DbProvider provider,
                        SyncConnectivityResult connResult,
                        SyncLogRecord logrec,
                        Context ctx)
    {
        super(drive, provider, connResult, logrec, ctx, TAG);
        itsFileFolders = new FileFolders(itsProviderClient);
    }

    /**
     * Get the account display name
     */
    public static String getDisplayName(Drive drive) throws IOException
    {
        About about = drive.about().get()
                           .setFields(GDriveProvider.ABOUT_FIELDS)
                           .execute();
        return (about.getUser() != null) ?
               about.getUser().getDisplayName() : null;
    }

    /** Get the sync state */
    public SyncUpdateHandler.GDriveState getSyncState()
    {
        return itsSyncState;
    }

    @Override
    protected SyncRemoteFiles getSyncRemoteFiles(List<DbFile> dbfiles)
            throws IOException
    {
        if (itsProviderClient == null) {
            itsSyncState = SyncUpdateHandler.GDriveState.PENDING_AUTH;
            return null;
        }

        SyncRemoteFiles driveFiles = new SyncRemoteFiles();

        String query =
                "not trashed" +
                " and ( mimeType = 'application/octet-stream' or " +
                "       mimeType = 'binary/octet-stream' or " +
                "       mimeType = '" + PasswdSafeUtil.MIME_TYPE_PSAFE3 + "' )" +
                " and fullText contains '.psafe3'";
        for (File file: listFiles(query)) {
            if (!isSyncFile(file)) {
                if (isFolderFile(file)) {
                    PasswdSafeUtil.dbginfo(TAG, "isdir %s", file);
                }
                continue;
            }
            PasswdSafeUtil.dbginfo(TAG, "File %s", fileToString(file));
            driveFiles.addRemoteFile(new GDriveProviderFile(
                    file, itsFileFolders.computeFileFolders(file)));
        }

        for (DbFile dbfile: dbfiles) {
            if (dbfile.itsRemoteId != null) {
                checkRenamedOrDeletedAndReplaced(dbfile, driveFiles);
            } else {
                checkRemoteFileForNew(dbfile, driveFiles);
            }
        }

        return driveFiles;
    }

    @Override
    protected AbstractLocalToRemoteSyncOper<Drive> createLocalToRemoteOper(
            DbFile dbfile)
    {
        return new GDriveLocalToRemoteOper(dbfile, itsFileFolders);
    }

    @Override
    protected AbstractRemoteToLocalSyncOper<Drive> createRemoteToLocalOper(
            DbFile dbfile)
    {
        return new GDriveRemoteToLocalOper(dbfile);
    }

    @Override
    protected AbstractRmSyncOper<Drive> createRmFileOper(DbFile dbfile)
    {
        return new GDriveRmFileOper(dbfile);
    }

    /**
     * Check whether any files were renamed/deleted and replaced with a
     * different file in the same location with the same name
     */
    private void checkRenamedOrDeletedAndReplaced(DbFile dbfile,
                                                  SyncRemoteFiles driveFiles)
            throws IOException
    {
        switch (dbfile.itsRemoteChange) {
        case NO_CHANGE: {
            File dbremfile =
                    itsFileFolders.getCachedFile(dbfile.itsRemoteId);
            if (dbremfile == null) {
                break;
            } else if (isSyncFile(dbremfile)) {
                ProviderRemoteFile remfile =
                        driveFiles.getRemoteFile(dbfile.itsRemoteId);
                if (remfile == null) {
                    break;
                }
                if (TextUtils.equals(dbfile.itsRemoteTitle,
                                     remfile.getTitle()) &&
                    TextUtils.equals(dbfile.itsRemoteFolder,
                                     remfile.getFolder())) {
                    break;
                }
            }

            List<String> parents = dbremfile.getParents();
            if (parents == null) {
                break;
            }
            PasswdSafeUtil.dbginfo(TAG, "check replace %s", dbremfile);
            parentsloop:
            for (String parent: parents) {
                for (File replacedFile: listFiles(
                        String.format("'%s' in parents and name='%s'",
                                      encodeQueryVal(parent),
                                      encodeQueryVal(dbfile.itsRemoteTitle)))) {
                    if (driveFiles.getRemoteFile(replacedFile.getId()) ==
                        null) {
                        continue;
                    }

                    PasswdSafeUtil.dbginfo(TAG, "File %s replaced with %s",
                                           fileToString(dbremfile),
                                           fileToString(replacedFile));
                    driveFiles.addUpdatedRemoteId(dbfile.itsId,
                                                  replacedFile.getId());
                    break parentsloop;
                }
            }
            break;
        }
        case ADDED:
        case MODIFIED:
        case REMOVED: {
            break;
        }
        }
    }

    /**
     * Check whether there is a remote file for a new local file
     */
    private void checkRemoteFileForNew(DbFile dbfile,
                                       SyncRemoteFiles driveFiles)
            throws IOException
    {
        List<File> fileList =
                listFiles(String.format("'root' in parents and name='%s'",
                                        encodeQueryVal(dbfile.itsLocalTitle)));
        if (!fileList.isEmpty()) {
            File remfile = fileList.get(0);
            driveFiles.addRemoteFileForNew(
                    dbfile.itsId,
                    new GDriveProviderFile(
                            remfile,
                            itsFileFolders.computeFileFolders(remfile)));
        }
    }

    /** List files */
    private List<File> listFiles(String query) throws IOException
    {
        ArrayList<File> retfiles = new ArrayList<>();

        Drive.Files.List request = itsProviderClient
                .files().list().setQ(query)
                .setFields("nextPageToken,files(" +
                           GDriveProvider.FILE_FIELDS + ")");
        do {
            FileList files = request.execute();
            PasswdSafeUtil.dbginfo(TAG, "num files: %d",
                                   files.getFiles().size());
            retfiles.addAll(files.getFiles());
            request.setPageToken(files.getNextPageToken());
        } while(!TextUtils.isEmpty(request.getPageToken()));

        return retfiles;
    }

    /** Should the file be synced */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isSyncFile(@NonNull File file)
    {
        if (isFolderFile(file) || file.getTrashed()) {
            return false;
        }
        String ext = file.getFileExtension();
        return (ext != null) && ext.equals("psafe3");
    }

    /** Encode a value used in a query */
    private static String encodeQueryVal(String str)
    {
        return str.replace("'", "\\'");
    }

    /** Is the file a folder */
    public static boolean isFolderFile(@NonNull File file)
    {
        return !file.getTrashed() &&
                GDriveProvider.FOLDER_MIME.equals(file.getMimeType());
    }


    /** Get a string form for a remote file */
    public static String fileToString(File file)
    {
        if (file == null) {
            return "{null}";
        }
        return String.format(Locale.US,
                             "{id:%s, name:%s, mime:%s, md5:%s, mod:%d}",
                             file.getId(), file.getName(),
                             file.getMimeType(), file.getMd5Checksum(),
                             file.getModifiedTime().getValue());
    }
}
