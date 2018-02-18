/*
 * Copyright (©) 2018 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

/**
 * Results of the syncs for a provider
 */
public final class SyncResults
{
    private static final long UNKNOWN = 0;

    private long itsLastSuccess = UNKNOWN;
    private long itsLastFailure = UNKNOWN;

    /**
     * Set the result of a sync
     */
    public synchronized void setResult(boolean success, long syncTime)
    {
        if (success) {
            itsLastSuccess = syncTime;
        } else {
            itsLastFailure = syncTime;
        }
    }

    /**
     * Has there been a successful sync
     */
    public synchronized boolean hasLastSuccess()
    {
        return itsLastSuccess != UNKNOWN;
    }

    /**
     * Get the time of the last successful sync
     */
    public synchronized long getLastSuccess()
    {
        return itsLastSuccess;
    }

    /**
     * Has there been a failed sync
     */
    public synchronized boolean hasLastFailure()
    {
        return itsLastFailure != UNKNOWN;
    }

    /**
     * Get the time of the last failed sync
     */
    public synchronized long getLastFailure()
    {
        return itsLastFailure;
    }
}
