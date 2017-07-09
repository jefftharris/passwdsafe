/*
 * Copyright (©) 2012 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pwsafe.lib.file.PwsRecord;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.Utils;

/** A filter for records */
public final class PasswdRecordFilter implements Closeable
{
    /** Type of filter */
    public enum Type
    {
        QUERY,
        EXPIRATION,
        SIMILAR
    }

    // TODO: i18n for similar_to, find_similar

    /** Default options to match */
    public static final int OPTS_DEFAULT =          0;
    /** Record can not have an alias referencing it */
    public static final int OPTS_NO_ALIAS =         1 << 0;
    /** Record can not have a shortcut referencing it */
    public static final int OPTS_NO_SHORTCUT =      1 << 1;

    /** Filter type */
    private final Type itsType;

    /** Regex to match on various fields */
    private final Pattern itsSearchQuery;

    /** Expiration filter type */
    private final PasswdExpiryFilter itsExpiryFilter;

    /** The expiration time to match on a record's expiration */
    private final long itsExpiryAtMillis;

    /** Matcher for records with similar fields */
    private final RecordSimilarFields itsSimilarFields;

    /** Filter options */
    private final int itsOptions;

    public static final String QUERY_MATCH = "";
    private static String QUERY_MATCH_TITLE;
    private static String QUERY_MATCH_USERNAME;
    private static String QUERY_MATCH_PASSWORD;
    private static String QUERY_MATCH_URL;
    private static String QUERY_MATCH_EMAIL;
    private static String QUERY_MATCH_NOTES;

    /** Constructor for a query */
    public PasswdRecordFilter(Pattern query, int opts)
    {
        itsType = Type.QUERY;
        itsSearchQuery = query;
        itsExpiryFilter = PasswdExpiryFilter.ANY;
        itsExpiryAtMillis = 0;
        itsSimilarFields = null;
        itsOptions = opts;
    }

    /** Constructor for expiration */
    public PasswdRecordFilter(PasswdExpiryFilter filter,
                              Date customDate,
                              int opts)
    {
        itsType = Type.EXPIRATION;
        itsSearchQuery = null;
        itsExpiryFilter = filter;
        itsExpiryAtMillis = itsExpiryFilter.getExpiryFromNow(customDate);
        itsSimilarFields = null;
        itsOptions = opts;
    }

    /** Constructor for similar record */
    public PasswdRecordFilter(RecordSimilarFields similarFields)
    {
        itsType = Type.SIMILAR;
        itsSearchQuery = null;
        itsExpiryFilter = null;
        itsExpiryAtMillis = 0;
        itsSimilarFields = similarFields;
        itsOptions = OPTS_DEFAULT;
    }

    /**
     * Initialize the query matches
     */
    public static void initMatches(Context ctx)
    {
        if (QUERY_MATCH_TITLE == null) {
            QUERY_MATCH_TITLE = ctx.getString(R.string.title);
            QUERY_MATCH_USERNAME = ctx.getString(R.string.username);
            QUERY_MATCH_PASSWORD = ctx.getString(R.string.password);
            QUERY_MATCH_URL = ctx.getString(R.string.url);
            QUERY_MATCH_EMAIL = ctx.getString(R.string.email);
            QUERY_MATCH_NOTES = ctx.getString(R.string.notes);
        }
    }

