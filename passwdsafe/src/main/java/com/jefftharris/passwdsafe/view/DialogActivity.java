/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.jefftharris.passwdsafe.PasswdSafeApp;

/**
 * Base class for a dialog themed activity
 */
public abstract class DialogActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        PasswdSafeApp.setupDialogTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(getViewLayoutId());
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                              ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * Get the layout id for the content view
     */
    abstract protected @LayoutRes int getViewLayoutId();
}
