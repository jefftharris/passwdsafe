/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractLocalToRemoteSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.extensions.DriveItem;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IDriveItemStreamRequest;
import com.microsoft.graph.extensions.IGraphServiceClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * An OneDrive sync operation to sync a local file to a remote one
 */
public class OnedriveLocalToRemoteOper
        extends AbstractLocalToRemoteSyncOper<IGraphServiceClient>
{
    private static final String TAG = "OnedriveLocalToRemoteOp";

    /** Constructor */
    public OnedriveLocalToRemoteOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    /** Perform the sync operation */
    @Override
    public void doOper(IGraphServiceClient providerClient,
                       Context ctx) throws IOException, ClientException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncLocalToRemote %s", itsFile);

        File tmpFile = null;
        InputStream is = null;
        ByteArrayOutputStream os = null;
        try {
            File uploadFile;
            String remotePath;
            if (itsFile.itsLocalFile != null) {
                uploadFile = ctx.getFileStreamPath(itsFile.itsLocalFile);
                setLocalFile(uploadFile);
                if (isInsert()) {
                    remotePath =
                            OnedriveSyncer.createRemoteIdFromLocal(itsFile);
                } else {
                    remotePath = itsFile.itsRemoteId;
                }
            } else {
                tmpFile = File.createTempFile("passwd", ".psafe3");
                tmpFile.deleteOnExit();
                uploadFile = tmpFile;
                remotePath = OnedriveSyncer.createRemoteIdFromLocal(itsFile);
            }

            is = new BufferedInputStream(new FileInputStream(uploadFile));
            os = new ByteArrayOutputStream();
            Utils.copyStream(is, os);

            IDriveItemRequestBuilder requestBuilder =
                    OnedriveProvider.getFilePathRequest(providerClient,
                                                        remotePath);
            IDriveItemStreamRequest request =
                    requestBuilder.getContent().buildRequest();
            request.addHeader("Content-Type", PasswdSafeUtil.MIME_TYPE_PSAFE3);
            DriveItem updatedItem = request.put(os.toByteArray());
            setUpdatedFile(new OnedriveProviderFile(updatedItem));
        } finally {
            Utils.closeStreams(is, os);
            if ((tmpFile != null) && !tmpFile.delete()) {
                Log.e(TAG, "Can't delete temp file " + tmpFile);
            }
        }
    }
}
