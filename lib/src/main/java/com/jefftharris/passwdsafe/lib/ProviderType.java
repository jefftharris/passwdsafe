/*
 * Copyright (©) 2013-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.content.Context;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The type of provider
 */
public enum ProviderType
{
    GDRIVE,
    DROPBOX,
    BOX,
    ONEDRIVE,
    OWNCLOUD;

    /** Set the ImageView to the icon of the provider type */
    public void setIcon(@NonNull ImageView iv)
    {
        iv.setImageResource(getIconId(false));
    }

    /** Set the ImageView to the icon of the provider type */
    public void setIcon(@NonNull MenuItem item)
    {
        item.setIcon(getIconId(true));
    }

    /**
     * Get the icon resource id for the provider type
     */
    public int getIconId(boolean forMenu)
    {
        if (GenericProviderNaming.ENABLED) {
            return GenericProviderNaming.GENERIC_CLOUD_DRAWABLE;
        }

        return switch (this) {
            case GDRIVE -> R.drawable.google_drive;
            case DROPBOX -> {
                if (forMenu) {
                    yield R.drawable.dropbox_trace;
                }
                yield R.drawable.dropbox;
            }
            case BOX -> R.drawable.box;
            case ONEDRIVE -> R.drawable.onedrive;
            case OWNCLOUD -> R.drawable.owncloud;
        };
    }

    /** Set the TextView to the name of the provider type */
    public void setText(@NonNull TextView tv)
    {
        tv.setText(getName(tv.getContext()));
    }

    /** Get the name of the provider */
    @NonNull
    public String getName(Context context)
    {
        if (GenericProviderNaming.ENABLED) {
            return GenericProviderNaming.getEnabledName(this);
        }

        return switch (this) {
            case GDRIVE -> context.getString(R.string.google_drive);
            case DROPBOX -> context.getString(R.string.dropbox);
            case BOX -> context.getString(R.string.box);
            case ONEDRIVE -> context.getString(R.string.onedrive);
            case OWNCLOUD -> context.getString(R.string.owncloud);
        };
    }

    /** Convert the string name to the ProviderType */
    @Nullable
    public static ProviderType fromString(String name)
    {
        try {
            return ProviderType.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
