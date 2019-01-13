/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;

import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An OneDrive sync operation to sync a remote file to a local one
 */
public class OnedriveRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<IGraphServiceClient>
{
    private static final String TAG = "OnedriveRemoteToLocalOp";

    /** Constructor */
    public OnedriveRemoteToLocalOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    IGraphServiceClient providerClient,
                                    Context ctx)
            throws IOException, ClientException
    {
        OutputStream os = null;
        InputStream is = null;
        try {
            IDriveItemRequestBuilder rootRequest =
                    OnedriveUtils.getFilePathRequest(providerClient,
                                                     itsFile.itsRemoteId);
            is = rootRequest.getContent().buildRequest().get();

            os = new BufferedOutputStream(new FileOutputStream(destFile));
            Utils.copyStream(is, os);
        } finally {
            Utils.closeStreams(is, os);
        }
    }
}
