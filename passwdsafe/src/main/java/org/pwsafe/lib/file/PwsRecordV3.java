/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;

import org.pwsafe.lib.Log;
import org.pwsafe.lib.UUID;
import org.pwsafe.lib.Util;
import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.RecordLoadException;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;

/**
 * Support for new v3 Record type.
 *
 * @author Glen Smith (based on Kevin's code for V2 records)
 */
@SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
public class PwsRecordV3 extends PwsRecord
{
    @Serial
    private static final long serialVersionUID = -3160317668375599155L;

    private static final Log LOG = Log.getInstance(
            Objects.requireNonNull(PwsRecordV3.class.getPackage()).getName());

    /**
     * Minor version for PasswordSafe 3.25 with protected entry support
     */
    public static final byte DB_FMT_MINOR_3_25 = 8;

    /**
     * Minor version for PasswordSafe 3.28 with password policy support
     */
    public static final byte DB_FMT_MINOR_3_28 = 10;

    /**
     * Minor version for PasswordSafe 3.30
     */
    public static final byte DB_FMT_MINOR_3_30 = 0x0D;

    /**
     * Minor version for PasswordSafe 3.47
     */
    public static final byte DB_FMT_MINOR_3_47 = 0x0E;

    /**
     * Minor version of the max supported database format
     */
    public static final byte DB_FMT_MINOR_VERSION = DB_FMT_MINOR_3_30;

    /**
     * All the valid type codes.
     */
    private static final Object[] VALID_TYPES;

    static {
        var types = new ArrayList<Object[]>(PwsFieldTypeV3.values().length);
        for (var type : PwsFieldTypeV3.values()) {
            switch (type) {
            case V3_ID_STRING ->
                    addValidType(types, type, PwsVersionField.class);
            case UUID -> addValidType(types, type, PwsUUIDField.class);
            case GROUP,
                 TITLE,
                 USERNAME,
                 NOTES,
                 PASSWORD_POLICY_DEPRECATED,
                 URL,
                 AUTOTYPE,
                 PASSWORD_HISTORY,
                 PASSWORD_POLICY,
                 RUN_COMMAND,
                 EMAIL,
                 OWN_PASSWORD_SYMBOLS,
                 PASSWORD_POLICY_NAME ->
                    addValidType(types, type, PwsStringUnicodeField.class);
            case PASSWORD ->
                    addValidType(types, type, PwsPasswdUnicodeField.class);
            case CREATION_TIME,
                 PASSWORD_MOD_TIME,
                 LAST_ACCESS_TIME,
                 PASSWORD_LIFETIME,
                 LAST_MOD_TIME -> addValidType(types, type, PwsTimeField.class);
            case PASSWORD_EXPIRY_INTERVAL,
                 ENTRY_KEYBOARD_SHORTCUT ->
                    addValidType(types, type, PwsIntegerField.class);
            case DOUBLE_CLICK_ACTION,
                 SHIFT_DOUBLE_CLICK_ACTION ->
                    addValidType(types, type, PwsShortField.class);
            case PROTECTED_ENTRY ->
                    addValidType(types, type, PwsByteField.class);
            case END_OF_RECORD,
                 UNKNOWN -> {
            }
            }
        }

        VALID_TYPES = types.toArray(new Object[0]);
    }

    /**
     * Create a new record with all mandatory fields given their default value.
     */
    PwsRecordV3()
    {
        super(VALID_TYPES);

        setField(new PwsUUIDField(PwsFieldTypeV3.UUID, new UUID()));
        setField(new PwsStringUnicodeField(PwsFieldTypeV3.TITLE, ""));
        setField(new PwsPasswdUnicodeField(PwsFieldTypeV3.PASSWORD));
        setField(new PwsTimeField(PwsFieldTypeV3.CREATION_TIME, new Date()));
    }

    /**
     * A special version for header records
     *
     * @param ignoredIsHeader Marker for header record
     * @noinspection SameParameterValue
     */
    PwsRecordV3(boolean ignoredIsHeader)
    {
        super(VALID_TYPES, true);
        setField(new PwsVersionField(PwsHeaderTypeV3.VERSION,
                                     new byte[]{DB_FMT_MINOR_VERSION, 3}));
        setField(new PwsUUIDField(PwsHeaderTypeV3.UUID, new UUID()));
    }

