/*
 * Copyright (©) 2024-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.Log;
import org.pwsafe.lib.Util;
import org.pwsafe.lib.crypto.HmacPws;
import org.pwsafe.lib.crypto.SHA256Pws;
import org.pwsafe.lib.crypto.TwofishPws;
import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.MemoryKeyException;
import org.pwsafe.lib.exception.RecordLoadException;
import org.pwsafe.lib.exception.UnsupportedFileVersionException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SealedObject;

/**
 * Encapsulates version 3 PasswordSafe files.
 *
 * @author Glen Smith (based on Kevin Preece's v2 implementation).
 */
public final class PwsFileV3 extends PwsFile
{

    /**
     * The PasswordSafe database version number that this class supports.
     */
    public static final int VERSION = 3;

    /**
     * The string that identifies a database as V3 rather than V2 or V1
     */
    public static final byte[] ID_STRING = "PWS3".getBytes();

    private SealedObject sealedHeaderV3;

    /**
     * End of File marker. HMAC follows this tag.
     */
    private static final byte[] EOF_BYTES_RAW = "PWS3-EOFPWS3-EOF".getBytes();

    private byte[] stretchedPassword;
    public byte[] decryptedRecordKey;
    public byte[] decryptedHmacKey;

    private TwofishPws twofishCbc;
    HmacPws hasher;
    private PwsRecordV3 headerRecord;

    /**
     * Constructs and initialises a new, empty version 3 PasswordSafe
     * database in memory.
     */
    public PwsFileV3()
    {
        super();
        setHeaderV3(new PwsFileHeaderV3());
        headerRecord = new PwsRecordV3(true);
    }

    /**
     * Use of this constructor to load a PasswordSafe database is STRONGLY
     * discouraged since it's use ties the caller to a particular file version.
     * </p><p>
     * <b>N.B. </b>this constructor's visibility may be reduced in future
     * releases.
     * </p>
     *
     * @param storage the underlying storage to use to open the database.
     * @param passwd  the passphrase for the database.
     */
    public PwsFileV3(PwsStorage storage, Owner<PwsPassword>.Param passwd)
            throws EndOfFileException, IOException,
                   UnsupportedFileVersionException
    {
        super(storage, passwd, null);
    }


    /* (non-Javadoc)
     * @see org.pwsafe.lib.file.PwsFile#dispose()
     */
    @Override
    public void dispose()
    {
        super.dispose();
        if (stretchedPassword != null)
            Arrays.fill(stretchedPassword, (byte)0);
        if (decryptedHmacKey != null)
            Arrays.fill(decryptedHmacKey, (byte)0);
        if (decryptedRecordKey != null)
            Arrays.fill(decryptedRecordKey, (byte)0);
    }

    @Nullable
    private byte[] checkPassword(@NonNull Owner<PwsPassword>.Param passwdParam,
                                 String encoding,
                                 @NonNull PwsFileHeaderV3 headerV3,
                                 int iter)
    {
        try {
            try (Owner<PwsPassword> passwd = passwdParam.use()) {
                byte[] stretch =
                        Util.stretchPassphrase(passwd.get().getBytes(encoding),
                                               headerV3.getSalt(), iter);
                if (Util.bytesAreEqual(headerV3.getPassword(),
                                       SHA256Pws.digest(stretch))) {
                    return stretch;
                }
            }
        } catch (UnsupportedEncodingException e) {
            // Skip this charset
        }

        return null;
    }

    @Override
    protected void open(Owner<PwsPassword>.Param passwdParam, String encoding)
            throws EndOfFileException, IOException
    {
        setPassphrase(passwdParam);

        if (storage != null) {
            inStream = new ByteArrayInputStream(storage.load());
            lastStorageChange = storage.getModifiedDate();
        }
        PwsFileHeaderV3 theHeaderV3 = new PwsFileHeaderV3(this);

        setHeaderV3(theHeaderV3);

        int iter = theHeaderV3.getIter();
        stretchedPassword = null;

        if (encoding != null) {
            stretchedPassword = checkPassword(passwdParam, encoding,
                                              theHeaderV3, iter);
        }

        if (stretchedPassword == null) {
            for (String charset : PwsFile.getPasswordEncodings()) {
                stretchedPassword = checkPassword(passwdParam, charset,
                                                  theHeaderV3, iter);
                if (stretchedPassword != null) {
                    encoding = charset;
                    break;
                }
            }
        }

        if (stretchedPassword == null) {
            //try another method to avoid asymmetric encoding bug in V0.8 Beta1
            try (Owner<PwsPassword> passwd = passwdParam.use()) {
                stretchedPassword =
                        Util.stretchPassphrase(passwd.get().getBytes(null),
                                               theHeaderV3.getSalt(), iter);
                if (Util.bytesAreEqual(theHeaderV3.getPassword(),
                                       SHA256Pws.digest(stretchedPassword))) {
                    encoding = Charset.defaultCharset().name();
                } else {
                    throw new IOException("Invalid password");
                }
            }
        }

        setOpenPasswordEncoding(encoding);

        try {

            byte[] rka = TwofishPws.processECB(stretchedPassword, false,
                                               theHeaderV3.getB1());
            byte[] rkb = TwofishPws.processECB(stretchedPassword, false,
                                               theHeaderV3.getB2());
            decryptedRecordKey = Util.mergeBytes(rka, rkb);

            byte[] hka = TwofishPws.processECB(stretchedPassword, false,
                                               theHeaderV3.getB3());
            byte[] hkb = TwofishPws.processECB(stretchedPassword, false,
                                               theHeaderV3.getB4());
            decryptedHmacKey = Util.mergeBytes(hka, hkb);
            hasher = new HmacPws(decryptedHmacKey);

        } catch (Exception e) {
            var ioe = new IOException("Error reading encrypted fields", e);
            Log.getInstance(PwsFileV3.class.getName()).error(ioe);
            throw ioe;
        }
        twofishCbc = new TwofishPws(decryptedRecordKey, false,
                                    theHeaderV3.getIV());

        try {
            readExtraHeader();
        } catch (RecordLoadException rle) {
            throw new IOException("Error reading header record", rle);
        }
    }


