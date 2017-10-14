/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;

import java.io.File;
import java.util.List;

/**
 * The FileSharer class provides a method for sharing files with other apps
 */
public final class FileSharer
{
    private final Activity itsActivity;
    private final String itsPkgName;
    private final File itsFile;

    /**
     * Constructor
     */
    public FileSharer(String fileName, Activity act, String pkgName)
    {
        itsActivity = act;
        itsPkgName = pkgName;

        File shareDir = new File(itsActivity.getCacheDir(), "shared-tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        shareDir.mkdirs();
        itsFile = new File(shareDir, fileName);
    }

    /**
     * Get the file being shared
     */
    public File getFile()
    {
        return itsFile;
    }

    /**
     * Share the file
     */
    public void share(String chooserMsg,
                      String contentType,
                      String[] emailAddrs,
                      String subject)
            throws Exception
    {
        Uri fileUri = FileProvider.getUriForFile(itsActivity,
                                                 itsPkgName + ".fileprovider",
                                                 itsFile);
        Intent sendIntent = ShareCompat.IntentBuilder
                .from(itsActivity)
                .setStream(fileUri)
                .setType(contentType)
                .setEmailTo(emailAddrs)
                .setSubject(subject)
                .getIntent()
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Workaround for Android bug.
        // grantUriPermission also needed for KITKAT,
        // see https://code.google.com/p/android/issues/detail?id=76683
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            List<ResolveInfo> resInfoList =
                    itsActivity.getPackageManager().queryIntentActivities(
                            sendIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                itsActivity.grantUriPermission(
                        resolveInfo.activityInfo.packageName, fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        itsActivity.startActivity(Intent.createChooser(sendIntent, chooserMsg));
    }
}
