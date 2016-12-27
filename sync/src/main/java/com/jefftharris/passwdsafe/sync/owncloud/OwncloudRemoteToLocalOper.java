/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation;

/**
 * An ownCloud sync operation to sync a remote file to a local one
 */
public class OwncloudRemoteToLocalOper extends
        AbstractRemoteToLocalSyncOper<OwnCloudClient>
{
    private static final String TAG = "OwncloudRemoteToLocalOp";

    /** Constructor */
    public OwncloudRemoteToLocalOper(DbFile file)
    {
        super(file);
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.AbstractSyncOper#doOper(java.lang.Object, android.content.Context)
     */
    @Override
    public void doOper(OwnCloudClient providerClient, Context ctx)
            throws IOException
    {
        PasswdSafeUtil.dbginfo(TAG, "syncRemoteToLocal %s", itsFile);
        setLocalFileName(SyncHelper.getLocalFileName(itsFile.itsId));

        File tmpfile = File.createTempFile("tmp", "psafe", ctx.getFilesDir());
        try {
            DownloadRemoteFileOperation oper = new DownloadRemoteFileOperation(
                    itsFile.itsRemoteId, tmpfile, true);
            RemoteOperationResult res = oper.execute(providerClient);
            OwncloudSyncer.checkOperationResult(res, ctx);
        } catch (IOException e) {
            if (!tmpfile.delete()) {
                Log.e(TAG, "Can't delete tmp file " + tmpfile);
            }
            throw e;
//            ctx.deleteFile(getLocalFileName());
//            setDownloaded(false);
//            Log.e(TAG, "Sync failed to download: " + itsFile, e);
//            throw e;
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
