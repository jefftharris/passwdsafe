/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.SyncExpirationReceiver;

/**
 *  Abstract provider that uses a system timer to perform syncing
 */
public abstract class AbstractSyncTimerProvider extends AbstractProvider
{
    private final int BROADCAST_REQUEST_SYNC_DROPBOX = 0;
    private final int BROADCAST_REQUEST_SYNC_BOX = 1;
    private final int BROADCAST_REQUEST_SYNC_OWNCLOUD = 2;
    private final int BROADCAST_REQUEST_SYNC_ONEDRIVE = 3;
    private final int BROADCAST_REQUEST_SYNC_GDRIVE = 4;

    private final ProviderType itsProviderType;
    private final Context itsContext;
    private final String itsTag;
    private Handler itsHandler = null;
    private PendingIntent itsSyncTimeoutIntent = null;
    private SyncRequestTask itsSyncTask = null;
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
        itsHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void fini()
    {
        if (itsSyncTimeoutIntent != null) {
            AlarmManager alarmMgr = (AlarmManager)
                    itsContext.getSystemService(Context.ALARM_SERVICE);
            if (alarmMgr != null) {
                alarmMgr.cancel(itsSyncTimeoutIntent);
            }
        }
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

    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.Provider#updateSyncFreq(android.accounts.Account, int)
     */
    @Override
    public void updateSyncFreq(Account acct, final int freq)
    {
        super.updateSyncFreq(acct, freq);
        itsHandler.post(() -> {
            String userId = getAccountUserId();
            PasswdSafeUtil.dbginfo(itsTag, "updateSyncFreq acct %s, freq %d",
                                   userId, freq);

            if ((userId != null) && (freq > 0)) {
                if (itsSyncTimeoutIntent == null) {
                    Intent timeoutIntent =
                            new Intent(ACTION_SYNC_EXPIRATION_TIMEOUT);
                    timeoutIntent.putExtra(SYNC_EXPIRATION_TIMEOUT_EXTRA_TYPE,
                                           itsProviderType.toString());
                    timeoutIntent.setClass(itsContext.getApplicationContext(),
                                           SyncExpirationReceiver.class);

                    int requestCode;
                    switch (itsProviderType) {
                    case BOX: {
                        requestCode = BROADCAST_REQUEST_SYNC_BOX;
                        break;
                    }
                    case DROPBOX: {
                        requestCode = BROADCAST_REQUEST_SYNC_DROPBOX;
                        break;
                    }
                    case ONEDRIVE: {
                        requestCode = BROADCAST_REQUEST_SYNC_ONEDRIVE;
                        break;
                    }
                    case OWNCLOUD: {
                        requestCode = BROADCAST_REQUEST_SYNC_OWNCLOUD;
                        break;
                    }
                    case GDRIVE: {
                        requestCode = BROADCAST_REQUEST_SYNC_GDRIVE;
                        break;
                    }
                    //noinspection UnnecessaryDefault
                    default: {
                        throw new IllegalStateException("GDRIVE not valid");
                    }
                    }

                    itsSyncTimeoutIntent = PendingIntent.getBroadcast(
                            itsContext, requestCode, timeoutIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
                }

                AlarmManager alarmMgr = (AlarmManager)
                        itsContext.getSystemService(Context.ALARM_SERVICE);
                if (alarmMgr != null) {
                    long interval = freq * 1000;
                    alarmMgr.setInexactRepeating(
                            AlarmManager.RTC,
                            System.currentTimeMillis() + interval,
                            interval, itsSyncTimeoutIntent);
                }
            } else {
                if (itsSyncTimeoutIntent != null) {
                    itsSyncTimeoutIntent.cancel();
                    itsSyncTimeoutIntent = null;
                }
            }
        });
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
        if ((itsSyncTask == null) || itsSyncTask.isFinished()) {
            itsSyncTask = new SyncRequestTask(this, manual);
        }
        itsSyncTask.checkSync();
    }

    /** Get the account user identifier */
    protected abstract String getAccountUserId();

    /** Get the context */
    protected final Context getContext()
    {
        return itsContext;
    }

    /** Background sync request for a timer provider */
    private static class SyncRequestTask extends AsyncTask<Void, Void, Void>
            implements Runnable
    {
        private final ManagedRef<AbstractSyncTimerProvider> itsProviderRef;
        private final boolean itsIsManual;
        private boolean itsIsTimerPending = false;
        private boolean itsIsRunning = true;

        /** Constructor */
        protected SyncRequestTask(AbstractSyncTimerProvider provider,
                                  boolean manual)
        {
            itsProviderRef = new ManagedRef<>(provider);
            itsIsManual = manual;
        }

        /** Check the status of the sync */
        protected synchronized void checkSync()
        {
            AbstractSyncTimerProvider provider = itsProviderRef.get();
            if (provider == null) {
                return;
            }

            Status status = getStatus();
            PasswdSafeUtil.dbginfo(
                    provider.itsTag,
                    "SyncRequestTask checkSync status %s timer %b",
                    status, itsIsTimerPending);
            long delay = 0;
            switch (status) {
            case PENDING: {
                delay = 3000;
                break;
            }
            case RUNNING:
            case FINISHED: {
                delay = 15000;
                break;
            }
            }
            if (!itsIsTimerPending) {
                itsIsTimerPending = true;
                provider.itsHandler.postDelayed(this, delay);
            }
        }

        /** Get whether the task is finished */
        protected synchronized boolean isFinished()
        {
            return !itsIsTimerPending && !itsIsRunning;
        }

        /** Timer expired */
        @Override
        public synchronized void run()
        {
            AbstractSyncTimerProvider provider = itsProviderRef.get();
            if (provider == null) {
                return;
            }

            Status status = getStatus();
            PasswdSafeUtil.dbginfo(provider.itsTag,
                                   "SyncRequestTask timer expired status %s",
                                   status);
            itsIsTimerPending = false;
            switch (status) {
            case PENDING: {
                execute();
                break;
            }
            case RUNNING:
            case FINISHED: {
                provider.doRequestSync(itsIsManual);
                break;
            }
            }
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(Void... params)
        {
            AbstractSyncTimerProvider provider = itsProviderRef.get();
            if (provider == null) {
                return null;
            }

            String acctUserId = provider.getAccountUserId();
            if (acctUserId == null) {
                return null;
            }

            final Account account = provider.getAccount(acctUserId);
            DbProvider dbprovider;
            try {
                dbprovider = SyncDb.useDb(
                        db -> SyncHelper.getDbProviderForAcct(account, db));
            } catch (Exception e) {
                dbprovider = null;
            }

            if (dbprovider == null) {
                Log.e(provider.itsTag, "No provider for sync of " + account);
                return null;
            }

            new ProviderSync(account, dbprovider, provider, provider.itsContext)
                    .sync(itsIsManual);
            return null;
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected synchronized void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            itsIsRunning = false;
        }
    }
}
