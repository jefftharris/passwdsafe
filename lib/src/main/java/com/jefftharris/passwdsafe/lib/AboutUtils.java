/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
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
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.preference.PreferenceManager;

import com.jefftharris.passwdsafe.lib.view.GuiUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        if ((pkgInfo != null) && (pkgInfo.applicationInfo != null)) {
            name = act.getString(pkgInfo.applicationInfo.labelRes);
            version.append(pkgInfo.versionName);
        } else {
            name = null;
        }

        if (PasswdSafeUtil.DEBUG) {
            version.append(" (DEBUG)");
        }

        TextView tv = detailsView.findViewById(R.id.version);
        tv.setText(version);
        tv = detailsView.findViewById(R.id.build_id);
        tv.setText(BuildConfig.BUILD_ID);
        tv = detailsView.findViewById(R.id.build_date);
        tv.setText(BuildConfig.BUILD_DATE);
        tv = detailsView.findViewById(R.id.release_notes);
        tv.setText(
                Html.fromHtml(tv.getText().toString().replace("\n", "<br>")));

        ToggleButton btn = detailsView.findViewById(R.id.toggle_license);
        final TextView licenseView = detailsView.findViewById(R.id.license);
        btn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            licenseView.setText(extraLicenseInfo);
            GuiUtils.setVisible(licenseView, isChecked);
        });
        GuiUtils.setVisible(btn, !TextUtils.isEmpty(extraLicenseInfo));

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
}
