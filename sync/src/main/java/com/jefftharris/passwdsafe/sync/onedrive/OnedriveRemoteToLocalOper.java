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
import com.microsoft.onedriveaccess.IOneDriveService;
import com.microsoft.onedriveaccess.model.Item;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import retrofit.RetrofitError;

/**
 * An OneDrive sync operation to sync a remote file to a local one
 */
public class OnedriveRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<IOneDriveService>
{
    private static final String TAG = "OnedriveRemoteToLocalOp";

    /** Constructor */
    public OnedriveRemoteToLocalOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    @Override
    protected final void doDownload(File destFile,
                                    IOneDriveService providerClient,
                                    Context ctx)
            throws RetrofitError, IOException
    {
        OutputStream os = null;
        InputStream is = null;
        HttpURLConnection urlConn = null;
        try {
            Item item = providerClient.getItemByPath(itsFile.itsRemoteId, null);
            URL url = new URL(item.Content_downloadUrl);
            urlConn = (HttpURLConnection)url.openConnection();
            urlConn.setInstanceFollowRedirects(true);
            is = urlConn.getInputStream();

            os = new BufferedOutputStream(new FileOutputStream(destFile));
            Utils.copyStream(is, os);
        } finally {
            Utils.closeStreams(is, os);

            if (urlConn != null) {
                urlConn.disconnect();
            }
        }
    }
}
