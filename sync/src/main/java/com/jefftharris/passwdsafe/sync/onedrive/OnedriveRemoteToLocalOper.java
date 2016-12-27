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
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;
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
        super(dbfile);
    }

    /** Perform the sync operation */
    @Override
    public void doOper(IOneDriveService providerClient,
                       Context ctx) throws RetrofitError, IOException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncRemoteToLocal %s", itsFile);
        setLocalFileName(SyncHelper.getLocalFileName(itsFile.itsId));

        File tmpfile = File.createTempFile("tmp", "psafe", ctx.getFilesDir());
        OutputStream os = null;
        InputStream is = null;
        HttpURLConnection urlConn = null;
        try {
            Item item = providerClient.getItemByPath(itsFile.itsRemoteId, null);
            URL url = new URL(item.Content_downloadUrl);
            urlConn = (HttpURLConnection)url.openConnection();
            urlConn.setInstanceFollowRedirects(true);
            is = urlConn.getInputStream();

            os = new BufferedOutputStream(new FileOutputStream(tmpfile));
            Utils.copyStream(is, os);
        } catch (Exception e) {
            if (!tmpfile.delete()) {
                Log.e(TAG, "Can't delete tmp file " + tmpfile);
            }
            throw e;
        } finally {
            Utils.closeStreams(is, os);

            if (urlConn != null) {
                urlConn.disconnect();
            }
        }

        File localFile = ctx.getFileStreamPath(getLocalFileName());
        if (!tmpfile.renameTo(localFile)) {
            throw new IOException("Error renaming to " + localFile);
        }
        if (!localFile.setLastModified(itsFile.itsRemoteModDate)) {
            Log.e(TAG, "Can't set mod time on " + itsFile);
        }
        setDownloaded(true);
    }
}
