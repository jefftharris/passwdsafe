/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
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

public class PasswdSafeRecordBasicViewModel extends AndroidViewModel
{
    public enum TotpVisibiltyChange
    {
        HIDE,
        TOGGLE
    }

    @SuppressWarnings("ClassCanBeRecord")
    public static final class TotpConfig implements AutoCloseable
    {
        private final @Nullable Owner<Totp> itsTotp;
        private final boolean itsIsShown;

        private TotpConfig(@Nullable Owner<Totp>.Param totp, boolean isShown)
        {
            itsTotp = (totp != null) ? totp.use() : null;
            itsIsShown = isShown;
        }

        @NonNull
        @Contract("_ -> new")
        private TotpConfig updateShown(boolean isShown)
        {
            return new TotpConfig((itsTotp != null) ? itsTotp.pass() : null,
                                  isShown);
        }

        public boolean hasTotp()
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

    public static final class TotpState implements AutoCloseable
    {
        private final @Nullable Totp.Status itsStatus;
        private final boolean itsIsShown;
        private final @Nullable Owner<PwsPassword> itsValue;
        private final int itsTimeProgress;

        private TotpState(@Nullable Totp.Status status)
        {
            this(status, false, null, 100);
        }

        private TotpState(@Nullable Totp.Status status,
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
        private TotpState updateProgress(int progress)
        {
            return new TotpState(itsStatus, itsIsShown,
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
    private static final String TAG = "PasswdSafeRecordBasicViewModel";

    private final CloseableLiveData<TotpConfig> itsTotpConfig;
    private final CloseableLiveData<TotpState> itsTotpState;
    private final Handler itsHandler = new Handler();
    private final Runnable itsTotpConfigHideRun =
            () -> updateTotpConfigShown(TotpVisibiltyChange.HIDE);
    private final Runnable itsTotpStateHideRun =
            () -> updateTotpStateShown(TotpVisibiltyChange.HIDE);
    private final Runnable itsUpdateTotpValueRun = this::updateTotpValue;
    private long itsNextUpdateTime = INIT_UPDATE_TIME;

    public PasswdSafeRecordBasicViewModel(Application app)
    {
        super(app);
        itsTotpConfig = new CloseableLiveData<>(new TotpConfig(null, false));
        itsTotpState = new CloseableLiveData<>(newTotpState());
    }

    public LiveData<TotpConfig> getTotpConfig()
    {
        return itsTotpConfig;
    }

    public void updateTotpConfigShown(@NonNull TotpVisibiltyChange change)
    {
        var currConfig = getConfigValue();
        boolean shown = changeVisibility(currConfig.isShown(), change,
                                         itsTotpConfigHideRun);
        setConfigValue(currConfig.updateShown(shown));
    }

    public LiveData<TotpState> getTotpState()
    {
        return itsTotpState;
    }

    public void updateTotpStateShown(@NonNull TotpVisibiltyChange change)
    {
        boolean shown = changeVisibility(getStateValue().isShown(),
                                         change, itsTotpStateHideRun);
        updateTotpState(shown);
    }

    public void setTotp(@Nullable Owner<Totp>.Param totp)
    {
        PasswdSafeUtil.dbginfo(TAG, "setTotp totp %b -> %b",
                               getConfigValue().hasTotp(), totp != null);
        updateTotp(totp);
        updateTotpValue();
    }

    @Override
    protected void onCleared()
    {
        PasswdSafeUtil.dbginfo(TAG, "onCleared");
        super.onCleared();
        updateTotp(null);
        itsTotpState.close();
    }

    private void resetConfigTimers()
    {
        itsHandler.removeCallbacks(itsTotpConfigHideRun);
    }

    private void resetStateTimers()
    {
        itsNextUpdateTime = INIT_UPDATE_TIME;
        itsHandler.removeCallbacks(itsTotpStateHideRun);
        itsHandler.removeCallbacks(itsUpdateTotpValueRun);
    }

    private void updateTotp(@Nullable Owner<Totp>.Param newTotp)
    {
        resetConfigTimers();
        resetStateTimers();
        setConfigValue(new TotpConfig(newTotp, false));
    }

    private void updateTotpValue()
    {
        updateTotpState(getStateValue().isShown());
    }

    private void updateTotpState(boolean shown)
    {
        try (var totpOwner = getConfigValue().getTotp()) {
            PasswdSafeUtil.dbginfo(TAG, "updateTotpState shown %b, totp %b",
                                   shown, (totpOwner != null));

            if ((totpOwner == null) || !shown) {
                resetStateTimers();
                setStateValue(newTotpState());
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

            itsHandler.postDelayed(itsUpdateTotpValueRun,
                                   nextWallUpdate(timeStepMs / 5));

            if (updateValue) {
                PasswdSafeUtil.dbginfo(TAG,
                                       "updateTotpState new value, progress " +
                                       "%d", progress);

                try (var value = totp.generate()) {
                    setStateValue(new TotpState(totp.getStatus(), true,
                                                (value != null) ? value.pass() :
                                                null, progress));
                }
            } else {
                var state = getStateValue();
                setStateValue(state.updateProgress(progress));
            }
        }
    }

    private boolean changeVisibility(boolean currShown,
                                     @NonNull TotpVisibiltyChange change,
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

    private @NonNull TotpState newTotpState()
    {
        try (var totpOwner = getConfigValue().getTotp()) {
            return new TotpState(
                    (totpOwner != null) ? totpOwner.get().getStatus() : null);
        }
    }

    private @NonNull TotpConfig getConfigValue()
    {
        return Objects.requireNonNull(itsTotpConfig.getValue());
    }

    private void setConfigValue(@NonNull TotpConfig totpConfig)
    {
        itsTotpConfig.setValue(totpConfig);
    }

    private @NonNull TotpState getStateValue()
    {
        return Objects.requireNonNull(itsTotpState.getValue());
    }

    private void setStateValue(@NonNull TotpState totpState)
    {
        itsTotpState.setValue(totpState);
    }
}
