/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.extensions.IGraphServiceClient;

/**
 * OneDrive utilities
 */
public class OnedriveUtils
{
    /**
     * Get a request builder for accessing a file path
     */
    public static IDriveItemRequestBuilder getFilePathRequest(
            IGraphServiceClient client,
            String path)
    {
        IDriveItemRequestBuilder rootRequest =
                client.getMe().getDrive().getRoot();
        if (path.length() > 1) {
            rootRequest = rootRequest.getItemWithPath(path.substring(1));
        }
        return rootRequest;
    }
}
