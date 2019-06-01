/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.sync.ProviderSyncFreqPref;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Async task to update an account's sync frequency
 */
public class AccountSyncFreqUpdateTask extends AccountUpdateTask
{
    private final ProviderSyncFreqPref itsFreq;

    /**
     * Constructor
     */
    public AccountSyncFreqUpdateTask(Uri providerUri,
                                     ProviderSyncFreqPref freq,
                                     Context ctx)
    {
        super(providerUri, ctx.getString(R.string.updating_account));
        itsFreq = freq;
    }

    @Override
    protected void doAccountUpdate(ContentResolver cr)
    {
        ContentValues values = new ContentValues();
        values.put(PasswdSafeContract.Providers.COL_SYNC_FREQ,
                   itsFreq.getFreq());
        cr.update(itsAccountUri, values, null, null);
    }
}
