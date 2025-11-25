/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ENumeration of V2 record field types
 */
public enum PwsFieldTypeV2 implements PwsFieldType
{
    V2_ID_STRING(0, PwsStringField.class),
    UUID(1, PwsUUIDField.class),
    GROUP(2, PwsStringField.class),
    TITLE(3, PwsStringField.class),
    USERNAME(4, PwsStringField.class),
    NOTES(5, PwsStringField.class),
    PASSWORD(6, PwsPasswdField.class),
    CREATION_TIME(7, PwsTimeField.class),
    PASSWORD_MOD_TIME(8, PwsTimeField.class),
    LAST_ACCESS_TIME(9, PwsTimeField.class),
    PASSWORD_LIFETIME(10, PwsIntegerField.class),
    PASSWORD_POLICY(11, PwsStringField.class),
    LAST_MOD_TIME(12, PwsTimeField.class),
    URL(13, PwsStringField.class),
    END_OF_RECORD(255, null),

    UNKNOWN(-1, null);

    private final int itsId;
    private final Class<? extends PwsField> itsFieldClass;

    private static final SparseArray<PwsFieldTypeV2> itsTypesById =
            new SparseArray<>(PwsFieldTypeV2.values().length);
    static {
        for (var type: PwsFieldTypeV2.values()) {
            itsTypesById.append(type.itsId, type);
        }
    }

    PwsFieldTypeV2(int anId, Class<? extends PwsField> clazz)
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

    public static @NonNull PwsFieldTypeV2 fromType(int type)
    {
        return itsTypesById.get(type, UNKNOWN);
    }
}
