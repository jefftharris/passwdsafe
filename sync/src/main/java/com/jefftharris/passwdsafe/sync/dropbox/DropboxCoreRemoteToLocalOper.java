/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.dropbox;

import android.content.Context;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;

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
        super(dbfile);
    }

    /** Perform the sync operation */
    @Override
    public void doOper(DbxClientV2 providerClient,
                       Context ctx) throws DbxException, IOException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncRemoteToLocal %s", itsFile);
        setLocalFileName(SyncHelper.getLocalFileName(itsFile.itsId));

        File tmpfile = File.createTempFile("tmp", "psafe", ctx.getFilesDir());
        OutputStream fos = null;
        try {
            fos = new BufferedOutputStream(new FileOutputStream(tmpfile));
            providerClient.files().download(itsFile.itsRemoteId).download(fos);
        } catch (Exception e) {
            if (!tmpfile.delete()) {
                Log.e(TAG, "Can't delete tmp file " + tmpfile);
            }
            throw e;
        } finally {
            Utils.closeStreams(fos);
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
