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

import com.jefftharris.passwdsafe.LauncherFileShortcuts;
import com.jefftharris.passwdsafe.util.Optional;

/**
 * Activity contract to create a file shortcut
 */
public class CreateFileShortcutActResultContract
        extends ActivityResultContract<Void, Optional<Uri>>
{
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context ctx, Void ignored)
    {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT, null, ctx,
                                   LauncherFileShortcuts.class);
        intent.putExtra(LauncherFileShortcuts.EXTRA_IS_DEFAULT_FILE, true);
        return intent;
    }

    @Nullable
    @Override
    public Optional<Uri> parseResult(int resultCode, @Nullable Intent intent)
    {
        if ((intent == null) || (resultCode != Activity.RESULT_OK)) {
            return null;
        }
        Intent val = intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        return Optional.ofNullable((val != null) ? val.getData() : null);
    }
}