    /**
     * Filter a record
     * @return A non-null string if the record matches the filter; null if it
     * does not
     */
    public final String filterRecord(PwsRecord rec,
                                     PasswdFileData fileData,
                                     Context ctx)
    {
        PasswdRecord passwdRec = fileData.getPasswdRecord(rec);
        String queryMatch = null;
        switch (itsType) {
        case QUERY: {
            if (itsSearchQuery != null) {
                if (filterField(fileData.getTitle(rec))) {
                    queryMatch = QUERY_MATCH_TITLE;
                } else if (filterField(fileData.getUsername(rec))) {
                    queryMatch = QUERY_MATCH_USERNAME;
                } else if (filterField(fileData.getURL(rec))) {
                    queryMatch = QUERY_MATCH_URL;
                } else if (filterField(fileData.getEmail(rec))) {
                    queryMatch = QUERY_MATCH_EMAIL;
                } else if (filterField(fileData.getNotes(rec))) {
                    queryMatch = QUERY_MATCH_NOTES;
                }
            } else {
                queryMatch = QUERY_MATCH;
            }
            break;
        }
        case EXPIRATION: {
            PasswdExpiration expiry = fileData.getPasswdExpiry(rec);
            if (expiry == null) {
                break;
            }
            long expire = expiry.itsExpiration.getTime();
            if (expire < itsExpiryAtMillis) {
                queryMatch = Utils.formatDate(expire, ctx, true, true, true);
            }
            break;
        }
        case SIMILAR: {
            if (itsSimilarFields.isRecord(passwdRec)) {
                break;
            }

            List<String> matches = null;
            if (itsSimilarFields.matchTitle(fileData.getTitle(rec))) {
                //noinspection ConstantConditions
                matches = addMatch(matches, QUERY_MATCH_TITLE);
            }
            String username = fileData.getUsername(rec);
            if (itsSimilarFields.matchUserName(username) ||
                    itsSimilarFields.matchEmail(username)) {
                matches = addMatch(matches, QUERY_MATCH_USERNAME);
            }
            if (itsSimilarFields.matchPassword(
                    passwdRec.getPassword(fileData),
                    fileData.getPasswdHistory(rec))) {
                matches = addMatch(matches, QUERY_MATCH_PASSWORD);
            }
            if (itsSimilarFields.matchUrl(fileData.getURL(rec))) {
                matches = addMatch(matches, QUERY_MATCH_URL);
            }
            String email = fileData.getEmail(rec);
            if (itsSimilarFields.matchEmail(email) ||
                    itsSimilarFields.matchUserName(email)) {
                matches = addMatch(matches, QUERY_MATCH_EMAIL);
            }
            if (matches != null) {
                queryMatch = TextUtils.join(", ", matches);
            }
            break;
        }
        }

        if ((queryMatch != null) &&
            (itsOptions != PasswdRecordFilter.OPTS_DEFAULT)) {
            if (passwdRec != null) {
                for (PwsRecord ref: passwdRec.getRefsToRecord()) {
                    PasswdRecord passwdRef = fileData.getPasswdRecord(ref);
                    if (passwdRef == null) {
                        continue;
                    }
                    switch (passwdRef.getType()) {
                    case NORMAL: {
                        break;
                    }
                    case ALIAS: {
                        if (hasOptions(PasswdRecordFilter.OPTS_NO_ALIAS)) {
                            queryMatch = null;
                        }
                        break;
                    }
                    case SHORTCUT: {
                        if (hasOptions(PasswdRecordFilter.OPTS_NO_SHORTCUT)) {
                            queryMatch = null;
                        }
                        break;
                    }
                    }
                    if (queryMatch == null) {
                        break;
                    }
                }
            }
        }

        return queryMatch;
    }


    /**
     * Is the filter's type a query
     */
    public final boolean isQueryType()
    {
        switch (itsType) {
        case QUERY: {
            return true;
        }
        case EXPIRATION:
        case SIMILAR: {
            return false;
        }
        }
        return false;
    }

    /** Convert the filter to a string */
    public final String toString(Context ctx)
    {
        switch (itsType) {
        case QUERY: {
            if (itsSearchQuery != null) {
                return itsSearchQuery.pattern();
            }
            break;
        }
        case EXPIRATION: {
            switch (itsExpiryFilter) {
            case EXPIRED: {
                return ctx.getString(R.string.password_expired);
            }
            case TODAY: {
                return ctx.getString(R.string.password_expires_today);
            }
            case IN_A_WEEK:
            case IN_TWO_WEEKS:
            case IN_A_MONTH:
            case IN_A_YEAR:
            case CUSTOM: {
                return ctx.getString(
                    R.string.password_expires_before,
                    Utils.formatDate(itsExpiryAtMillis, ctx,
                                     true, true, false));
            }
            case ANY: {
                return ctx.getString(R.string.password_with_expiration);
            }
            }
        }
        case SIMILAR: {
            return ctx.getString(R.string.similar_to,
                                 itsSimilarFields.getDescription());
        }
        }
        return "";
    }

    @Override
    public void close()
    {
        if (itsSimilarFields != null) {
            itsSimilarFields.close();
        }
    }

    /** Does the filter have the given options */
    private boolean hasOptions(int opts)
    {
        return (itsOptions & opts) != 0;
    }


    /** Match a field against the search query */
    private boolean filterField(String field)
    {
        if (field != null) {
            Matcher m = itsSearchQuery.matcher(field);
            return m.find();
        } else {
            return false;
        }
    }

    /**
     * Add a match to the list, creating the list if needed
     */
    private static @NonNull List<String> addMatch(
            @Nullable List<String> matches,
            String match)
    {
        if (matches == null) {
            matches = new ArrayList<>();
        }
        matches.add(match);
        return matches;
    }
}
