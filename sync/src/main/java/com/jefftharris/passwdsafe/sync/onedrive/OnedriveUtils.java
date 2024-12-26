/*
 * Copyright (©) 2019-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import androidx.annotation.NonNull;

import com.microsoft.graph.drives.item.items.item.DriveItemItemRequestBuilder;

/**
 * OneDrive utilities
 */
public class OnedriveUtils
{
    private static final String ROOT_PATH = "root:/";

    /**
     * Get a request builder for accessing a file path
     */
    @NonNull
    public static DriveItemItemRequestBuilder getFilePathRequest(
            @NonNull OnedriveProviderClient providerClient,
            @NonNull String path)
    {
        if (path.length() > 1) {
            path = ROOT_PATH + path.substring(1) + ":";
        } else {
            path = "root";
        }

        return providerClient.itsClient
                .drives()
                .byDriveId(providerClient.itsDriveId)
                .items()
                .byDriveItemId(path);
    }
}
