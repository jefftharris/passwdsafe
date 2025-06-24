/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.lib.view.GuiUtils;

/**
 * The keyboard view for the PasswdSafe IME
 */
public class PasswdSafeIMEKeyboardView extends KeyboardView
{
    /**
     * Constructor
     */
    public PasswdSafeIMEKeyboardView(Context context,
                                     AttributeSet attrs)
    {
        super(context, attrs);
        GuiUtils.disableForceDark(this);
    }

    /**
     * Constructor
     */
    public PasswdSafeIMEKeyboardView(Context context, AttributeSet attrs,
                                     int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        GuiUtils.disableForceDark(this);
    }

    @Override
    protected boolean onLongPress(@NonNull Keyboard.Key key)
    {
        return switch (key.codes[0]) {
            case PasswdSafeIME.KEYBOARD_NEXT_KEY,
                 PasswdSafeIME.KEYBOARD_CHOOSE_KEY -> {
                getOnKeyboardActionListener().onKey(
                        PasswdSafeIME.KEYBOARD_CHOOSE_KEY, null);
                yield true;
            }
            default -> super.onLongPress(key);
        };
    }
}