    /**
     * Create a new record by reading it from <code>file</code>.
     *
     * @param file the file to read data from.
     *
     * @throws EndOfFileException If end of file is reached
     * @throws IOException If a read error occurs.
     */
    PwsRecordV3(PwsFile file)
            throws EndOfFileException, IOException, RecordLoadException
    {
        super(file, VALID_TYPES);
    }

    /**
     * A special version which reads and ignores all headers since they have
     * different ids to standard types.
     *
     * @param file the file to read data from.
     * @param ignoreFieldTypes true if all fields types should be ignored, false
     * otherwise
     *
     * @throws EndOfFileException If end of file is reached
     * @throws IOException If a read error occurs.
     */
    PwsRecordV3(PwsFile file,
                @SuppressWarnings("SameParameterValue")
                boolean ignoreFieldTypes)
            throws EndOfFileException, IOException, RecordLoadException
    {
        super(file, VALID_TYPES, ignoreFieldTypes);
    }

    /**
     * The V3 format allows and requires the ability to add formerly unknown
     * fields.
     *
     * @return true
     */
    @Override
    protected boolean allowUnknownFieldTypes()
    {
        return true;
    }

    /**
     * Compares this record to another returning a value that is less than zero
     * if this record is "less than" <code>other</code>, zero if they are
     * "equal", or greater than zero if this record is "greater than"
     * <code>other</code>.
     *
     * @param other the record to compare this record to.
     *
     * @return A value &lt; zero if this record is "less than"
     * <code>other</code> , zero if they're equal and &gt; zero if this record
     * is "greater than" <code>other</code>.
     *
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(@NonNull Object other)
    {
        return 0;
    }

    /**
     * Compares this record to another returning <code>true</code> if they're
     * equal and <code>false</code> if they're unequal.
     *
     * @param that the record this one is compared to.
     *
     * @return <code>true</code> if the records are equal, <code>false</code> if
     * they're unequal.
     *
     * @throws ClassCastException if <code>that</code> is not a
     * <code>PwsRecordV1</code>.
     */
    @Override
    public boolean equals(Object that)
    {
        if (that instanceof PwsRecordV3) {
            UUID thisUUID = (UUID)getField(PwsFieldTypeV3.UUID).getValue();
            UUID thatUUID = (UUID)((PwsRecord)that)
                    .getField(PwsFieldTypeV3.UUID)
                    .getValue();
            return thisUUID.equals(thatUUID);
        } else {
            return false;
        }
    }

    /**
     * Checks to see whether this record is one that we should display to the
     * user or not. The header record is the only one we suppress, and we
     * determine the header record by checking for the presence of the type 0
     * field which represents the file format version.
     *
     * @return <code>true</code> if it's valid or <code>false</code> if unequal.
     */
    @Override
    protected boolean isValid()
    {
        PwsField idField = getField(PwsFieldTypeV3.V3_ID_STRING);
        return idField == null;
    }

    protected boolean isHeaderRecord()
    {
        PwsField idField = getField(PwsFieldTypeV3.V3_ID_STRING);
        return idField != null;
    }

    static final byte[] EOF_BYTES_RAW = "PWS3-EOFPWS3-EOF".getBytes();

    protected static class ItemV3 extends Item
    {
        public ItemV3(@NonNull PwsFileV3 file)
                throws EndOfFileException, IOException
        {
            super();
            try {
                rawData = file.readBlock();
            } catch (EndOfFileException eofe) {
                data = new byte[32]; // to hold closing HMAC
                file.readBytes(data);
                byte[] hash = file.hasher.doFinal();
                if (!Util.bytesAreEqual(data, hash)) {
                    LOG.error("HMAC record did not match. File may have been " +
                              "tampered");
                    throw new IOException("HMAC record did not match. File " +
                                          "has been tampered");
                }
                throw eofe;
            }

            length = Util.getIntFromByteArray(rawData, 0);
            type = rawData[4] & 0x000000ff; // rest of header is now random data
            try {
                data = new byte[length];
            } catch (OutOfMemoryError e) {
                throw new IOException(
                        "Out of memory.  Record length too long: " + length);
            }
            byte[] remainingDataInRecord = Util.getBytes(rawData, 5, 11);
            if (length <= 11) {
                System.arraycopy(remainingDataInRecord, 0, data, 0, length);
            } else {
                int bytesToRead = length - 11;
                final int blockSize = file.getBlockSize();
                int blocksToRead = bytesToRead / blockSize;

                System.arraycopy(remainingDataInRecord, 0, data, 0,
                                 remainingDataInRecord.length);
                int pos = remainingDataInRecord.length;

                byte[] nextBlock = new byte[blockSize];
                int nextBlockLen = nextBlock.length;
                for (int i = 0; i < blocksToRead; i++, pos += nextBlockLen) {
                    file.readDecryptedBytes(nextBlock);
                    System.arraycopy(nextBlock, 0, data, pos, nextBlockLen);
                }

                // if blocksToRead doesn't fit neatly into current block
                // size, add an extra block for the remaining bytes
                if ((bytesToRead % blockSize) != 0) {
                    file.readDecryptedBytes(nextBlock);
                    int bytesRead = pos - remainingDataInRecord.length;
                    nextBlockLen = bytesToRead - bytesRead;
                    System.arraycopy(nextBlock, 0, data, pos, nextBlockLen);
                }
            }
            byte[] dataToHash = data;
            file.hasher.digest(dataToHash);
        }
    }

