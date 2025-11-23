/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.Nullable;

/**
 * Enumeration of V3 header field types
 */
public enum PwsHeaderTypeV3 implements PwsFieldType
{
    VERSION(0x00, PwsShortField.class),
    UUID(0x01, PwsUUIDField.class),
    LAST_SAVE_TIME(0x04, PwsTimeField.class),
    LAST_SAVE_WHO(0x05, PwsStringUnicodeField.class),
    LAST_SAVE_WHAT(0x06, PwsStringUnicodeField.class),
    LAST_SAVE_USER(0x07, PwsStringUnicodeField.class),
    LAST_SAVE_HOST(0x08, PwsStringUnicodeField.class),
    NAMED_PASSWORD_POLICIES(0x10, PwsStringUnicodeField.class),
    YUBICO(0x12, PwsStringUnicodeField.class),
    LAST_PASSWORD_CHANGE(0x13, PwsTimeField.class),
    END_OF_RECORD(255, null);

    private final int itsId;
    private final Class<? extends PwsField> itsFieldClass;

    PwsHeaderTypeV3(int id, Class<? extends PwsField> clazz)
    {
        itsId = id;
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
