/*
 * Copyright (©) 2013 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.Provider;

/**
 *  Receiver for the sync expiration events
 */
public class SyncExpirationReceiver extends BroadcastReceiver
{
    private static final String TAG = "SyncExpirationReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent)
    {
        PasswdSafeUtil.dbginfo(TAG, "onReceive: %s", intent);
        String action = intent.getAction();
        if (Provider.ACTION_SYNC_EXPIRATION_TIMEOUT.equals(action)) {
            ProviderType type = ProviderType.fromString(intent.getStringExtra(
                    Provider.SYNC_EXPIRATION_TIMEOUT_EXTRA_TYPE));
            Provider providerImpl = ProviderFactory.getProvider(type, ctx);
            providerImpl.requestSync(false);
        }
    }
}
