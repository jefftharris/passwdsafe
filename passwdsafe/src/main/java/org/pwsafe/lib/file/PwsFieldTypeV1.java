/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.Nullable;

/**
 * Enumeration of V1 record field types
 */
public enum PwsFieldTypeV1 implements PwsFieldType
{
    DEFAULT(0, null),
    TITLE(3, PwsStringField.class),
    USERNAME(4, PwsStringField.class),
    NOTES(5, PwsStringField.class),
    PASSWORD(6, PwsPasswdField.class),
    UUID(7, PwsStringField.class),

    UNKNOWN(-1, null);

    private final int itsId;
    private final Class<? extends PwsField> itsFieldClass;


    PwsFieldTypeV1(int anId, Class<? extends PwsField> clazz)
    {
        itsId = anId;
        itsFieldClass = clazz;
    }

    public int getId()
    {
        return itsId;
    }

    @Nullable
    public Class<? extends PwsField> getFieldClass()
    {
        return itsFieldClass;
    }
}
