/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.Preferences;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.file.PasswdExpiryFilter;
import com.jefftharris.passwdsafe.file.PasswdExpiration;
import com.jefftharris.passwdsafe.file.PasswdFileData;
import com.jefftharris.passwdsafe.file.PasswdRecord;
import com.jefftharris.passwdsafe.file.PasswdRecordFilter;
import com.jefftharris.passwdsafe.file.RecordSimilarFields;
import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.pref.PasswdExpiryNotifPref;

import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The PasswdFileDataView contains state for viewing a password file
 */
public final class PasswdFileDataView
{
    /**
     * Visitor interface for iterating records
     */
    public interface RecordVisitor
    {
        /** Visit a record */
        void visitRecord(String recordUuid);
    }

    private GroupNode itsRootNode;
    private GroupNode itsCurrGroupNode;
    private final ArrayList<String> itsCurrGroups = new ArrayList<>();
    private Owner<PasswdRecordFilter> itsFilter;
    private int itsNumExpired = 0;
    private boolean itsIsExpiryChanged = true;
    private PasswdRecordDisplayOptions itsRecordOptions =
            new PasswdRecordDisplayOptions();
    private boolean itsIsSearchCaseSensitive =
            Preferences.PREF_SEARCH_CASE_SENSITIVE_DEF;
    private boolean itsIsSearchRegex =
            Preferences.PREF_SEARCH_REGEX_DEF;
    private PasswdExpiryNotifPref itsExpiryNotifPref =
            Preferences.PREF_PASSWD_EXPIRY_NOTIF_DEF;
    private Context itsContext;
    private ActContext itsActContext;
    private int itsFolderIcon;
    private int itsRecordIcon;

    private static final String TAG = "PasswdFileDataView";

    /**
     * Constructor
     */
    public PasswdFileDataView()
    {
        itsRootNode = new GroupNode();
    }

    /**
     * Handle when the owning fragment is attached to the context
     */
    public void onAttach(@NonNull Context ctx, SharedPreferences prefs)
    {
        itsContext = ctx.getApplicationContext();
        itsActContext = new ActContext(ctx);
        itsRecordOptions = new PasswdRecordDisplayOptions(prefs);
        itsIsSearchCaseSensitive =
                Preferences.getSearchCaseSensitivePref(prefs);
        itsIsSearchRegex = Preferences.getSearchRegexPref(prefs);
        itsExpiryNotifPref = Preferences.getPasswdExpiryNotifPref(prefs);

        Resources.Theme theme = ctx.getTheme();
        TypedValue attr = new TypedValue();
        theme.resolveAttribute(R.attr.drawableFolder, attr, true);
        itsFolderIcon = attr.resourceId;
        theme.resolveAttribute(R.attr.drawablePersonOutline, attr, true);
        itsRecordIcon = attr.resourceId;
    }

    /**
     * Handle when the owning fragment is detached from its fragment
     */
    public void onDetach()
    {
        itsContext = null;
        itsActContext = null;
    }

    /**
     * Handle when the owning fragment is destroyed
     */
    public void onDestroy()
    {
        if (itsFilter != null) {
            itsFilter.close();
        }
    }

    /**
     * Handle a shared preference change
     * @return Whether the file data should be refreshed
     */
    public boolean handleSharedPreferenceChanged(SharedPreferences prefs,
                                                 @Nullable String key)
    {
        boolean rebuild = false;
        boolean rebuildSearch = false;
        if (key == null) {
            itsRecordOptions = new PasswdRecordDisplayOptions(prefs);
            itsIsSearchCaseSensitive =
                    Preferences.getSearchCaseSensitivePref(prefs);
            itsIsSearchRegex = Preferences.getSearchRegexPref(prefs);
            itsExpiryNotifPref = Preferences.getPasswdExpiryNotifPref(prefs);
            rebuildSearch = true;
            rebuild = true;
            itsIsExpiryChanged = true;
        } else {
            switch (key) {
            case Preferences.PREF_SORT_ASCENDING:
            case Preferences.PREF_SORT_CASE_SENSITIVE:
            case Preferences.PREF_GROUP_RECORDS:
            case Preferences.PREF_RECORD_SORT_ORDER:
            case Preferences.PREF_RECORD_FIELD_SORT: {
                itsRecordOptions = new PasswdRecordDisplayOptions(prefs);
                rebuild = true;
                break;
            }
            case Preferences.PREF_SEARCH_CASE_SENSITIVE: {
                itsIsSearchCaseSensitive =
                        Preferences.getSearchCaseSensitivePref(prefs);
                rebuildSearch = true;
                break;
            }
            case Preferences.PREF_SEARCH_REGEX: {
                itsIsSearchRegex = Preferences.getSearchRegexPref(prefs);
                rebuildSearch = true;
                break;
            }
            case Preferences.PREF_PASSWD_EXPIRY_NOTIF: {
                itsExpiryNotifPref =
                        Preferences.getPasswdExpiryNotifPref(prefs);
                rebuild = true;
                itsIsExpiryChanged = true;
                break;
            }
            }
        }

        if (rebuildSearch &&
            (itsFilter != null) && itsFilter.get().isQueryType()) {
            try {
                PasswdRecordFilter filter = createRecordFilter(
                        itsFilter.get().toString(itsContext));
                setRecordFilter(filter);
            } catch (Exception e) {
                PasswdSafeUtil.showError(e.getMessage(), TAG, e, itsActContext);
            }
        }

        return rebuild || rebuildSearch;
    }

