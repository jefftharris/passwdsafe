/*
 * Copyright (©) 2023-2024 Jeff Harris <jefftharris@gmail.com>
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

import java.util.ArrayList;

/**
 * Logging class for PasswdSafe
 */
public final class PasswdSafeLog
{
    private static String[] DEBUG_TAGS = null;
    private static boolean DEBUG_ALL = false;
    private static ArrayList<Listener> DEBUG_LISTENERS = null;

    /**
     * Listener for logging changes
     */
    public interface Listener
    {
        /// Notification when the debug tags have changed
        void handleDebugTagsChanged();
    }

    /**
     * Add a listener for logging changes
     */
    public static synchronized void addListener(Listener listener)
    {
        if (DEBUG_LISTENERS == null) {
            DEBUG_LISTENERS = new ArrayList<>(1);
        }
        DEBUG_LISTENERS.add(listener);
    }

    /**
     * Remove a listener for logging changes
     */
    public static synchronized void removeListener(Listener listener)
    {
        if (DEBUG_LISTENERS != null) {
            DEBUG_LISTENERS.remove(listener);
            if (DEBUG_LISTENERS.isEmpty()) {
                DEBUG_LISTENERS = null;
            }
        }
    }

    /**
     * Log a formatted message at error level
     */
    public static void error(@NonNull String tag,
                             @NonNull Throwable t,
                             @PrintFormat String fmt,
                             Object... args)
    {
        Log.e(tag, String.format(fmt, args), t);
    }

    /**
     * Log a formatted message at info level
     *
     * @noinspection RedundantSuppression
     */
    public static void info(@NonNull String tag,
                            @PrintFormat String fmt,
                            Object... args)
    {
        //noinspection PatternValidation
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
     * Log a formatted message at debug level if enabled
     */
    private static void debug(@NonNull String tag,
                              @NonNull Throwable t,
                              @PrintFormat String fmt,
                              Object... args)
    {
        if (isDebugEnabled(tag)) {
            Log.d(tag, String.format(fmt, args), t);
        }
    }

    /**
     * Log a formatted message and stack trace at debug level if enabled
     *
     * @noinspection unused, RedundantSuppression
     */
    public static void debugTrace(@NonNull String tag,
                                  @PrintFormat String fmt,
                                  Object... args)
    {
        //noinspection PatternValidation
        debug(tag, new Exception(), fmt, args);
    }

    /**
     *  Set the enabled debug tags
     */
    public static synchronized void setDebugTags(String tags)
    {
        if (tags != null) {
            tags = tags.trim();
        }

        DEBUG_ALL = false;
        if (TextUtils.isEmpty(tags)) {
            DEBUG_TAGS = null;
        } else {
            DEBUG_TAGS = TextUtils.split(tags, "\\s+");
            if (doIsInDebugTagsLocked("*")) {
                DEBUG_ALL = true;
            }
        }

        if (DEBUG_LISTENERS != null) {
            for (var listener: DEBUG_LISTENERS) {
                listener.handleDebugTagsChanged();
            }
        }
    }

    /**
     * Check whether a logging tag is explicitly (not all) enabled for
     * debugging
     */
    public static synchronized boolean isExplicitlyEnabled(@NonNull String tag)
    {
        return doIsInDebugTagsLocked(tag);
    }

    /**
     * Check whether a logging tag is enabled for debugging
     */
    private static synchronized boolean isDebugEnabled(@NonNull String tag)
    {
        return DEBUG_ALL || doIsInDebugTagsLocked(tag);
    }

    /**
     * Check whether a logging tag is enabled without checking for all while
     * locked.
     */
    private static boolean doIsInDebugTagsLocked(@NonNull String tag)
    {
        if (DEBUG_TAGS == null) {
            return false;
        }

        for (var debugTag: DEBUG_TAGS) {
            if (TextUtils.equals(debugTag, tag)) {
                return true;
            }
        }
        return false;
    }
}
