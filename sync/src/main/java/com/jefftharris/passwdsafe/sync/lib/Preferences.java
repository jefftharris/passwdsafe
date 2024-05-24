/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * The Preferences class manages preferences for the application
 */
public class Preferences
{
    private static final String PREF_NOTIF_SHOW_SYNC = "notifShowSyncPref";
    private static final boolean PREF_NOTIF_SHOW_SYNC_DEF = true;
    private static final String PREF_SHOW_HELP_GDRIVE = "showHelpGdrivePref";
    private static final boolean PREF_SHOW_HELP_GDRIVE_DEF = true;
    private static final String PREF_SHOW_GDRIVE_FILE_MIGRATION =
            "showGdriveFileMigrationPref";

    public static final String PREF_DEBUG_TAGS = "debugTagsPref";
    public static final String PREF_DEBUG_TAGS_DEF = "";

    /**
     * Get the default shared preferences
     */
    public static SharedPreferences getSharedPrefs(Context ctx)
    {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    /**
     * Get the preference to show sync notifications
     */
    public static boolean getNotifShowSyncPref(@NonNull SharedPreferences prefs)
    {
        return prefs.getBoolean(PREF_NOTIF_SHOW_SYNC, PREF_NOTIF_SHOW_SYNC_DEF);
    }

    /**
     * Get the preference to show the help for GDrive
     */
    public static boolean getShowHelpGDrivePref(
            @NonNull SharedPreferences prefs)
    {
        return prefs.getBoolean(PREF_SHOW_HELP_GDRIVE,
                                PREF_SHOW_HELP_GDRIVE_DEF);
    }

    /**
     * Set the preference to show the help for GDrive
     */
    public static void setShowHelpGDrivePref(@NonNull SharedPreferences prefs,
                                             boolean show)
    {
        prefs.edit().putBoolean(PREF_SHOW_HELP_GDRIVE, show).apply();
    }

    /**
     * Get whether to show the GDrive file migration prompt
     */
    public static boolean getShowGDriveFileMigrationPref(
            @NonNull SharedPreferences prefs)
    {
        return prefs.getBoolean(PREF_SHOW_GDRIVE_FILE_MIGRATION, true);
    }

    /**
     * Clear the show the GDrive file migration prompt
     */
    public static void clearShowGDriveFileMigrationPref(
            SharedPreferences prefs)
    {
        if (getShowGDriveFileMigrationPref(prefs)) {
            prefs.edit().putBoolean(PREF_SHOW_GDRIVE_FILE_MIGRATION, false)
                 .apply();
        }
    }

    /**
     * Get the debugging tags
     */
    public static String getDebugTagsPref(@NonNull SharedPreferences prefs)
    {
        return prefs.getString(PREF_DEBUG_TAGS, PREF_DEBUG_TAGS_DEF);
    }
}
