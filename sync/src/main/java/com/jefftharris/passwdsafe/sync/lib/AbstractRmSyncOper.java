/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Abstract sync operation to remove a file
 */
public abstract class AbstractRmSyncOper<ProviderClientT>
        extends SyncOper<ProviderClientT>
{
    private final boolean itsIsRmLocal;
    private final boolean itsIsRmRemote;

    /** Constructor */
    protected AbstractRmSyncOper(DbFile dbfile, String tag)
    {
        super(dbfile, tag);
        itsIsRmLocal = (itsFile.itsLocalFile != null);
        itsIsRmRemote = (!itsFile.itsIsRemoteDeleted &&
                (itsFile.itsRemoteId != null));
    }

    @Override
    public final void doOper(ProviderClientT providerClient, Context ctx)
            throws Exception
    {
        PasswdSafeUtil.dbginfo(itsTag, "removeFile %s", itsFile);
        if (itsIsRmRemote) {
            doRemoteRemove(providerClient);
        }
    }

    @Override
    public final void doPostOperUpdate(boolean updateLocal,
                                       SQLiteDatabase db, Context ctx)
            throws SQLException
    {
        if (updateLocal) {
            if (itsIsRmLocal) {
                ctx.deleteFile(itsFile.itsLocalFile);
            }
            SyncDb.removeFile(itsFile.itsId, db);
        } else {
            SyncDb.updateRemoteFileDeleted(itsFile.itsId, db);
        }
    }

    @Override
    public String getDescription(Context ctx)
    {
        String name = (itsFile.itsLocalTitle != null) ?
                itsFile.getLocalTitleAndFolder() :
                itsFile.getRemoteTitleAndFolder();

        if (itsIsRmLocal && !itsIsRmRemote) {
            return ctx.getString(R.string.sync_oper_rmfile_local, name);
        } else if (!itsIsRmLocal && itsIsRmRemote) {
            return ctx.getString(R.string.sync_oper_rmfile_remote, name);
        } else {
            return ctx.getString(R.string.sync_oper_rmfile, name);
        }
    }

    /** Remove the remote file */
    protected abstract void doRemoteRemove(ProviderClientT providerClient)
            throws Exception;
}
