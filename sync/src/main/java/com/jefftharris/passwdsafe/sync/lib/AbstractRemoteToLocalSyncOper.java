/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
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

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Abstract sync operation to sync a remote file to a local file
 */
public abstract class AbstractRemoteToLocalSyncOper<ProviderClientT>
        extends SyncOper<ProviderClientT>
{
    private String itsLocalFileName;
    private boolean itsIsDownloaded = false;

    /** Constructor */
    protected AbstractRemoteToLocalSyncOper(DbFile dbfile, String tag)
    {
        super(dbfile, tag);
    }

    @Override
    public final void doOper(ProviderClientT providerClient, Context ctx)
        throws Exception
    {
        PasswdSafeUtil.dbginfo(itsTag, "syncRemoteToLocal %s", itsFile);
        itsLocalFileName = SyncHelper.getLocalFileName(itsFile.itsId);

        File tmpfile = File.createTempFile("tmp", "psafe", ctx.getFilesDir());
        try {
            doDownload(tmpfile, providerClient, ctx);

            File localFile = ctx.getFileStreamPath(itsLocalFileName);
            if (!tmpfile.renameTo(localFile)) {
                throw new IOException("Error renaming to " + localFile);
            }
            tmpfile = null;

            if (!localFile.setLastModified(itsFile.itsRemoteModDate)) {
                Log.e(itsTag, "Can't set mod time on " + itsFile);
            }
            itsIsDownloaded = true;
        } finally {
            if ((tmpfile != null) && !tmpfile.delete()) {
                Log.e(itsTag, "Can't delete tmp file " + tmpfile);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.SyncOper#doPostOperUpdate(android.database.sqlite.SQLiteDatabase, android.content.Context)
     */
    @Override
    public final void doPostOperUpdate(SQLiteDatabase db, Context ctx)
            throws IOException, SQLException
    {
        if (itsIsDownloaded && (itsLocalFileName != null)) {
            try {
                SyncDb.updateLocalFile(itsFile.itsId, itsLocalFileName,
                                       itsFile.itsRemoteTitle,
                                       itsFile.itsRemoteFolder,
                                       itsFile.itsRemoteModDate, db);
                clearFileChanges(db);
            } catch (SQLException e) {
                ctx.deleteFile(itsLocalFileName);
                throw e;
            }
        }
    }

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.SyncOper#getDescription(android.content.Context)
     */
    @Override
    public final String getDescription(Context ctx)
    {
        return ctx.getString(R.string.sync_oper_remote_to_local,
                             itsFile.getRemoteTitleAndFolder());
    }

    /**
     * Download the remote file to the given destination file
     */
    protected abstract void doDownload(File destFile,
                                       ProviderClientT providerClient,
                                       Context ctx) throws Exception;

}
