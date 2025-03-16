/*
 * Copyright (©) 2023-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * The ApiCompatTiramisu class contains helper methods that are usable on
 * Tiramisu and higher
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class ApiCompatTiramisu
{
    /**
     * API compatible call to register a broadcast receiver
     */
    public static void registerNotExportedBroadcastReceiver(
            @NonNull Context ctx,
            @Nullable BroadcastReceiver receiver,
            IntentFilter filter)
    {
        ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
}
