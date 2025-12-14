/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.file.PasswdFileData;

import org.pwsafe.lib.file.PwsRecord;

import java.util.Date;

/**
 * Holder class for password record data in a list view
 */
public record PasswdRecordListData(
        String title,
        String user,
        @NonNull
        RecordFields fields,
        int icon,
        boolean isRecord)
{
    /**
     * Fields associated with a record list entry, as compared to a group
     */
    public static class RecordFields
    {
        public final String itsUuid;

        public final String itsEmail;

        public final String itsUrl;

        public final Date itsCreationTime;

        public final Date itsModTime;

        public final String itsMatch;

        /**
         * Constructor
         */
        public RecordFields(PwsRecord rec,
                            @NonNull PasswdFileData fileData,
                            String match)
        {
            itsUuid = fileData.getUUID(rec);
            itsCreationTime = fileData.getCreationTime(rec);
            itsEmail = fileData.getEmail(rec, PasswdFileData.EmailStyle.FULL);
            itsUrl = fileData.getURL(rec, PasswdFileData.UrlStyle.FULL);
            Date modTime = fileData.getLastModTime(rec);
            Date passwdModTime = fileData.getPasswdLastModTime(rec);
            if ((modTime != null) && (passwdModTime != null)) {
                if (passwdModTime.compareTo(modTime) > 0) {
                    modTime = passwdModTime;
                }
            } else if (modTime == null) {
                modTime = passwdModTime;
            }
            itsModTime = modTime;
            itsMatch = match;
        }

        /**
         * Group entry constructor
         */
        private RecordFields()
        {
            itsUuid = null;
            itsEmail = null;
            itsUrl = null;
            itsCreationTime = null;
            itsModTime = null;
            itsMatch = null;
        }
    }

    ///  Fields to use for a group entry
    private static final RecordFields GROUP_FIELDS = new RecordFields();

    /**
     * Constructor
     */
    public PasswdRecordListData(String title,
                                String user,
                                @Nullable RecordFields fields,
                                int icon,
                                boolean isRecord)
    {
        this.title = title;
        this.user = user;
        this.fields = (fields != null) ? fields : GROUP_FIELDS;
        this.icon = icon;
        this.isRecord = isRecord;
    }
}
