/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;

import org.intellij.lang.annotations.PrintFormat;

/**
 * Logging class for PasswdSafe
 */
public final class PasswdSafeLog
{
    private static String[] DEBUG_TAGS = null;

    private static final String[] ALL_TAGS = new String[]{};

    /**
     * Log a formatted message at info level
     */
    public static void info(@NonNull String tag,
                            @PrintFormat String fmt,
                            Object... args)
    {
        PasswdSafeUtil.info(tag, fmt, args);
    }

    /**
     * Log a formatted message at debug level if enabled
     */
    public static void debug(@NonNull String tag,
                             @PrintFormat String fmt,
                             Object... args)
    {
        if (isDebugEnabled(tag)) {
            Log.d(tag, String.format(fmt, args));
        }
    }

    /**
     * Log a formatted message and stack trace at debug level if enabled
     */
    public static void debugTrace(@NonNull String tag,
                                  @PrintFormat String fmt,
                                  Object... args)
    {
        if (isDebugEnabled(tag)) {
            Log.d(tag, String.format(fmt, args), new Exception());
        }
    }

    /**
     *  Set the enabled debug tags
     */
    public static synchronized void setDebugTags(String tags)
    {
        if (tags != null) {
            tags = tags.trim();
        }
        if (TextUtils.equals(tags, "*")) {
            DEBUG_TAGS = ALL_TAGS;
        } else if (!TextUtils.isEmpty(tags)) {
            DEBUG_TAGS = TextUtils.split(tags, "\\s+");
        } else {
            DEBUG_TAGS = null;
        }
    }

    /**
     * Check whether a logging tag is enabled for debugging
     */
    private static synchronized boolean isDebugEnabled(@NonNull String tag)
    {
        if (DEBUG_TAGS == null) {
            return false;
        } else if (DEBUG_TAGS == ALL_TAGS) {
            return true;
        }

        for (var debugTag: DEBUG_TAGS) {
            if (TextUtils.equals(debugTag, tag)) {
                return true;
            }
        }
        return false;
    }
}
