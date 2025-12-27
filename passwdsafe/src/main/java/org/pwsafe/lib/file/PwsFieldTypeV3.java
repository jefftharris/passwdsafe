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
 * Enumeration of V3 record field types
 */
public enum PwsFieldTypeV3 implements PwsFieldType
{
    V3_ID_STRING(0x00, PwsVersionField.class),
    UUID(0x01, PwsUUIDField.class),
    GROUP(0x02, PwsStringUnicodeField.class),
    TITLE(0x03, PwsStringUnicodeField.class),
    USERNAME(0x04, PwsStringUnicodeField.class),
    NOTES(0x05, PwsStringUnicodeField.class),
    PASSWORD(0x06, PwsPasswdUnicodeField.class),
    CREATION_TIME(0x07, PwsTimeField.class),
    PASSWORD_MOD_TIME(0x08, PwsTimeField.class),
    LAST_ACCESS_TIME(0x09, PwsTimeField.class),
    PASSWORD_LIFETIME(0x0a, PwsTimeField.class),
    PASSWORD_POLICY_DEPRECATED(0x0b, PwsStringUnicodeField.class),
    LAST_MOD_TIME(0x0c, PwsTimeField.class),
    URL(0x0d, PwsStringUnicodeField.class),
    AUTOTYPE(0x0e, PwsStringUnicodeField.class),
    PASSWORD_HISTORY(0x0f, PwsStringUnicodeField.class),
    PASSWORD_POLICY(0x10, PwsStringUnicodeField.class),
    PASSWORD_EXPIRY_INTERVAL(0x11, PwsIntegerField.class),
    RUN_COMMAND(0x12, PwsStringUnicodeField.class),
    DOUBLE_CLICK_ACTION(0x13, PwsShortField.class),
    EMAIL(0x14, PwsStringUnicodeField.class),
    PROTECTED_ENTRY(0x15, PwsByteField.class),
    OWN_PASSWORD_SYMBOLS(0x16, PwsStringUnicodeField.class),
    SHIFT_DOUBLE_CLICK_ACTION(0x17, PwsShortField.class),
    PASSWORD_POLICY_NAME(0x18, PwsStringUnicodeField.class),
    ENTRY_KEYBOARD_SHORTCUT(0x19, PwsIntegerField.class),
    TWO_FACTOR_KEY(0x1b, PwsPasswdUnicodeField.class),
    END_OF_RECORD(255, null),

    UNKNOWN(-1, null);

    private final int itsId;
    private final Class<? extends PwsField> itsFieldClass;

    private static final SparseArray<PwsFieldTypeV3> itsTypesById =
            new SparseArray<>(PwsFieldTypeV3.values().length);
    static {
        for (var type: PwsFieldTypeV3.values()) {
            itsTypesById.append(type.itsId, type);
        }
    }

    PwsFieldTypeV3(int anId, Class<? extends PwsField> clazz)
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

    public static @NonNull PwsFieldTypeV3 fromType(int type)
    {
        return itsTypesById.get(type, UNKNOWN);
    }
}
