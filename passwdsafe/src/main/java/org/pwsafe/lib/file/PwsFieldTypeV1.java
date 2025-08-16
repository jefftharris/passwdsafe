/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

/**
 * Enumeration of V1 record field types
 */
public enum PwsFieldTypeV1 implements PwsFieldType
{
    DEFAULT(0),
    TITLE(3),
    USERNAME(4),
    NOTES(5),
    PASSWORD(6),
    UUID(7),

    UNKNOWN(-1);

    private final int id;

    PwsFieldTypeV1(int anId)
    {
        id = anId;
    }

    public int getId()
    {
        return id;
    }
}
