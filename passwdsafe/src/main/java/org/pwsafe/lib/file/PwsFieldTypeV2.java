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
 * ENumeration of V2 record field types
 */
public enum PwsFieldTypeV2 implements PwsFieldType
{
    V2_ID_STRING(0),
    UUID(1),
    GROUP(2),
    TITLE(3),
    USERNAME(4),
    NOTES(5),
    PASSWORD(6),
    CREATION_TIME(7),
    PASSWORD_MOD_TIME(8),
    LAST_ACCESS_TIME(9),
    PASSWORD_LIFETIME(10),
    PASSWORD_POLICY(11),
    LAST_MOD_TIME(12),
    URL(13),
    END_OF_RECORD(255),

    UNKNOWN(-1);

    private final int id;

    private static final SparseArray<PwsFieldTypeV2> itsTypesById =
            new SparseArray<>(PwsFieldTypeV2.values().length);
    static {
        for (var type: PwsFieldTypeV2.values()) {
            itsTypesById.append(type.id, type);
        }
    }

    PwsFieldTypeV2(int anId)
    {
        id = anId;
    }

    public int getId()
    {
        return id;
    }

    public static @NonNull PwsFieldTypeV2 fromType(int type)
    {
        return itsTypesById.get(type, UNKNOWN);
    }
}