    /**
     * Clear the file data
     */
    public synchronized void clearFileData()
    {
        itsCurrGroups.clear();
        itsIsExpiryChanged = true;
        rebuildView(null);
    }

    /**
     * Set the file data
     */
    public synchronized void setFileData(PasswdFileData fileData)
    {
        itsCurrGroups.clear();
        itsIsExpiryChanged = true;
        rebuildView(fileData);
    }

    /**
     * Refresh the file data
     */
    public synchronized void refreshFileData(PasswdFileData fileData)
    {
        itsCurrGroups.clear();
        rebuildView(fileData);
    }

    /**
     * Get records
     */
    @NonNull
    public synchronized List<PasswdRecordListData> getRecords(
            boolean incRecords,
            boolean incGroups)
    {
        List<PasswdRecordListData> records = new ArrayList<>();
        if ((itsCurrGroupNode == null) || (itsContext == null)) {
            return records;
        }
        Resources res = itsContext.getResources();
        if (res == null) {
            return records;
        }

        if (incGroups) {
            Map<String, GroupNode> entryGroups = itsCurrGroupNode.getGroups();
            if (entryGroups != null) {
                for (Map.Entry<String, GroupNode> entry:
                        entryGroups.entrySet()) {
                    int items = entry.getValue().getNumRecords();
                    String str = res.getQuantityString(R.plurals.group_items,
                                                       items, items);

                    records.add(new PasswdRecordListData(
                            entry.getKey(), str, null, null, null,
                            null, itsFolderIcon, false));
                }
            }
        }

        if (incRecords) {
            List<MatchPwsRecord> entryRecs = itsCurrGroupNode.getRecords();
            if (entryRecs != null) {
                for (MatchPwsRecord rec: entryRecs) {
                    records.add(createListData(rec));
                }
            }
        }

        PasswdRecordListDataComparator comp =
                new PasswdRecordListDataComparator(itsRecordOptions);
        Collections.sort(records, comp);
        return records;
    }

    /**
     * Set the current groups
     */
    public synchronized void setCurrGroups(List<String> groups)
    {
        itsCurrGroups.clear();
        if (groups != null) {
            itsCurrGroups.addAll(groups);
        }
        updateCurrentGroup();
    }

    /**
     * Does the data view show the given group
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasGroup(String group)
    {
        if (TextUtils.isEmpty(group)) {
            return true;
        }

        ArrayList<String> groups = new ArrayList<>();
        PasswdFileData.splitGroup(group, groups);

        GroupNode node = itsRootNode;
        for (String checkGroup: groups) {
            GroupNode childNode = node.getGroup(checkGroup);
            if (childNode == null) {
                return false;
            }
            node = childNode;
        }
        return true;
    }

    /**
     * Is the view grouping records
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isGroupingRecords()
    {
        return itsRecordOptions.itsIsGroupRecords;
    }

    /**
     * Get the record filter
     */
    @Nullable
    public synchronized PasswdRecordFilter getRecordFilter()
    {
        return (itsFilter != null) ? itsFilter.get() : null;
    }

    /**
     * Create a record filter from a query string
     */
    public PasswdRecordFilter createRecordFilter(String query)
            throws Exception
    {
        PasswdRecordFilter filter = null;
        Pattern queryPattern = null;
        if (!TextUtils.isEmpty(query)) {
            try {
                int flags = 0;
                if (!itsIsSearchCaseSensitive) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                if (!itsIsSearchRegex) {
                    flags |= Pattern.LITERAL;
                }
                queryPattern = Pattern.compile(query, flags);
            } catch(PatternSyntaxException e) {
                throw new Exception("Invalid query regex", e);
            }
        }
        if (queryPattern != null) {
            filter = new PasswdRecordFilter(queryPattern,
                                            PasswdRecordFilter.OPTS_DEFAULT);
        }

        return filter;
    }

