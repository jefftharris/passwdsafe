/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import com.microsoft.identity.common.internal.providers.oauth2.AuthorizationStrategy;

/**
 * Activity request codes
 */
public interface ActivityRequest
{
    int DROPBOX_LINK = 1;
    int BOX_AUTH = 2;
    int ONEDRIVE_LINK = AuthorizationStrategy.BROWSER_FLOW;
    int OWNCLOUD_LINK = 4;
    int PERMISSIONS = 5;
    int APP_SETTINGS = 6;
    int GDRIVE_PLAY_LINK = 7;
    int GDRIVE_PLAY_SERVICES_ERROR = 8;
}
