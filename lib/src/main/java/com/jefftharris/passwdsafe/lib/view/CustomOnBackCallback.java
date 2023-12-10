/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib.view;

import android.app.Activity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * On-back callback which allows the activity to perform custom on-back behavior
 * or trigger the default behavior
 */
public abstract class CustomOnBackCallback extends OnBackPressedCallback
{
    /**
     * Constructor
     */
    protected CustomOnBackCallback()
    {
        super(true);
    }

    @Override
    public final void handleOnBackPressed()
    {
        Activity backAct = performCustomOnBack();
        if (backAct != null) {
            performDefaultOnBack(backAct);
        }
    }

    /**
     * Perform custom on-back behavior if desired when the back button is
     * pressed
     *
     * @return The activity if the default on-back behavior is desired; null if
     * custom behavior was performed
     */
    @Nullable
    protected abstract Activity performCustomOnBack();

    /**
     * Perform the default on-back behavior
     */
    protected final void performDefaultOnBack(@NonNull Activity backAct)
    {
        setEnabled(false);
        backAct.onBackPressed();
        setEnabled(true);
    }
}
