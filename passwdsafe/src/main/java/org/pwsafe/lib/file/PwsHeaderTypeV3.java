/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

/**
 * Enumeration of V3 header field types
 */
public enum PwsHeaderTypeV3 implements PwsFieldType
{
    VERSION(0x00),
    UUID(0x01),
    LAST_SAVE_TIME(0x04),
    LAST_SAVE_WHO(0x05),
    LAST_SAVE_WHAT(0x06),
    LAST_SAVE_USER(0x07),
    LAST_SAVE_HOST(0x08),
    NAMED_PASSWORD_POLICIES(0x10),
    YUBICO(0x12),
    LAST_PASSWORD_CHANGE(0x13),
    END_OF_RECORD(255);

    private final int itsId;

    PwsHeaderTypeV3(int id) {
        itsId = id;
    }

    public int getId()
    {
        return itsId;
    }
}
