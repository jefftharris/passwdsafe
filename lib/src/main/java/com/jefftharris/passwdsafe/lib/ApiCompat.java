/*
 * Copyright (©) 2016, 2021, 2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The ApiCompat class provides a compatibility interface for different Android
 * versions
 */
public final class ApiCompat
{
    public static final int SDK_KITKAT = 19;
    private static final int SDK_N = 24;
    public static final int SDK_OREO = 26;
    private static final int SDK_P = 28;
    public static final int SDK_Q = 29;
    public static final int SDK_R = 30;
    public static final int SDK_TIRAMISU = 33;

    @ChecksSdkIntAtLeast
    public static final int SDK_VERSION = Build.VERSION.SDK_INT;

    private static Boolean IS_SAMSUNG_DEVICE = null;

    /**
     * Is the app running on ChromeOS
     */
    public static boolean isChromeOS(@NonNull Context ctx)
    {
        // From https://stackoverflow.com/questions/39784415/how-to-detect-programmatically-if-android-app-is-running-in-chrome-book-or-in
        var pkgmgr = ctx.getPackageManager();
        return pkgmgr.hasSystemFeature("org.chromium.arc") ||
               pkgmgr.hasSystemFeature("org.chromium.arc.device_management");
    }


    /**
     * Is the app running on a Samsung device
     */
    private static synchronized boolean isSamsungDevice()
    {
        if (IS_SAMSUNG_DEVICE == null) {
            var samsungRe = Pattern.compile(Pattern.quote("samsung"),
                                            Pattern.CASE_INSENSITIVE);
            IS_SAMSUNG_DEVICE = samsungRe.matcher(Build.BRAND).find() ||
                                samsungRe.matcher(Build.MANUFACTURER).find();
        }
        return IS_SAMSUNG_DEVICE;
    }


