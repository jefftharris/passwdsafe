/*
 * Copyright (©) 2013 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib.view;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.core.os.OperationCanceledException;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * A CursorLoader that handles background errors
 */
public class PasswdCursorLoader extends CursorLoader
{
    private Exception itsLoadException;

    /** Constructor */
    public PasswdCursorLoader(
            Context ctx, Uri uri, String[] projection, String selection,
            @SuppressWarnings("SameParameterValue") String[] selectionArgs,
            String sortOrder)
    {
        super(ctx.getApplicationContext(), uri, projection,
              selection, selectionArgs, sortOrder);
    }

    /** Load the cursor in the background */
    @Override
    public Cursor loadInBackground()
    {
        //noinspection ConstantConditions
        if (getUri() == null) {
            return null;
        }
        try {
            return super.loadInBackground();
        } catch (Exception e) {
            // Rethrow canceled exceptions (API 16+)
            if ((e instanceof OperationCanceledException) ||
                "OperationCanceledException".equals(
                        e.getClass().getSimpleName())) {
                throw e;
            }
            itsLoadException = e;
            return null;
        }
    }

    /** Check the result of the load and show an error if needed */
    public static boolean checkResult(Loader<Cursor> loader, Activity activity)
    {
        if (loader instanceof PasswdCursorLoader) {
            PasswdCursorLoader pwloader = (PasswdCursorLoader)loader;
            if (pwloader.itsLoadException != null) {
                Exception e = pwloader.itsLoadException;
                pwloader.itsLoadException = null;
                PasswdSafeUtil.showFatalMsg(e, activity);
                return false;
            }
        }
        return true;
    }
}
