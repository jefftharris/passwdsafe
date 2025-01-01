/*
 * Copyright (©) 2013-2014 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.Preferences;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  Application class for PasswdSafe Sync
 */
public class SyncApp extends Application
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
    public static final String WORK_TAG = "PasswdSafe Sync";

    private static final String TAG = "SyncApp";

    private SyncUpdateHandler itsSyncUpdateHandler;
    private SyncUpdateHandler.GDriveState itsSyncGDriveState =
            SyncUpdateHandler.GDriveState.OK;
    private boolean itsIsForceSyncFailure = false;

    @Override
    public void onCreate()
    {
        PasswdSafeUtil.dbginfo(TAG, "onCreate");
        super.onCreate();

        SharedPreferences prefs = Preferences.getSharedPrefs(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        initPrefs(prefs);

        Context appCtx = getApplicationContext();
        SyncDb.initializeDb(appCtx);

        WorkManager workMgr = WorkManager.getInstance(appCtx);
        workMgr.getWorkInfosByTagLiveData(WORK_TAG)
               .observeForever(workInfos -> {
                   PasswdSafeUtil.dbginfo(TAG, "Work changed, have %b",
                                          workInfos != null);
                   if (workInfos == null) {
                       return;
                   }
                   for (WorkInfo work : workInfos) {
                       PasswdSafeUtil.dbginfo(TAG, "Work %s %s",
                                              work.getState(), work.getTags());
                   }
               });
        workMgr.pruneWork();

        Map<ProviderType, DbProvider> providerMap = new HashMap<>();
        try {
            List<DbProvider> providers = SyncDb.useDb(SyncDb::getProviders);
            for (DbProvider provider: providers) {
                providerMap.put(Objects.requireNonNull(provider.itsType),
                                provider);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading providers", e);
        }
        for (ProviderType type: ProviderType.values()) {
            ProviderFactory.getProvider(type, this).init(providerMap.get(type));
        }
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs,
                                          @Nullable String key)
    {
        if (key == null) {
            initPrefs(prefs);
        } else {
            PasswdSafeUtil.dbginfo(TAG, "Preference change: %s, value: %s", key,
                                   prefs.getAll().get(key));

            switch (key) {
            case Preferences.PREF_DEBUG_TAGS: {
                setDebugTags(prefs);
                break;
            }
            }
        }
    }


    @Override
    public void onTerminate()
    {
        PasswdSafeUtil.dbginfo(TAG, "onTerminate");
        for (ProviderType type: ProviderType.values()) {
            ProviderFactory.getProvider(type, this).fini();
        }
        SyncDb.finalizeDb();
        super.onTerminate();
    }


    /** Get the Sync application */
    public static SyncApp get(Context ctx)
    {
        return (SyncApp)ctx.getApplicationContext();
    }


    /** Set the callback for sync updates */
    public void setSyncUpdateHandler(SyncUpdateHandler handler)
    {
        itsSyncUpdateHandler = handler;
        if (itsSyncUpdateHandler != null) {
            itsSyncUpdateHandler.updateGDriveState(itsSyncGDriveState);
        }
    }

    /**
     * Update the state of a Google Drive sync
     */
    @WorkerThread
    public void updateGDriveSyncState(final SyncUpdateHandler.GDriveState state)
    {
        SyncHelper.runOnUiThread(() -> {
            itsSyncGDriveState = state;
            if (itsSyncUpdateHandler != null) {
                itsSyncUpdateHandler.updateGDriveState(state);
            }
        });
    }

    /**
     * Update after a provider's state may have changed
     */
    public void updateProviderState()
    {
        SyncHelper.runOnUiThread(() -> {
            if (itsSyncUpdateHandler != null) {
                itsSyncUpdateHandler.updateProviderState();
            }
        });
    }

    /**
     * Get whether to force a sync failure
     */
    public boolean isForceSyncFailure()
    {
        return BuildConfig.DEBUG && itsIsForceSyncFailure;
    }

    /**
     * Set whether to force a sync failure
     */
    public void setIsForceSyncFailure(boolean forceFailure)
    {
        itsIsForceSyncFailure = forceFailure;
    }

    /**
     * Set the debugging tags from its preference
     */
    private static void setDebugTags(SharedPreferences prefs)
    {
        PasswdSafeLog.setDebugTags(Preferences.getDebugTagsPref(prefs));
    }

    /**
     * Initialize settings from preferences
     */
    private void initPrefs(SharedPreferences prefs)
    {
        setDebugTags(prefs);
    }
}
