/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

import com.jefftharris.passwdsafe.test.FileListActivityTest;
import com.jefftharris.passwdsafe.test.file.FileV3Test;

import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsFile;
import org.pwsafe.lib.file.PwsPassword;

import java.io.File;
import java.io.IOException;

/**
 * Test resource for a V3 password file
 */
public class TestV3FileRule extends ExternalResource
{
    public static final String PASSWD = "test123";

    private PwsFile itsFile;

    public File getFile()
    {
        return FileListActivityTest.FILE;
    }

    public String getFileName()
    {
        return getFile().getName();
    }

    @Override
    protected void before() throws IOException
    {
        deleteFile();
        try (Owner<PwsPassword> passwd = PwsPassword.create(PASSWD)) {
            itsFile = FileV3Test.createFile(getFile(), passwd.pass());
            itsFile.save();
        }
    }

    @Override
    protected void after()
    {
        if (itsFile != null) {
            itsFile.dispose();
        }
        deleteFile();
    }

    private void deleteFile()
    {
        if (getFile().exists()) {
            Assert.assertTrue(getFile().delete());
        }
    }
}