    @Override
    public void saveAs(PwsStorage saveStorage) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        outStream = baos;

        try {
            PwsFileHeaderV3 theHeaderV3 = getHeaderV3();
            theHeaderV3.save(this);

            // Can only be created once the V3 header resets key info

            twofishCbc = new TwofishPws(decryptedRecordKey, true,
                                        theHeaderV3.getIV());

            writeExtraHeader(this);

            PwsRecordV3 rec;
            for (Iterator<? extends PwsRecord> iter = getRecords();
                 iter.hasNext(); ) {
                rec = (PwsRecordV3)iter.next();
                if (!rec.isHeaderRecord())
                    rec.saveRecord(this);
            }

            outStream.write(PwsRecordV3.EOF_BYTES_RAW);
            outStream.write(hasher.doFinal());

            outStream.close();

            saveStorage.save(baos.toByteArray(), true);
        } catch (IOException e) {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (Exception e2) {
                // do nothing we're going to throw the original exception
            }
            throw e;
        } finally {
            outStream = null;
        }
    }


    /**
     * Returns the major version number for the file.
     *
     * @return The major version number for the file.
     */
    @Override
    public int getFileVersionMajor()
    {
        return VERSION;
    }

    /**
     * Allocates a new, empty record unowned by any file.  The record type is
     * {@link PwsRecordV2}.
     *
     * @return A new empty record
     * @see org.pwsafe.lib.file.PwsFile#newRecord()
     */
    @NonNull
    @Contract(" -> new")
    @Override
    public PwsRecord newRecord()
    {
        return new PwsRecordV3();
    }

    public PwsRecord getHeaderRecord()
    {
        return headerRecord;
    }

    /**
     * Reads the extra header present in version 3 files.
     *
     * @throws EndOfFileException              If end of file is reached.
     * @throws IOException                     If an error occurs whilst
     * reading.
     */
    @Override
    protected void readExtraHeader()
            throws EndOfFileException, IOException, RecordLoadException
    {
        //headerRecord = (PwsRecordV3) readRecord();
        headerRecord = new PwsRecordV3(this, true);
    }

    /**
     * Writes the extra version 2 header.
     *
     * @param file the file to write the header to.
     * @throws IOException if an error occurs whilst writing the header.
     */
    @Override
    protected void writeExtraHeader(PwsFile file)
            throws IOException
    {
        headerRecord.saveRecord(this);
    }

    /**
     * Reads bytes from the file and decrypts them.  <code>buff</code> may be
     * any length provided that is a multiple of <code>BLOCK_LENGTH</code>
     * bytes in length.
     *
     * @param buff the buffer to read the bytes into.
     * @throws EndOfFileException       If end of file has been reached.
     * @throws IOException              If a read error occurs.
     * @throws IllegalArgumentException If <code>buff.length</code> is not
     * an integral multiple of <code>BLOCK_LENGTH</code>.
     */
    @Override
    public void readDecryptedBytes(byte[] buff)
            throws EndOfFileException, IOException
    {
        if ((buff.length == 0) || ((buff.length % getBlockSize()) != 0)) {
            throw new IllegalArgumentException("buff length");
        }
        readBytes(buff);
        if (Util.bytesAreEqual(buff, EOF_BYTES_RAW)) {
            throw new EndOfFileException();
        }

        byte[] decrypted;
        try {
            decrypted = twofishCbc.processCBC(buff);
        } catch (Exception e) {
            var ioe = new IOException("Error decrypting field");
            Log.getInstance(PwsFileV3.class.getName()).error(ioe);
            throw ioe;
        }
        Util.copyBytes(decrypted, buff);
    }

    /**
     * Encrypts then writes the contents of <code>buff</code> to the file.
     *
     * @param buff the data to be written.
     */
    @Override
    public void writeEncryptedBytes(@NonNull byte[] buff)
            throws IOException
    {
        if ((buff.length == 0) || ((buff.length % getBlockSize()) !=
                                   0)) {
            throw new IllegalArgumentException("buff length");
        }

        byte[] temp;
        try {
            temp = twofishCbc.processCBC(buff);
        } catch (Exception e) {
            throw new IOException("Error writing encrypted field");
        }
        writeBytes(temp);
    }

    /**
     * @see org.pwsafe.lib.file.PwsFile#getBlockSize()
     */
    @Override
    protected int getBlockSize()
    {
        return 16;
    }

    /**
     * @return the headerV3
     */
    private PwsFileHeaderV3 getHeaderV3()
    {
        try {
            return (PwsFileHeaderV3)sealedHeaderV3.getObject(getReadCipher());
        } catch (IllegalBlockSizeException | IOException |
                ClassNotFoundException | BadPaddingException e) {
            throw new MemoryKeyException(e);
        }
    }

    /**
     * @param headerV3 the headerV3 to set
     */
    private void setHeaderV3(PwsFileHeaderV3 headerV3)
    {
        try {
            sealedHeaderV3 = new SealedObject(headerV3, getWriteCipher());
        } catch (IllegalBlockSizeException | IOException e) {
            throw new MemoryKeyException(e);
        }
    }
}
