/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The ApiCompatR class contains helper methods that are usable on
 * R and higher
 */
@RequiresApi(Build.VERSION_CODES.R)
public final class ApiCompatR
{
    /**
     * Is the activity being shown on an external display
     */
    public static boolean isOnExternalDisplay(@NonNull Activity act)
    {
        var display = act.getDisplay();
        return (display != null) && (display.getDisplayId() !=
                                     Display.DEFAULT_DISPLAY);
    }
}
