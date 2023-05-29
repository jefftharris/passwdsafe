/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import androidx.lifecycle.MutableLiveData;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

import java.io.Closeable;
import java.io.IOException;

/**
 * The CloseableLiveData class extends MutableLiveData to attempt to close
 * the data it contains.
 */
public class CloseableLiveData<T extends Closeable> extends MutableLiveData<T>
    implements Closeable
{
    private static final String TAG = "CloseableLiveData";

    /**
     * Default Constructor
     */
    public CloseableLiveData()
    {
    }

    /**
     * Close the data
     */
    @Override
    public void close()
    {
        T value = getValue();
        PasswdSafeUtil.dbginfo(TAG, "Closing: %s", value);
        if (value != null) {
            try {
                value.close();
            } catch (IOException e) {
                PasswdSafeUtil.dbginfo(TAG, e, "Error closing live data");
            }
            setValue(null);
        }
    }

    @Override
    protected void onInactive()
    {
        super.onInactive();
        PasswdSafeUtil.dbginfo(TAG, "onInactive");
        close();
    }

    /**
     * Finalize the object
     */
    @Override
    protected void finalize() throws Throwable
    {
        super.finalize();
        close();
    }
}
