/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractLocalToRemoteSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.graph.drives.item.items.item.content.ContentRequestBuilder;
import com.microsoft.kiota.ApiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * An OneDrive sync operation to sync a local file to a remote one
 */
public class OnedriveLocalToRemoteOper
        extends AbstractLocalToRemoteSyncOper<OnedriveProviderClient>
{
    private static final String TAG = "OnedriveLocalToRemoteOp";

    /** Constructor */
    public OnedriveLocalToRemoteOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    /** Perform the sync operation */
    @Override
    public void doOper(OnedriveProviderClient providerClient,
                       Context ctx) throws ApiException, IOException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncLocalToRemote %s", itsFile);

        File tmpFile = null;
        InputStream is = null;
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

            // Do not use a buffered stream as the put function may reset a
            // location with an error
            is = new FileInputStream(uploadFile);
            var request = OnedriveUtils.getFilePathRequest(providerClient,
                                                           remotePath);
            var updatedItem = request
                    .content()
                    .put(is, OnedriveLocalToRemoteOper::configureRequest);
            setUpdatedFile(new OnedriveProviderFile(updatedItem));
        } finally {
            Utils.closeStreams(is);
            if ((tmpFile != null) && !tmpFile.delete()) {
                Log.e(TAG, "Can't delete temp file " + tmpFile);
            }
        }
    }

    /**
     * Configure the PUT request on the file
     */
    private static void configureRequest(
            @NonNull ContentRequestBuilder.PutRequestConfiguration requestCfg)
    {
        requestCfg.headers.put("Content-Type", Collections.singleton(
                PasswdSafeUtil.MIME_TYPE_PSAFE3));
    }
}
