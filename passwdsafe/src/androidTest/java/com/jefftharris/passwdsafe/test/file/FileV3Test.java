/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.file;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;
import org.junit.Test;
import org.pwsafe.lib.UUID;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsFieldTypeV3;
import org.pwsafe.lib.file.PwsFile;
import org.pwsafe.lib.file.PwsFileFactory;
import org.pwsafe.lib.file.PwsFileStorage;
import org.pwsafe.lib.file.PwsFileV3;
import org.pwsafe.lib.file.PwsPasswdUnicodeField;
import org.pwsafe.lib.file.PwsPassword;
import org.pwsafe.lib.file.PwsRecord;
import org.pwsafe.lib.file.PwsStringUnicodeField;
import org.pwsafe.lib.file.PwsTimeField;
import org.pwsafe.lib.file.PwsUUIDField;
import org.pwsafe.lib.file.PwsUnknownField;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Low-level tests for V3 files
 */
public class FileV3Test
{
    private abstract static class LoadSaveTester
    {
        protected V3FileInfo itsFileInfo;

        public final void parseNewFileInfo(PwsFile file)
        {
            itsFileInfo = new V3FileInfo(file, getHdrTimeFormat());
            itsFileInfo.populateHeader();
        }

        public PwsTimeField.Format getHdrTimeFormat()
        {
            return PwsTimeField.Format.DEFAULT;
        }
        public abstract void populate(PwsFile file);

        public final void verify(@NonNull PwsFile file)
        {
            assertTrue(file instanceof PwsFileV3);
            assertFalse(file.isReadOnly());

            itsFileInfo.verifyFileHeader(file);

            doVerify(file);
        }

        protected abstract void doVerify(PwsFile file);
    }

    private static class RecInfo
    {
        final PwsRecord itsRec;
        final UUID itsUuid;
        final Date itsCreation;
        final String itsTitle;
        final String itsPassword;
        int itsUnknownField1 = -1;
        byte[] itsUnknownValue1 = null;
        int itsUnknownField2 = -1;
        byte[] itsUnknownValue2 = null;

        public RecInfo(PwsRecord rec, String title, String password)
        {
            itsRec = rec;
            itsTitle = title;
            itsPassword = password;

            itsUuid = getUuid(rec);
            var creationDate = rec.getField(PwsFieldTypeV3.CREATION_TIME);
            assertNotNull(creationDate);
            itsCreation = (Date)creationDate.getValue();
        }

        public static UUID getUuid(@NonNull PwsRecord rec)
        {
            var uuid = rec.getField(PwsFieldTypeV3.UUID);
            assertTrue(uuid instanceof PwsUUIDField);
            return (UUID)uuid.getValue();
        }
    }

    @Test
    public void testEmpty() throws Exception
    {
        doTestLoadSave(new LoadSaveTester()
        {
            @Override
            public void populate(PwsFile file)
            {
            }

            @Override
            public void doVerify(PwsFile file)
            {
                verifyEmpty(file);
            }
        });
    }

    @Test
    public void testSimple() throws Exception
    {
        doTestLoadSave(new LoadSaveTester()
        {
            RecInfo itsRec = null;

            @Override
            public void populate(PwsFile file)
            {
                itsRec = createRecord(file, "rec1", "recpass1");
                file.add(itsRec.itsRec);
            }

            @Override
            public void doVerify(PwsFile file)
            {
                assertEquals(1, file.getRecordCount());
                int idx = 0;
                var recIter = file.getRecords();
                while (recIter.hasNext()) {
                    assertEquals(0, idx++);
                    var rec = recIter.next();
                    verifyFields(rec, itsRec);
                }
            }
        });
    }