    /**
     * Create a record filter which matches records similar to the given one
     */
    @Nullable
    public PasswdRecordFilter createSimilarRecordFilter(
            String recUuid,
            @NonNull PasswdFileData fileData)
    {
        PwsRecord rec = fileData.getRecord(recUuid);
        if (rec == null) {
            return null;
        }

        PasswdRecord passwdRec = fileData.getPasswdRecord(rec);
        if (passwdRec == null) {
            return null;
        }

        return new PasswdRecordFilter(
                new RecordSimilarFields(
                        passwdRec, fileData, itsIsSearchCaseSensitive));
    }

    /**
     * Set the record filter
     */
    public synchronized void setRecordFilter(PasswdRecordFilter filter)
    {
        if (itsFilter != null) {
            itsFilter.close();
            itsFilter = null;
        }
        if (filter != null) {
            itsFilter = new Owner<>(filter);
        }
    }

    /**
     * Visit all records under the current group
     */
    public synchronized void walkGroupRecords(RecordVisitor visitor)
    {
        walkGroupRecords(itsCurrGroupNode, visitor);
    }

    /**
     * Check whether the expiration options have changed
     */
    public boolean checkExpiryChanged()
    {
        boolean changed = itsIsExpiryChanged;
        itsIsExpiryChanged = false;
        return changed;
    }

    /**
     * Reset whether the expiration options have changed
     */
    public void resetExpiryChanged()
    {
        itsIsExpiryChanged = true;
    }

    /**
     * Get whether there are expired records
     */
    public boolean hasExpiredRecords()
    {
        return itsNumExpired > 0;
    }

    /**
     * Get the description of the expired records
     */
    public String getExpiredRecordsStr(Context ctx)
    {
        String str = null;
        PasswdExpiryFilter filter = itsExpiryNotifPref.getFilter();
        if (filter != null) {
            str = filter.getRecordsExpireStr(itsNumExpired, ctx.getResources());
        }
        return str;
    }

    /**
     * Get the filter for expired records
     */
    public PasswdExpiryFilter getExpiredRecordsFilter()
    {
        return itsExpiryNotifPref.getFilter();
    }

    /**
     * Rebuild the view information
     */
    private synchronized void rebuildView(PasswdFileData fileData)
    {
        itsRootNode = new GroupNode();
        itsNumExpired = 0;
        if (fileData == null) {
            updateCurrentGroup();
            return;
        }

        List<PwsRecord> records = fileData.getRecords();
        if (itsRecordOptions.itsIsGroupRecords) {
            Comparator<String> groupComp =
                    itsRecordOptions.itsIsSortCaseSensitive ?
                    new StringComparator() : String.CASE_INSENSITIVE_ORDER;
            if (!itsRecordOptions.itsIsSortAscending) {
                final Comparator<String> comp = groupComp;
                groupComp = (s1, s2) -> -comp.compare(s1, s2);
            }

            for (PwsRecord rec: records) {
                String match = filterRecord(rec, fileData);
                if (match == null) {
                    continue;
                }

                String group = fileData.getGroup(rec);
                if (group == null) {
                    group = "";
                }
                ArrayList<String> groups = new ArrayList<>();
                PasswdFileData.splitGroup(group, groups);
                GroupNode node = itsRootNode;
                for (String g : groups) {
                    GroupNode groupNode = node.getGroup(g);
                    if (groupNode == null) {
                        groupNode = new GroupNode();
                        node.putGroup(g, groupNode, groupComp);
                    }
                    node = groupNode;
                }
                node.addRecord(new MatchPwsRecord(rec, fileData, match));
             }
        } else {
            for (PwsRecord rec: records) {
                String match = filterRecord(rec, fileData);
                if (match != null) {
                    itsRootNode.addRecord(
                            new MatchPwsRecord(rec, fileData, match));
                }
            }
        }
        updateCurrentGroup();

        PasswdExpiryFilter filter = itsExpiryNotifPref.getFilter();
        if (filter != null) {
            long expiration = filter.getExpiryFromNow(null);
            for (PasswdRecord rec : fileData.getPasswdRecords()) {
                PasswdExpiration expiry = rec.getPasswdExpiry();
                if ((expiry != null) &&
                    (expiry.itsExpiration.getTime() <= expiration)) {
                    ++itsNumExpired;
                }
            }
        }
    }

