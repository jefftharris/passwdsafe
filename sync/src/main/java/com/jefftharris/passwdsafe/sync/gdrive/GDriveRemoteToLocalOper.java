/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.gdrive;

import androidx.annotation.NonNull;

import com.google.api.services.drive.Drive;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A Google Drive sync operation to sync a remote file to a local file
 */
public class GDriveRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<Drive>
{
    private static final String TAG = "GDriveRemoteToLocalOper";

    /** Constructor */
    public GDriveRemoteToLocalOper(DbFile file)
    {
        super(file, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    @NonNull Drive providerClient)
            throws IOException
    {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(destFile));
            providerClient.files().get(itsFile.itsRemoteId)
                          .executeMediaAndDownloadTo(os);
        } finally {
            Utils.closeStreams(os);
        }
    }
}