    @Test
    public void testSimpleMultiple() throws Exception
    {
        doTestLoadSave(new LoadSaveTester()
        {
            final TreeMap<UUID, RecInfo> itsRecs = new TreeMap<>();

            @Override
            public void populate(PwsFile file)
            {
                assertTrue(itsRecs.isEmpty());
                for (int i = 0; i < 100; ++i) {
                    var rec = createRecord(file, String.format("rec%d", i),
                                           String.format("recpass-%d", i));
                    itsRecs.put(rec.itsUuid, rec);
                    file.add(rec.itsRec);
                }
            }

            @Override
            public void doVerify(PwsFile file)
            {
                assertEquals(100, file.getRecordCount());
                int idx = 0;
                TreeSet<UUID> verifyUuids = new TreeSet<>();
                var recIter = file.getRecords();
                while (recIter.hasNext()) {
                    assertTrue(idx++ < 100);
                    var rec = recIter.next();
                    var uuid = RecInfo.getUuid(rec);
                    var info = itsRecs.get(uuid);
                    assertNotNull(info);
                    verifyFields(rec, info);
                    verifyUuids.add(uuid);
                }

                assertEquals(itsRecs.keySet(), verifyUuids);
            }
        });
    }

    @Test
    public void testUnknown() throws Exception
    {
        doTestLoadSave(new LoadSaveTester()
        {
            RecInfo itsRec1 = null;

            @Override
            public void populate(PwsFile file)
            {
                itsRec1 = createRecord(file, "rec1", "recpass1");

                itsRec1.itsUnknownField1 = 0xf0;
                itsRec1.itsUnknownValue1 =
                        new byte[]{0x00, 0x0f, (byte)0xf0, (byte)0xff};
                itsRec1.itsRec.setField(
                        new PwsUnknownField(itsRec1.itsUnknownField1,
                                            PwsFieldTypeV3.UNKNOWN,
                                            itsRec1.itsUnknownValue1));

                itsRec1.itsUnknownField2 = 0xf1;
                itsRec1.itsUnknownValue2 =
                        new byte[]{(byte)0xff, (byte)0xf1, 0x7f, 0x55,
                                   (byte)0xaa};
                itsRec1.itsRec.setField(
                        new PwsUnknownField(itsRec1.itsUnknownField2,
                                            PwsFieldTypeV3.UNKNOWN,
                                            itsRec1.itsUnknownValue2));

                itsFileInfo.populateUnknownHeader(0xe0,
                                                  itsRec1.itsUnknownValue1,
                                                  0xe1,
                                                  itsRec1.itsUnknownValue2);
                file.add(itsRec1.itsRec);
            }

            @Override
            public void doVerify(PwsFile file)
            {
                assertEquals(1, file.getRecordCount());
                int idx = 0;
                var recIter = file.getRecords();
                while (recIter.hasNext()) {
                    assertEquals(0, idx++);
                    var rec = recIter.next();
                    verifyFields(rec, itsRec1);
                }
            }
        });
    }

    @Test
    public void testHdrAsciiTime() throws Exception
    {
        doTestLoadSave(new LoadSaveTester()
        {
            @Override
            public PwsTimeField.Format getHdrTimeFormat()
            {
                return PwsTimeField.Format.HEADER_ASCII;
            }

            @Override
            public void populate(PwsFile file)
            {
            }

            @Override
            public void doVerify(PwsFile file)
            {
                verifyEmpty(file);
            }
        });
    }

    private void doTestLoadSave(@NonNull LoadSaveTester tester)
            throws Exception
    {
        var saveFile = File.createTempFile("test", ".psafe3");
        saveFile.deleteOnExit();
        try (Owner<PwsPassword> PASSWD = PwsPassword.create("test123")) {
            {
                var file = createFile(saveFile);
                try {
                    file.setPassphrase(PASSWD.pass());
                    tester.parseNewFileInfo(file);
                    verifyEmpty(file);
                    tester.populate(file);
                    tester.verify(file);
                    file.save();
                } finally {
                    file.dispose();
                }
            }

            {
                var file = PwsFileFactory.loadFile(saveFile.getAbsolutePath(),
                                                   PASSWD.pass());
                try {
                    tester.verify(file);
                    file.save();
                } finally {
                    file.dispose();
                }
            }

            {
                var file = PwsFileFactory.loadFile(saveFile.getAbsolutePath(),
                                                   PASSWD.pass());
                try {
                    tester.verify(file);
                } finally {
                    file.dispose();
                }
            }
        } finally {
            assertTrue(saveFile.delete());
        }
    }

