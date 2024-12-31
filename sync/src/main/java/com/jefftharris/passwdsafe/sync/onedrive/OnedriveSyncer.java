/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
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
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;

import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * The OnedriveSyncer class encapsulates an OneDrive sync operation
 */
public class OnedriveSyncer extends ProviderSyncer<OnedriveProviderClient>
{
    private static final String TAG = "OnedriveSyncer";

    /**
     * Constructor
     */
    public OnedriveSyncer(GraphServiceClient service,
                          DbProvider provider,
                          SyncConnectivityResult connResult,
                          SyncLogRecord logrec, Context ctx)
            throws ApiException
    {
        super(new OnedriveProviderClient(service), provider, connResult, logrec,
              ctx, TAG);
    }


    /**
     * Get the account display name
     */
    public static String getDisplayName(@NonNull GraphServiceClient client)
    {
        User user = client.me().get();
        return (user != null) ? user.getDisplayName() : "";
    }

    /**
     * Check whether the exception is a 404 not found exception
     * @throws ApiException if not a 404 error
     */
    public static void check404Error(@NonNull ApiException e)
            throws ApiException
    {
        if (e.getResponseStatusCode() != 404) {
            throw e;
        }
    }


    /**
     * Does the exception represent a 401 unauthorized exception
     */
    public static boolean is401Error(@NonNull ApiException e)
    {
        return e.getResponseStatusCode() == 401;
    }


    /** Create a remote identifier from the local name of a file */
    @NonNull
    @Contract(pure = true)
    public static String createRemoteIdFromLocal(@NonNull DbFile dbfile)
    {
        return ProviderRemoteFile.PATH_SEPARATOR +
               Uri.encode(dbfile.itsLocalTitle);
    }


    @Override
    protected SyncRemoteFiles getSyncRemoteFiles(@NonNull List<DbFile> dbfiles)
    {
        SyncRemoteFiles files = new SyncRemoteFiles();
        for (DbFile dbfile: dbfiles) {
            if (dbfile.itsRemoteId == null) {
                DriveItem item = getRemoteFile(createRemoteIdFromLocal(dbfile));
                if (item != null) {
                    ProviderRemoteFile odfile = new OnedriveProviderFile(item);
                    PasswdSafeUtil.dbginfo(TAG, "file for local: %s",
                                           odfile.toDebugString());
                    files.addRemoteFileForNew(dbfile.itsId, odfile);
                }
            } else {
                switch (dbfile.itsRemoteChange) {
                case NO_CHANGE:
                case ADDED:
                case MODIFIED: {
                    DriveItem item = getRemoteFile(dbfile.itsRemoteId);
                    if (item != null) {
                        ProviderRemoteFile odfile =
                                new OnedriveProviderFile(item);
                        PasswdSafeUtil.dbginfo(TAG, "file: %s",
                                               odfile.toDebugString());
                        files.addRemoteFile(odfile);
                    }
                    break;
                }
                case REMOVED: {
                    break;
                }
                }
            }
        }
        return files;
    }


    /**
     * Create an operation to sync local to remote
     */
    @Override
    protected AbstractLocalToRemoteSyncOper<OnedriveProviderClient>
    createLocalToRemoteOper(DbFile dbfile)
    {
        return new OnedriveLocalToRemoteOper(dbfile);
    }


    /**
     * Create an operation to sync remote to local
     */
    @Override
    protected AbstractRemoteToLocalSyncOper<OnedriveProviderClient>
    createRemoteToLocalOper(DbFile dbfile)
    {
        return new OnedriveRemoteToLocalOper(dbfile);
    }


    /**
     * Create an operation to remove a file
     */
    @Override
    protected AbstractRmSyncOper<OnedriveProviderClient>
    createRmFileOper(DbFile dbfile)
    {
        return new OnedriveRmFileOper(dbfile);
    }


    /**
     * Get a remote file's entry from OneDrive
     * @return The file's entry if found; null if not found or deleted
     */
    @Nullable
    private DriveItem getRemoteFile(String remoteId) throws ApiException
    {
        try {
            // TODO: Use same query select as in files activity?
            var item = OnedriveUtils
                    .getFilePathRequest(itsProviderClient, remoteId)
                    .get();

            if ((item != null) && (item.getDeleted() != null)) {
                return null;
            }

            return item;
        } catch (ApiException e) {
            check404Error(e);
            return null;
        }
    }
}
