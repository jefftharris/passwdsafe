/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * Activity contract to create a new psafe3 file document
 */
public class CreatePsafe3DocActResultContract
        extends ActivityResultContract<String,
                                       CreatePsafe3DocActResultContract.Result>
{
    public record Result(
            Uri uri,
            String testDisplayName)
    {
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, String fileName)
    {
        Intent createIntent = new Intent(
                DocumentsContractCompat.INTENT_ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        createIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // Create a file with the requested MIME type and name.
        createIntent.setType(PasswdSafeUtil.MIME_TYPE_PSAFE3);
        createIntent.putExtra(Intent.EXTRA_TITLE, fileName);
        return createIntent;
    }

    @Nullable
    @Override
    public Result parseResult(int resultCode, @Nullable Intent intent)
    {
        if ((intent == null) || (resultCode != Activity.RESULT_OK)) {
            return null;
        }
        return new Result(intent.getData(),
                          intent.getStringExtra("__test_display_name"));
    }
}
