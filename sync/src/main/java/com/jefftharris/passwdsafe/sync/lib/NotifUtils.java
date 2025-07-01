/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.sync.MainActivity;
import com.jefftharris.passwdsafe.sync.R;
import com.jefftharris.passwdsafe.sync.SyncLogsActivity;

import org.jetbrains.annotations.Contract;

/**
 *  Utilities for notifications
 */
public final class NotifUtils
{
    public enum Type
    {
        //OWNCLOUD_CERT_TRUSTED(0),
        DROPBOX_MIGRATED(1),
        BOX_MIGRATGED(2),
        SYNC_PROGRESS(3),
        SYNC_RESULTS(4),
        SYNC_CONFLICT(5),
        SYNC_REPEAT_FAILURES(6),
        DRIVE_REAUTH_REQUIRED(7),
        ONEDRIVE_MIGRATED(8),
        //OWNCLOUD_USAGE(9),
        DRIVE_FILE_MIGRATION(10),
        OWNCLOUD_DISABLED(11);

        private final int itsNotifId;

        Type(int id)
        {
            itsNotifId = id;
        }
    }

    /** Show a notification */
    public static void showNotif(@NonNull Type type, Context ctx)
    {
        String content = "";
        switch (type) {
        case SYNC_PROGRESS:
        case SYNC_RESULTS:
        case SYNC_CONFLICT:
        case SYNC_REPEAT_FAILURES:
        case OWNCLOUD_DISABLED: {
            break;
        }
        case DROPBOX_MIGRATED:
        case BOX_MIGRATGED:
        case DRIVE_REAUTH_REQUIRED:
        case ONEDRIVE_MIGRATED:
        case DRIVE_FILE_MIGRATION: {
            content = ctx.getString(R.string.open_app_reauthorize);
            break;
        }
        }
        showNotif(type, content, ctx);
    }


    /** Show a notification with a custom content*/
    private static void showNotif(Type type, String content, Context ctx)
    {
        String title = getTitle(type, ctx);
        NotificationCompat.Builder builder =
                GuiUtils.createNotificationBuilder(ctx)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setTicker(title)
                        .setAutoCancel(true);
        showNotif(builder, type, null, ctx);
    }

    /**
     * Show a notification with a custom builder
     */
    public static void showNotif(@NonNull NotificationCompat.Builder builder,
                                 Type type,
                                 String tag,
                                 Context ctx)
    {
        final Intent launchIntent = createNotifLaunchIntent(type, ctx);
        PendingIntent intent = PendingIntent.getActivity(
                ctx, type.itsNotifId, launchIntent,
                (PendingIntent.FLAG_UPDATE_CURRENT |
                 PendingIntent.FLAG_IMMUTABLE));
        builder.setContentIntent(intent);

        builder.setSmallIcon(R.drawable.ic_stat_app);
        GuiUtils.showNotification(getNotifMgr(ctx), builder,
                                  R.mipmap.ic_launcher_sync,
                                  type.itsNotifId, tag, ctx);
    }

    /**
     * Cancel a notification
     */
    public static void cancelNotif(@NonNull Type type, String tag, Context ctx)
    {
        getNotifMgr(ctx).cancel(tag, type.itsNotifId);
    }

    /**
     * Cancel a notification
     */
    public static void cancelNotif(Type type, Context ctx)
    {
        cancelNotif(type, null, ctx);
    }

    /**
     * Get the title of a notification type
     */
    @NonNull
    public static String getTitle(@NonNull Type type, Context ctx)
    {
        return switch (type) {
            case DROPBOX_MIGRATED ->
                    ctx.getString(R.string.dropbox_service_updated);
            case BOX_MIGRATGED -> ctx.getString(R.string.box_service_updated);
            case SYNC_PROGRESS -> ctx.getString(R.string.syncing);
            case SYNC_RESULTS -> ctx.getString(R.string.sync_results);
            case SYNC_CONFLICT -> ctx.getString(R.string.sync_conflict);
            case SYNC_REPEAT_FAILURES ->
                    ctx.getString(R.string.repeated_sync_failures);
            case DRIVE_REAUTH_REQUIRED ->
                    ctx.getString(R.string.gdrive_reauth_required);
            case ONEDRIVE_MIGRATED ->
                    ctx.getString(R.string.onedrive_service_updated);
            case DRIVE_FILE_MIGRATION ->
                    ctx.getString(R.string.gdrive_file_auth_changed);
            case OWNCLOUD_DISABLED ->
                    ctx.getString(R.string.owncloud_sync_disabled);
        };
    }

    /**
     * Create the launch intent for a notification type
     */
    @Contract("_, _ -> new")
    @NonNull
    private static Intent createNotifLaunchIntent(@NonNull Type type,
                                                 Context ctx)
    {
        Class<? extends Activity> activityClass = null;
        switch (type) {
        case DROPBOX_MIGRATED:
        case BOX_MIGRATGED:
        case SYNC_PROGRESS:
        case DRIVE_REAUTH_REQUIRED:
        case ONEDRIVE_MIGRATED:
        case DRIVE_FILE_MIGRATION:
        case OWNCLOUD_DISABLED: {
            activityClass = MainActivity.class;
            break;
        }
        case SYNC_RESULTS:
        case SYNC_CONFLICT:
        case SYNC_REPEAT_FAILURES: {
            activityClass = SyncLogsActivity.class;
        }
        }

        return new Intent(ctx, activityClass);
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
