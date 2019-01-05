/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;

import com.jefftharris.passwdsafe.sync.lib.AbstractRmSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;

/**
 * An OneDrive sync operation to remove a file
 */
public class OnedriveRmFileOper
        extends AbstractRmSyncOper<IGraphServiceClient>
{
    private static final String TAG = "OnedriveRmFileOper";

    /** Constructor */
    public OnedriveRmFileOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    /** Remove the remote file */
    @Override
    protected void doRemoteRemove(IGraphServiceClient providerClient,
                                  Context ctx) throws ClientException
    {
        try {
            IDriveItemRequestBuilder rootRequest =
                    OnedriveProvider.getFilePathRequest(providerClient,
                                                        itsFile.itsRemoteId);
            rootRequest.buildRequest().delete();
        } catch (ClientException e) {
            OnedriveSyncer.check404Error(e);
        }
    }
}
