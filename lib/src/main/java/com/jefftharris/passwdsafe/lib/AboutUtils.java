/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jefftharris.passwdsafe.lib.view.GuiUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.regex.Pattern;

/**
 * Utilities for about dialogs
 */
public class AboutUtils
{
    private static final String TAG = "AboutUtils";
    private static final String PREF_RELEASE_NOTES = "releaseNotes";
    private static final String PRIVACY_POLICY_URL =
            "https://sourceforge.net/p/passwdsafe/wiki/PrivacyPolicy/";

    private static String itsAppVersion;

    /**
     * Update the fields of the about fragment
     */
    public static String updateAboutFields(View detailsView,
                                           final String extraLicenseInfo,
                                           final Activity act)
    {
        String name;
        StringBuilder version = new StringBuilder();
        final PackageInfo pkgInfo = PasswdSafeUtil.getAppPackageInfo(act);
        if (pkgInfo != null) {
            name = act.getString(pkgInfo.applicationInfo.labelRes);
            version.append(pkgInfo.versionName);
        } else {
            name = null;
        }

        if (PasswdSafeUtil.DEBUG) {
            version.append(" (DEBUG)");
        }

        TextView tv = (TextView)detailsView.findViewById(R.id.version);
        tv.setText(version);
        tv = (TextView)detailsView.findViewById(R.id.build_id);
        tv.setText(BuildConfig.BUILD_ID);
        tv = (TextView)detailsView.findViewById(R.id.build_date);
        tv.setText(BuildConfig.BUILD_DATE);
        tv = (TextView)detailsView.findViewById(R.id.release_notes);
        //noinspection deprecation
        tv.setText(
                Html.fromHtml(tv.getText().toString().replace("\n", "<br>")));

        ToggleButton btn =
                (ToggleButton)detailsView.findViewById(R.id.toggle_license);
        final TextView licenseView =
                (TextView)detailsView.findViewById(R.id.license);
        btn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            licenseView.setText(extraLicenseInfo);
            GuiUtils.setVisible(licenseView, isChecked);
        });
        GuiUtils.setVisible(btn, !TextUtils.isEmpty(extraLicenseInfo));

        View sendToBtn = detailsView.findViewById(R.id.send_log);
        sendToBtn.setOnClickListener(
                v -> sendLog(act, (pkgInfo != null) ?
                                  pkgInfo.packageName : null));

        View privacyPolicyBtn = detailsView.findViewById(R.id.privacy_policy);
        privacyPolicyBtn.setOnClickListener(v -> {
            Intent policyIntent = new Intent(Intent.ACTION_VIEW,
                                             Uri.parse(PRIVACY_POLICY_URL));
            if (policyIntent.resolveActivity(act.getPackageManager()) !=
                null) {
                act.startActivity(Intent.createChooser(
                        policyIntent,
                        act.getString(R.string.privacy_policy)));
            }
        });
        return name;
    }

    /**
     * Get the licenses
     */
    public static String getLicenses(Context ctx, String... assets)
    {
        StringBuilder licenses = new StringBuilder();
        AssetManager assetMgr = ctx.getResources().getAssets();
        for (String asset: assets) {
            licenses.append(asset).append(":\n");
            try {
                InputStream is = null;
                try {
                    is = assetMgr.open(asset);
                    BufferedReader r =
                            new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = r.readLine()) != null) {
                        licenses.append(line).append("\n");
                    }
                } finally {
                    Utils.closeStreams(is, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Can't load asset: " + asset, e);
            }
            licenses.append("\n\n\n");
        }
        return licenses.toString();
    }

    /**
     * Check whether the app should show release notes on startup
     */
    public static boolean checkShowNotes(Context ctx)
    {
        if (itsAppVersion != null) {
            return false;
        }
        itsAppVersion = PasswdSafeUtil.getAppVersion(ctx);
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(ctx);
        String prefVersion = prefs.getString(PREF_RELEASE_NOTES, "");
        if (!itsAppVersion.equals(prefVersion)) {
            SharedPreferences.Editor prefEdit = prefs.edit();
            prefEdit.putString(PREF_RELEASE_NOTES, itsAppVersion);
            prefEdit.apply();
            return true;
        }
        return false;
    }

    /**
     * Send a log file
     */
    private static void sendLog(Activity act, String pkgName)
    {
        try {
            FileSharer sharer = new FileSharer("logcat.txt", act, pkgName);
            File logFile = sharer.getFile();
            if (!runLogcat(true, logFile)) {
                runLogcat(false, logFile);
            }

            sharer.share(act.getString(R.string.send_log_to),
                         "text/plain",
                         new String[] { "jeffharris@users.sourceforge.net" },
                         "PasswdSafe log");
        } catch (Exception e) {
            PasswdSafeUtil.dbginfo(TAG, e, "Error sharing log");
        }
    }

    /**
     * Run logcat and collect the output
     */
    private static boolean runLogcat(boolean useUid, File logFile)
            throws IOException, InterruptedException
    {
        Process proc = new ProcessBuilder("logcat", "-d", "-v",
                                          useUid ? "uid" : "threadtime", "*:D")
                .redirectErrorStream(true)
                .start();

        BufferedReader procReader = null;
        Writer fileWriter = null;
        try {
            procReader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            fileWriter = new BufferedWriter(new FileWriter(logFile));

            Pattern p = null;
            if (!useUid) {
                p = Pattern.compile(
                        "^\\S+\\s+\\S+\\s+" + android.os.Process.myPid() +
                        "\\s.*");
            }

            String line;
            while ((line = procReader.readLine()) != null) {
                if (useUid || p.matcher(line).matches()) {
                    fileWriter.append(line).append("\n");
                }
            }
        } finally {
            Utils.closeStreams(procReader, fileWriter);
        }
        return (proc.waitFor() == 0);
    }
}
