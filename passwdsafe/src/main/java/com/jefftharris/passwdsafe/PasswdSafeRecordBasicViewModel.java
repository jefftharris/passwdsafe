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

    private @Nullable Owner<Totp> itsTotp = null;
    private final CloseableLiveData<TotpState> itsTotpState;
    private final Handler itsHandler = new Handler();
    private final Runnable itsTotpHideRun =
            () -> updateTotpShown(TotpVisibiltyChange.HIDE);
    private final Runnable itsUpdateTotpValueRun = this::updateTotpValue;
    private long itsNextUpdateTime = INIT_UPDATE_TIME;

    public PasswdSafeRecordBasicViewModel(Application app)
    {
        super(app);
        itsTotpState = new CloseableLiveData<>(newTotpState());
    }

    public LiveData<TotpState> getTotpState()
    {
        return itsTotpState;
    }

    public void updateTotpShown(@NonNull TotpVisibiltyChange change)
    {
        var currState = getDataValue();
        boolean shown = currState.isShown();
        switch (change) {
        case HIDE -> shown = false;
        case TOGGLE -> shown = !shown;
        }

        itsHandler.removeCallbacks(itsTotpHideRun);
        if (shown) {
            var prefs = Preferences.getSharedPrefs(getApplication());
            var timeout = Preferences.getPasswdVisibleTimeoutPref(prefs);
            switch (timeout) {
            case TO_15_SEC,
                 TO_30_SEC,
                 TO_1_MIN,
                 TO_5_MIN -> itsHandler.postDelayed(itsTotpHideRun,
                                                    timeout.getTimeout());
            case TO_NONE -> {
            }
            }
        }

        updateTotpState(shown);
    }

    public void setTotp(@Nullable Owner<Totp>.Param totp)
    {
        PasswdSafeUtil.dbginfo(TAG, "setTotp totp %b -> %b",
                               (itsTotp != null), totp != null);
        clearTotp();
        if (totp != null) {
            itsTotp = totp.use();
        }
        updateTotpValue();
    }

    @Override
    protected void onCleared()
    {
        PasswdSafeUtil.dbginfo(TAG, "onCleared");
        super.onCleared();
        clearTotp();
        itsTotpState.close();
    }

    private void resetTimers()
    {
        itsNextUpdateTime = INIT_UPDATE_TIME;
        itsHandler.removeCallbacks(itsTotpHideRun);
        itsHandler.removeCallbacks(itsUpdateTotpValueRun);
    }

    private void clearTotp()
    {
        if (itsTotp != null) {
            itsTotp.close();
            itsTotp = null;
        }
        resetTimers();
    }

    private void updateTotpValue()
    {
        var currState = getDataValue();
        updateTotpState(currState.isShown());
    }

    private void updateTotpState(boolean shown)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateTotpState shown %b, totp %b",
                               shown, itsTotp != null);

        if ((itsTotp == null) || !shown) {
            resetTimers();
            setDataValue(newTotpState());
            return;
        }

        long timeStepMs =
                TimeUnit.SECONDS.toMillis(itsTotp.get().getTimeStep());

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
            PasswdSafeUtil.dbginfo(TAG, "updateTotpState new value, progress " +
                                        "%d", progress);

            var totp = itsTotp.get();
            try (var value = totp.generate()) {
                setDataValue(new TotpState(totp.getStatus(), true,
                                           (value != null) ? value.pass() :
                                           null, progress));
            }
        } else {
            var state = getDataValue();
            setDataValue(state.updateProgress(progress));
        }
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
        return new TotpState(
                (itsTotp != null) ? itsTotp.get().getStatus() : null);
    }

    private @NonNull TotpState getDataValue()
    {
        return Objects.requireNonNull(itsTotpState.getValue());
    }

    private void setDataValue(@NonNull TotpState totpState)
    {
        itsTotpState.setValue(totpState);
    }
}
