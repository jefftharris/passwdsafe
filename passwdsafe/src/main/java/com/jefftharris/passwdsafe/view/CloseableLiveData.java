/*
 * Copyright (©) 2023-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import androidx.lifecycle.MutableLiveData;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * The CloseableLiveData class extends MutableLiveData to attempt to close
 * the data it contains.
 */
public class CloseableLiveData<T extends AutoCloseable>
        extends MutableLiveData<T> implements AutoCloseable
{
    private static final String TAG = "CloseableLiveData";

    /**
     * Default Constructor
     */
    public CloseableLiveData()
    {
    }

    /**
     * Constructor with a value
     */
    public CloseableLiveData(T value)
    {
        super(value);
    }

    @Override
    public void setValue(T value)
    {
        doClose(false);
        super.setValue(value);
    }

   /**
     * Close the data
     */
    @Override
    public void close()
    {
        doClose(true);
    }

    @Override
    protected void onInactive()
    {
        super.onInactive();
        close();
    }

    /**
     * Finalize the object
     */
    @Override
    protected void finalize() throws Throwable
    {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private void doClose(boolean setNull)
    {
        T value = getValue();
        if (value != null) {
            try {
                value.close();
            } catch (Exception e) {
                PasswdSafeUtil.dbginfo(TAG, e, "Error closing live data");
            }
            if (setNull) {
                setValue(null);
            }
        }
    }
}
