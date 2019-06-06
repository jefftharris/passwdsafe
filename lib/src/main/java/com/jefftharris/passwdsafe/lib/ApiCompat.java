/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * The ApiCompat class provides a compatibility interface for different Android
 * versions
 */
public final class ApiCompat
{
    public static final int SDK_KITKAT = 19;
    public static final int SDK_LOLLIPOP = 21;
    public static final int SDK_MARSHMALLOW = 23;
    public static final int SDK_OREO = 26;

    public static final int SDK_VERSION = Build.VERSION.SDK_INT;

    /** Set whether the window is visible in the recent apps list */
    public static void setRecentAppsVisible(
            Window w, @SuppressWarnings("SameParameterValue") boolean visible)
    {
        if (visible) {
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


    /** Can the account manager get an auth token with showing a dialog */
    public static boolean canAccountMgrGetAuthTokenWithDialog()
    {
        return SDK_VERSION < SDK_KITKAT;
    }


    /** API compatible call for DocumentsContract.deleteDocument */
    public static boolean documentsContractDeleteDocument(ContentResolver cr,
                                                          Uri uri)
    {
        return (SDK_VERSION >= SDK_KITKAT) &&
                ApiCompatKitkat.documentsContractDeleteDocument(cr, uri);
    }

    /**
     * Copy text to the clipboard
     */
    public static void copyToClipboard(String str, Context ctx)
    {
        ClipboardManager clipMgr = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipMgr != null) {
            ClipData clip = ClipData.newPlainText(null, str);
            clipMgr.setPrimaryClip(clip);
        }
    }

    /**
     * Does the clipboard have text
     */
    public static boolean clipboardHasText(Context ctx)
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
    public static boolean hasVibrator(Context ctx)
    {
        Vibrator vib = (Vibrator)ctx.getSystemService(Context.VIBRATOR_SERVICE);
        return (vib != null) &&
               ((SDK_VERSION < SDK_KITKAT) || ApiCompatKitkat.hasVibrator(vib));
    }
}
