/*
 * Copyright (©) 2012-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.util;

import androidx.annotation.NonNull;

/**
 * Generic pair class
 */
public record Pair<T, U>(
        T first,
        U second)
{
    /**
     * Convert the object to a string
     */
    @Override
    @NonNull
    public String toString()
    {
        return "[[" + first + "], [" + second + "]]";
    }
}
