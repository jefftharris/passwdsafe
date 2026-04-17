/*
 * Copyright (©) 2025-2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Application;
import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.jefftharris.passwdsafe.file.Totp;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.view.CloseableLiveData;

import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Password record view model for TOTP
 */
public class PasswdSafeRecordTotpViewModel extends AndroidViewModel
{
    public enum VisibiltyChange
    {
        HIDE,
        TOGGLE
    }

    public static final class Config implements AutoCloseable
    {
        private final @Nullable Owner<Totp> itsTotp;
        private final boolean itsIsShown;

        private Config(@Nullable Owner<Totp>.Param totp, boolean isShown)
        {
            itsTotp = (totp != null) ? totp.use() : null;
            itsIsShown = isShown;
        }

        @NonNull
        @Contract("_ -> new")
        private Config updateShown(boolean isShown)
        {
            return new Config((itsTotp != null) ? itsTotp.pass() : null,
                              isShown);
        }

        private boolean hasTotp()
        {
            return itsTotp != null;
        }

        @CheckResult
        @Nullable
        public Owner<Totp> getTotp()
        {
            return (itsTotp != null) ? itsTotp.pass().use() : null;
        }

        public boolean isShown()
        {
            return itsIsShown;
        }

        @Override
        public void close()
        {
            if (itsTotp != null) {
                itsTotp.close();
            }
        }
    }

    public static final class State implements AutoCloseable
    {
        private final @Nullable Totp.Status itsStatus;
        private final boolean itsIsShown;
        private final @Nullable Owner<PwsPassword> itsValue;
        private final int itsTimeProgress;

        private State(@Nullable Totp.Status status)
        {
            this(status, false, null, 100);
        }

        private State(@Nullable Totp.Status status,
                      boolean isShown,
                      @Nullable Owner<PwsPassword>.Param totpValue,
                      int timeProgress)
        {
            itsStatus = status;
            itsIsShown = isShown;
            itsValue = (totpValue != null) ? totpValue.use() : null;
            itsTimeProgress = timeProgress;
        }

        @NonNull
        @Contract("_ -> new")
        private State updateProgress(int progress)
        {
            return new State(itsStatus, itsIsShown,
                             (itsValue != null) ? itsValue.pass() : null,
                             progress);
        }

        @Override
        public void close()
        {
            if (itsValue != null) {
                itsValue.close();
            }
        }

        /**
         * Get the TOTP status; null if not available
         */
        @Nullable
        public Totp.Status getStatus()
        {
            return itsStatus;
        }

        public boolean isShown()
        {
            return itsIsShown;
        }

        public int getTimeProgress()
        {
            return itsTimeProgress;
        }

        @CheckResult
        @Nullable
        public Owner<PwsPassword> getValue()
        {
            return (itsValue != null) ? itsValue.pass().use() : null;
        }
    }

    private static final long INIT_UPDATE_TIME = Long.MIN_VALUE;
    private static final long PROGRESS_STEPS = 10;
    private static final String TAG = "PasswdSafeRecordTotpVM";

    private final CloseableLiveData<Config> itsConfig;
    private final CloseableLiveData<State> itsState;
    private final Handler itsHandler = new Handler();
    private final Runnable itsConfigHideRun =
            () -> updateConfigShown(VisibiltyChange.HIDE);
    private final Runnable itsStateHideRun =
            () -> updateStateShown(VisibiltyChange.HIDE);
    private final Runnable itsUpdateValueRun = this::updateStateValue;
    private long itsNextUpdateTime = INIT_UPDATE_TIME;

    public PasswdSafeRecordTotpViewModel(Application app)
    {
        super(app);
        itsConfig = new CloseableLiveData<>(new Config(null, false));
        itsState = new CloseableLiveData<>(createState());
    }

    public LiveData<Config> getConfig()
    {
        return itsConfig;
    }

    public void updateConfigShown(@NonNull VisibiltyChange change)
    {
        var currConfig = getConfigValue();
        boolean shown = changeVisibility(currConfig.isShown(), change,
                                         itsConfigHideRun);
        setConfigValue(currConfig.updateShown(shown));
    }

    public LiveData<State> getState()
    {
        return itsState;
    }

    public void updateStateShown(@NonNull VisibiltyChange change)
    {
        boolean shown = changeVisibility(getStateValue().isShown(), change,
                                         itsStateHideRun);
        updateState(shown);
    }

    public void setTotp(@Nullable Owner<Totp>.Param totp)
    {
        PasswdSafeUtil.dbginfo(TAG, "setTotp totp %b -> %b",
                               getConfigValue().hasTotp(), totp != null);
        updateTotp(totp);
        updateStateValue();
    }

