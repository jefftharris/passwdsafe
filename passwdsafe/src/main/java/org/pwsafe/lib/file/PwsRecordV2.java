/*
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;

import org.pwsafe.lib.UUID;
import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.RecordLoadException;
import org.pwsafe.lib.exception.UnimplementedConversionException;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Kevin
 */
@SuppressWarnings("WeakerAccess")
public class PwsRecordV2 extends PwsRecord
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * All the valid type codes.
     */
    private static final Object[] VALID_TYPES;

    static {
        var types = new ArrayList<Object[]>(PwsFieldTypeV2.values().length);
        for (var type : PwsFieldTypeV2.values()) {
            switch (type) {
            case V2_ID_STRING,
                 GROUP,
                 TITLE,
                 USERNAME,
                 NOTES,
                 PASSWORD_POLICY,
                 URL -> addValidType(types, type, PwsStringField.class);
            case UUID -> addValidType(types, type, PwsUUIDField.class);
            case PASSWORD -> addValidType(types, type, PwsPasswdField.class);
            case CREATION_TIME,
                 PASSWORD_MOD_TIME,
                 LAST_ACCESS_TIME,
                 LAST_MOD_TIME -> addValidType(types, type, PwsTimeField.class);
            case PASSWORD_LIFETIME ->
                    addValidType(types, type, PwsIntegerField.class);
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
    PwsRecordV2()
    {
        super(VALID_TYPES);

        setField(new PwsUUIDField(PwsFieldTypeV2.UUID, new UUID()));
        setField(new PwsStringField(PwsFieldTypeV2.TITLE, ""));
        setField(new PwsPasswdField(PwsFieldTypeV2.PASSWORD));
    }

    /**
     * Create a new record by reading it from <code>file</code>.
     *
     * @param file the file to read data from.
     * @throws EndOfFileException If end of file is reached
     * @throws IOException        If a read error occurs.
     */
    PwsRecordV2(PwsFile file)
            throws EndOfFileException, IOException, RecordLoadException
    {
        super(file, VALID_TYPES);
    }

    /**
     * Compares this record to another returning a value that is less than
     * zero if this record is "less than" <code>other</code>, zero if they are
     * "equal", or greater than zero if this record is "greater than"
     * <code>other</code>.
     *
     * @param other the record to compare this record to.
     * @return A value &lt; zero if this record is "less than"
     * <code>other</code>, zero if they're equal and &gt; zero if this record
     * is "greater than" <code>other</code>.
     * @throws ClassCastException If <code>other</code> is not a
     * <code>PwsRecordV1</code>.
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
     * @return <code>true</code> if the records are equal, <code>false</code> if
     * they're unequal.
     */
    @Override
    public boolean equals(Object that)
    {
        if (that instanceof PwsRecordV2) {
            UUID thisUUID = (UUID)getField(PwsFieldTypeV2.UUID).getValue();
            UUID thatUUID = (UUID)((PwsRecord)that)
                    .getField(PwsFieldTypeV2.UUID)
                    .getValue();
            return thisUUID.equals(thatUUID);
        } else {
            return false;
        }
    }

    /**
     * Validates the record, returning <code>true</code> if it's valid or
     * <code>false</code> if unequal.
     *
     * @return <code>true</code> if it's valid or <code>false</code> if unequal.
     */
    @Override
    protected boolean isValid()
    {
        return !((PwsStringField)getField(PwsFieldTypeV2.TITLE)).equals(
                PwsFileV2.ID_STRING);
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
                Item item = new Item(file);
                if (item.getType() == PwsFieldTypeV2.END_OF_RECORD.getId()) {
                    break; // out of the for loop
                }

                PwsField itemVal = null;
                boolean nullIsError = true;
                var type = PwsFieldTypeV2.fromType(item.getType());
                switch (type) {
                case UUID:
                    itemVal = new PwsUUIDField(type, item.getByteData());
                    break;

                case V2_ID_STRING:
                case GROUP:
                case TITLE:
                case USERNAME:
                case NOTES:
                case URL:
                    itemVal =
                            new PwsStringField(item.getType(), item.getData());
                    break;

                case PASSWORD:
                    itemVal = new PwsPasswdField(item.getType(), item.getData(),
                                                 file);
                    item.clear();
                    break;

                case CREATION_TIME:
                case PASSWORD_MOD_TIME:
                case LAST_ACCESS_TIME:
                case LAST_MOD_TIME:
                    itemVal = new PwsTimeField(item.getType(),
                                               item.getByteData());
                    break;

                case PASSWORD_LIFETIME:
                    itemVal = new PwsIntegerField(item.getType(),
                                                  item.getByteData());
                    break;

                case PASSWORD_POLICY:
                case END_OF_RECORD:
                case UNKNOWN:
                    nullIsError = false;
                    break;
                }
                if (itemVal != null) {
                    setField(itemVal);
                } else if (nullIsError) {
                    throw new UnimplementedConversionException();
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
     * @throws IOException if a write error occurs.
     * @see org.pwsafe.lib.file.PwsRecord#saveRecord(org.pwsafe.lib.file.PwsFile)
     */
    @Override
    protected void saveRecord(PwsFile file)
            throws IOException
    {
        for (Iterator<?> iter = getFields(); iter.hasNext(); ) {
            int type;
            PwsField value;

            type = (Integer)iter.next();
            value = getField(type);

            writeField(file, value);
        }
        writeField(file, new PwsStringField(PwsFieldTypeV2.END_OF_RECORD, ""));
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
            Integer key;
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
                switch (PwsFieldTypeV2.fromType((Integer)type[0])) {
                case PASSWORD -> showValue = false;
                case V2_ID_STRING,
                     UUID,
                     GROUP,
                     TITLE,
                     USERNAME,
                     NOTES,
                     CREATION_TIME,
                     PASSWORD_MOD_TIME,
                     LAST_ACCESS_TIME,
                     PASSWORD_LIFETIME,
                     PASSWORD_POLICY,
                     LAST_MOD_TIME,
                     URL,
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
