/*
 * Copyright (©) 2018 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.ProviderSyncFreqPref;

/**
 * Results of the syncs for a provider
 */
public final class SyncResults
{
    private static final long UNKNOWN = Long.MIN_VALUE;

    private static final String TAG = "SyncResults";

    private long itsLastSuccess = UNKNOWN;
    private long itsFirstFailure = UNKNOWN;
    private long itsLastFailure = UNKNOWN;
    private int itsSyncFreq = 0;

    /**
     * Set the result of a sync
     */
    public synchronized void setResult(boolean success, long syncTime)
    {
        if (success) {
            itsLastSuccess = syncTime;
            itsFirstFailure = UNKNOWN;
        } else {
            if (itsFirstFailure == UNKNOWN) {
                itsFirstFailure = syncTime;
            }
            itsLastFailure = syncTime;
        }
    }

    /**
     * Set the sync frequency
     */
    public synchronized void setSyncFreq(int syncFreq)
    {
        itsSyncFreq = syncFreq;
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

    /**
     * Have there been repeated sync failures
     */
    public synchronized boolean isRepeatedFailure()
    {
        if (itsFirstFailure == UNKNOWN) {
            return false;
        }
        long now = System.currentTimeMillis();
        long failureDuration = now - itsFirstFailure;
        int freq1daySecs = ProviderSyncFreqPref.FREQ_1_DAY.getFreq();
        PasswdSafeUtil.dbginfo(
                TAG, "isRepeatedFailure dur %d, first %d last %d now %d",
                failureDuration, itsFirstFailure, itsLastFailure, now);
        if (itsSyncFreq < freq1daySecs) {
            return (failureDuration >= (freq1daySecs * 1000));
        }
        return (failureDuration >= (3 * freq1daySecs * 1000));
    }
}
