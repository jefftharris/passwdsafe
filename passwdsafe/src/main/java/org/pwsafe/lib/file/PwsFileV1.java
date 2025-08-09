/*
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * Copyright (©) 2024-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.file;

import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.UnsupportedFileVersionException;

import java.io.IOException;

/**
 * Encapsulates version 1 PasswordSafe files.
 *
 * @author Kevin Preece
 */
public class PwsFileV1 extends PwsFileV1V2
{
    /**
     * Use of this constructor to load a PasswordSafe database is STRONGLY
     * discouraged since it's use ties the caller to a particular file version.
     * </p><p>
     * <b>N.B. </b>this constructor's visibility may be reduced in future
     * releases.
     * </p>
     *
     * @param storage the password file storage
     * @param passwd   the passphrase needed to open the database.
     * @param encoding the password encoding
     */
    public PwsFileV1(PwsStorage storage,
                     Owner<PwsPassword>.Param passwd, String encoding)
            throws EndOfFileException, IOException,
                   UnsupportedFileVersionException
    {
        super(storage, passwd, encoding);
    }

    /**
     * Returns the major version number for the file.
     *
     * @return The file's major version number.
     */
    @Override
    public PwsFileVersion getFileVersionMajor()
    {
        return PwsFileVersion.V1;
    }

    /**
     * Allocates a new, empty record unowned by any file.  The record
     * type is {@link PwsRecordV1}.
     *
     * @return A new empty record
     * @see org.pwsafe.lib.file.PwsFile#newRecord()
     */
    @Override
    public PwsRecord newRecord()
    {
        return new PwsRecordV1();
    }

    @Override
    protected int getBlockSize()
    {
        // See org.pwsafe.lib.file.PwsFile#getBlockSize()
        return 8;
    }
}
