/*
 * Copyright (©) 2009-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.PasswdSafeApp;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.util.Pair;

import org.pwsafe.lib.UUID;
import org.pwsafe.lib.Util;
import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.InvalidPassphraseException;
import org.pwsafe.lib.exception.RecordLoadException;
import org.pwsafe.lib.exception.UnsupportedFileVersionException;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsByteField;
import org.pwsafe.lib.file.PwsField;
import org.pwsafe.lib.file.PwsFieldTypeV1;
import org.pwsafe.lib.file.PwsFieldTypeV2;
import org.pwsafe.lib.file.PwsFieldTypeV3;
import org.pwsafe.lib.file.PwsFile;
import org.pwsafe.lib.file.PwsFileStorage;
import org.pwsafe.lib.file.PwsFileV3;
import org.pwsafe.lib.file.PwsHeaderTypeV3;
import org.pwsafe.lib.file.PwsIntegerField;
import org.pwsafe.lib.file.PwsPasswdField;
import org.pwsafe.lib.file.PwsPasswdUnicodeField;
import org.pwsafe.lib.file.PwsPassword;
import org.pwsafe.lib.file.PwsRecord;
import org.pwsafe.lib.file.PwsRecordV3;
import org.pwsafe.lib.file.PwsStorage;
import org.pwsafe.lib.file.PwsStringField;
import org.pwsafe.lib.file.PwsStringUnicodeField;
import org.pwsafe.lib.file.PwsTimeField;
import org.pwsafe.lib.file.PwsUUIDField;
import org.pwsafe.lib.file.PwsUnknownField;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("SameParameterValue")
public class PasswdFileData
{
    private PasswdFileUri itsUri;
    private PwsFile itsPwsFile;
    private final HashMap<String, PwsRecord> itsRecordsByUUID = new HashMap<>();
    private final Map<PwsRecord, PasswdRecord> itsPasswdRecords =
        new IdentityHashMap<>();
    private final ArrayList<PwsRecord> itsRecords = new ArrayList<>();
    private HeaderPasswdPolicies itsHdrPolicies = new HeaderPasswdPolicies();
    private boolean itsIsUriWritable = false;
    private boolean itsIsYubikey = false;

    private static final List<PasswdFileDataObserver> itsObservers =
            new ArrayList<>();

    private static final String TAG = "PasswdFileData";

    private static final int FIELD_UNSUPPORTED = -1;
    private static final int FIELD_NOT_PRESENT = -2;

    public enum EmailStyle
    {
        FULL,
        ADDR_ONLY
    }

    public enum UrlStyle
    {
        FULL,
        URL_ONLY
    }

    public PasswdFileData(PasswdFileUri uri)
    {
        itsUri = uri;
    }

    /** @noinspection RedundantSuppression*/
    public void load(Owner<PwsPassword>.Param passwd, Context context)
            throws IOException, EndOfFileException, InvalidPassphraseException,
                   UnsupportedFileVersionException
    {
        itsPwsFile = itsUri.load(passwd, context);
        //noinspection ConstantConditions
        itsPwsFile.setReadOnly(PasswdSafeApp.DEBUG_AUTO_FILE == null);
        itsIsUriWritable = itsUri.isWritable().first;
        finishOpenFile();
    }

    public void createNewFile(Owner<PwsPassword>.Param passwd, Context context)
        throws IOException
    {
        itsPwsFile = itsUri.createNew(passwd, context);
        itsPwsFile.setReadOnly(false);
        itsIsUriWritable = true;
        save(context);
        finishOpenFile();
    }

    /**
     * Save the file
     */
    public void save(Context context)
            throws IOException, ConcurrentModificationException
    {
        doSave(new PasswdFileSaveHelper(context), null, context);
    }

    /**
     * Save the file to the given file name without a backup
     */
    public void saveAsNoBackup(@NonNull File file, Context context)
            throws IOException, ConcurrentModificationException
    {
        doSave(null, new PwsFileStorage(file.getPath(), null), context);
    }

    /**
     * Save the file to the given URI
     */
    public void saveAs(@NonNull PasswdFileUri fileUri,
                       @NonNull Context context)
            throws IOException, ConcurrentModificationException
    {
        doSave(new PasswdFileSaveHelper(context),
               fileUri.createStorageForSave(context), context);
    }

    public void close()
    {
        itsUri = null;
        itsPwsFile.dispose();
        itsPwsFile = null;
        itsIsUriWritable = false;
        indexRecords();
    }

    public ArrayList<PwsRecord> getRecords()
    {
        return itsRecords;
    }

    public PwsRecord getRecord(String uuid)
    {
        return itsRecordsByUUID.get(uuid);
    }

    public PasswdRecord getPasswdRecord(PwsRecord rec)
    {
        return itsPasswdRecords.get(rec);
    }

    /** Get the collection of PasswdRecords in the file */
    public Collection<PasswdRecord> getPasswdRecords()
    {
        return itsPasswdRecords.values();
    }

    @Nullable
    public PwsRecord createRecord()
    {
        if (itsPwsFile != null) {
            return itsPwsFile.newRecord();
        } else {
            return null;
        }
    }

    public final void addRecord(PwsRecord rec)
    {
        if (itsPwsFile != null) {
            itsPwsFile.add(rec);
            indexRecords();
        }
    }

    public final boolean removeRecord(PwsRecord rec, ActContext context)
    {
        int errMsg = doRemoveRecord(rec);
        if (errMsg != 0) {
            Context ctx = context.getContext();
            if (ctx != null) {
                String msg = ctx.getString(R.string.cannot_delete_record,
                                           ctx.getString(errMsg));
                PasswdSafeUtil.showErrorMsg(msg, context);
            }
            return false;
        }
        return true;
    }

    public final void changePasswd(Owner<PwsPassword>.Param passwd)
    {
        setHdrLastPasswordChange(new Date());
        itsPwsFile.setPassphrase(passwd);
    }

    public final PasswdFileUri getUri()
    {
        return itsUri;
    }

    /**
     * Get whether a Yubikey was used to open the file
     */
    public final boolean isNotYubikey()
    {
        return !itsIsYubikey;
    }

    /**
     * Set whether a Yubikey was used to open the file
     */
    public final void setYubikey(boolean yubikey)
    {
        itsIsYubikey = yubikey;
    }

    /**
     * Is the file writable
     */
    public final boolean isWritable()
    {
        return itsIsUriWritable &&
               (itsPwsFile != null) && !itsPwsFile.isReadOnly();
    }

    /**
     * Set the file writable if allowed
     */
    public final void setWritable(boolean writable)
    {
        if (itsPwsFile != null) {
            itsPwsFile.setReadOnly(!(writable && itsIsUriWritable));
        }
    }

    /**
     * Is the file capable of being written
     */
    public final boolean isWriteCapable()
    {
        return itsIsUriWritable;
    }

    public final boolean canEdit()
    {
        if (!isWritable() || (itsPwsFile == null)) {
            return false;
        }
        switch (itsPwsFile.getFileVersionMajor()) {
        case V2,
             V3 -> {
            return true;
        }
        case V1 -> {
        }
        }
        return false;
    }

    public final boolean canDelete()
    {
        return isWritable() && itsUri.isDeletable();
    }

    public final boolean isV3()
    {
        if (itsPwsFile == null) {
            return false;
        }
        switch (itsPwsFile.getFileVersionMajor()) {
        case V3 -> {
            return true;
        }
        case V2,
             V1 -> {
        }
        }
        return false;
    }

    private boolean isV2()
    {
        if (itsPwsFile == null) {
            return false;
        }
        switch (itsPwsFile.getFileVersionMajor()) {
        case V2 -> {
            return true;
        }
        case V3,
             V1 -> {
        }
        }
        return false;
    }

    @Nullable
    public final String getOpenPasswordEncoding()
    {
        return (itsPwsFile != null) ? itsPwsFile.getOpenPasswordEncoding() :
            null;
    }

    @NonNull
    public final String getId(PwsRecord rec)
    {
        return PasswdRecord.getRecordId(getGroup(rec), getTitle(rec),
                                        getUsername(rec));
    }

    /** Get the time the record was created */
    public final Date getCreationTime(PwsRecord rec)
    {
        return getDateField(rec, PwsFieldTypeV3.CREATION_TIME);
    }

    public final String getEmail(PwsRecord rec, @NonNull EmailStyle style)
    {
        String email = getField(rec, PwsFieldTypeV3.EMAIL);
        switch (style) {
        case FULL: {
            break;
        }
        case ADDR_ONLY: {
            if (!TextUtils.isEmpty(email)) {
                int queryPos = email.indexOf('?');
                if (queryPos != -1) {
                    email = email.substring(0, queryPos);
                }
            }
            break;
        }
        }
        return email;
    }

    public final void setEmail(String str, PwsRecord rec)
    {
        setField(str, rec, PwsFieldTypeV3.EMAIL);
    }

    public final String getGroup(PwsRecord rec)
    {
        return getField(rec, PwsFieldTypeV3.GROUP);
    }

    public final void setGroup(String str, PwsRecord rec)
    {
        setField(str, rec, PwsFieldTypeV3.GROUP);
    }

    /**
     * Split the group into the given list
     */
    public static void splitGroup(String group,
                                  @NonNull ArrayList<String> groups)
    {
        groups.clear();
        String[] splitGroups = TextUtils.split(group, "\\.");
        for (String splitGroup: splitGroups) {
            if (TextUtils.isEmpty(splitGroup)) {
                if (!groups.isEmpty()) {
                    int pos = groups.size() - 1;
                    String last = groups.get(pos);
                    groups.set(pos, last + ".");
                }
            } else {
                groups.add(splitGroup);
            }
        }
    }

    /** Get the time the record was last modified */
    public final Date getLastModTime(PwsRecord rec)
    {
        return getDateField(rec, PwsFieldTypeV3.LAST_MOD_TIME);
    }

    public final @NonNull PasswdNotes getNotes(PwsRecord rec, Context ctx)
    {
        return new PasswdNotes(getField(rec, PwsFieldTypeV3.NOTES), ctx);
    }

    public final void setNotes(String str, PwsRecord rec)
    {
        if (str != null) {
            str = str.replace("\n", "\r\n");
        }
        setField(str, rec, PwsFieldTypeV3.NOTES);
    }

    public final String getPassword(PwsRecord rec)
    {
        return getField(rec, PwsFieldTypeV3.PASSWORD);
    }

    public final void setPassword(String oldPasswd, String newPasswd,
                                  PwsRecord rec)
    {
        PasswdHistory history = getPasswdHistory(rec);
        if ((history != null) && !TextUtils.isEmpty(oldPasswd)) {
            Date passwdDate = getPasswdLastModTime(rec);
            if (passwdDate == null) {
                passwdDate = getCreationTime(rec);
            }
            history.addPasswd(oldPasswd, passwdDate);
            setPasswdHistory(history, rec, false);
        }
        setField(newPasswd, rec, PwsFieldTypeV3.PASSWORD);

        PasswdExpiration expiry = getPasswdExpiry(rec);
        Date expTime = null;
        if ((expiry != null) && expiry.itsIsRecurring &&
            (expiry.itsInterval > 0)) {
            long exp = System.currentTimeMillis();
            exp += (long)expiry.itsInterval * DateUtils.DAY_IN_MILLIS;
            expTime = new Date(exp);
        }
        setField(expTime, rec, PwsFieldTypeV3.PASSWORD_LIFETIME, false);

        // Update PasswdRecord and indexes if the record exists
        PasswdRecord passwdRec = getPasswdRecord(rec);
        if (passwdRec != null) {
            PwsRecord oldRef = passwdRec.getRef();
            if (oldRef != null) {
                PasswdRecord oldPasswdRec = getPasswdRecord(oldRef);
                oldPasswdRec.removeRefToRecord(rec);
            }
            passwdRec.passwordChanged(this);
            PwsRecord newRef = passwdRec.getRef();
            if (newRef != null) {
                PasswdRecord newPasswdRec = getPasswdRecord(newRef);
                newPasswdRec.addRefToRecord(rec);
            }
        }
    }

    /** Get the password expiration */
    public final PasswdExpiration getPasswdExpiry(PwsRecord rec)
    {
        PasswdExpiration expiry = null;
        Date expTime = getDateField(rec, PwsFieldTypeV3.PASSWORD_LIFETIME);
        if (expTime != null) {
            Integer expInt =
                getIntField(rec, PwsFieldTypeV3.PASSWORD_EXPIRY_INTERVAL);
            boolean haveInt = (expInt != null);
            expiry = new PasswdExpiration(expTime, haveInt ? expInt : 0,
                                          haveInt);
        }
        return expiry;
    }

    /** Set the password expiration */
    public final void setPasswdExpiry(PasswdExpiration expiry, PwsRecord rec)
    {
        Date expDate = null;
        int expInterval = 0;
        if (expiry != null) {
            expDate = expiry.itsExpiration;
            if (expiry.itsIsRecurring) {
                expInterval = expiry.itsInterval;
            }
        }
        setField(expDate, rec, PwsFieldTypeV3.PASSWORD_LIFETIME);
        setField((expInterval != 0) ? expInterval : null, rec,
                 PwsFieldTypeV3.PASSWORD_EXPIRY_INTERVAL);

        PasswdRecord passwdRec = getPasswdRecord(rec);
        if (passwdRec != null) {
            passwdRec.passwdExpiryChanged(this);
        }
    }

    /** Get the time the password was last modified */
    public final Date getPasswdLastModTime(PwsRecord rec)
    {
        return getDateField(rec, PwsFieldTypeV3.PASSWORD_MOD_TIME);
    }

    /** Clear the time the password was last modified */
    public final void clearPasswdLastModTime(PwsRecord rec)
    {
        setField(null, rec, PwsFieldTypeV3.PASSWORD_MOD_TIME);
    }

    @Nullable
    public final PasswdHistory getPasswdHistory(PwsRecord rec)
    {
        String fieldStr = getField(rec, PwsFieldTypeV3.PASSWORD_HISTORY);
        if (!TextUtils.isEmpty(fieldStr)) {
            try {
                return new PasswdHistory(Objects.requireNonNull(fieldStr));
            } catch (Exception e) {
                Log.e(TAG, "Error reading password history: " + e, e);
            }
        }
        return null;
    }

    public final void setPasswdHistory(PasswdHistory history, PwsRecord rec,
                                       boolean updateModTime)
    {
        setField((history == null) ? null : history.toString(),
                 rec, PwsFieldTypeV3.PASSWORD_HISTORY, updateModTime);
    }

    /** Get the password policy contained in a record */
    public final PasswdPolicy getPasswdPolicy(PwsRecord rec)
    {
        return PasswdPolicy.parseRecordPolicy(
            getField(rec, PwsFieldTypeV3.PASSWORD_POLICY_NAME),
            getField(rec, PwsFieldTypeV3.PASSWORD_POLICY),
            getField(rec, PwsFieldTypeV3.OWN_PASSWORD_SYMBOLS));
    }

    /** Set the password policy for a record */
    public final void setPasswdPolicy(PasswdPolicy policy, PwsRecord rec)
    {
        setPasswdPolicyImpl(policy, rec, true);
    }

    public final boolean isProtected(PwsRecord rec)
    {
        boolean prot = false;
        PwsField field = doGetRecField(rec, PwsFieldTypeV3.PROTECTED_ENTRY);
        if (field != null) {
            byte[] value = field.getBytes();
            if ((value != null) && (value.length > 0)) {
                prot = (value[0] != 0);
            }
        }
        return prot;
    }

    public final void setProtected(boolean prot, PwsRecord rec)
    {
        byte val = prot ? (byte)1 : (byte)0;
        setField(val, rec, PwsFieldTypeV3.PROTECTED_ENTRY);
        updateFormatVersion(PwsRecordV3.DB_FMT_MINOR_3_25);
    }

    public final String getTitle(PwsRecord rec)
    {
        return getField(rec, PwsFieldTypeV3.TITLE);
    }

    public final void setTitle(String str, PwsRecord rec)
    {
        setField(str, rec, PwsFieldTypeV3.TITLE);
    }

    public final String getUsername(PwsRecord rec)
    {
        return getField(rec, PwsFieldTypeV3.USERNAME);
    }

    public final void setUsername(String str, PwsRecord rec)
    {
        setField(str, rec, PwsFieldTypeV3.USERNAME);
    }

    public final String getURL(PwsRecord rec, @NonNull UrlStyle style)
    {
        String url = getField(rec, PwsFieldTypeV3.URL);
        switch (style) {
        case FULL: {
            break;
        }
        case URL_ONLY: {
            if (!TextUtils.isEmpty(url)) {
                for (String key: new String[]{"[alt]", "{alt}", "[ssh]",
                                              "[autotype]", "[xa]"}) {
                    url = url.replace(key, "");
                }
            }
            break;
        }
        }
        return url;
    }

    public final void setURL(String str, PwsRecord rec)
    {
        setField(str, rec, PwsFieldTypeV3.URL);
    }

    public final String getUUID(PwsRecord rec)
    {
        return getField(rec, PwsFieldTypeV3.UUID);
    }

    public final String getHdrVersion()
    {
        return getHdrField(PwsHeaderTypeV3.VERSION);
    }

    public final String getHdrLastSaveUser()
    {
        return getHdrField(PwsHeaderTypeV3.LAST_SAVE_USER);
    }

    private void setHdrLastSaveUser(String user)
    {
        setHdrField(PwsHeaderTypeV3.LAST_SAVE_USER, user);
    }

    public final String getHdrLastSaveHost()
    {
        return getHdrField(PwsHeaderTypeV3.LAST_SAVE_HOST);
    }

    private void setHdrLastSaveHost(String host)
    {
        setHdrField(PwsHeaderTypeV3.LAST_SAVE_HOST, host);
    }

    public final String getHdrLastSaveApp()
    {
        return getHdrField(PwsHeaderTypeV3.LAST_SAVE_WHAT);
    }

    private void setHdrLastSaveApp(String app)
    {
        setHdrField(PwsHeaderTypeV3.LAST_SAVE_WHAT, app);
    }

    public final String getHdrLastSaveTime()
    {
        return getHdrField(PwsHeaderTypeV3.LAST_SAVE_TIME);
    }

    private void setHdrLastSaveTime(Date date)
    {
        setHdrField(PwsHeaderTypeV3.LAST_SAVE_TIME, date);
    }

    public final String getHdrLastPasswordChange()
    {
        return getHdrField(PwsHeaderTypeV3.LAST_PASSWORD_CHANGE);
    }

    private void setHdrLastPasswordChange(Date date)
    {
        setHdrField(PwsHeaderTypeV3.LAST_PASSWORD_CHANGE, date);
        updateFormatVersion(PwsRecordV3.DB_FMT_MINOR_3_47);
    }


    /** Get the named password policies from the file header */
    public HeaderPasswdPolicies getHdrPasswdPolicies()
    {
        return itsHdrPolicies;
    }

    /**
     * Set the named password policies in the file header
     *
     * @param policies The policies; null to remove the field
     * @param policyRename If non-null the old and new names of a renamed
     *                      policy
     */
    public final void setHdrPasswdPolicies(List<PasswdPolicy> policies,
                                           Pair<String, String> policyRename)
    {
        setHdrField(PwsHeaderTypeV3.NAMED_PASSWORD_POLICIES,
                    PasswdPolicy.hdrPoliciesToString(policies));
        updateFormatVersion(PwsRecordV3.DB_FMT_MINOR_3_28);
        if (policyRename != null) {
            // Rename policy in records as needed
            for (PasswdRecord rec: itsPasswdRecords.values()) {
                PasswdPolicy recPolicy = rec.getPasswdPolicy();
                if ((recPolicy == null) ||
                    (recPolicy.getLocation() !=
                        PasswdPolicy.Location.RECORD_NAME) ||
                    (!recPolicy.getName().equals(policyRename.first))) {
                    continue;
                }
                recPolicy = new PasswdPolicy(policyRename.second, recPolicy);
                PasswdSafeUtil.dbginfo(TAG, "Rename policy to %s for %s",
                                       recPolicy.getName(),
                                       getId(rec.getRecord()));

                setPasswdPolicyImpl(recPolicy, rec.getRecord(), false);
            }
            indexRecords();
        } else {
            indexPasswdPolicies();
        }
    }

    /**
     * Get the errors which occurred when opening the file
     * @return The list of errors; null if none occurred
     */
    public @Nullable List<RecordLoadException> getRecordErrors()
    {
        return itsPwsFile.getLoadErrors();
    }

    private static int hexBytesToInt(byte[] bytes, int pos, int len)
    {
        int i = 0;
        for (int idx = pos; idx < (pos + len); ++idx) {
            i <<= 4;
            i |= Character.digit(bytes[idx], 16);
        }
        return i;
    }

    /** Add an observer for file changes */
    public static void addObserver(PasswdFileDataObserver observer)
    {
        itsObservers.add(observer);
    }

    private void setSaveHdrFields(Context context)
    {
        setHdrLastSaveApp(PasswdSafeUtil.getAppTitle(context) +
                          " " +
                          PasswdSafeUtil.getAppVersion(context));
        setHdrLastSaveUser("User");
        setHdrLastSaveHost(Build.MODEL);
        setHdrLastSaveTime(new Date());
    }

    private void updateFormatVersion(byte minMinor)
    {
        if (isV3()) {
            PwsRecord rec = ((PwsFileV3)itsPwsFile).getHeaderRecord();
            int minor = getHdrMinorVersion(rec);
            if ((minor != -1) && (minor < minMinor)) {
                setHdrMinorVersion(rec, minMinor);
            }
        }
    }

    /** Set the password policy for a record and optionally update indexes */
    private void setPasswdPolicyImpl(PasswdPolicy policy,
                                           PwsRecord rec,
                                           boolean index)
    {
        PasswdPolicy.RecordPolicyStrs strs =
            PasswdPolicy.recordPolicyToString(policy);
        setField((strs == null) ? null : strs.itsPolicyName,
                 rec, PwsFieldTypeV3.PASSWORD_POLICY_NAME);
        setField((strs == null) ? null : strs.itsPolicyStr,
                 rec, PwsFieldTypeV3.PASSWORD_POLICY);
        setField((strs == null) ? null : strs.itsOwnSymbols,
                 rec, PwsFieldTypeV3.OWN_PASSWORD_SYMBOLS);
        updateFormatVersion(PwsRecordV3.DB_FMT_MINOR_3_28);

        if (index) {
            PasswdRecord passwdRec = getPasswdRecord(rec);
            if (passwdRec != null) {
                passwdRec.passwdPolicyChanged(this);
            }
            indexPasswdPolicies();
        }
    }

    /** Get a field value as a string */
    @Nullable
    private String getField(PwsRecord rec, PwsFieldTypeV3 fieldId)
    {
        if (itsPwsFile == null) {
            return "";
        }

        int fieldIdVal = getVersionFieldId(fieldId);
        if (fieldIdVal== FIELD_UNSUPPORTED) {
            return "(unsupported)";
        }
        PwsField field = doGetField(rec, fieldIdVal);
        return (field == null) ? null : field.toString();
    }

    /** Get a field value as an 4 byte integer */
    @Nullable
    private Integer getIntField(PwsRecord rec, PwsFieldTypeV3 fieldId)
    {
        Integer val = null;
        PwsField field = doGetRecField(rec, fieldId);
        if (field instanceof PwsIntegerField) {
            val = (Integer)field.getValue();
        }
        return val;
    }

    /** Get a field value as a Date */
    @Nullable
    private Date getDateField(PwsRecord rec, PwsFieldTypeV3 fieldId)
    {
        Date date = null;
        PwsField field = doGetRecField(rec, fieldId);
        if (field instanceof PwsTimeField) {
            date = (Date)field.getValue();
        }
        return date;
    }

    private int getVersionFieldId(PwsFieldTypeV3 field)
    {
        if (itsPwsFile == null) {
            return FIELD_NOT_PRESENT;
        }

        int fieldId = FIELD_NOT_PRESENT;
        boolean versionSupported = false;
        switch (itsPwsFile.getFileVersionMajor()) {
        case V3: {
            versionSupported = true;
            fieldId = field.getId();
            break;
        }
        case V2: {
            versionSupported = true;
            PwsFieldTypeV2 v2Field = null;
            switch (field) {
            case GROUP -> v2Field = PwsFieldTypeV2.GROUP;
            case NOTES -> v2Field = PwsFieldTypeV2.NOTES;
            case PASSWORD -> v2Field = PwsFieldTypeV2.PASSWORD;
            case PASSWORD_LIFETIME ->
                    v2Field = PwsFieldTypeV2.PASSWORD_LIFETIME;
            case TITLE -> v2Field = PwsFieldTypeV2.TITLE;
            case USERNAME -> v2Field = PwsFieldTypeV2.USERNAME;
            case UUID -> v2Field = PwsFieldTypeV2.UUID;
            case URL -> v2Field = PwsFieldTypeV2.URL;
            case EMAIL,
                 PASSWORD_HISTORY,
                 PROTECTED_ENTRY,
                 OWN_PASSWORD_SYMBOLS,
                 PASSWORD_POLICY_NAME,
                 CREATION_TIME,
                 PASSWORD_MOD_TIME,
                 LAST_MOD_TIME,
                 PASSWORD_EXPIRY_INTERVAL,
                 V3_ID_STRING,
                 LAST_ACCESS_TIME,
                 PASSWORD_POLICY_DEPRECATED,
                 AUTOTYPE,
                 PASSWORD_POLICY,
                 RUN_COMMAND,
                 DOUBLE_CLICK_ACTION,
                 SHIFT_DOUBLE_CLICK_ACTION,
                 ENTRY_KEYBOARD_SHORTCUT,
                 END_OF_RECORD,
                 UNKNOWN -> {
            }
            }
            if (v2Field != null) {
                fieldId = v2Field.getId();
            }
            break;
        }
        case V1: {
            versionSupported = true;
            PwsFieldTypeV1 v1Field = null;
            switch (field) {
            case NOTES -> v1Field = PwsFieldTypeV1.NOTES;
            case PASSWORD -> v1Field = PwsFieldTypeV1.PASSWORD;
            case TITLE -> v1Field = PwsFieldTypeV1.TITLE;
            case USERNAME -> v1Field = PwsFieldTypeV1.USERNAME;
            case UUID ->
                    // No real UUID field for V1, so just use the phantom one
                    v1Field = PwsFieldTypeV1.UUID;
            case EMAIL,
                 GROUP,
                 PASSWORD_LIFETIME,
                 URL,
                 PASSWORD_HISTORY,
                 PROTECTED_ENTRY,
                 OWN_PASSWORD_SYMBOLS,
                 PASSWORD_POLICY_NAME,
                 CREATION_TIME,
                 PASSWORD_MOD_TIME,
                 LAST_MOD_TIME,
                 PASSWORD_EXPIRY_INTERVAL,
                 V3_ID_STRING,
                 LAST_ACCESS_TIME,
                 PASSWORD_POLICY_DEPRECATED,
                 AUTOTYPE,
                 PASSWORD_POLICY,
                 RUN_COMMAND,
                 DOUBLE_CLICK_ACTION,
                 SHIFT_DOUBLE_CLICK_ACTION,
                 ENTRY_KEYBOARD_SHORTCUT,
                 END_OF_RECORD,
                 UNKNOWN -> {
            }
            }
            if (v1Field != null) {
                fieldId = v1Field.getId();
            }
            break;
        }
        }

        if (!versionSupported) {
            return FIELD_UNSUPPORTED;
        }
        return fieldId;
    }


    @Nullable
    private String getHdrField(PwsHeaderTypeV3 fieldId)
    {
        if (itsPwsFile == null) {
            return "";
        }

        boolean headerSupported = false;
        switch (itsPwsFile.getFileVersionMajor()) {
        case V3 -> headerSupported = true;
        case V1,
             V2 -> {
        }
        }
        if (!headerSupported) {
            return null;
        }

        if (isV3()) {
            PwsRecord rec = ((PwsFileV3)itsPwsFile).getHeaderRecord();
            switch (fieldId) {
            case VERSION: {
                return String.format(Locale.US, "%d.%02d", 3,
                                     getHdrMinorVersion(rec));
            }
            case LAST_SAVE_TIME:
            case LAST_PASSWORD_CHANGE: {
                PwsField time = doGetHeaderField(rec, fieldId);
                if (time == null) {
                    return null;
                }
                byte[] bytes = time.getBytes();
                if (bytes.length == 8) {
                    byte[] binbytes = new byte[4];
                    Util.putIntToByteArray(binbytes, hexBytesToInt(bytes, 0,
                                                                   bytes.length),
                                           0);
                    bytes = binbytes;
                }
                Date d = new Date(Util.getMillisFromByteArray(bytes, 0));
                return d.toString();
            }
            case LAST_SAVE_USER: {
                PwsField field = doGetHeaderField(rec, fieldId);
                if (field != null) {
                    return doHdrFieldToString(field);
                }

                return getHdrLastSaveWhoField(rec, true);
            }
            case LAST_SAVE_HOST: {
                PwsField field = doGetHeaderField(rec, fieldId);
                if (field != null) {
                    return doHdrFieldToString(field);
                }

                return getHdrLastSaveWhoField(rec, false);
            }
            case LAST_SAVE_WHO:
            case LAST_SAVE_WHAT:
            case NAMED_PASSWORD_POLICIES: {
                PwsField field = doGetHeaderField(rec, fieldId);
                if (field != null) {
                    return doHdrFieldToString(field);
                }
                return null;
            }
            case UUID:
            case YUBICO:
            case END_OF_RECORD: {
                return null;
            }
            }
        }
        return null;
    }

    private void setHdrField(PwsHeaderTypeV3 fieldId, Object value)
    {
        if (itsPwsFile == null) {
            return;
        }

        if (isV3()) {
            PwsRecord rec = ((PwsFileV3)itsPwsFile).getHeaderRecord();
            switch (fieldId) {
            case LAST_SAVE_TIME:
            case LAST_PASSWORD_CHANGE: {
                long timeVal = ((Date)value).getTime();
                byte[] newbytes;
                int minor = getHdrMinorVersion(rec);
                if (minor >= 2) {
                    newbytes = new byte[4];
                    Util.putMillisToByteArray(newbytes, timeVal, 0);
                } else {
                    int secs = (int)(timeVal / 1000);
                    String str = String.format("%08x", secs);
                    newbytes = str.getBytes();
                }
                rec.setField(new PwsUnknownField(fieldId, newbytes));
                break;
            }
            case LAST_SAVE_WHAT:
            case NAMED_PASSWORD_POLICIES: {
                doSetHdrFieldString(rec, fieldId,
                                    (value == null) ? null : value.toString());
                break;
            }
            case LAST_SAVE_USER: {
                int minor = getHdrMinorVersion(rec);
                if (minor >= 2) {
                    doSetHdrFieldString(rec, PwsHeaderTypeV3.LAST_SAVE_USER,
                                        value.toString());
                } else {
                    setHdrLastSaveWhoField(rec, value.toString(),
                                           getHdrLastSaveWhoField(rec, false));
                }
                break;
            }
            case LAST_SAVE_HOST: {
                int minor = getHdrMinorVersion(rec);
                if (minor >= 2) {
                    doSetHdrFieldString(rec, PwsHeaderTypeV3.LAST_SAVE_HOST,
                                        value.toString());
                } else {
                    setHdrLastSaveWhoField(rec,
                                           getHdrLastSaveWhoField(rec, true),
                                           value.toString());
                }
                break;
            }
            case VERSION:
            case UUID:
            case LAST_SAVE_WHO:
            case YUBICO:
            case END_OF_RECORD: {
                break;
            }
            }
        }
    }


    @Nullable
    private static String doHdrFieldToString(@NonNull PwsField field)
    {
        try {
            //noinspection CharsetObjectCanBeUsed
            return new String(field.getBytes(), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            return null;
        }
    }


    private static void doSetHdrFieldString(PwsRecord rec,
                                            PwsHeaderTypeV3 fieldId,
                                            String val)
    {
        try {
            PwsField field = null;
            if (val != null) {
                //noinspection CharsetObjectCanBeUsed
                field = new PwsUnknownField(fieldId, val.getBytes("UTF-8"));
            }
            setOrRemoveField(field, fieldId.getId(), rec);
        }
        catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Invalid encoding", e);
        }
    }

    /** Get a non-header record's field after translating its field
     * identifier */
    private PwsField doGetRecField(PwsRecord rec, PwsFieldTypeV3 fieldId)
    {
        return doGetField(rec, getVersionFieldId(fieldId));
    }

    /** Get a header record's field */
    private static PwsField doGetHeaderField(PwsRecord rec,
                                             @NonNull PwsHeaderTypeV3 fieldId)
    {
        return doGetField(rec, fieldId.getId());
    }

    /** Get a field from a record */
    @Nullable
    private static PwsField doGetField(PwsRecord rec, int fieldId)
    {
        return switch (fieldId) {
            case FIELD_UNSUPPORTED,
                 FIELD_NOT_PRESENT -> null;
            default -> rec.getField(fieldId);
        };
    }

    private void setField(Object val, PwsRecord rec, PwsFieldTypeV3 fieldId)
    {
        setField(val, rec, fieldId, true);
    }

    private void setField(Object val, PwsRecord rec, PwsFieldTypeV3 fieldId,
                          boolean updateModTime)
    {
        PwsField field = null;
        boolean versionSupported = false;
        switch (itsPwsFile.getFileVersionMajor()) {
        case V3: {
            versionSupported = true;
            switch (fieldId) {
            case EMAIL:
            case GROUP:
            case NOTES:
            case TITLE:
            case URL:
            case USERNAME:
            case PASSWORD_HISTORY:
            case PASSWORD_POLICY:
            case OWN_PASSWORD_SYMBOLS:
            case PASSWORD_POLICY_NAME: {
                String str = (val == null) ? null : val.toString();
                if (!TextUtils.isEmpty(str)) {
                    field = new PwsStringUnicodeField(fieldId, str);
                }
                break;
            }
            case PASSWORD: {
                String str = (val == null) ? null : val.toString();
                if (!TextUtils.isEmpty(str)) {
                    field = new PwsPasswdUnicodeField(fieldId, str, itsPwsFile);
                }
                break;
            }
            case PROTECTED_ENTRY: {
                Byte b = (Byte)val;
                if ((b != null) && (b != 0)) {
                    field = new PwsByteField(fieldId, b);
                }
                break;
            }
            case PASSWORD_LIFETIME: {
                Date d = (Date)val;
                if ((d != null) && (d.getTime() != 0)) {
                    field = new PwsTimeField(fieldId, d);
                }
                break;
            }
            case PASSWORD_EXPIRY_INTERVAL: {
                Integer ival = (Integer)val;
                if ((ival != null) && (ival != 0)) {
                    field = new PwsIntegerField(fieldId, ival);
                }
                break;
            }
            case V3_ID_STRING:
            case UUID:
            case CREATION_TIME:
            case PASSWORD_MOD_TIME:
            case LAST_ACCESS_TIME:
            case PASSWORD_POLICY_DEPRECATED:
            case LAST_MOD_TIME:
            case AUTOTYPE:
            case RUN_COMMAND:
            case DOUBLE_CLICK_ACTION:
            case SHIFT_DOUBLE_CLICK_ACTION:
            case ENTRY_KEYBOARD_SHORTCUT:
            case END_OF_RECORD:
            case UNKNOWN: {
                fieldId = null;
                break;
            }
            }
            break;
        }
        case V2: {
            versionSupported = true;
            switch (fieldId) {
            case GROUP:
            case NOTES:
            case TITLE:
            case USERNAME: {
                String str = (val == null) ? null : val.toString();
                if (!TextUtils.isEmpty(str)) {
                    field = new PwsStringField(fieldId, str);
                }
                break;
            }
            case PASSWORD: {
                String str = (val == null) ? null : val.toString();
                if (!TextUtils.isEmpty(str)) {
                    field = new PwsPasswdField(fieldId, str, itsPwsFile);
                }
                break;
            }
            case V3_ID_STRING:
            case UUID:
            case CREATION_TIME:
            case PASSWORD_MOD_TIME:
            case LAST_ACCESS_TIME:
            case PASSWORD_LIFETIME:
            case PASSWORD_POLICY_DEPRECATED:
            case LAST_MOD_TIME:
            case URL:
            case AUTOTYPE:
            case PASSWORD_HISTORY:
            case PASSWORD_POLICY:
            case PASSWORD_EXPIRY_INTERVAL:
            case RUN_COMMAND:
            case DOUBLE_CLICK_ACTION:
            case EMAIL:
            case PROTECTED_ENTRY:
            case OWN_PASSWORD_SYMBOLS:
            case SHIFT_DOUBLE_CLICK_ACTION:
            case PASSWORD_POLICY_NAME:
            case ENTRY_KEYBOARD_SHORTCUT:
            case END_OF_RECORD:
            case UNKNOWN: {
                fieldId = null;
                break;
            }
            }
            break;
        }
        case V1: {
            break;
        }
        }

        if (versionSupported && (fieldId != null)) {
            setOrRemoveField(field, fieldId.getId(), rec);
            if (updateModTime && isV3() && itsPasswdRecords.containsKey(rec)) {
                var modFieldId = (fieldId == PwsFieldTypeV3.PASSWORD) ?
                                 PwsFieldTypeV3.PASSWORD_MOD_TIME :
                                 PwsFieldTypeV3.LAST_MOD_TIME;
                rec.setField(new PwsTimeField(modFieldId, new Date()));
            }
        }
    }

    private static void setOrRemoveField(PwsField field, int fieldId,
                                         PwsRecord rec)
    {
        if (field != null) {
            rec.setField(field);
        } else {
            rec.removeField(fieldId);
        }
    }

    private void finishOpenFile()
    {
        indexRecords();
        notifyObservers(this);
        PasswdSafeUtil.dbginfo(TAG, "file loaded");
    }

    private void indexRecords()
    {
        itsRecords.clear();
        itsRecordsByUUID.clear();
        itsPasswdRecords.clear();
        if (itsPwsFile != null) {
            itsRecords.ensureCapacity(itsPwsFile.getRecordCount());
            Iterator<PwsRecord> recIter = itsPwsFile.getRecords();
            while (recIter.hasNext()) {
                PwsRecord rec = recIter.next();
                String uuid = getUUID(rec);
                if (uuid == null) {
                    // Add a UUID field for records without one.  The record
                    // will not be marked as modified unless the user manually
                    // edits it.
                    PwsUUIDField uuidField = new PwsUUIDField(
                        isV2() ? PwsFieldTypeV2.UUID : PwsFieldTypeV3.UUID,
                        new UUID());
                    boolean modified = rec.isModified();
                    rec.setField(uuidField);
                    if (!modified) {
                        rec.resetModified();
                    }
                    uuid = uuidField.toString();
                }

                itsRecords.add(rec);
                itsRecordsByUUID.put(uuid, rec);
            }
        }
        for (PwsRecord rec: itsRecords) {
            itsPasswdRecords.put(rec, new PasswdRecord(rec, this));
        }
        for (PasswdRecord passwdRec: itsPasswdRecords.values()) {
            PwsRecord ref = passwdRec.getRef();
            PasswdRecord referencedRecord = itsPasswdRecords.get(ref);
            if (referencedRecord != null) {
                referencedRecord.addRefToRecord(passwdRec.getRecord());
            }
        }

        indexPasswdPolicies();
    }

    /** Index the password policies */
    private void indexPasswdPolicies()
    {
        List<PasswdPolicy> hdrPolicies =
            PasswdPolicy.parseHdrPolicies(
                getHdrField(PwsHeaderTypeV3.NAMED_PASSWORD_POLICIES));
        itsHdrPolicies = new HeaderPasswdPolicies(itsPasswdRecords.values(),
                                                  hdrPolicies);
    }

    /**
     * Implementation of saving the file or saving as another file
     */
    private void doSave(PwsStorage.SaveHelper saveHelper,
                        PwsStorage saveAsStorage,
                        Context context)
            throws IOException, ConcurrentModificationException
    {
        try {
            if (itsPwsFile != null) {
                for (int idx = 0; idx < itsRecords.size(); ++idx) {
                    PwsRecord rec = itsRecords.get(idx);
                    if (rec.isModified()) {
                        PasswdSafeUtil.dbginfo(TAG, "Updating idx: %d", idx);
                        itsPwsFile.set(idx, rec);
                        rec.resetModified();
                    }
                }

                setSaveHdrFields(context);

                PwsStorage storage = (saveAsStorage != null) ? saveAsStorage :
                                     itsPwsFile.getStorage();
                try {
                    if (saveHelper != null) {
                        storage.setSaveHelper(saveHelper);
                    }

                    if (saveAsStorage != null) {
                        itsPwsFile.saveAs(saveAsStorage);
                    } else {
                        itsPwsFile.save();
                    }
                    notifyObservers(this);
                } finally {
                    if (saveHelper != null) {
                        storage.setSaveHelper(null);
                    }
                }
            }
        } catch (Exception e) {
            var ioe = new IOException("Error saving to " + itsUri, e);
            PasswdSafeLog.error(TAG, ioe, "Error saving %s",
                                itsUri.getIdentifier(context, false));
            throw ioe;
        }
    }

    /**
     * Implementation of removing a record
     * @return 0 if successful; error message id otherwise
     */
    private int doRemoveRecord(PwsRecord rec)
    {
        if (itsPwsFile == null) {
            return R.string.record_not_found;
        }
        PasswdRecord passwdRec = getPasswdRecord(rec);
        if (passwdRec == null) {
            return R.string.record_not_found;
        }
        if (!passwdRec.getRefsToRecord().isEmpty()) {
            return R.string.record_has_references;
        }

        String recuuid = getUUID(rec);
        if (recuuid == null) {
            return R.string.record_not_found;
        }

        for (int i = 0; i < itsRecords.size(); ++i) {
            PwsRecord r = itsRecords.get(i);
            String ruuid = getUUID(r);
            if (recuuid.equals(ruuid)) {
                boolean rc = itsPwsFile.removeRecord(i);
                if (rc) {
                    indexRecords();
                } else {
                    return R.string.record_not_found;
                }
                break;
            }
        }

        return 0;
    }

    private static int getHdrMinorVersion(PwsRecord rec)
    {
        PwsField ver = doGetHeaderField(rec, PwsHeaderTypeV3.VERSION);
        if (ver == null) {
            return -1;
        }
        byte[] bytes = ver.getBytes();
        if ((bytes == null) || (bytes.length == 0)) {
            return -1;
        }
        return bytes[0];
    }

    private static void setHdrMinorVersion(PwsRecord rec, byte minor)
    {
        PwsField ver = doGetHeaderField(rec, PwsHeaderTypeV3.VERSION);
        if (ver == null) {
            return;
        }
        byte[] bytes = ver.getBytes();
        if ((bytes == null) || (bytes.length == 0)) {
            return;
        }

        byte[] newbytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, newbytes, 0, bytes.length);
        newbytes[0] = minor;
        PwsField newVer =
                new PwsUnknownField(PwsHeaderTypeV3.VERSION, newbytes);
        rec.setField(newVer);
    }

    @Nullable
    private static String getHdrLastSaveWhoField(PwsRecord rec,
                                                 boolean isUser) {
        PwsField field = doGetHeaderField(rec, PwsHeaderTypeV3.LAST_SAVE_WHO);
        if (field == null) {
            return null;
        }

        String str = doHdrFieldToString(field);
        if (str == null) {
            return null;
        }

        if (str.length() < 4) {
            Log.e(TAG, "Invalid who length: " + str.length());
            return null;
        }
        int len = Integer.parseInt(str.substring(0, 4), 16);

        if ((len + 4) > str.length()) {
            Log.e(TAG, "Invalid user length: " + (len + 4));
            return null;
        }

        if (isUser) {
            return str.substring(4, len + 4);
        } else {
            return str.substring(len + 4);
        }
    }


    private static void setHdrLastSaveWhoField(PwsRecord rec,
                                               String user, String host)
    {
        String who = String.format("%04x%s%s", user.length(), user, host);
        doSetHdrFieldString(rec, PwsHeaderTypeV3.LAST_SAVE_WHO, who);
    }


    /** Notify observer of file changes */
    private static void notifyObservers(PasswdFileData fileData)
    {
        new NotifyTask().execute(fileData);
    }

    /**
     * Async task to notify observers of a file change
     */
    private static class NotifyTask
            extends AsyncTask<PasswdFileData, Void, PasswdFileData>
    {
        @Override
        protected PasswdFileData doInBackground(
                @NonNull PasswdFileData... params)
        {
            return params[0];
        }

        @Override
        protected void onPostExecute(PasswdFileData fileData)
        {
            for (PasswdFileDataObserver obs: itsObservers) {
                obs.passwdFileDataChanged(fileData);
            }
        }
    }
}
