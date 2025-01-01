/*
 * Copyright (©) 2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import androidx.annotation.NonNull;

import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;

/**
 * The OneDrive service client and cached information
 */
public class OnedriveProviderClient
{
    @NonNull
    public final GraphServiceClient itsClient;

    @NonNull
    public final String itsDriveId;

    /**
     * Constructor
     */
    public OnedriveProviderClient(@NonNull GraphServiceClient client)
            throws ApiException
    {
        itsClient = client;

        var drive = itsClient.me().drive().get();
        if (drive == null) {
            throw new ApiException("No drive");
        }
        var id = drive.getId();
        if (id == null) {
            throw new ApiException("No drive ID");
        }
        itsDriveId = id;
    }
}
