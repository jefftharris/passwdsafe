/*
 * Copyright (©) 2021-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.content.ClipboardManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * The ApiCompatP class contains helper methods that are usable on
 * P and higher
 */
@RequiresApi(Build.VERSION_CODES.P)
public final class ApiCompatP
{
    /**
     * Clear the clipboard
     */
    public static void clearClipboard(@NonNull ClipboardManager clipMgr)
    {
        clipMgr.clearPrimaryClip();
    }
}
