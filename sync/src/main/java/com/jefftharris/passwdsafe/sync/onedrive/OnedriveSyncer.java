/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;
import android.net.Uri;

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
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.extensions.DriveItem;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;
import com.microsoft.graph.extensions.User;

import java.util.List;

/**
 * The OnedriveSyncer class encapsulates an OneDrive sync operation
 */
public class OnedriveSyncer extends ProviderSyncer<IGraphServiceClient>
{
    private static final String TAG = "OnedriveSyncer";

    /**
     * Constructor
     */
    public OnedriveSyncer(IGraphServiceClient service,
                          DbProvider provider,
                          SyncConnectivityResult connResult,
                          SyncLogRecord logrec, Context ctx)
    {
        super(service, provider, connResult, logrec, ctx, TAG);
    }


    /**
     * Get the account display name
     */
    public static String getDisplayName(IGraphServiceClient client)
    {
        User user = client.getMe().buildRequest().get();
        return user.displayName;
    }

    /**
     * Check whether the exception is a 404 not found exception
     * @throws ClientException if not a 404 error
     */
    public static void check404Error(ClientException e)
            throws ClientException
    {
        if (!e.getMessage().contains("\n404 : Not Found\n")) {
            throw e;
        }
    }


    /** Create a remote identifier from the local name of a file */
    public static String createRemoteIdFromLocal(DbFile dbfile)
    {
        return ProviderRemoteFile.PATH_SEPARATOR +
               Uri.encode(dbfile.itsLocalTitle);
    }


    @Override
    protected SyncRemoteFiles getSyncRemoteFiles(List<DbFile> dbfiles)
    {
        SyncRemoteFiles files = new SyncRemoteFiles();
        for (DbFile dbfile: dbfiles) {
            if (dbfile.itsRemoteId == null) {
                DriveItem item = getRemoteFile(createRemoteIdFromLocal(dbfile));
                if (item != null) {
                    files.addRemoteFileForNew(dbfile.itsId,
                                              new OnedriveProviderFile(item));
                }
            } else {
                switch (dbfile.itsRemoteChange) {
                case NO_CHANGE:
                case ADDED:
                case MODIFIED: {
                    DriveItem item = getRemoteFile(dbfile.itsRemoteId);
                    if (item != null) {
                        files.addRemoteFile(new OnedriveProviderFile(item));
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
    protected AbstractLocalToRemoteSyncOper<IGraphServiceClient>
    createLocalToRemoteOper(DbFile dbfile)
    {
        return new OnedriveLocalToRemoteOper(dbfile);
    }


    /**
     * Create an operation to sync remote to local
     */
    @Override
    protected AbstractRemoteToLocalSyncOper<IGraphServiceClient>
    createRemoteToLocalOper(DbFile dbfile)
    {
        return new OnedriveRemoteToLocalOper(dbfile);
    }


    /**
     * Create an operation to remove a file
     */
    @Override
    protected AbstractRmSyncOper<IGraphServiceClient>
    createRmFileOper(DbFile dbfile)
    {
        return new OnedriveRmFileOper(dbfile);
    }


    /**
     * Get a remote file's entry from OneDrive
     * @return The file's entry if found; null if not found or deleted
     */
    private DriveItem getRemoteFile(String remoteId) throws ClientException
    {
        try {
            IDriveItemRequestBuilder rootRequest =
                    OnedriveProvider.getFilePathRequest(itsProviderClient,
                                                        remoteId);
            DriveItem item = rootRequest.buildRequest().get();
            if ((item != null) && (item.deleted != null)) {
                return null;
            }
            return item;
        } catch (ClientException e) {
            check404Error(e);
            return null;
        }
    }
}
