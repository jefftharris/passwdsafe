/*
 * Copyright (©) 2015-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import com.jefftharris.passwdsafe.sync.lib.AbstractRmSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.microsoft.kiota.ApiException;

/**
 * An OneDrive sync operation to remove a file
 */
public class OnedriveRmFileOper
        extends AbstractRmSyncOper<OnedriveProviderClient>
{
    private static final String TAG = "OnedriveRmFileOper";

    /** Constructor */
    public OnedriveRmFileOper(DbFile dbfile)
    {
        super(dbfile, TAG);
    }

    /** Remove the remote file */
    @Override
    protected void doRemoteRemove(OnedriveProviderClient providerClient)
            throws ApiException
    {
        try {
            var request = OnedriveUtils.getFilePathRequest(providerClient,
                                                           itsFile.itsRemoteId);
            request.delete();
        } catch (ApiException e) {
            OnedriveSyncer.check404Error(e);
        }
    }
}
