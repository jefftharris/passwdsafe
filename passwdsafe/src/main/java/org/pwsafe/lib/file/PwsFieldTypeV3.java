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

/**
 * Enumeration of V3 record field types
 */
public enum PwsFieldTypeV3 implements PwsFieldType
{
    V3_ID_STRING(0x00),
    UUID(0x01),
    GROUP(0x02),
    TITLE(0x03),
    USERNAME(0x04),
    NOTES(0x05),
    PASSWORD(0x06),
    CREATION_TIME(0x07),
    PASSWORD_MOD_TIME(0x08),
    LAST_ACCESS_TIME(0x09),
    PASSWORD_LIFETIME(0x0a),
    PASSWORD_POLICY_DEPRECATED(0x0b),
    LAST_MOD_TIME(0x0c),
    URL(0x0d),
    AUTOTYPE(0x0e),
    PASSWORD_HISTORY(0x0f),
    PASSWORD_POLICY(0x10),
    PASSWORD_EXPIRY_INTERVAL(0x11),
    RUN_COMMAND(0x12),
    DOUBLE_CLICK_ACTION(0x13),
    EMAIL(0x14),
    PROTECTED_ENTRY(0x15),
    OWN_PASSWORD_SYMBOLS(0x16),
    SHIFT_DOUBLE_CLICK_ACTION(0x17),
    PASSWORD_POLICY_NAME(0x18),
    ENTRY_KEYBOARD_SHORTCUT(0x19),
    END_OF_RECORD(255),

    UNKNOWN(-1);

    private final int id;

    private static final SparseArray<PwsFieldTypeV3> itsTypesById =
            new SparseArray<>(PwsFieldTypeV3.values().length);
    static {
        for (var type: PwsFieldTypeV3.values()) {
            itsTypesById.append(type.id, type);
        }
    }

    PwsFieldTypeV3(int anId)
    {
        id = anId;
    }

    public int getId()
    {
        return id;
    }

    public static @NonNull PwsFieldTypeV3 fromType(int type)
    {
        return itsTypesById.get(type, UNKNOWN);
    }
}