    /** Update the current group */
    private void updateCurrentGroup()
    {
        itsCurrGroupNode = itsRootNode;
        for (int i = 0; i < itsCurrGroups.size(); ++i) {
            String group = itsCurrGroups.get(i);
            GroupNode childNode = itsCurrGroupNode.getGroup(group);
            if (childNode == null) {
                // Prune groups from current item in the stack on down
                itsCurrGroups.subList(i, itsCurrGroups.size()).clear();
                break;
            }
            itsCurrGroupNode = childNode;
        }
    }

    /**
     * Filter a record
     * @return A non-null string if the record matches the filter; null if it
     * does not
     */
    private String filterRecord(PwsRecord rec, PasswdFileData fileData)
    {
        if (itsFilter == null) {
            return PasswdRecordFilter.QUERY_MATCH;
        }
        return itsFilter.get().filterRecord(rec, fileData, itsContext);
    }

    /**
     * Recursively visit all records under a group.  Must be called while
     * synchronized.
     */
    private void walkGroupRecords(GroupNode node, RecordVisitor visitor)
    {
        if (node == null) {
            return;
        }

        Map<String, GroupNode> childGroups = node.getGroups();
        if (childGroups != null) {
            for (GroupNode child : childGroups.values()) {
                walkGroupRecords(child, visitor);
            }
        }

        List<MatchPwsRecord> childRecords = node.getRecords();
        if (childRecords != null) {
            for (MatchPwsRecord matchRec : childRecords) {
                visitor.visitRecord(matchRec.itsUuid);
            }
        }
    }

    /**
     * Create a record item for a password record
     */
    @NonNull
    @Contract("_ -> new")
    private PasswdRecordListData createListData(@NonNull MatchPwsRecord rec)
    {
        String title = rec.itsTitle;
        if (title == null) {
            title = "Untitled";
        }
        String user = rec.itsUsername;
        if (!TextUtils.isEmpty(user)) {
            user = "[" + user + "]";
        }

        return new PasswdRecordListData(title, user, rec.itsUuid,
                                        rec.itsCreationTime, rec.itsModTime,
                                        rec.itsMatch, itsRecordIcon, true);
    }


    /**
     * A group node
     */
    private static final class GroupNode
    {
        private List<MatchPwsRecord> itsRecords = null;
        private TreeMap<String, GroupNode> itsGroups = null;

        /** Constructor */
        private GroupNode()
        {
        }

        /** Add a record */
        private void addRecord(MatchPwsRecord rec)
        {
            if (itsRecords == null) {
                itsRecords = new ArrayList<>();
            }
            itsRecords.add(rec);
        }

        /** Get the records */
        private List<MatchPwsRecord> getRecords()
        {
            return itsRecords;
        }

        /** Put a child group */
        private void putGroup(String name,
                              GroupNode node,
                              Comparator<String> groupComp)
        {
            if (itsGroups == null) {
                itsGroups = new TreeMap<>(groupComp);
            }
            itsGroups.put(name, node);
        }

        /** Get a group */
        @Nullable
        private GroupNode getGroup(String name)
        {
            if (itsGroups == null) {
                return null;
            } else {
                return itsGroups.get(name);
            }
        }

        /** Get the groups */
        private Map<String, GroupNode> getGroups()
        {
            return itsGroups;
        }

        /** Get the number of records */
        private int getNumRecords()
        {
            int num = 0;
            if (itsRecords != null) {
                num += itsRecords.size();
            }
            if (itsGroups != null) {
                for (GroupNode child: itsGroups.values()) {
                    num += child.getNumRecords();
                }
            }
            return num;
        }
    }


    /**
     * A matched PwsRecord
     */
    private static final class MatchPwsRecord
    {
        private final String itsTitle;
        private final String itsUsername;
        private final String itsUuid;
        private final Date itsCreationTime;
        private final Date itsModTime;
        private final String itsMatch;

        private MatchPwsRecord(PwsRecord rec,
                               @NonNull PasswdFileData fileData,
                               String match)
        {
            itsTitle = fileData.getTitle(rec);
            itsUsername = fileData.getUsername(rec);
            itsUuid = fileData.getUUID(rec);
            itsCreationTime = fileData.getCreationTime(rec);
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
    }


    /**
     * Case-sensitive string comparator
     */
    private static final class StringComparator implements Comparator<String>
    {
        /** Compare the strings */
        public int compare(@NonNull String arg0, String arg1)
        {
            return arg0.compareTo(arg1);
        }
    }
}
