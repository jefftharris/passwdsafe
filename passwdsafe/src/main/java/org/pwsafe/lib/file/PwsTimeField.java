/*
 * Copyright (©) 2025-2026 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.pwsafe.lib.Util;

import java.io.Serial;
import java.util.Date;

/**
 * @author Kevin Preece
 */
public class PwsTimeField extends PwsField
{
    @Serial
    private static final long serialVersionUID = -3091539688166386331L;

    public enum Format
    {
        /// 32-bit, little-endian seconds since epoch.  May be extended to 5 or
        /// 8 bytes
        DEFAULT,
        /// 32-bit only (for V2 format)
        ONLY_32BIT,
        /// Allow early 8 byte hex string of seconds since epoch for header
        /// fields
        ALLOW_HEADER_ASCII
    }

    private final Format itsFormat;

    /**
     * Constructor from bytes
     */
    public PwsTimeField(@NonNull PwsFieldType type,
                        @NonNull Format format,
                        byte[] value)
    {
        this(type, parseDateFromBytes(format, value));
    }

    /**
     * Constructor from a date
     */
    @SuppressWarnings("SameParameterValue")
    public PwsTimeField(@NonNull PwsFieldType type,
                        @NonNull Format format,
                        Date aDate)
    {
        super(type, normalizeDate(aDate));
        itsFormat = format;
    }

    /**
     * Constructor from a parsed date
     */
    private PwsTimeField(@NonNull PwsFieldType type, @NonNull ParsedDate date)
    {
        super(type, date.date());
        itsFormat = date.format();
    }

    /**
     * Returns the field's value as a byte array.
     *
     * @return A byte array containing the field's data.
     * @see org.pwsafe.lib.file.PwsField#getBytes()
     */
    @Override
    public byte[] getBytes()
    {
        long value = ((Date)getValue()).getTime();

        switch (itsFormat) {
        case DEFAULT,
            ONLY_32BIT -> {
        }
        case ALLOW_HEADER_ASCII -> {
            int secs = (int)(value / 1000);
            String str = String.format("%08x", secs);
            return str.getBytes();
        }
        }

        // Force a size of 4, otherwise it would be set to a size of
        // blocklength
        byte[] retval = new byte[4];
        Util.putMillisToByteArray(retval, value);
        return retval;
    }

    /**
     * Compares this <code>PwsTimeField</code> to another returning a
     * value less than zero if <code>this</code> is "less than"
     * <code>that</code>, zero if they're equal and greater
     * than zero if <code>this</code> is "greater than" <code>that</code>.
     *
     * @param that the other field to compare to.
     * @return A value less than zero if <code>this</code> is "less than"
     * <code>that</code>, zero if they're equal and greater than zero if
     * <code>this</code> is "greater than" <code>that</code>.
     */
    public int compareTo(@NonNull Object that)
    {
        return ((Date)this.getValue()).compareTo(
                (Date)((PwsTimeField)that).getValue());
    }

    /**
     * Compares this object to another <code>PwsTimeField</code> or
     * <code>java.util.Date</code> returning <code>true</code> if they're equal
     * or <code>false</code> otherwise.
     *
     * @param arg0 the other object to compare to.
     * @return <code>true</code> if they're equal or <code>false</code>
     * otherwise.
     */
    @Override
    public boolean equals(Object arg0)
    {
        if (arg0 instanceof PwsTimeField) {
            return getValue().equals(((PwsTimeField)arg0).getValue());
        } else if (arg0 instanceof Date) {
            return getValue().equals(arg0);
        }
        throw new ClassCastException();
    }

    /**
     * Normalize a date to remove microseconds as if it had been loaded from
     * a file
     */
    @Nullable
    public static Date normalizeDate(Date date)
    {
        if (date == null) {
            return null;
        }
        long value = date.getTime();
        value -= (value % 1000);
        return new Date(value);
    }

    /**
     * Create a date value from its bytes value
     */
    @NonNull
    private static ParsedDate parseDateFromBytes(@NonNull Format format,
                                                 @NonNull byte[] value)
    {
        switch (format) {
        case DEFAULT,
             ONLY_32BIT -> {
        }
        case ALLOW_HEADER_ASCII -> {
            // Check deprecated ASCII form, else fall through
            if (value.length == 8) {
                var asciiTime = headerAsciiToDate(value);
                if (asciiTime != null) {
                    return new ParsedDate(format, asciiTime);
                }
            }
            format = Format.DEFAULT;
        }
        }

        // Read 4, 5, 8 as little-endian seconds
        return new ParsedDate(format,
                              new Date(Util.getMillisFromByteArray(value)));
    }

    /**
     * Convert hex bytes to an integer
     * @return The parsed time from hex bytes if successful; null otherwise
     */
    @Nullable
    private static Date headerAsciiToDate(@NonNull byte[] bytes)
    {
        long tsecs = 0;
        for (byte aByte : bytes) {
            tsecs <<= 4;
            int d = Character.digit(aByte, 16);
            if (d == -1) {
                return null;
            }
            tsecs |= d;
        }
        return new Date(tsecs * 1000);
    }

    /**
     * Date and supported format parsed from bytes
     */
    private record ParsedDate(@NonNull Format format, @NonNull Date date)
    {
    }
}