    /**
     * Initialises this record by reading its data from <code>file</code>.
     *
     * @param file the file to read the data from.
     */
    @Override
    protected void loadRecord(PwsFile file)
            throws EndOfFileException, RecordLoadException
    {
        ArrayList<Throwable> itemErrors = null;
        for (; ; ) {
            try {
                Item item = new ItemV3((PwsFileV3)file);
                int itemType = item.getType();
                if (itemType == PwsFieldTypeV3.END_OF_RECORD.getId()) {
                    break; // out of the for loop
                }

                PwsField itemVal = null;
                if (ignoreFieldTypes) {
                    // header record has no valid types...
                    itemVal = new PwsUnknownField(itemType,
                                                  item.getByteData());
                    attributes.put(item.getType(), itemVal);
                } else {
                    var type = PwsFieldTypeV3.fromType(itemType);
                    switch (type) {
                    case V3_ID_STRING:
                        itemVal = new PwsVersionField(type, item.getByteData());
                        break;

                    case UUID:
                        itemVal = new PwsUUIDField(type, item.getByteData());
                        break;

                    case GROUP:
                    case TITLE:
                    case USERNAME:
                    case NOTES:
                    case PASSWORD_POLICY:
                    case PASSWORD_HISTORY:
                    case URL:
                    case AUTOTYPE:
                    case RUN_COMMAND:
                    case EMAIL:
                    case OWN_PASSWORD_SYMBOLS:
                    case PASSWORD_POLICY_NAME:
                        itemVal = new PwsStringUnicodeField(type,
                                                            item.getByteData());
                        break;

                    case PASSWORD:
                        itemVal = new PwsPasswdUnicodeField(type,
                                                            item.getByteData(),
                                                            file);
                        item.clear();
                        break;

                    case CREATION_TIME:
                    case PASSWORD_MOD_TIME:
                    case LAST_ACCESS_TIME:
                    case LAST_MOD_TIME:
                    case PASSWORD_LIFETIME:
                        itemVal = new PwsTimeField(type, item.getByteData());
                        break;

                    case PASSWORD_EXPIRY_INTERVAL:
                    case ENTRY_KEYBOARD_SHORTCUT:
                        itemVal = new PwsIntegerField(type, item.getByteData());
                        break;

                    case DOUBLE_CLICK_ACTION:
                    case SHIFT_DOUBLE_CLICK_ACTION:
                        itemVal = new PwsShortField(type, item.getByteData());
                        break;

                    case PROTECTED_ENTRY:
                        itemVal = new PwsByteField(type, item.getByteData());
                        break;

                    case PASSWORD_POLICY_DEPRECATED:
                    case END_OF_RECORD:
                    case UNKNOWN:
                        break;
                    }
                    if (itemVal == null) {
                        itemVal = new PwsUnknownField(itemType,
                                                      item.getByteData());
                    }
                    setField(itemVal);
                }
            } catch (EndOfFileException eof) {
                if (itemErrors != null) {
                    throw new RecordLoadException(this, itemErrors);
                }
                throw eof;
            } catch (Throwable t) {
                if (itemErrors == null) {
                    itemErrors = new ArrayList<>();
                }
                itemErrors.add(t);
            }
        }

        if (itemErrors != null) {
            throw new RecordLoadException(this, itemErrors);
        }
    }

