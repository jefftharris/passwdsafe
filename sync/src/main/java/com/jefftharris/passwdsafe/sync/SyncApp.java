/*
 * Copyright (©) 2013-2014 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *  Application class for PasswdSafe Sync
 */
public class SyncApp extends Application
{
    private static final String TAG = "SyncApp";

    private Handler itsHandler = null;
    private SyncUpdateHandler itsSyncUpdateHandler;
    private SyncUpdateHandler.GDriveState itsSyncGDriveState =
            SyncUpdateHandler.GDriveState.OK;
    private boolean itsIsForceSyncFailure = false;

    /* (non-Javadoc)
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate()
    {
        PasswdSafeUtil.dbginfo(TAG, "onCreate");
        super.onCreate();

        SyncDb.initializeDb(getApplicationContext());
        itsHandler = new Handler(Looper.getMainLooper());

        Map<ProviderType, DbProvider> providerMap = new HashMap<>();
        try {
            List<DbProvider> providers = SyncDb.useDb(SyncDb::getProviders);
            for (DbProvider provider: providers) {
                providerMap.put(provider.itsType, provider);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading providers", e);
        }
        for (ProviderType type: ProviderType.values()) {
            ProviderFactory.getProvider(type, this).init(providerMap.get(type));
        }
    }


    /* (non-Javadoc)
     * @see android.app.Application#onTerminate()
     */
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
        itsHandler.post(() -> {
            itsSyncGDriveState = state;
            if (itsSyncUpdateHandler != null) {
                itsSyncUpdateHandler.updateGDriveState(state);
            }
        });
    }

    /**
     * Update after a provider's state may have changed
     */
    @WorkerThread
    public void updateProviderState()
    {
        itsHandler.post(() -> {
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
}
