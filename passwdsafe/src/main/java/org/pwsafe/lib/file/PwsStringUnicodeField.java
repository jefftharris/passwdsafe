/*
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;

import java.io.Serial;
import java.io.UnsupportedEncodingException;

/**
 * @author Kevin Preece
 */
public class PwsStringUnicodeField extends PwsField
{
    @Serial
    private static final long serialVersionUID = -4530429748953931053L;

    /**
     * Constructor
     *
     * @param type  the field's type.
     * @param value the field's value.
     */
    public PwsStringUnicodeField(int type, byte[] value)
            throws UnsupportedEncodingException
    {

        //noinspection CharsetObjectCanBeUsed
        super(type, new String(value, "UTF-8"));
    }

    /**
     * Constructor
     *
     * @param type  the field's type.
     * @param value the field's value.
     */
    public PwsStringUnicodeField(int type, String value)
    {
        super(type, value);
    }

    /**
     * Constructor
     *
     * @param type  the field's type.
     * @param value the field's value.
     */
    public PwsStringUnicodeField(
            @SuppressWarnings("SameParameterValue") PwsFieldType type,
            @SuppressWarnings("SameParameterValue") String value)
    {
        super(type, value);
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
        try {
            //noinspection CharsetObjectCanBeUsed
            return ((String)super.getValue()).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Compares this <code>PwsStringField</code> to another returning a value
     * less than zero if <code>this</code> is "less than" <code>that</code>,
     * zero if they're equal and greater than zero if <code>this</code>
     * is "greater than" <code>that</code>.
     *
     * @param that the other field to compare to.
     * @return A value less than zero if <code>this</code> is "less than"
     * <code>that</code>, zero if they're equal and greater than zero if
     * <code>this</code> is "greater than" <code>that</code>.
     */
    public int compareTo(@NonNull Object that)
    {
        return ((String)this.getValue()).compareTo(
                (String)((PwsStringUnicodeField)that).getValue());
    }

    /**
     * Compares this object to another <code>PwsStringField</code> or
     * <code>String</code> returning <code>true</code> if they're equal or
     * <code>false</code> otherwise.
     *
     * @param arg0 the other object to compare to.
     * @return <code>true</code> if they're equal or <code>false</code>
     * otherwise.
     */
    @Override
    public boolean equals(Object arg0)
    {
        if (arg0 instanceof PwsStringUnicodeField) {
            return super.getValue().equals(
                    ((PwsStringUnicodeField)arg0).getValue());
        } else if (arg0 instanceof String) {
            return super.getValue().equals(arg0);
        }
        throw new ClassCastException();
    }
}
