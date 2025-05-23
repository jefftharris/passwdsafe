/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *  The ApiCompatKitkat class contains helper compatibility methods for Kitkat
 *  and higher
 */
@SuppressLint("ObsoleteSdkInt")
@RequiresApi(Build.VERSION_CODES.KITKAT)
@SuppressWarnings({"unchecked", "CanBeFinal"})
public final class ApiCompatKitkat
{
    private static Method itsTakePersistableUriPermissionMeth;
    private static Method itsReleasePersistableUriPermissionMeth;
    private static Method itsGetPersistedUriPermissionsMeth;
    private static Method itsDeleteDocumentMeth;
    private static Method itsUriPermissionsGetUriMeth;
    private static final String TAG = "ApiCompatKitkat";

    static {
        try {
            itsTakePersistableUriPermissionMeth =
                    ContentResolver.class.getMethod(
                            "takePersistableUriPermission",
                            Uri.class,
                            int.class);


            itsReleasePersistableUriPermissionMeth =
                    ContentResolver.class.getMethod(
                            "releasePersistableUriPermission",
                            Uri.class,
                            int.class);

            itsGetPersistedUriPermissionsMeth =
                    ContentResolver.class.getMethod(
                            "getPersistedUriPermissions");

            ClassLoader loader = Objects.requireNonNull(
                    ApiCompatKitkat.class.getClassLoader());
            Class<?> docContractClass =
                    loader.loadClass("android.provider.DocumentsContract");

            itsDeleteDocumentMeth = docContractClass.getMethod(
                    "deleteDocument", ContentResolver.class, Uri.class);

            Class<?> uriPermissionsClass =
                    loader.loadClass("android.content.UriPermission");
            itsUriPermissionsGetUriMeth =
                    uriPermissionsClass.getMethod("getUri");
        } catch (Throwable e) {
            PasswdSafeLog.error(TAG, e, "static init error");
        }
    }


    /**
     * API compatible call for Context.getExternalFilesDirs
     */
    public static File[] getExternalFilesDirs(@NonNull Context ctx, String type)
    {
        return ctx.getExternalFilesDirs(type);
    }


    /** API compatible call for ContentResolver.takePersistableUriPermission */
    public static void takePersistableUriPermission(ContentResolver cr,
                                                    Uri uri,
                                                    int flags)
    {
        try {
            itsTakePersistableUriPermissionMeth.invoke(cr, uri, flags);
        } catch (Exception e) {
            PasswdSafeLog.error(TAG, e,
                                "takePersistableUriPermission error");
        }
    }


    /** API compatible call for DocumentsContract.deleteDocument */
    public static boolean documentsContractDeleteDocument(ContentResolver cr,
                                                          Uri uri)
    {
        try {
            Object rc = Objects.requireNonNull(
                    itsDeleteDocumentMeth.invoke(null, cr, uri));
            return (Boolean)rc;
        } catch (Exception e) {
            PasswdSafeLog.error(TAG, e,
                                "documentsContractDeleteDocument error");
            return false;
        }
    }


    /** API compatible call for
     * ContentResolver.releasePersistableUriPermission */
    public static void releasePersistableUriPermission(ContentResolver cr,
                                                       Uri uri,
                                                       int flags)
    {
        try {
            itsReleasePersistableUriPermissionMeth.invoke(cr, uri, flags);
        } catch (Exception e) {
            PasswdSafeLog.error(TAG, e,
                                "releasePersistableUriPermission error");
        }
    }


    /** API compatible call for ContentResolver.getPersistedUriPermissions */
    @NonNull
    public static List<Uri> getPersistedUriPermissions(ContentResolver cr)
    {
        try {
            List<Object> perms = (List<Object>) Objects.requireNonNull(
                    itsGetPersistedUriPermissionsMeth.invoke(cr));

            List<Uri> uris = new ArrayList<>(perms.size());
            for (Object perm: perms) {
                uris.add((Uri)itsUriPermissionsGetUriMeth.invoke(perm));
            }
            return uris;
        } catch (Exception e) {
            PasswdSafeLog.error(TAG, e, "getPersistedUriPermissions error");
            return Collections.emptyList();
        }
    }


    /**
     * API compatible call for
     * InputMethodManager.shouldOfferSwitchingToNextInputMethod
     */
    public static boolean shouldOfferSwitchingToNextInputMethod(
            @NonNull InputMethodManager imm,
            IBinder imeToken)
    {
        return imm.shouldOfferSwitchingToNextInputMethod(imeToken);
    }

    /**
     * API compatible call for
     * InputMethodManager.switchToNextInputMethod
     */
    public static boolean switchToNextInputMethod(
            @NonNull InputMethodManager imm,
            IBinder imeToken,
            boolean onlyCurrentIme)
    {
        return imm.switchToNextInputMethod(imeToken, onlyCurrentIme);
    }

    /**
     * Does the device have a system vibrator
     */
    public static boolean hasVibrator(Vibrator vib)
    {
        return (vib != null) && vib.hasVibrator();
    }
}
