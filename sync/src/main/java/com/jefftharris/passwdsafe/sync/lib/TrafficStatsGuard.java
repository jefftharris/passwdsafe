/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.net.TrafficStats;
import androidx.annotation.NonNull;

/**
 * The TrafficStatsGuard class manages a traffic stats tag set for a thread
 */
public class TrafficStatsGuard implements AutoCloseable
{
    /**
     * Types of traffic stats
     */
    public enum Stats
    {
        BOX,
        DROPBOX,
        GDRIVE,
        ONEDRIVE
    }

    private static final int BASE_TAG = 1000;

    private final int itsPrevTag;

    /**
     * Constructor
     */
    public TrafficStatsGuard(@NonNull Stats stats)
    {
        itsPrevTag = TrafficStats.getAndSetThreadStatsTag(
                BASE_TAG + stats.ordinal());
    }

    @Override
    public void close()
    {
        TrafficStats.setThreadStatsTag(itsPrevTag);
    }
}
