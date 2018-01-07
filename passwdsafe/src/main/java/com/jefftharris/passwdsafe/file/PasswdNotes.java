/*
 * Copyright (©) 2018 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.content.Context;
import android.support.annotation.Nullable;

import com.jefftharris.passwdsafe.R;

/**
 * Notes in a password entry
 */
public final class PasswdNotes
{
    private static final int TRUNCATE_LEN = 10*1024;

    private final String itsNotes;
    private final boolean itsIsTruncated;

    /**
     * Constructor
     */
    public PasswdNotes(String notes, Context ctx)
    {
        if (notes == null) {
            itsNotes = null;
            itsIsTruncated = false;
        } else if (notes.length() > TRUNCATE_LEN) {
            itsNotes =
                    replaceNl(notes.substring(0, TRUNCATE_LEN)) +
                    ctx.getString(R.string.notes_truncated_msg);
            itsIsTruncated = true;
        } else {
            itsNotes = replaceNl(notes);
            itsIsTruncated = false;
        }
    }

    /**
     * Get the notes
     */
    public @Nullable String getNotes()
    {
        return itsNotes;
    }

    /**
     * Get whether the notes are truncated
     */
    public boolean isTruncated()
    {
        return itsIsTruncated;
    }

    /**
     * Replace carriage-return/newline with just newline
     */
    private static String replaceNl(String str)
    {
        return str.replace("\r\n", "\n");
    }
}
