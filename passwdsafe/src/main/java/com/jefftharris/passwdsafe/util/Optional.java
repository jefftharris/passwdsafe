/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * The Optional class represents an optional value
 */
public final class Optional<T>
{
    private final boolean itsHasObj;
    private final T itsObj;

    /**
     * Create an empty optional value
     */
    @NonNull
    public static <T> Optional<T> empty()
    {
        return new Optional<>(false, null);
    }

    /**
     * Create a non-empty optional value
     */
    @NonNull
    public static <T> Optional<T> of(@NonNull T obj)
    {
        return new Optional<>(true, Objects.requireNonNull(obj));
    }

    /**
     * Does the object have a value
     */
    public boolean isPresent()
    {
        return itsHasObj;
    }

    /**
     * Get the value of the non-empty object or the passed value if empty
     */
    @Nullable
    public T orElse(@Nullable T other)
    {
        if (itsHasObj) {
            return itsObj;
        } else {
            return other;
        }
    }

    /**
     * Convert the object to a string
     */
    @Override
    @NonNull
    public String toString()
    {
        return itsHasObj ? String.format("Optional{%s}", itsObj) :
               "Optional(empty)";
    }

    /**
     * Constructor
     */
    private Optional(boolean hasObj, T obj)
    {
        itsHasObj = hasObj;
        itsObj = obj;
    }
}
