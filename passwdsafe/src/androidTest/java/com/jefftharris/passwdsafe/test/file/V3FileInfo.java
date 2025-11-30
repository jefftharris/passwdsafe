/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.pwsafe.lib.UUID;
import org.pwsafe.lib.file.PwsFile;
import org.pwsafe.lib.file.PwsFileV3;
import org.pwsafe.lib.file.PwsHeaderTypeV3;
import org.pwsafe.lib.file.PwsRecord;
import org.pwsafe.lib.file.PwsRecordV3;
import org.pwsafe.lib.file.PwsStringUnicodeField;
import org.pwsafe.lib.file.PwsTimeField;
import org.pwsafe.lib.file.PwsUUIDField;
import org.pwsafe.lib.file.PwsUnknownField;
import org.pwsafe.lib.file.PwsVersionField;

import java.util.Date;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Testing V3 file info
 */
class V3FileInfo
{
    private final PwsRecord itsHeaderRec;
    private final int itsVersion;
    private final UUID itsUuid;
    private String itsNonDefaultPrefs;
    private String itsTreeDisplayStatus;
    private Date itsLastSaveTime;
    private final String itsLastSaveWho;
    private String itsLastSaveWhat;
    private String itsLastSaveUser;
    private String itsLastSaveHost;
    private String itsNamedPasswordPolicies;
    private String itsYubico;
    private Date itsLastPasswordChange;
    private int itsUnknownField1 = -1;
    private byte[] itsUnknownValue1 = null;
    private int itsUnknownField2 = -1;
    private byte[] itsUnknownValue2 = null;

    public V3FileInfo(PwsFile file)
    {
        itsHeaderRec = getHeaderRec(file);

        var version = itsHeaderRec.getField(PwsHeaderTypeV3.VERSION);
        assertTrue(version instanceof PwsVersionField);
        assertTrue(version.getValue() instanceof Integer);
        itsVersion = (Integer)version.getValue();
        assertEquals((3 << 24 | PwsRecordV3.DB_FMT_MINOR_3_30 << 16),
                     itsVersion);
        assertEquals(3, ((PwsVersionField)version).getMajor());
        assertEquals(PwsRecordV3.DB_FMT_MINOR_3_30,
                     ((PwsVersionField)version).getMinor());

        itsUuid = getUuid(itsHeaderRec);

        itsNonDefaultPrefs = getHeaderStr(PwsHeaderTypeV3.NON_DEFAULT_PREFS);
        itsTreeDisplayStatus =
                getHeaderStr(PwsHeaderTypeV3.TREE_DISPLAY_STATUS);
        itsLastSaveTime = getHeaderTime(PwsHeaderTypeV3.LAST_SAVE_TIME);
        itsLastSaveWho = getHeaderStr(PwsHeaderTypeV3.LAST_SAVE_WHO);
        itsLastSaveWhat = getHeaderStr(PwsHeaderTypeV3.LAST_SAVE_WHAT);
        itsLastSaveUser = getHeaderStr(PwsHeaderTypeV3.LAST_SAVE_USER);
        itsLastSaveHost = getHeaderStr(PwsHeaderTypeV3.LAST_SAVE_HOST);
        itsNamedPasswordPolicies =
                getHeaderStr(PwsHeaderTypeV3.NAMED_PASSWORD_POLICIES);
        itsYubico = getHeaderStr(PwsHeaderTypeV3.YUBICO);
        itsLastPasswordChange =
                getHeaderTime(PwsHeaderTypeV3.LAST_PASSWORD_CHANGE);
    }

