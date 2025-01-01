/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;

import java.util.List;

/**
 *  Abstract provider that uses a system timer to perform syncing
 */
public abstract class AbstractSyncTimerProvider extends AbstractProvider
{
    private final ProviderType itsProviderType;
    private final Context itsContext;
    private final String itsTag;
    private boolean itsIsPendingAdd = false;

    protected AbstractSyncTimerProvider(ProviderType type,
                                        Context ctx, String tag)
    {
        itsProviderType = type;
        itsContext = ctx;
        itsTag = tag;
    }

    @Override
    @CallSuper
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
    }

    @Override
    public void fini()
    {
    }

    /**
     * Get the provider type
     */
    public ProviderType getType()
    {
        return itsProviderType;
    }

    /**
     * Get whether there is a pending add
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected synchronized boolean isPendingAdd()
    {
        return itsIsPendingAdd;
    }

    /**
     * Set whether there is a pending add
     */
    public synchronized void setPendingAdd(boolean pending)
    {
        itsIsPendingAdd = pending;
    }

    /**
     * Check whether a provider can be added.  By default, only a single
     * account can be added for a provider type.
     */
    @Override
    @MainThread
    public void checkProviderAdd(SQLiteDatabase db)
            throws Exception
    {
        List<DbProvider> providers = SyncDb.getProviders(db);
        for (DbProvider provider: providers) {
            if (provider.itsType == itsProviderType) {
                throw new Exception(
                        String.format("Only one %s account allowed",
                                      itsProviderType.getName(itsContext)));
            }
        }
    }

    @Override
    public void updateSyncFreq(Account acct, final int freq)
    {
        super.updateSyncFreq(acct, freq);
        SyncHelper.runOnUiThread(() -> {
            String userId = getAccountUserId();
            PasswdSafeUtil.dbginfo(itsTag, "updateSyncFreq acct %s, freq %d",
                                   userId, freq);

            if ((userId != null) && (freq > 0)) {
                SyncWorker.schedule(itsProviderType, userId, freq, itsContext);
            } else {
                SyncWorker.schedule(itsProviderType, userId, 0, itsContext);
            }
        });
    }

    @Nullable
    @Override
    public final ProviderSync createBackgroundSync(boolean manual)
    {
        String acctUserId = getAccountUserId();
        if (acctUserId == null) {
            return null;
        }

        final Account account = getAccount(acctUserId);
        DbProvider dbprovider;
        try {
            dbprovider = SyncDb.useDb(
                    db -> SyncHelper.getDbProviderForAcct(account, db));
        } catch (Exception e) {
            dbprovider = null;
        }

        if (dbprovider == null) {
            Log.e(itsTag, "No provider for sync of " + account);
            return null;
        }

        return new ProviderSync(account, dbprovider, this, manual, itsContext);
    }

    /** Update the sync frequency for this provider */
    protected final void updateProviderSyncFreq(final String userId)
            throws Exception
    {
        SyncDb.useDb((SyncDb.DbUser<Void>)db -> {
            DbProvider provider = SyncDb.getProvider(userId, itsProviderType,
                                                     db);
            updateSyncFreq(null, (provider != null) ? provider.itsSyncFreq : 0);
            return null;
        });
    }

    /** Check whether to start a sync */
    protected synchronized final void doRequestSync(boolean manual)
    {
        SyncWorker.requestSync(itsProviderType, getAccountUserId(),
                               manual, itsContext);
    }

    /** Get the account user identifier */
    @Nullable
    protected abstract String getAccountUserId();

    /** Get the context */
    protected final Context getContext()
    {
        return itsContext;
    }
}
