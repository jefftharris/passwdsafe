/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

import org.pwsafe.lib.file.PwsFileStorage;
import org.pwsafe.lib.file.PwsStreamStorage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * A PwsStreamStorage implementation for sync providers
 */
public final class PasswdFileSyncStorage extends PwsStreamStorage
{
    private final Uri itsUri;

    private static final String TAG = "PasswdFileSyncStorage";

    /**
     * Constructor
     */
    public PasswdFileSyncStorage(Uri uri, String id, InputStream stream)
    {
        super(id, stream);
        itsUri = uri;
    }

    @Override
    public void save(byte[] data, boolean isV3) throws IOException
    {
        File file = null;
        try {
            PasswdFileSaveHelper helper =
                    (PasswdFileSaveHelper)getSaveHelper();
            Context ctx = helper.getContext();

            helper.createBackup(itsUri, getIdentifier());

            file = File.createTempFile("passwd", ".tmp", ctx.getCacheDir());
            PwsFileStorage.writeFile(file, data);
            Uri fileUri = PasswdClientProvider.addFile(file);

            ContentResolver cr = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(PasswdSafeContract.Files.COL_FILE, fileUri.toString());
            cr.update(itsUri, values, null, null);

            PasswdSafeUtil.dbginfo(TAG, "Update %s with %s", itsUri, file);
        } finally {
            if (file != null) {
                PasswdClientProvider.removeFile(file);
                if (!file.delete()) {
                    Log.e(TAG, "Error deleting " + file);
                }
            }
        }
    }
}
