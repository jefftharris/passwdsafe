/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Abstract sync operation to sync a remote file to a local file
 */
public abstract class AbstractRemoteToLocalSyncOper<ProviderClientT>
        extends SyncOper<ProviderClientT>
{
    private File itsDownloadFile;

    /** Constructor */
    protected AbstractRemoteToLocalSyncOper(DbFile dbfile, String tag)
    {
        super(dbfile, tag);
    }

    @Override
    public final void doOper(ProviderClientT providerClient,
                             @NonNull Context ctx)
        throws Exception
    {
        PasswdSafeUtil.dbginfo(itsTag, "syncRemoteToLocal %s", itsFile);
        itsDownloadFile = File.createTempFile("passwd", ".tmp",
                                              ctx.getFilesDir());
        doDownload(itsDownloadFile, providerClient);
    }

    @Override
    public final void doPostOperUpdate(boolean updateLocal,
                                       SQLiteDatabase db, Context ctx)
            throws IOException, SQLException
    {
        String localFileName = SyncHelper.getLocalFileName(itsFile.itsId);
        if (updateLocal) {
            File localFile = ctx.getFileStreamPath(localFileName);
            if (!itsDownloadFile.renameTo(localFile)) {
                throw new IOException("Error renaming to " + localFile);
            }
            itsDownloadFile = null;

            if (!localFile.setLastModified(itsFile.itsRemoteModDate)) {
                Log.e(itsTag, "Can't set mod time on " + itsFile);
            }
        }

        try {
            if (updateLocal) {
                SyncDb.updateLocalFile(itsFile.itsId, localFileName,
                                       itsFile.itsRemoteTitle,
                                       itsFile.itsRemoteFolder,
                                       itsFile.itsRemoteModDate, db);
            }
            clearFileChanges(updateLocal, db);
        } catch (SQLException e) {
            ctx.deleteFile(localFileName);
            throw e;
        }
    }

    @Override
    public final void finish()
    {
        if ((itsDownloadFile != null) && !itsDownloadFile.delete()) {
            Log.e(itsTag, "Can't delete tmp file " + itsDownloadFile);
        }
    }

    @NonNull
    @Override
    public final String getDescription(@NonNull Context ctx)
    {
        return ctx.getString(R.string.sync_oper_remote_to_local,
                             itsFile.getRemoteTitleAndFolder());
    }

    /**
     * Download the remote file to the given destination file
     */
    protected abstract void doDownload(File destFile,
                                       ProviderClientT providerClient)
            throws Exception;
}
