/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.app.Activity;
import android.view.Menu;
import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

/**
 * Helper class to setup generic provider names for play store artifacts
 */
public class GenericProviderNaming
{
    public static final boolean ENABLED = false;

    public static final int GENERIC_CLOUD_DRAWABLE = R.drawable.generic_cloud;

    public static final String ACCT_USER_NAME = "PasswdSafe User";

    @Contract(pure = true)
    @NonNull
    public static String getEnabledName(@NonNull ProviderType type)
    {
        switch (type) {
        case BOX: {
            return "Cloud One";
        }
        case DROPBOX: {
            return "Cloud Two";
        }
        case GDRIVE: {
            return "Cloud Three";
        }
        case ONEDRIVE: {
            return "Cloud Four";
        }
        case OWNCLOUD: {
            return "Cloud Five";
        }
        }

        return "Cloud One";
    }

    public static void setAddProviderMenuItem(Menu menu,
                                              int menuId,
                                              ProviderType type)
    {
        if (ENABLED) {
            var item = menu.findItem(menuId);
            if (item != null) {
                item.setIcon(type.getIconId(true));
                item.setTitle(getEnabledName(type));
            }
        }
    }

    public static void setSyncedFilesActivityTitle(Activity act,
                                                   ProviderType type)
    {
        if (ENABLED) {
            act.setTitle(
                    String.format("%s Synced Files", getEnabledName(type)));
        }
    }

    @NonNull
    public static String updateGdriveHelp(@NonNull String help)
    {
        if (ENABLED) {
            return help.replace("Google Drive",
                                getEnabledName(ProviderType.GDRIVE));
        }
        return help;
    }
}
