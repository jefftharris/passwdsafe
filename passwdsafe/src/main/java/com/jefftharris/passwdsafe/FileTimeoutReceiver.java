/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeLog;
import com.jefftharris.passwdsafe.pref.FileTimeoutPref;

/**
 * The FileTimeoutReceiver class manages a timeout for file activity
 */
public class FileTimeoutReceiver extends BroadcastReceiver
    implements SharedPreferences.OnSharedPreferenceChangeListener,
               DefaultLifecycleObserver
{
    private final Activity itsActivity;
    private final AlarmManager itsAlarmMgr;
    private final PendingIntent itsCloseIntent;
    private int itsFileCloseTimeout = 0;
    private boolean itsIsCloseScreenOff =
            Preferences.PREF_FILE_CLOSE_SCREEN_OFF_DEF;
    private boolean itsIsPaused = true;
    private boolean itsIsResumed = false;

    private static final String TAG = "FileTimeoutReceiver";

    /**
     * Constructor
     */
    public FileTimeoutReceiver(@NonNull AppCompatActivity act)
    {
        itsActivity = act;
        itsAlarmMgr = (AlarmManager)
                itsActivity.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(PasswdSafeApp.FILE_TIMEOUT_INTENT);
        intent.setPackage("com.jefftharris.passwdsafe");
        itsCloseIntent = PendingIntent.getBroadcast(
                itsActivity, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter =
                new IntentFilter(PasswdSafeApp.FILE_TIMEOUT_INTENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        ApiCompat.registerNotExportedBroadcastReceiver(itsActivity, this,
                                                       filter);

        SharedPreferences prefs = Preferences.getSharedPrefs(itsActivity);
        prefs.registerOnSharedPreferenceChangeListener(this);
        updatePrefs(prefs);

        act.getLifecycle().addObserver(this);
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner)
    {
        setResumed(false);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner)
    {
        setResumed(true);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner)
    {
        PasswdSafeLog.debug(TAG, "onDestroy");
        cancel();
        SharedPreferences prefs = Preferences.getSharedPrefs(itsActivity);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        itsActivity.unregisterReceiver(this);
    }

    /**
     * Update the file timeout
     * @param paused Whether the timeout is paused
     */
    public void updateTimeout(boolean paused)
    {
        PasswdSafeLog.debug(TAG, "updateTimeout paused %b -> %b, timeout" +
                                 " %d, resumed %b", itsIsPaused, paused,
                            itsFileCloseTimeout, itsIsResumed);
        if (!itsIsResumed) {
            return;
        }

        if (paused) {
            if (!itsIsPaused) {
                itsIsPaused = true;
                cancel();
            }
        } else {
            itsIsPaused = false;
            if (itsFileCloseTimeout != 0) {
                itsAlarmMgr.set(
                        AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + itsFileCloseTimeout,
                        itsCloseIntent);
            }
        }
    }

    @Override
    public void onReceive(Context ctx, @NonNull Intent intent)
    {
        boolean close = false;
        switch (String.valueOf(intent.getAction())) {
        case PasswdSafeApp.FILE_TIMEOUT_INTENT: {
            PasswdSafeLog.info(TAG, "File timeout");
            close = true;
            break;
        }
        case Intent.ACTION_SCREEN_OFF: {
            if (itsIsCloseScreenOff) {
                PasswdSafeLog.info(TAG, "Screen off");
                close = true;
            }
            break;
        }
        }
        if (close) {
            itsActivity.finish();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs,
                                          @Nullable String key)
    {
        if (key == null) {
            updatePrefs(prefs);
        } else {
            switch (key) {
            case Preferences.PREF_FILE_CLOSE_SCREEN_OFF:
            case Preferences.PREF_FILE_CLOSE_TIMEOUT: {
                updatePrefs(prefs);
                break;
            }
            }
        }
    }

    /**
     * Update the preferences
     */
    private void updatePrefs(SharedPreferences prefs)
    {
        FileTimeoutPref pref = Preferences.getFileCloseTimeoutPref(prefs);
        itsIsCloseScreenOff = Preferences.getFileCloseScreenOffPref(prefs);
        PasswdSafeLog.debug(TAG, "update prefs timeout: %s, screen: %b",
                            pref, itsIsCloseScreenOff);

        itsFileCloseTimeout = pref.getTimeout();
        if (itsFileCloseTimeout == 0) {
            cancel();
        } else {
            updateTimeout(itsIsPaused);
        }
    }

    /**
     * Set whether the activity has fully resumed
     */
    private void setResumed(boolean resumed)
    {
        if (resumed != itsIsResumed) {
            PasswdSafeLog.debug(TAG, "setResumed %b -> %b", itsIsResumed,
                                resumed);
            itsIsResumed = resumed;
        }
    }

    /**
     * Cancel the file timeout timer
     */
    private void cancel()
    {
        PasswdSafeLog.debug(TAG, "cancel");
        itsAlarmMgr.cancel(itsCloseIntent);
    }
}
