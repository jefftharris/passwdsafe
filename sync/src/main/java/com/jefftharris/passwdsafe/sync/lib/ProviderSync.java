/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.BuildConfig;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.sync.SyncApp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulation of a sync operation for a provider
 */
public class ProviderSync
{
    private static final HashSet<String> itsLastProviderFailures =
            new HashSet<>();
    private static final Handler itsUIHandler =
            new Handler(Looper.getMainLooper());
    private static final Object itsLock = new Object();
    private static final int SYNC_TIMEOUT_MINS = 1;

    private static final String TAG = "ProviderSync";

    private final Account itsAccount;
    private final DbProvider itsProvider;
    private final Provider itsProviderImpl;
    private final Context itsContext;
    private final String itsNotifTag;
    private final boolean itsIsShowNotifs;
    private PowerManager.WakeLock itsWakeLock;

    /**
     * Constructor
     */
    public ProviderSync(Account acct,
                        DbProvider provider,
                        Provider providerImpl,
                        Context ctx)
    {
        itsAccount = acct;
        itsProvider = provider;
        itsProviderImpl = providerImpl;
        itsContext = ctx;
        itsNotifTag = Long.toString(itsProvider.itsId);

        SharedPreferences prefs = Preferences.getSharedPrefs(itsContext);
        itsIsShowNotifs = Preferences.getNotifShowSyncPref(prefs);
    }

