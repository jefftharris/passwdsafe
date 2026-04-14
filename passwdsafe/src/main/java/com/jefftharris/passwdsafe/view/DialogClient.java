/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;

/**
 * Abstract client for showing and checking results of a dialog
 */
public abstract class DialogClient
{
    private static final String ARG_REQUEST_KEY = "requestKey";

    private final String itsKey;
    private final FragmentManager itsFragMgr;

    /**
     * Common constructor
     */
    protected DialogClient(@NonNull String requestKeyBase,
                           @NonNull String id,
                           @NonNull FragmentManager fragMgr,
                           @NonNull LifecycleOwner lifecycleOwner,
                           @NonNull FragmentResultListener listener)
    {
        itsKey = requestKeyBase + "-" + id;
        itsFragMgr = fragMgr;
        itsFragMgr.setFragmentResultListener(itsKey, lifecycleOwner,
                                             listener);
    }

    /**
     * Check whether the fragment result key matches the dialog
     */
    public boolean checkKey(@NonNull String requestKey)
    {
        return itsKey.equals(requestKey);
    }

    /**
     * Show an instance of the dialog with arguments
     */
    protected void doShow(@NonNull DialogFragment dialog,
                          @NonNull Bundle args)
    {
        args.putString(ARG_REQUEST_KEY, itsKey);
        dialog.setArguments(args);
        dialog.show(itsFragMgr, itsKey);
    }

    /**
     * Set the result of the dialog
     */
    protected static void setResult(@NonNull Bundle dialogArgs,
                                    @NonNull Bundle result,
                                    @NonNull FragmentManager fragMgr)
    {
        var requestKey = dialogArgs.getString(ARG_REQUEST_KEY);
        fragMgr.setFragmentResult(Objects.requireNonNull(requestKey), result);
    }
}
