/*
 * Copyright (©) 2022-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.app.NotificationManager;
import android.content.ClipData;
import android.os.Build;
import android.os.PersistableBundle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The ApiCompatM class contains helper methods that are usable on N and higher
 */
@RequiresApi(Build.VERSION_CODES.N)
public class ApiCompatN
{
    private static final String CLIPBOARD_SENSITIVE_FLAG =
            "android.content.extra.IS_SENSITIVE";

    /**
     * Are notifications enabled
     */
    public static boolean areNotificationsEnabled(
            @NonNull NotificationManager notifyMgr)
    {
        return notifyMgr.areNotificationsEnabled();
    }

    /**
     * Set the clipboard data as sensitive
     */
    public static void setClipboardSensitive(@NonNull ClipData clip)
    {
        var extras = new PersistableBundle();
        extras.putBoolean(CLIPBOARD_SENSITIVE_FLAG, true);
        clip.getDescription().setExtras(extras);
    }
}
