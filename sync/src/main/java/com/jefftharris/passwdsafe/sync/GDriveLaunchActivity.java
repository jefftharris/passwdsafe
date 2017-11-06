/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;

import java.util.List;

/**
 * The GDriveLaunchActivity is used to open files from within the Google Drive
 * app
 */
public class GDriveLaunchActivity extends AppCompatActivity
{
    private static final String TAG = "GDriveLaunchActivity";

    @Override
    protected void onCreate(Bundle args)
    {
        super.onCreate(args);

        Intent intent = getIntent();
        String action = intent.getAction();
        boolean doFinish = true;
        if (action.equals("com.google.android.apps.drive.DRIVE_OPEN")) {
            String fileId = intent.getStringExtra("resourceId");
            PasswdSafeUtil.dbginfo(TAG, "Open GDrive file %s", fileId);

            Uri uri = getFileUri(fileId);
            if (uri != null) {
                PasswdSafeUtil.dbginfo(TAG, "uri %s", uri);
                Intent viewIntent = PasswdSafeUtil.createOpenIntent(uri, null);
                try {
                    startActivity(viewIntent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "Can not open file", e);
                }
            } else {
                PasswdSafeUtil.showFatalMsg(
                        getString(R.string.cant_launch_drive_file), this);
                doFinish = false;
            }
        }
        if (doFinish) {
            finish();
        }
    }

    /**
     * Get the URI for the drive file
     */
    private static Uri getFileUri(final String fileId)
    {
        try {
            return SyncDb.useDb(db -> {
                List<DbProvider> providers = SyncDb.getProviders(db);
                for (DbProvider provider: providers) {
                    if (provider.itsType != ProviderType.GDRIVE) {
                        continue;
                    }
                    DbFile file = SyncDb.getFileByRemoteId(provider.itsId,
                                                           fileId, db);
                    if (file == null) {
                        continue;
                    }

                    Uri uri = PasswdSafeContract.Providers.CONTENT_URI;
                    Uri.Builder builder = uri.buildUpon();
                    ContentUris.appendId(builder, provider.itsId);
                    builder.appendPath(PasswdSafeContract.Files.TABLE);
                    ContentUris.appendId(builder, file.itsId);
                    return builder.build();
                }
                return null;
            });
        } catch (Exception e) {
            Log.e(TAG, "Error opening Google Drive file: " + fileId, e);
            return null;
        }
    }
}
