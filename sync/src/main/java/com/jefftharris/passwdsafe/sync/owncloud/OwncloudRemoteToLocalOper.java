/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import android.content.Context;

import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation;

import java.io.File;
import java.io.IOException;

/**
 * An ownCloud sync operation to sync a remote file to a local one
 */
public class OwncloudRemoteToLocalOper extends
        AbstractRemoteToLocalSyncOper<OwnCloudClient>
{
    private static final String TAG = "OwncloudRemoteToLocalOp";

    /** Constructor */
    public OwncloudRemoteToLocalOper(DbFile file)
    {
        super(file, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    OwnCloudClient providerClient,
                                    Context ctx)
            throws IOException
    {
        DownloadRemoteFileOperation oper = new DownloadRemoteFileOperation(
                itsFile.itsRemoteId, destFile, true);
        RemoteOperationResult res = oper.execute(providerClient);
        OwncloudSyncer.checkOperationResult(res, ctx);
    }
}
