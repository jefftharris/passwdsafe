/*
 * Copyright (©) 2019-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.net.Uri;
import androidx.annotation.NonNull;

import com.microsoft.graph.drives.item.items.item.DriveItemItemRequestBuilder;
import com.microsoft.graph.drives.item.items.item.children.ChildrenRequestBuilder;
import com.microsoft.graph.models.DriveItem;

/**
 * OneDrive utilities
 */
public class OnedriveUtils
{
    private static final String[] QUERY_SELECT =
            new String[]{"children", "deleted", "eTag", "file", "folder", "id",
                         "lastModifiedDateTime", "name", "parentReference"};

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
            path = ROOT_PATH + Uri.decode(path.substring(1)) + ":";
        } else {
            path = "root";
        }

        return providerClient.itsClient
                .drives()
                .byDriveId(providerClient.itsDriveId)
                .items()
                .byDriveItemId(path);
    }

    /**
     * Has a file been deleted
     */
    public static boolean isDeleted(DriveItem item)
    {
        return (item == null) || (item.getDeleted() != null);
    }

    /**
     * Update a GET request for a drive item
     */
    public static void updateGetItemRequest(
            @NonNull DriveItemItemRequestBuilder.GetRequestConfiguration requestCfg)
    {
        if (requestCfg.queryParameters != null) {
            requestCfg.queryParameters.select = QUERY_SELECT;
        }
    }

    /**
     * Update a GET request for the children of a drive item
     */
    public static void updateGetChildrenRequest(
            @NonNull ChildrenRequestBuilder.GetRequestConfiguration requestCfg)
    {
        if (requestCfg.queryParameters != null) {
            requestCfg.queryParameters.select = QUERY_SELECT;
        }
    }
}
