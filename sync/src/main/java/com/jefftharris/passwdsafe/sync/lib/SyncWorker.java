/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.ProviderFactory;
import com.jefftharris.passwdsafe.sync.SyncApp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SyncWorker extends Worker
{
    private static final String KEY_TYPE = "type";
    private static final String KEY_USERID = "userId";
    private static final String KEY_MANUAL = "manual";

    private static final ReentrantLock itsLock = new ReentrantLock();

    private static final String TAG = "SyncWorker";

    private final ProviderSync itsSync;
    private final String itsTag;

    /**
     * Constructor
     */
    public SyncWorker(@NonNull Context context,
                      @NonNull WorkerParameters workerParams)
    {
        super(context, workerParams);

        Data syncData = getInputData();
        ProviderType providerType = ProviderType.fromString(
                syncData.getString(KEY_TYPE));
        String userId = syncData.getString(KEY_USERID);
        boolean manual = syncData.getBoolean(KEY_MANUAL, false);
        itsTag = String.format("%s [%s]", providerType, userId);

        Provider providerImpl = ProviderFactory.getProvider(
                providerType, getApplicationContext());
        itsSync = providerImpl.createBackgroundSync(manual);
    }

    @NonNull @Override
    public Result doWork()
    {
        if (!itsLock.tryLock()) {
            PasswdSafeUtil.dbginfo(TAG, "doWork defer %s", itsTag);
            return Result.retry();
        }
        try {
            PasswdSafeUtil.dbginfo(TAG, "doWork start %s", itsTag);
            if (itsSync != null) {
                itsSync.sync();
            }

            PasswdSafeUtil.dbginfo(TAG, "doWork success %s", itsTag);
            return Result.success();
        } catch (Exception e) {
            PasswdSafeUtil.dbginfo(TAG, e, "doWork failed %s", itsTag);
            return Result.failure();
        } finally {
            itsLock.unlock();
        }
    }

    @Override
    public void onStopped()
    {
        super.onStopped();

        PasswdSafeUtil.dbginfo(TAG, "stopped %s", itsTag);
        if (itsSync != null) {
            itsSync.cancel();
        }
    }

    /**
     * Schedule the sync for a provider
     */
    public static void schedule(ProviderType type,
                                String userId,
                                int freq,
                                Context ctx)
    {
        String uniqueId = getUniqueId(type, true);
        WorkManager workMgr = WorkManager.getInstance(ctx);
        if (freq > 0) {
            Data syncData = new Data.Builder()
                    .putString(KEY_TYPE, type.toString())
                    .putString(KEY_USERID, userId)
                    .putBoolean(KEY_MANUAL, false)
                    .build();

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();

            PeriodicWorkRequest workReq = new PeriodicWorkRequest.Builder(
                    SyncWorker.class, freq, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .setInputData(syncData)
                    .addTag(SyncApp.WORK_TAG)
                    .addTag(uniqueId)
                    .build();

            workMgr.enqueueUniquePeriodicWork(
                    uniqueId, ExistingPeriodicWorkPolicy.REPLACE, workReq);
        } else {
            workMgr.cancelUniqueWork(uniqueId);
        }
    }

    /**
     * Request a sync of a provider
     */
    public static void requestSync(ProviderType type,
                                   String userId,
                                   boolean manual,
                                   Context ctx)
    {
        String uniqueId = getUniqueId(type, false);
        WorkManager workMgr = WorkManager.getInstance(ctx);

        Data syncData = new Data.Builder()
                .putString(KEY_TYPE, type.toString())
                .putString(KEY_USERID, userId)
                .putBoolean(KEY_MANUAL, manual)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();

        OneTimeWorkRequest workReq = new OneTimeWorkRequest.Builder(
                SyncWorker.class)
                .setConstraints(constraints)
                .setInputData(syncData)
                .addTag(SyncApp.WORK_TAG)
                .addTag(uniqueId)
                .build();

        workMgr.enqueueUniqueWork(uniqueId, ExistingWorkPolicy.KEEP, workReq);
    }

    /**
     * Get a unique ID for a provider sync
     */
    private static String getUniqueId(ProviderType type, boolean background)
    {
        return (background ? "SyncWorker-" : "ManualSync-") + type;
    }
}
