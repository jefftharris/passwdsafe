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
import android.text.TextUtils;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Abstract sync operation to sync a local file to a remote file
 */
public abstract class AbstractLocalToRemoteSyncOper<ProviderClientT>
        extends SyncOper<ProviderClientT>
{
    private final boolean itsIsInsert;
    private File itsLocalFile;
    private ProviderRemoteFile itsUpdatedFile;

    /** Constructor */
    protected AbstractLocalToRemoteSyncOper(DbFile file, String tag)
    {
        super(file, tag);
        itsIsInsert = TextUtils.isEmpty(itsFile.itsRemoteId);
    }


    @Override
    public final String getDescription(Context ctx)
    {
        return ctx.getString(R.string.sync_oper_local_to_remote,
                             itsFile.getLocalTitleAndFolder());
    }


    @Override
    public final void doPostOperUpdate(boolean updateLocal,
                                       SQLiteDatabase db, Context ctx)
            throws IOException, SQLException
    {
        if (itsUpdatedFile == null) {
            return;
        }
        String title = itsUpdatedFile.getTitle();
        String folders = itsUpdatedFile.getFolder();
        long modDate = itsUpdatedFile.getModTime();
        SyncDb.updateRemoteFile(itsFile.itsId, itsUpdatedFile.getRemoteId(),
                                title, folders, modDate,
                                itsUpdatedFile.getHash(), db);
        if (updateLocal) {
            SyncDb.updateLocalFile(itsFile.itsId, itsFile.itsLocalFile,
                                   title, folders, modDate, db);
            if ((itsLocalFile != null) &&
                !itsLocalFile.setLastModified(modDate)) {
                Log.e(itsTag, "Can't set mod time on " + itsFile);
            }
        }
        clearFileChanges(updateLocal, db);
    }


    /** Get whether an insert is performed instead of an update */
    protected final boolean isInsert()
    {
        return itsIsInsert;
    }


    /** Get the local file which was updated */
    protected final File getLocalFile()
    {
        return itsLocalFile;
    }


    /** Set the local file which was updated */
    protected final void setLocalFile(File file)
    {
        itsLocalFile = file;
    }


    /** Set the updated remote file */
    protected final void setUpdatedFile(ProviderRemoteFile updatedFile)
    {
        PasswdSafeUtil.dbginfo(itsTag, "updated file: %s",
                               updatedFile.toDebugString());
        itsUpdatedFile = updatedFile;
    }
}
