/*
 * Copyright (©) 2016-2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.pwsafe.lib.Log;

/**
 * The Owner class encapsulates an object to ensure it is closed after all
 * users are finished with it
 */
public final class Owner<T extends AutoCloseable> implements AutoCloseable
{
    private T itsItem;
    private int itsRefCount = 1;
    private final StackTraceElement[] itsCtorStackTrace;

    private static final boolean DEBUG_CTOR = false;
    private static final String TAG = "org.pwsafe.lib.file.Owner";

    /**
     * Owner as a method parameter
     */
    public final class Param
    {
        private final Owner<T> itsOwnedItem;

        /**
         * Constructor
         */
        private Param(@NonNull Owner<T> item)
        {
            itsOwnedItem = item;
        }

        /**
         * Use the owner in the parameter.  Close must be called on the
         * returned instance
         */
        public @NonNull @CheckResult(suggest="#close()")
        Owner<T> use()
        {
            ++itsOwnedItem.itsRefCount;
            return itsOwnedItem;
        }
    }

    /**
     * Constructor to take ownership of an object
     */
    public Owner(@NonNull T item)
    {
        itsItem = item;
        if (DEBUG_CTOR) {
            itsCtorStackTrace = Thread.currentThread().getStackTrace();
        } else {
            itsCtorStackTrace = null;
        }
    }

    /**
     * Get the owned object
     */
    public @NonNull T get()
    {
        return itsItem;
    }

    /**
     * Pass the owner as a method parameter
     */
    public @NonNull Param pass()
    {
        return new Param(this);
    }

    /**
     * Close the owner and its owned object if the last user
     */
    @Override
    public void close()
    {
        if (itsItem != null) {
            if (--itsRefCount <= 0) {
                try {
                    itsItem.close();
                } catch (Exception e) {
                    Log.getInstance(TAG).error(e);
                }
                itsItem = null;
            }
        }
    }

    /**
     * Maybe pass the given object if non-null
     *
     * @param obj The object to pass if non-null
     * @return The passed object if non-null; null otherwise
     */
    @Nullable
    public static <T extends AutoCloseable> Owner<T>.Param maybePass(
            @Nullable Owner<T> obj)
    {
        return (obj != null) ? obj.pass() : null;
    }

    /**
     * Finalize the object to check for missed calls to close
     */
    @Override
    protected void finalize() throws Throwable
    {
        try {
            if ((itsItem != null) && (itsRefCount > 0)) {
                Exception ctorEx = null;
                if (DEBUG_CTOR) {
                    ctorEx = new Exception("CTOR Stack Trace");
                    ctorEx.setStackTrace(itsCtorStackTrace);
                }
                Exception e = new Exception(
                        String.format("NOT FINALIZED class %s",
                                      itsItem.getClass()), ctorEx);
                Log.getInstance(TAG).error(e);
            }
        } finally {
            super.finalize();
        }
    }
}
