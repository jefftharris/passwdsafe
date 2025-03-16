/*
 * Copyright (©) 2020-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib.view;

import android.os.Build;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The GuiUtilsQ class contains helper GUI methods that are usable on
 * Q and higher
 */

@RequiresApi(Build.VERSION_CODES.Q)
public final class GuiUtilsQ
{
    /**
     * Disable force-dark mode
     */
    public static void disableForceDark(@NonNull View view)
    {
        view.setForceDarkAllowed(false);
    }
}
