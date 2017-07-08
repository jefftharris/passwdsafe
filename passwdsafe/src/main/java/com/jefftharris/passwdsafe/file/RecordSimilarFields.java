/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.pwsafe.lib.file.PwsRecord;

/**
 *  Matcher for records with similar fields
 */
public final class RecordSimilarFields
{
    private final String itsTitle;
    private final String itsUserName;
    private final String itsPassword;
    private final String itsUrl;
    private final String itsEmail;
    private final String itsRecUuid;
    private final boolean itsIsCaseSensitive;

    /**
     * Constructor
     */
    public RecordSimilarFields(@NonNull PasswdRecord passwdRec,
                               @NonNull PasswdFileData fileData,
                               boolean caseSensitive)
    {
        PwsRecord rec = passwdRec.getRecord();
        itsTitle = getField(fileData.getTitle(rec));
        itsUserName = getField(fileData.getUsername(rec));
        itsPassword = getField(passwdRec.getPassword(fileData));
        itsUrl = getField(fileData.getURL(rec));
        itsEmail = getField(fileData.getEmail(rec));
        itsRecUuid = passwdRec.getUUID();
        itsIsCaseSensitive = caseSensitive;
    }

    /**
     * Is the given record the same as the matcher
     */
    public boolean isRecord(PasswdRecord passwdRec)
    {
        return TextUtils.equals(itsRecUuid, passwdRec.getUUID());
    }

    /**
     * Does the title match
     */
    public boolean matchTitle(String recTitle)
    {
        return matchField(itsTitle, recTitle);
    }

    /**
     * Does the user name match
     */
    public boolean matchUserName(String recUserName)
    {
        return matchField(itsUserName, recUserName);
    }

    /**
     * Does the password match
     */
    public boolean matchPassword(String recPassword)
    {
        return matchField(itsPassword, recPassword);
    }

    /**
     * Does the URL match
     */
    public boolean matchUrl(String recUrl)
    {
        return matchField(itsUrl, recUrl);
    }

    /**
     * Does the email match
     */
    public boolean matchEmail(String recEmail)
    {
        return matchField(itsEmail, recEmail);
    }

    /**
     * Get a description of the record being matched
     */
    public String getDescription()
    {
        return PasswdRecord.getRecordId(null, itsTitle, itsUserName);
    }

    /**
     * Does the similar field value match the value from a record
     */
    private boolean matchField(String similarValue, String recValue)
    {
        return (similarValue != null) &&
               (itsIsCaseSensitive ?
                similarValue.equals(recValue) :
                similarValue.equalsIgnoreCase(recValue));
    }

    /**
     * Return the value if non-null and non-empty; null otherwise
     */
    private static String getField(String value)
    {
        return TextUtils.isEmpty(value) ? null : value;
    }
}
