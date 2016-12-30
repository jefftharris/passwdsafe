/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import java.io.IOException;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 *  A generic sync operation
 */
public abstract class SyncOper<ProviderClientT>
{
    protected final DbFile itsFile;
    protected final String itsTag;

    /** Constructor */
    protected SyncOper(DbFile file, String tag)
    {
        itsFile = file;
        itsTag = tag;
    }

    /** Get the sync database file */
    public DbFile getFile()
    {
        return itsFile;
    }

    /** Perform the sync operation */
    public abstract void doOper(ProviderClientT providerClient, Context ctx)
            throws Exception;

    /** Perform the database update after the sync operation */
    @SuppressWarnings("RedundantThrows")
    public abstract void doPostOperUpdate(boolean updateLocal,
                                          SQLiteDatabase db, Context ctx)
            throws IOException, SQLException;

    /** Finish the operation */
    public void finish()
    {
    }

    /** Get a description of the operation */
    public abstract String getDescription(Context ctx);

    /** Clear the file change indications */
    protected void clearFileChanges(boolean updateLocal, SQLiteDatabase db)
            throws SQLException
    {
        SyncDb.updateRemoteFileChange(itsFile.itsId,
                                      DbFile.FileChange.NO_CHANGE, db);
        if (updateLocal) {
            SyncDb.updateLocalFileChange(itsFile.itsId,
                                         DbFile.FileChange.NO_CHANGE, db);
        }
    }
}
