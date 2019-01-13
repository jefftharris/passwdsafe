/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.util.Log;

import com.microsoft.graph.logger.DefaultLogger;

/**
 * OneDrive API client logger extension.  Implementation copied from MS
 * DefaultLogger to turn off debug messages based on logging level
 */
public class ClientLogger extends DefaultLogger
{
    /**
     * Logs a debug message.
     * @param message The message.
     */
    @Override
    public void logDebug(final String message) {
        switch (getLoggingLevel()) {
        case Debug: {
            for (final String line : message.split("\n")) {
                Log.d(getTag(), line);
            }
            break;
        }
        case Error: {
            break;
        }
        }
    }

    /**
     * Creates the tag automatically.
     * @return The tag for the current method.
     * Sourced from https://gist.github.com/eefret/a9c7ac052854a10a8936
     */
    private String getTag() {
        try {
            final StringBuilder sb = new StringBuilder();
            final int callerStackDepth = 4;
            final String className = Thread.currentThread().getStackTrace()[callerStackDepth].getClassName();
            sb.append(className.substring(className.lastIndexOf(".") + 1));
            sb.append("[");
            sb.append(Thread.currentThread().getStackTrace()[callerStackDepth].getMethodName());
            sb.append("] - ");
            sb.append(Thread.currentThread().getStackTrace()[callerStackDepth].getLineNumber());
            return sb.toString();
        } catch (final Exception ex) {
            Log.e("ClientLogger", ex.getMessage());
        }
        return null;
    }
}
