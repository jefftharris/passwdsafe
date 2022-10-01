/*
 * Copyright (©) 2022 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.os.Build;
import androidx.annotation.NonNull;

/**
 * The ApiCompatM class contains helper methods that are usable on N and higher
 */
@TargetApi(Build.VERSION_CODES.N)
public class ApiCompatN
{
    /**
     * Are notifications enabled
     */
    public static boolean areNotificationsEnabled(
            @NonNull NotificationManager notifyMgr)
    {
        return notifyMgr.areNotificationsEnabled();
    }
}
