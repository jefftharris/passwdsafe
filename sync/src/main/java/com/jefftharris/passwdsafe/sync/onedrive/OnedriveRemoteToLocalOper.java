/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.kiota.ApiException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An OneDrive sync operation to sync a remote file to a local one
 */
public class OnedriveRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<OnedriveProviderClient>
{
    private static final String TAG = "OnedriveRemoteToLocalOp";

    /** Constructor */
    public OnedriveRemoteToLocalOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    OnedriveProviderClient providerClient)
            throws ApiException, IOException, NullPointerException
    {
        OutputStream os = null;
        InputStream is = null;
        try {
            var request = OnedriveUtils.getFilePathRequest(providerClient,
                                                           itsFile.itsRemoteId);
            is = Objects.requireNonNull(request.content().get(),
                                        "No file content");
            os = new BufferedOutputStream(new FileOutputStream(destFile));
            Utils.copyStream(is, os);
        } finally {
            Utils.closeStreams(is, os);
        }
    }
}