    /**
     * Saves this record to <code>file</code>.
     *
     * @param file the file that the record will be written to.
     *
     * @throws IOException if a write error occurs.
     * @see org.pwsafe.lib.file.PwsRecord#saveRecord(org.pwsafe.lib.file.PwsFile)
     */
    @Override
    protected void saveRecord(PwsFile file) throws IOException
    {
        for (Iterator<Integer> iter = getFields(); iter.hasNext(); ) {
            int type;
            PwsField value;

            type = iter.next();
            value = getField(type);

            writeField(file, value);

            PwsFileV3 fileV3 = (PwsFileV3)file;
            fileV3.hasher.digest(value.getBytes());
        }
        writeField(file, new PwsStringField(PwsFieldTypeV3.END_OF_RECORD, ""));
    }

    /**
     * Writes a single field to the file.
     *
     * @param file the file to write the field to.
     * @param field the field to be written.
     * @param type the type to write to the file instead of
     * <code>field.getType()</code>
     */
    @Override
    protected void writeField(@NonNull PwsFile file,
                              @NonNull PwsField field,
                              int type) throws IOException
    {
        byte[] lenBlock = new byte[5];
        byte[] dataBlock = field.getBytes();

        Util.putIntToByteArray(lenBlock, dataBlock.length, 0);
        lenBlock[4] = (byte)type;

        final int blockSize = 16;
        byte[] nextBlock = new byte[blockSize];

        int firstBlockLen = Math.min(dataBlock.length, 11);
        System.arraycopy(lenBlock, 0, nextBlock, 0, lenBlock.length);
        System.arraycopy(dataBlock, 0, nextBlock, lenBlock.length,
                         firstBlockLen);
        file.writeEncryptedBytes(nextBlock);

        int bytesToWrite = dataBlock.length - 11;
        if (bytesToWrite > 0) {
            int blocksToWrite = bytesToWrite / blockSize;
            int pos = 11;
            int nextBlockLen = nextBlock.length;
            for (int i = 0; i < blocksToWrite; ++i, pos += nextBlockLen) {
                System.arraycopy(dataBlock, pos, nextBlock, 0, nextBlockLen);
                file.writeEncryptedBytes(nextBlock);
            }

            if ((bytesToWrite % blockSize) != 0) {
                int bytesWritten = pos - 11;
                nextBlockLen = bytesToWrite - bytesWritten;
                System.arraycopy(dataBlock, pos, nextBlock, 0, nextBlockLen);
                Arrays.fill(nextBlock, nextBlockLen, nextBlock.length, (byte)0);
                file.writeEncryptedBytes(nextBlock);
            }
        }
    }

    /**
     * Returns a string representation of this record.
     *
     * @return A string representation of this object.
     */
    @Override
    @NonNull
    public String toString()
    {
        boolean first = true;
        final StringBuilder sb = new StringBuilder();

        sb.append("{ ");

        for (Iterator<?> iter = getFields(); iter.hasNext(); ) {
            int key;
            String value;

            key = (Integer)iter.next();
            value = getField(key).toString();

            if (!first) {
                sb.append(", ");
            }
            first = false;

            boolean showValue = true;
            if (key < VALID_TYPES.length) {
                Object[] type = (Object[])VALID_TYPES[key];
                sb.append(type[1]);
                switch (PwsFieldTypeV3.fromType((Integer)type[0])) {
                case PASSWORD,
                     NOTES -> showValue = false;
                case V3_ID_STRING,
                     UUID,
                     GROUP,
                     TITLE,
                     USERNAME,
                     CREATION_TIME,
                     PASSWORD_MOD_TIME,
                     LAST_ACCESS_TIME,
                     PASSWORD_LIFETIME,
                     PASSWORD_POLICY_DEPRECATED,
                     LAST_MOD_TIME,
                     URL,
                     AUTOTYPE,
                     PASSWORD_HISTORY,
                     PASSWORD_POLICY,
                     PASSWORD_EXPIRY_INTERVAL,
                     RUN_COMMAND,
                     DOUBLE_CLICK_ACTION,
                     EMAIL,
                     PROTECTED_ENTRY,
                     OWN_PASSWORD_SYMBOLS,
                     SHIFT_DOUBLE_CLICK_ACTION,
                     PASSWORD_POLICY_NAME,
                     ENTRY_KEYBOARD_SHORTCUT,
                     END_OF_RECORD,
                     UNKNOWN -> {
                }
                }
            } else {
                sb.append(key);
            }
            sb.append("=");
            if (showValue) {
                sb.append(value);
            }
        }
        sb.append(" }");

        return sb.toString();
    }

}
