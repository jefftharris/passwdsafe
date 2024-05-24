/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.dropbox;

import androidx.annotation.NonNull;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A Dropbox sync operation to sync a remote file to a local one
 */
public class DropboxCoreRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<DbxClientV2>
{
    private static final String TAG = "DropboxCoreRemoteToLoca";

    /** Constructor */
    public DropboxCoreRemoteToLocalOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    @NonNull DbxClientV2 providerClient)
            throws DbxException, IOException
    {
        OutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(destFile));
            try (var downloader =
                         providerClient.files().download(itsFile.itsRemoteId)) {
                downloader.download(fos);
            }
        } finally {
            Utils.closeStreams(fos);
        }
    }
}