    @Override
    protected void onCleared()
    {
        PasswdSafeUtil.dbginfo(TAG, "onCleared");
        super.onCleared();
        updateTotp(null);
        itsState.close();
    }

    private void resetConfigTimers()
    {
        itsHandler.removeCallbacks(itsConfigHideRun);
    }

    private void resetStateTimers()
    {
        itsNextUpdateTime = INIT_UPDATE_TIME;
        itsHandler.removeCallbacks(itsStateHideRun);
        itsHandler.removeCallbacks(itsUpdateValueRun);
    }

    private void updateTotp(@Nullable Owner<Totp>.Param newTotp)
    {
        resetConfigTimers();
        resetStateTimers();
        setConfigValue(new Config(newTotp, false));
    }

    private void updateStateValue()
    {
        updateState(getStateValue().isShown());
    }

    private void updateState(boolean shown)
    {
        try (var totpOwner = getConfigValue().getTotp()) {
            PasswdSafeUtil.dbginfo(TAG, "updateState shown %b, totp %s", shown,
                                   ((totpOwner != null) ?
                                    totpOwner.get().getStatus() : null));

            if (shouldResetStateFromUpdate(
                    ((totpOwner != null) ? totpOwner.get() : null), shown)) {
                resetStateTimers();
                setStateValue(createState());
                return;
            }

            var totp = totpOwner.get();
            long timeStepMs = TimeUnit.SECONDS.toMillis(totp.getTimeStep());

            boolean updateValue = false;
            long now = SystemClock.uptimeMillis();
            if (now >= itsNextUpdateTime) {
                updateValue = true;
                itsNextUpdateTime = now + nextWallUpdate(timeStepMs);
            }
            int progress = calcNextProgress(now, timeStepMs);

            itsHandler.postDelayed(itsUpdateValueRun,
                                   nextWallUpdate(timeStepMs / PROGRESS_STEPS));

            if (updateValue) {
                PasswdSafeUtil.dbginfo(TAG,
                                       "updateState new value, progress %d",
                                       progress);

                try (var value = totp.generate()) {
                    setStateValue(new State(totp.getStatus(), true,
                                            (value != null) ? value.pass() :
                                            null, progress));
                }
            } else {
                var state = getStateValue();
                setStateValue(state.updateProgress(progress));
            }
        }
    }

    private static boolean shouldResetStateFromUpdate(@Nullable Totp totp,
                                                      boolean shown)
    {
        if ((totp == null) || !shown) {
            return true;
        }
        return switch (totp.getStatus()) {
            case OK -> false;
            case INVALID_ALGORITHM,
                 INVALID_NUM_DIGITS,
                 INVALID_SECRET_KEY,
                 INVALID_TIME_STEP -> true;
        };
    }

    private boolean changeVisibility(boolean currShown,
                                     @NonNull VisibiltyChange change,
                                     @NonNull Runnable hideRun)
    {
        boolean shown = currShown;
        switch (change) {
        case HIDE -> shown = false;
        case TOGGLE -> shown = !shown;
        }

        itsHandler.removeCallbacks(hideRun);
        if (shown) {
            var prefs = Preferences.getSharedPrefs(getApplication());
            var timeout = Preferences.getPasswdVisibleTimeoutPref(prefs);
            switch (timeout) {
            case TO_15_SEC,
                 TO_30_SEC,
                 TO_1_MIN,
                 TO_5_MIN ->
                    itsHandler.postDelayed(hideRun, timeout.getTimeout());
            case TO_NONE -> {
            }
            }
        }

        return shown;
    }

    private static long nextWallUpdate(long intervalMs)
    {
        long nowWall = System.currentTimeMillis();
        return intervalMs - (nowWall % intervalMs);
    }

    private int calcNextProgress(long now, long timeStepMs)
    {
        return Math.clamp(((itsNextUpdateTime - now) * 100L) / timeStepMs, 0,
                          100);
    }

    private @NonNull State createState()
    {
        try (var totpOwner = getConfigValue().getTotp()) {
            return new State(
                    (totpOwner != null) ? totpOwner.get().getStatus() : null);
        }
    }

    private @NonNull Config getConfigValue()
    {
        return Objects.requireNonNull(itsConfig.getValue());
    }

    private void setConfigValue(@NonNull Config config)
    {
        itsConfig.setValue(config);
    }

    private @NonNull State getStateValue()
    {
        return Objects.requireNonNull(itsState.getValue());
    }

    private void setStateValue(@NonNull State state)
    {
        itsState.setValue(state);
    }
}
