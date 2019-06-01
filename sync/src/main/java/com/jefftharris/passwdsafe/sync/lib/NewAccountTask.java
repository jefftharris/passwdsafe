/*
 * Copyright (©) 2014 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Task to complete the addition of a new account
 */
public class NewAccountTask<ProviderT extends AbstractSyncTimerProvider>
        extends AccountUpdateTask
{
    protected String itsNewAcct;
    private final ManagedRef<ProviderT> itsProvider;
    private final String itsTag;

    /** Constructor */
    public NewAccountTask(Uri currAcctUri,
                          String newAcct,
                          ProviderT provider,
                          boolean hasNotification,
                          Context ctx,
                          String tag)
    {
        super(currAcctUri,
              ctx.getString(hasNotification ?
                                R.string.adding_account_notification :
                                R.string.adding_account));
        itsNewAcct = newAcct;
        itsProvider = new ManagedRef<>(provider);
        itsTag = tag;
    }

    @Override
    protected final void doAccountUpdate(ContentResolver cr)
    {
        ProviderT provider = itsProvider.get();
        if (provider == null) {
            return;
        }

        provider.setPendingAdd(true);
        try {
            if (!doProviderUpdate(provider)) {
                cr.notifyChange(itsAccountUri, null);
                return;
            }

            // Stop syncing for the previously selected account.
            if (itsAccountUri != null) {
                cr.delete(itsAccountUri, null, null);
            }

            if (itsNewAcct != null) {
                ContentValues values = new ContentValues();
                values.put(PasswdSafeContract.Providers.COL_ACCT,
                           itsNewAcct);
                values.put(PasswdSafeContract.Providers.COL_TYPE,
                           provider.getType().name());
                cr.insert(PasswdSafeContract.Providers.CONTENT_URI, values);
            }
        } catch (Exception e) {
            Log.e(itsTag, "Error adding account: " + itsNewAcct, e);
        } finally {
            provider.setPendingAdd(false);
        }
    }

    /**
     * Implement any provider-specific updates when adding the account
     * @return false to cancel the add
     */
    protected boolean doProviderUpdate(@NonNull ProviderT provider)
            throws Exception
    {
        return true;
    }
}