    /**
     * Perform a sync
     */
    public void sync(boolean manual)
    {
        synchronized (itsLock) {
            BackgroundSync sync = new BackgroundSync(manual);

            PowerManager powerMgr = (PowerManager)
                    itsContext.getSystemService(Context.POWER_SERVICE);
            if (powerMgr == null) {
                Log.e(TAG, "Null power manager");
                return;
            }

            itsWakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                               "sync");
            itsWakeLock.acquire(TimeUnit.MINUTES.toMillis(SYNC_TIMEOUT_MINS));
            try {
                FutureTask<Void> task = new FutureTask<>(sync, null);
                try {
                    Thread t = new Thread(task);
                    t.start();
                    task.get(SYNC_TIMEOUT_MINS, TimeUnit.MINUTES);
                } catch (Exception e) {
                    sync.setTaskException(e);
                    task.cancel(true);
                }
            } finally {
                itsWakeLock.release();
            }
        }
    }

    /**
     * Update the UI at the beginning of a sync
     */
    private void updateUIBegin()
    {
        itsUIHandler.post(() -> {
            if (itsIsShowNotifs) {
                showProgressNotif();
            }
        });
    }

    /**
     * Update the UI at the end of a sync
     */
    private void updateUIEnd(final SyncLogRecord logrec)
    {
        itsUIHandler.post(() -> {
            if (itsIsShowNotifs) {
                NotifUtils.cancelNotif(NotifUtils.Type.SYNC_PROGRESS,
                                       itsNotifTag, itsContext);
            }
            showResultNotifs(logrec);
        });
    }

    /**
     * Show the sync progress notification
     */
    private void showProgressNotif()
    {
        String title = NotifUtils.getTitle(NotifUtils.Type.SYNC_PROGRESS,
                                           itsContext);
        String content = itsProvider.getTypeAndDisplayName(itsContext);

        NotificationCompat.Builder builder =
                GuiUtils.createNotificationBuilder(itsContext)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setTicker(title)
                        .setAutoCancel(true)
                        .setProgress(100, 0, true)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        NotifUtils.showNotif(builder, NotifUtils.Type.SYNC_PROGRESS,
                             itsNotifTag, itsContext);
    }

    /**
     * Show any sync result notifications
     */
    private void showResultNotifs(SyncLogRecord logrec)
    {
        if (logrec.isSuccess()) {
            if (itsIsShowNotifs) {
                List<String> results = new ArrayList<>(logrec.getEntries());
                if (!results.isEmpty()) {
                    showResultNotif(NotifUtils.Type.SYNC_RESULTS,
                                    true, results);
                }
            }

            synchronized (itsLastProviderFailures) {
                if (itsLastProviderFailures.contains(itsNotifTag)) {
                    NotifUtils.cancelNotif(NotifUtils.Type.SYNC_REPEAT_FAILURES,
                                           itsNotifTag, itsContext);
                    itsLastProviderFailures.remove(itsNotifTag);
                }
            }
        } else if (itsProviderImpl.getSyncResults().isRepeatedFailure()) {
            showResultNotif(NotifUtils.Type.SYNC_REPEAT_FAILURES, false, null);

            synchronized (itsLastProviderFailures) {
                itsLastProviderFailures.add(itsNotifTag);
            }
        }

        List<String> conflicts = logrec.getConflictFiles();
        if (!conflicts.isEmpty()) {
            showResultNotif(NotifUtils.Type.SYNC_CONFLICT, false, conflicts);
        }
    }

    /**
     * Show a sync result notification
     */
    private void showResultNotif(NotifUtils.Type type,
                                 boolean success,
                                 List<String> results)
    {
        String title = NotifUtils.getTitle(type, itsContext);
        String content = itsProvider.getTypeAndDisplayName(itsContext);

        NotificationCompat.Builder builder =
                GuiUtils.createNotificationBuilder(itsContext)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setTicker(title)
                        .setAutoCancel(true);
        if (success) {
            builder.setCategory(NotificationCompat.CATEGORY_STATUS);
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
            builder.setCategory(NotificationCompat.CATEGORY_ERROR);
        }

        if (results != null) {
            GuiUtils.setInboxStyle(builder, title, content, results);
        }
        NotifUtils.showNotif(builder, type, itsNotifTag, itsContext);
    }

    /**
     * Runnable for doing a sync in a background thread
     */
    @WorkerThread
    private class BackgroundSync implements Runnable
    {
        private final SyncLogRecord itsLogrec;
        private final ArrayList<Pair<String, Long>> itsTraces =
                new ArrayList<>();
        private boolean itsSaveTraces = false;
        private boolean itsIsCanceled = false;
        private final SimpleDateFormat itsDateFmt =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);

        /**
         * Constructor
         */
        public BackgroundSync(boolean manual)
        {
            addTrace("BackgroundSync");

            PasswdSafeUtil.dbginfo(TAG, "Performing sync %s (%s), manual %b",
                                   itsAccount.name, itsAccount.type, manual);
            String displayName =
                    TextUtils.isEmpty(itsProvider.itsDisplayName) ?
                    itsProvider.itsAcct : itsProvider.itsDisplayName;

            itsLogrec = new SyncLogRecord(
                    displayName,
                    ((itsProvider.itsType != null) ?
                     itsProvider.itsType.getName(itsContext) : null),
                    manual);

            updateUIBegin();
        }

        @Override
        public void run()
        {
            itsWakeLock.acquire(TimeUnit.MINUTES.toMillis(SYNC_TIMEOUT_MINS));
            try {
                sync();
            } finally {
                addTrace("sync done");
                itsWakeLock.release();
            }
            addTrace("run done");
        }

        /**
         * Set a failure in running the task for the background sync
         */
        public void setTaskException(Exception e)
        {
            addTrace("task exception");
            itsIsCanceled = true;
            itsLogrec.addFailure(e);
        }

        /**
         * Perform a sync
         */
        private void sync()
        {
            addTrace("sync");
            try {
                SyncConnectivityResult connResult = checkConnectivity();
                performSync(connResult);
            } finally {
                finish();
            }
        }

        /**
         * Check the connectivity of a provider before syncing
         */
        private SyncConnectivityResult checkConnectivity()
        {
            addTrace("checkConnectivity");
            SyncConnectivityResult connResult = null;
            ConnectivityManager connMgr = (ConnectivityManager)
                    itsContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean online;
            if (connMgr != null) {
                NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
                addTrace("got network info");
                online = (netInfo != null) && netInfo.isConnected();
                addTrace("got connected");
            } else {
                online = false;
                itsLogrec.addFailure(new NullPointerException("connMgr"));
            }
            if (online) {
                try {
                    connResult =
                            itsProviderImpl.checkSyncConnectivity(itsAccount);
                    itsLogrec.checkSyncInterrupted();
                    online = (connResult != null);
                } catch (Exception e) {
                    Log.e(TAG, "checkSyncConnectivity error", e);
                    online = false;
                    itsLogrec.addFailure(e);
                }
            }
            addTrace("got connectivity");
            itsLogrec.setNotConnected(!online);
            return connResult;
        }

        /**
         * Perform the sync of a provider
         */
        private void performSync(SyncConnectivityResult connResult)
        {
            addTrace("performSync");
            try {
                if (!itsIsCanceled && !itsLogrec.isNotConnected()) {
                    itsLogrec.checkSyncInterrupted();
                    itsProviderImpl.sync(itsAccount, itsProvider,
                                         connResult, itsLogrec);
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync error", e);
                itsLogrec.addFailure(e);
            }
            addTrace("performSync finished");
        }

        /**
         * Finish the sync of a provider
         */
        private void finish()
        {
            addTrace("finish");
            PasswdSafeUtil.dbginfo(
                    TAG, "Sync finished for %s, online %b, canceled %b",
                    itsAccount.name, !itsLogrec.isNotConnected(),
                    itsIsCanceled);
            itsLogrec.setEndTime();
            boolean isSuccess = itsLogrec.isSuccess();

            if (itsSaveTraces || !isSuccess) {
                for (Pair<String, Long> entry : itsTraces) {
                    itsLogrec.addEntry(entry.first);
                }
            }

            try {
                SyncDb.useDb((SyncDb.DbUser<Void>)db -> {
                    Log.i(TAG, itsLogrec.toString(itsContext));
                    SyncDb.deleteSyncLogs(System.currentTimeMillis()
                                          - 2 * DateUtils.WEEK_IN_MILLIS, db);
                    SyncDb.addSyncLog(itsLogrec, db, itsContext);
                    return null;
                });

                if (!itsLogrec.isNotConnected() || !isSuccess) {
                    itsProviderImpl.setLastSyncResult(isSuccess,
                                                      itsLogrec.getEndTime());
                    SyncApp.get(itsContext).updateProviderState();
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync write log error", e);
            } finally {
                updateUIEnd(itsLogrec);
            }
        }

        /**
         * Add a trace statement
         */
        private void addTrace(String trace)
        {
            // TODO: remove tracing
            if (!BuildConfig.DEBUG) return;

            long now = System.currentTimeMillis();
            if (itsTraces.size() > 0) {
                @SuppressWarnings("ConstantConditions") long prev =
                        itsTraces.get(itsTraces.size() - 1).second;
                if ((now - prev) > 20000) {
                    itsSaveTraces = true;
                }
            }
            String s = trace + " - " + itsDateFmt.format(now);
            itsTraces.add(new Pair<>(s, now));
        }
    }
}
