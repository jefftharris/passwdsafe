/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.box;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;
import android.util.Log;

import com.box.androidsdk.content.BoxApiFile;
import com.box.androidsdk.content.BoxException;
import com.box.androidsdk.content.listeners.ProgressListener;
import com.box.androidsdk.content.models.BoxSession;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;

/**
 * A Box sync operation to sync a remote file to a local one
 */
public class BoxRemoteToLocalOper
        extends AbstractRemoteToLocalSyncOper<BoxSession>
{
    private static final String TAG = "BoxRemoteToLocalOper";

    /** Constructor */
    public BoxRemoteToLocalOper(DbFile file)
    {
        super(file);
    }

    @Override
    public void doOper(BoxSession providerClient, Context ctx)
            throws IOException, BoxException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncRemoteToLocal %s", itsFile);
        setLocalFileName(SyncHelper.getLocalFileName(itsFile.itsId));

        File tmpfile = File.createTempFile("tmp", "psafe", ctx.getFilesDir());
        OutputStream os = null;
        try {
            BoxApiFile fileApi = new BoxApiFile(providerClient);
            os = new BufferedOutputStream(new FileOutputStream(tmpfile));
            fileApi.getDownloadRequest(os, itsFile.itsRemoteId)
                   .setProgressListener(new ProgressListener()
                   {
                       @Override
                       public void onProgressChanged(long numBytes,
                                                     long totalBytes)
                       {
                           PasswdSafeUtil.dbginfo(TAG, "progress %d/%d",
                                                  numBytes, totalBytes);
                       }
                   })
                   .send();
        } catch (Exception e) {
            if (!tmpfile.delete()) {
                Log.e(TAG, "Can't delete tmp file " + tmpfile);
            }
            throw e;
        } finally {
            Utils.closeStreams(os);
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
