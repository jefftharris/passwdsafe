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

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;

/**
 * Activity contract to open a psafe3 file document
 */
public class OpenPsafe3DocActResultContract
        extends ActivityResultContract<Uri, Intent>
{
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context ctx, @Nullable Uri lastOpenUri)
    {
        Intent intent = new Intent(
                DocumentsContractCompat.INTENT_ACTION_OPEN_DOCUMENT);

        Uri initialUri = (lastOpenUri != null) ? lastOpenUri :
                         ApiCompat.getPrimaryStorageRootUri(ctx);
        if (initialUri != null) {
            intent.putExtra(DocumentsContractCompat.EXTRA_INITIAL_URI,
                            initialUri);
        }

        intent.putExtra(DocumentsContractCompat.EXTRA_PROMPT,
                        ctx.getString(R.string.open_password_file));
        intent.putExtra(DocumentsContractCompat.EXTRA_SHOW_ADVANCED, true);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("application/*");

        return intent;
    }

    @Nullable
    @Override
    public Intent parseResult(int resultCode, @Nullable Intent intent)
    {
        if ((intent == null) || (resultCode != Activity.RESULT_OK)) {
            return null;
        }
        return intent;
    }
}
