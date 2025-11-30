/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;
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
        /// 32-bit, little-endian seconds since epoch
        DEFAULT,
        /// Early 8 byte hex string of seconds since epoch for header fields
        HEADER_ASCII
    }

    private final Format itsFormat;

    /**
     * Constructor
     *
     * @param type  the field's type.
     * @param value the field's value.
     */
    public PwsTimeField(PwsFieldType type, byte[] value)
    {
        super(type, createDateFromBytes(value));
        itsFormat = formatFromBytes(value);
    }

    /**
     * Constructor
     *
     * @param type  the field's type.
     * @param aDate the field's value.
     */
    @SuppressWarnings("SameParameterValue")
    public PwsTimeField(PwsFieldType type, Format format, Date aDate)
    {
        super(type, normalizeDate(aDate));
        itsFormat = format;
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
        case DEFAULT -> {
        }
        case HEADER_ASCII -> {
            int secs = (int)(value / 1000);
            String str = String.format("%08x", secs);
            return str.getBytes();
        }
        }

        // Force a size of 4, otherwise it would be set to a size of
        // blocklength
        byte[] retval = new byte[4];
        Util.putMillisToByteArray(retval, value, 0);
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
    private static Date normalizeDate(Date date)
    {
        if (date == null) {
            return null;
        }
        long value = date.getTime();
        value -= (value % 1000);
        return new Date(value);
    }

    /**
     * Get the time format from its bytes value
     */
    @NonNull
    private static Format formatFromBytes(@NonNull byte[] value)
    {
        if (value.length == 8) {
            return Format.HEADER_ASCII;
        }
        return Format.DEFAULT;
    }

    /**
     * Create a date value from its bytes value
     */
    @Contract("_ -> new")
    @NonNull
    private static Date createDateFromBytes(@NonNull byte[] value)
    {
        switch (formatFromBytes(value)) {
        case DEFAULT -> {
        }
        case HEADER_ASCII -> {
            byte[] binbytes = new byte[4];
            Util.putIntToByteArray(binbytes, hexBytesToInt(value), 0);
            value = binbytes;
        }
        }
        return new Date(Util.getMillisFromByteArray(value, 0));
    }

    /**
     * Convert hex bytes to an integer
     */
    private static int hexBytesToInt(@NonNull byte[] bytes)
    {
        int i = 0;
        for (byte aByte : bytes) {
            i <<= 4;
            i |= Character.digit(aByte, 16);
        }
        return i;
    }
}
