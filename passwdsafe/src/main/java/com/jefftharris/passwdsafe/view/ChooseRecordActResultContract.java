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

import com.jefftharris.passwdsafe.LauncherRecordShortcuts;
import com.jefftharris.passwdsafe.PasswdSafeApp;
import com.jefftharris.passwdsafe.file.PasswdRecord;
import com.jefftharris.passwdsafe.util.Optional;

/**
 * Activity contract to choose a record
 */
public class ChooseRecordActResultContract
        extends ActivityResultContract<ChooseRecordActResultContract.Args,
                                       Optional<String>>
{
    public record Args(@Nullable Uri fileUri,
                       @NonNull PasswdRecord.Type recType)
    {
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context ctx, @NonNull Args args)
    {
        Intent intent =
                new Intent(PasswdSafeApp.CHOOSE_RECORD_INTENT, args.fileUri(),
                           ctx, LauncherRecordShortcuts.class);

        // Do not allow mixed alias and shortcut references to a
        // record to work around a bug in Password Safe that does
        // not allow both
        switch (args.recType()) {
        case NORMAL -> {
        }
        case ALIAS ->
                intent.putExtra(LauncherRecordShortcuts.FILTER_NO_SHORTCUT,
                                true);
        case SHORTCUT ->
                intent.putExtra(LauncherRecordShortcuts.FILTER_NO_ALIAS, true);
        }

        return intent;
    }

    @Nullable
    @Override
    public Optional<String> parseResult(int resultCode, @Nullable Intent intent)
    {
        if ((intent == null) || (resultCode != Activity.RESULT_OK)) {
            return null;
        }
        return Optional.ofNullable(
                intent.getStringExtra(PasswdSafeApp.RESULT_DATA_UUID));
    }
}