    public void populateHeader()
    {
        assertNull(itsNonDefaultPrefs);
        itsNonDefaultPrefs = String.format("I 11 2 S 12 %s", itsUuid);
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.NON_DEFAULT_PREFS,
                                          itsNonDefaultPrefs));

        assertNull(itsTreeDisplayStatus);
        itsTreeDisplayStatus = "10100001101";
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.TREE_DISPLAY_STATUS,
                                          itsTreeDisplayStatus));

        assertNull(itsLastSaveTime);
        itsLastSaveTime = PwsTimeField.normalizeDate(new Date());
        itsHeaderRec.setField(new PwsTimeField(PwsHeaderTypeV3.LAST_SAVE_TIME,
                                               PwsTimeField.Format.DEFAULT,
                                               itsLastSaveTime));

        assertNull(itsLastSaveWhat);
        itsLastSaveWhat = String.format("APP - %s", itsUuid);
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.LAST_SAVE_WHAT,
                                          itsLastSaveWhat));

        assertNull(itsLastSaveUser);
        itsLastSaveUser = String.format("USER - %s", itsUuid);
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.LAST_SAVE_USER,
                                          itsLastSaveUser));

        assertNull(itsLastSaveHost);
        itsLastSaveHost = String.format("HOST - %s", itsUuid);
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.LAST_SAVE_HOST,
                                          itsLastSaveHost));

        assertNull(itsNamedPasswordPolicies);
        itsNamedPasswordPolicies =
                String.format("HEADER POLICIES - %s", itsUuid);
        itsHeaderRec.setField(new PwsStringUnicodeField(
                PwsHeaderTypeV3.NAMED_PASSWORD_POLICIES,
                itsNamedPasswordPolicies));

        assertNull(itsYubico);
        itsYubico = String.format("YUBICO - %s", itsUuid);
        itsHeaderRec.setField(
                new PwsStringUnicodeField(PwsHeaderTypeV3.YUBICO, itsYubico));

        assertNull(itsLastPasswordChange);
        itsLastPasswordChange = PwsTimeField.normalizeDate(new Date());
        itsHeaderRec.setField(
                new PwsTimeField(PwsHeaderTypeV3.LAST_PASSWORD_CHANGE,
                                 PwsTimeField.Format.DEFAULT,
                                 itsLastPasswordChange));
    }

    public void populateUnknownHeader(int field1, byte[] value1,
                                      int field2, byte[] value2)
    {
        itsUnknownField1 = field1;
        itsUnknownValue1 = value1;
        itsHeaderRec.setField(
                new PwsUnknownField(itsUnknownField1, PwsHeaderTypeV3.UNKNOWN,
                                    itsUnknownValue1));

        itsUnknownField2 = field2;
        itsUnknownValue2 = value2;
        itsHeaderRec.setField(
                new PwsUnknownField(itsUnknownField2, PwsHeaderTypeV3.UNKNOWN,
                                    itsUnknownValue2));
    }

    public void verifyFileHeader(@NonNull PwsFile file)
    {
        V3FileInfo fileInfo = new V3FileInfo(file);
        assertNotNull(itsUuid);
        assertEquals(itsUuid, fileInfo.itsUuid);
        assertEquals(itsVersion, fileInfo.itsVersion);
        assertNotNull(itsNonDefaultPrefs);
        assertEquals(itsNonDefaultPrefs, fileInfo.itsNonDefaultPrefs);
        assertNotNull(itsTreeDisplayStatus);
        assertEquals(itsTreeDisplayStatus, fileInfo.itsTreeDisplayStatus);
        assertNotNull(itsLastSaveTime);
        assertEquals(itsLastSaveTime, fileInfo.itsLastSaveTime);
        assertNull(itsLastSaveWho);
        //noinspection ConstantValue
        assertEquals(itsLastSaveWho, fileInfo.itsLastSaveWho);
        assertNotNull(itsLastSaveWhat);
        assertEquals(itsLastSaveWhat, fileInfo.itsLastSaveWhat);
        assertNotNull(itsLastSaveUser);
        assertEquals(itsLastSaveUser, fileInfo.itsLastSaveUser);
        assertNotNull(itsLastSaveHost);
        assertEquals(itsLastSaveHost, fileInfo.itsLastSaveHost);
        assertNotNull(itsNamedPasswordPolicies);
        assertEquals(itsNamedPasswordPolicies,
                     fileInfo.itsNamedPasswordPolicies);
        assertNotNull(itsYubico);
        assertEquals(itsYubico, fileInfo.itsYubico);
        assertNotNull(itsLastPasswordChange);
        assertEquals(itsLastPasswordChange, fileInfo.itsLastPasswordChange);

        boolean unknown1Verified = false;
        boolean unknown2Verified = false;
        for(var headerFieldIter = fileInfo.itsHeaderRec.getFields();
            headerFieldIter.hasNext(); ) {
            var headerFieldId = headerFieldIter.next();
            switch (PwsHeaderTypeV3.fromType(headerFieldId)) {
            case VERSION,
                 UUID,
                 NON_DEFAULT_PREFS,
                 TREE_DISPLAY_STATUS,
                 LAST_SAVE_TIME,
                 LAST_SAVE_WHO,
                 LAST_SAVE_WHAT,
                 LAST_SAVE_USER,
                 LAST_SAVE_HOST,
                 NAMED_PASSWORD_POLICIES,
                 YUBICO,
                 LAST_PASSWORD_CHANGE -> {
            }
            case END_OF_RECORD -> fail();
            case UNKNOWN -> {
                var headerField = fileInfo.itsHeaderRec.getField(headerFieldId);
                if ((itsUnknownField1 != -1) &&
                    (itsUnknownField1 == headerFieldId)) {
                    assertTrue(headerField instanceof PwsUnknownField);
                    assertEquals(itsUnknownField1, headerField.getTypeId());
                    assertNotNull(itsUnknownValue1);
                    assertTrue(headerField.getValue() instanceof byte[]);
                    assertArrayEquals(itsUnknownValue1,
                                      (byte[])headerField.getValue());
                    unknown1Verified = true;
                } else if ((itsUnknownField2 != -1) &&
                           (itsUnknownField2 == headerFieldId)) {
                    assertTrue(headerField instanceof PwsUnknownField);
                    assertEquals(itsUnknownField2, headerField.getTypeId());
                    assertNotNull(itsUnknownValue2);
                    assertTrue(headerField.getValue() instanceof byte[]);
                    assertArrayEquals(itsUnknownValue2,
                                      (byte[])headerField.getValue());
                    unknown2Verified = true;
                } else {
                    fail();
                }
            }
            }
        }

        assertEquals(itsUnknownField1 != -1, unknown1Verified);
        assertEquals(itsUnknownField2 != -1, unknown2Verified);
    }

    private static PwsRecord getHeaderRec(@NonNull PwsFile file)
    {
        assertTrue(file instanceof PwsFileV3);
        return ((PwsFileV3)file).getHeaderRecord();
    }

    private static UUID getUuid(@NonNull PwsRecord headerRec)
    {
        var uuid = headerRec.getField(PwsHeaderTypeV3.UUID);
        assertTrue(uuid instanceof PwsUUIDField);
        assertTrue(uuid.getValue() instanceof UUID);
        return (UUID)uuid.getValue();
    }

    @Nullable
    private String getHeaderStr(@NonNull PwsHeaderTypeV3 field)
    {
        var strField = itsHeaderRec.getField(field);
        if (strField != null) {
            assertTrue(strField instanceof PwsStringUnicodeField);
            assertTrue(strField.getValue() instanceof String);
            return (String)strField.getValue();
        } else {
            return null;
        }
    }

    @Nullable
    private Date getHeaderTime(@NonNull PwsHeaderTypeV3 field)
    {
        var timeField = itsHeaderRec.getField(field);
        if (timeField != null) {
            assertTrue(timeField instanceof PwsTimeField);
            assertTrue(timeField.getValue() instanceof Date);
            return (Date)timeField.getValue();
        } else {
            return null;
        }
    }
}
