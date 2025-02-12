/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.os.StrictMode;
import androidx.annotation.NonNull;

/**
 * The StrictModePermitGuard class manages permitted actions for strict mode
 */
public class StrictModePermitGuard implements AutoCloseable
{
    /**
     * Permitted action
     */
    public enum Permit
    {
        SLOW_CALLS
    }

    private final StrictMode.ThreadPolicy itsThreadPolicy;

    /**
     * Constructor
     */
    public StrictModePermitGuard(@NonNull Permit permit)
    {
        itsThreadPolicy = StrictMode.getThreadPolicy();
        var builder = new StrictMode.ThreadPolicy.Builder(itsThreadPolicy);
        switch (permit) {
        case SLOW_CALLS: {
            builder.permitCustomSlowCalls();
            break;
        }
        }
        StrictMode.setThreadPolicy(builder.build());
    }

    @Override
    public void close()
    {
        if (itsThreadPolicy != null) {
            StrictMode.setThreadPolicy(itsThreadPolicy);
        }

    }
}
