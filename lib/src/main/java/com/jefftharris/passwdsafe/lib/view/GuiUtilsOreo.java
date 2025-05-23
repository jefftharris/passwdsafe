/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib.view;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.jefftharris.passwdsafe.lib.R;

import org.jetbrains.annotations.Contract;

/**
 * The GuiUtilsOreo class contains helper GUI methods that are usable on
 * Oreo and higher
 */

@RequiresApi(Build.VERSION_CODES.O)
public final class GuiUtilsOreo
{
    private static String NOTIF_CHANNEL_APP = null;

    /**
     * Create a notification builder
     */
    @NonNull
    @Contract("_ -> new")
    public static NotificationCompat.Builder createNotificationBuilder(
            Context ctx)
    {
        return new NotificationCompat.Builder(ctx, getAppNotifChannel(ctx));
    }

    /**
     * Get the app notification channel.  The channel is created if needed.
     */
    private static synchronized String getAppNotifChannel(Context ctx)
    {
        if (NOTIF_CHANNEL_APP == null) {
            NOTIF_CHANNEL_APP = "app";
            NotificationManager notifMgr = getNotifMgr(ctx);
            if (notifMgr.getNotificationChannel(NOTIF_CHANNEL_APP) == null) {
                for (NotificationChannel channel:
                        notifMgr.getNotificationChannels()) {
                    notifMgr.deleteNotificationChannel(channel.getId());
                }
                NotificationChannel channel = new NotificationChannel(
                        NOTIF_CHANNEL_APP, ctx.getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW);
                notifMgr.createNotificationChannel(channel);
            }
        }
        return NOTIF_CHANNEL_APP;
    }

    /**
     * Get the notification manager
     */
    private static NotificationManager getNotifMgr(@NonNull Context ctx)
    {
        return (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