    /**
     * Protect the window from being shown in the recent apps and from
     * screenshots
     */
    public static void protectDisplay(Activity act,
                                      boolean showUntrustedExternal)
    {
        boolean setRecentVisible = false;
        if (supportsExternalDisplays()) {
            setRecentVisible = showUntrustedExternal &&
                               ApiCompatR.isOnExternalDisplay(act);
        }

        var w = act.getWindow();
        if (setRecentVisible) {
            w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }


    /**
     * API compatible call for Context.getExternalFilesDirs
     */
    @SuppressWarnings("SameParameterValue")
    public static File[] getExternalFilesDirs(Context ctx, String type)
    {
        if (SDK_VERSION >= SDK_KITKAT) {
            return ApiCompatKitkat.getExternalFilesDirs(ctx, type);
        } else {
            return new File[] {ctx.getExternalFilesDir(type)};
        }
    }


    /**
     * Are external displays supported
     */
    public static boolean supportsExternalDisplays()
    {
        return SDK_VERSION >= SDK_R;
    }


    /**
     * Are the external files directories supported
     */
    public static boolean supportsExternalFilesDirs()
    {
        return SDK_VERSION < SDK_Q;
    }


    /**
     * Is the write external storage permission supported
     */
    public static boolean supportsWriteExternalStoragePermission()
    {
        return SDK_VERSION < SDK_TIRAMISU;
    }


    /**
     * Is the post notifications permission supported
     */
    public static boolean supportsPostNotificationsPermission()
    {
        return SDK_VERSION >= SDK_TIRAMISU;
    }

    /**
     * Are notifications enabled
     */
    public static boolean areNotificationsEnabled(
            @NonNull NotificationManager notifyMgr)
    {
        if (SDK_VERSION >= SDK_N) {
            return ApiCompatN.areNotificationsEnabled(notifyMgr);
        } else {
            return true;
        }
    }


    /** API compatible call for ContentResolver.takePersistableUriPermission */
    public static void takePersistableUriPermission(ContentResolver cr,
                                                    Uri uri,
                                                    int flags)
    {
        if (SDK_VERSION >= SDK_KITKAT) {
            ApiCompatKitkat.takePersistableUriPermission(cr, uri, flags);
        }
    }


    /** API compatible call for
     * ContentResolver.releasePersistableUriPermission */
    public static void releasePersistableUriPermission(ContentResolver cr,
                                                       Uri uri,
                                                       int flags)
    {
        if (SDK_VERSION >= SDK_KITKAT) {
            ApiCompatKitkat.releasePersistableUriPermission(cr, uri, flags);
        }
    }


    /** API compatible call for ContentResolver.getPersistedUriPermissions */
    public static List<Uri> getPersistedUriPermissions(ContentResolver cr)
    {
        if (SDK_VERSION >= SDK_KITKAT) {
            return ApiCompatKitkat.getPersistedUriPermissions(cr);
        }
        return Collections.emptyList();
    }


    /** API compatible call for DocumentsContract.deleteDocument */
    public static boolean documentsContractDeleteDocument(ContentResolver cr,
                                                          Uri uri)
    {
        return (SDK_VERSION >= SDK_KITKAT) &&
                ApiCompatKitkat.documentsContractDeleteDocument(cr, uri);
    }

    /**
     * API compatible call to register a broadcast receiver
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void registerNotExportedBroadcastReceiver(
            @NonNull Context ctx,
            @Nullable BroadcastReceiver receiver,
            IntentFilter filter)
    {
        if (SDK_VERSION >= SDK_TIRAMISU) {
            ApiCompatTiramisu.registerNotExportedBroadcastReceiver(ctx,
                                                                   receiver,
                                                                   filter);
        } else {
            ctx.registerReceiver(receiver, filter);
        }
    }

    /**
     * API compatible call to get the root URI for the primary storage volume
     */
    public static @Nullable Uri getPrimaryStorageRootUri(@NonNull Context ctx)
    {
        if (SDK_VERSION >= SDK_Q) {
            return ApiCompatQ.getPrimaryStorageRootUri(ctx);
        }
        return null;
    }

    /**
     * Copy text to the clipboard
     */
    public static void copyToClipboard(String str,
                                       boolean sensitive,
                                       Context ctx)
    {
        setClipboardText(str, sensitive, ctx);
    }

    /**
     * Clear the clipboard
     */
    public static void clearClipboard(Context ctx)
    {
        if (isSamsungDevice()) {
            setClipboardText(" ", true, ctx);
        }
        ClipboardManager clipMgr = setClipboardText("", true, ctx);
        if ((clipMgr != null) && (SDK_VERSION >= SDK_P)) {
            ApiCompatP.clearClipboard(clipMgr);
        }
    }

    /**
     * Does the clipboard have text
     */
    public static boolean clipboardHasText(@NonNull Context ctx)
    {
        ClipboardManager clipMgr = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        return (clipMgr != null) && clipMgr.hasPrimaryClip();
    }

    /**
     * API compatible call for
     * InputMethodManager.shouldOfferSwitchingToNextInputMethod
     */
    public static boolean shouldOfferSwitchingToNextInputMethod(
            InputMethodManager imm,
            IBinder imeToken)
    {
        return (SDK_VERSION >= SDK_KITKAT) &&
               ApiCompatKitkat.shouldOfferSwitchingToNextInputMethod(imm,
                                                                     imeToken);
    }

    /**
     * API compatible call for
     * InputMethodManager.switchToNextInputMethod
     */
    @SuppressWarnings("SameParameterValue")
    public static boolean switchToNextInputMethod(InputMethodManager imm,
                                                  IBinder imeToken,
                                                  boolean onlyCurrentIme)
    {
        return (SDK_VERSION >= SDK_KITKAT) &&
               ApiCompatKitkat.switchToNextInputMethod(imm, imeToken,
                                                       onlyCurrentIme);
    }

    /**
     * Does the device have a system vibrator
     */
    public static boolean hasVibrator(@NonNull Context ctx)
    {
        Vibrator vib = (Vibrator)ctx.getSystemService(Context.VIBRATOR_SERVICE);
        return (vib != null) &&
               ((SDK_VERSION < SDK_KITKAT) || ApiCompatKitkat.hasVibrator(vib));
    }

    /**
     * Set the text in the clipboard
     * @return The clipboard manager
     */
    private static ClipboardManager setClipboardText(String str,
                                                     boolean sensitive,
                                                     @NonNull Context ctx)
    {
        ClipboardManager clipMgr = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipMgr != null) {
            ClipData clip = ClipData.newPlainText(null, str);
            if (sensitive && (SDK_VERSION >= SDK_N)) {
                ApiCompatN.setClipboardSensitive(clip);
            }
            clipMgr.setPrimaryClip(clip);
        }
        return clipMgr;
    }
}
