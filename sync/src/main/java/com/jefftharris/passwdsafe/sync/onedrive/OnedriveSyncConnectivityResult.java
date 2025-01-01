/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.microsoft.graph.serviceclient.GraphServiceClient;

public final class OnedriveSyncConnectivityResult extends SyncConnectivityResult
{
    private final GraphServiceClient itsService;

    /**
     * Constructor
     */
    public OnedriveSyncConnectivityResult(String displayName,
                                          GraphServiceClient service)
    {
        super(displayName);
        itsService = service;
    }

    /**
     * Get the client
     */
    public GraphServiceClient getService()
    {
        return itsService;
    }
}