    private static void verifyFields(@NonNull PwsRecord rec,
                                     @NonNull RecInfo info)
    {
        boolean uuidVerified = false;
        boolean titleVerified = false;
        boolean passwordVerified = false;
        boolean creationDateVerified = false;
        boolean unknown1Verified = false;
        boolean unknown2Verified = false;

        var fieldIter = rec.getFields();
        while (fieldIter.hasNext()) {
            var fieldId = fieldIter.next();
            var field = rec.getField(fieldId);
            if (fieldId == PwsFieldTypeV3.UUID.getId()) {
                assertTrue(field instanceof PwsUUIDField);
                assertNotNull(info.itsUuid);
                assertEquals(info.itsUuid, field.getValue());
                uuidVerified = true;
            } else if (fieldId == PwsFieldTypeV3.TITLE.getId()) {
                assertTrue(field instanceof PwsStringUnicodeField);
                assertNotNull(info.itsTitle);
                assertEquals(info.itsTitle, field.getValue());
                titleVerified = true;
            } else if (fieldId == PwsFieldTypeV3.PASSWORD.getId()) {
                assertTrue(field instanceof PwsPasswdUnicodeField);
                assertNotNull(info.itsPassword);
                assertEquals(info.itsPassword, field.toString());
                passwordVerified = true;
            } else if (fieldId == PwsFieldTypeV3.CREATION_TIME.getId()) {
                assertTrue(field instanceof PwsTimeField);
                assertNotNull(info.itsCreation);
                assertEquals(info.itsCreation, field.getValue());
                creationDateVerified = true;
            } else if ((info.itsUnknownField1 != -1) &&
                       (fieldId == info.itsUnknownField1)) {
                assertTrue(field instanceof PwsUnknownField);
                assertEquals(info.itsUnknownField1, field.getTypeId());
                assertNotNull(info.itsUnknownValue1);
                assertTrue(field.getValue() instanceof byte[]);
                var fieldVal = (byte[])field.getValue();
                assertArrayEquals(info.itsUnknownValue1, fieldVal);
                unknown1Verified = true;
            } else if ((info.itsUnknownField2 != -1) &&
                       (fieldId == info.itsUnknownField2)) {
                assertTrue(field instanceof PwsUnknownField);
                assertEquals(info.itsUnknownField2, field.getTypeId());
                assertNotNull(info.itsUnknownValue2);
                assertTrue(field.getValue() instanceof byte[]);
                var fieldVal = (byte[])field.getValue();
                assertArrayEquals(info.itsUnknownValue2, fieldVal);
                unknown2Verified = true;
            } else {
                fail();
            }
        }

        assertTrue(uuidVerified);
        assertTrue(creationDateVerified);
        assertTrue(passwordVerified);
        assertTrue(titleVerified);
        assertEquals(info.itsUnknownField1 != -1, unknown1Verified);
        assertEquals(info.itsUnknownField2 != -1, unknown2Verified);
    }

    private static void verifyEmpty(@NonNull PwsFile file)
    {
        assertTrue(file instanceof PwsFileV3);
        assertFalse(file.isModified());
        assertFalse(file.isReadOnly());
        assertEquals(0, file.getRecordCount());

        var recIter = file.getRecords();
        while (recIter.hasNext()) {
            fail();
        }
    }

    @NonNull
    @Contract("_, _, _ -> new")
    private RecInfo createRecord(@NonNull PwsFile file,
                                 String title,
                                 String password)
    {
        var rec = file.newRecord();
        rec.setField(new PwsStringUnicodeField(PwsFieldTypeV3.TITLE,
                                               title));
        rec.setField(new PwsPasswdUnicodeField(PwsFieldTypeV3.PASSWORD,
                                               password, file));
        return new RecInfo(rec, title, password);
    }

    @NonNull
    private PwsFile createFile(@NonNull File saveFile) throws IOException
    {
        var file = new PwsFileV3();
        file.setStorage(
                new PwsFileStorage(saveFile.getAbsolutePath(), null));
        return file;
    }
}
