/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.dropbox;

import androidx.annotation.NonNull;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.jefftharris.passwdsafe.sync.lib.AbstractRmSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;

/**
 * A Dropbox sync operation to remove a file
 */
public class DropboxCoreRmFileOper extends AbstractRmSyncOper<DbxClientV2>
{
    private static final String TAG = "DropboxCoreRmFileOper";

    /** Constructor */
    public DropboxCoreRmFileOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    /** Remove the remote file */
    @Override
    protected void doRemoteRemove(@NonNull DbxClientV2 providerClient)
            throws DbxException
    {
        providerClient.files().deleteV2(itsFile.itsRemoteId);
    }
}
