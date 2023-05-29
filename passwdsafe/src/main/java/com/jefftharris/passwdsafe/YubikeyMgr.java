/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Activity;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.CountDownTimer;
import android.util.Log;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.util.ClearingByteArrayOutputStream;
import com.jefftharris.passwdsafe.util.YubiState;
import com.jefftharris.passwdsafe.view.CloseableLiveData;
import com.yubico.yubikit.android.YubiKitManager;
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration;
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable;
import com.yubico.yubikit.android.transport.usb.UsbConfiguration;
import com.yubico.yubikit.core.Logger;
import com.yubico.yubikit.core.YubiKeyDevice;
import com.yubico.yubikit.yubiotp.Slot;
import com.yubico.yubikit.yubiotp.YubiOtpSession;

import org.pwsafe.lib.Util;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;


/**
 * The YubikeyMgr class encapsulates the interaction with a YubiKey
 */
public class YubikeyMgr
{
    private static final int SHA1_MAX_BLOCK_SIZE = 64;
    private static final int KEY_TIMEOUT = 30*1000;

    private static final boolean TEST = false;//PasswdSafeUtil.DEBUG;

    private static final String TAG = "YubikeyMgr";

    private User itsUser = null;
    private CountDownTimer itsTimer = null;
    private final YubiKitManager itsYubiMgr;

    private final MutableLiveData<YubiKeyDevice> itsYubiDevice =
            new MutableLiveData<>();
    private final CloseableLiveData<KeyResult> itsResult =
            new CloseableLiveData<>();

    static {
        Logger.setLogger(new YubiLogger());
    }

    /// Interface for a user of the YubikeyMgr
    public interface User
    {
        /// Get the activity using the key
        Activity getActivity();

        /// Get the password to be sent to the key
        @CheckResult
        @Nullable
        Owner<PwsPassword> getUserPassword();

        /// Get the slot number to use on the key
        int getSlotNum();

        /// Finish interaction with the key
        void finish(Owner<PwsPassword>.Param password, Exception e);

        /// Handle an update on the timer until the start times out
        void timerTick(@SuppressWarnings("SameParameterValue") int totalTime,
                       int remainingTime);
    }

    /**
     * Constructor
     */
    public YubikeyMgr(Context ctx, Fragment openFrag)
    {
        itsYubiMgr = new YubiKitManager(ctx);
        itsYubiDevice.observe(openFrag, this::onYubikeyDeviceChanged);
        itsResult.observe(openFrag, this::onYubikeyResultChanged);

        itsYubiMgr.startUsbDiscovery(new UsbConfiguration(), device -> {
            PasswdSafeUtil.dbginfo(TAG, "USB discovery, device: %s", device);
            if (!device.hasPermission()) {
                return;
            }

            device.setOnClosed(() -> {
                PasswdSafeUtil.dbginfo(TAG, "USB device removed");
                itsYubiDevice.postValue(null);
            });

            itsYubiDevice.postValue(device);
        });
    }

    /**
     * Get the state of support for the YubiKey
     */
    public YubiState getState(Activity act)
    {
        if (TEST) {
            return YubiState.ENABLED;
        }

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(act);
        if (adapter == null) {
            return YubiState.UNAVAILABLE;
        } else if (!adapter.isEnabled()) {
            return YubiState.DISABLED;
        }
        return YubiState.ENABLED;
    }

    /**
     * Start the interaction with the YubiKey
     */
    public void start(User user)
    {
        if (itsUser != null) {
            stop();
        }
        itsUser = user;

        if (TEST) {
            testYubikey();
        } else if (itsYubiDevice.getValue() != null) {
            useYubikey(itsYubiDevice.getValue());
        } else {
            try {
                itsYubiMgr.startNfcDiscovery(
                        new NfcConfiguration().timeout(KEY_TIMEOUT),
                        itsUser.getActivity(),
                        device -> {
                            PasswdSafeUtil.dbginfo(TAG, "NFC discover, " +
                                                        "device: %s", device);

                            itsUser.getActivity().runOnUiThread(() -> {
                                itsYubiDevice.setValue(device);
                                itsYubiDevice.postValue(null);
                            });
                        });
            } catch (NfcNotAvailable e) {
                PasswdSafeUtil.dbginfo(TAG, e, "NFC discovery failed");
            }
        }

        itsTimer = new CountDownTimer(KEY_TIMEOUT, 1 * 1000)
        {
            @Override
            public void onFinish()
            {
                stop();
            }

            @Override
            public void onTick(long millisUntilFinished)
            {
                itsUser.timerTick(KEY_TIMEOUT / 1000,
                                  (int)(millisUntilFinished / 1000));
            }
        };
        itsTimer.start();
    }

    /**
     * Stop the interaction with the key
     */
    public void stop()
    {
        onPause();
        stopUser(null, null);
        itsTimer = null;
        itsUser = null;
    }

    /**
     * Handle a pause of the using fragment
     */
    public void onPause()
    {
        if (itsUser != null) {
            itsYubiMgr.stopNfcDiscovery(itsUser.getActivity());
        }
    }

    /**
     * Handle a destroy of the using fragment
     */
    public void onDestroy()
    {
        itsYubiDevice.setValue(null);
        itsYubiMgr.stopUsbDiscovery();
    }

    @UiThread
    private void useYubikey(YubiKeyDevice device)
    {
        PasswdSafeUtil.dbginfo(TAG, "Use YubiKey %s", device);
        var userPassword = itsUser.getUserPassword();
        YubiOtpSession.create(device, result -> {
            try (userPassword;
                 var pwbytes = new ClearingByteArrayOutputStream()) {
                YubiOtpSession otp = result.getValue();

                if (userPassword == null) {
                    throw new Exception("No password");
                }
                PwsPassword pw = userPassword.get();

                int pwlen = pw.length();
                if (pwlen > 0) {
                    if (pwlen > SHA1_MAX_BLOCK_SIZE / 2) {
                        pwlen = SHA1_MAX_BLOCK_SIZE / 2;
                    }
                    // Chars are encoded as little-endian UTF-16.  A trailing
                    // zero must be skipped as the PC API will skip it.
                    for (int i = 0; i < pwlen - 1; ++i) {
                        char c = pw.charAt(i);
                        pwbytes.write(c & 0xff);
                        pwbytes.write((c >> 8) & 0xff);
                    }

                    char c = pw.charAt(pwlen - 1);
                    pwbytes.write(c & 0xff);
                    int last = (c >> 8) & 0xff;
                    if (last != 0) {
                        pwbytes.write(last);
                    }
                } else {
                    // Empty password needs a single null byte
                    pwbytes.write(0);
                }

                byte[] resp = otp.calculateHmacSha1(
                        itsUser.getSlotNum() == 1 ? Slot.ONE : Slot.TWO,
                        pwbytes.toByteArray(), null);
                try {
                    // Prune response bytes and convert
                    char[] pwstr = Util.bytesToHexChars(resp, 0, resp.length);
                    try (Owner<PwsPassword> newPassword = PwsPassword.create(
                            pwstr)) {
                        itsResult.postValue(
                                new KeyResult(newPassword.pass(), null));
                    }
                } finally {
                    Util.clearArray(resp);
                }
            } catch (Exception e) {
                PasswdSafeUtil.dbginfo(TAG, e, "Error creating OTP session");
                itsResult.postValue(new KeyResult(null, e));
            }
        });
    }

    /**
     * Test using the YubiKey
     */
    @UiThread
    private void testYubikey()
    {
        new CountDownTimer(5000, 5000)
        {
            @Override
            public void onTick(long millisUntilFinished)
            {
            }

            @Override
            public void onFinish()
            {
                if (itsUser == null) {
                    return;
                }
                try (Owner<PwsPassword> password = itsUser.getUserPassword()) {
                    if (password == null) {
                        itsResult.postValue(new KeyResult(null, null));
                        return;
                    }
                    String utf8 = "UTF-8";
                    byte[] bytes = password.get().getBytes(utf8);
                    String passwordStr = new String(bytes, utf8).toLowerCase();
                    try (Owner<PwsPassword> newPassword = PwsPassword.create(
                            passwordStr)) {
                        itsResult.postValue(
                                new KeyResult(newPassword.pass(), null));
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "encode error", e);
                    itsResult.postValue(new KeyResult(null, e));
                }
            }
        }.start();
    }

    /**
     * Handle a change notification for the YubiKey device
     */
    private void onYubikeyDeviceChanged(YubiKeyDevice device)
    {
        PasswdSafeUtil.dbginfo(TAG, "YubiDevice changed: %s", device);
        if ((itsUser != null) && (device != null)) {
            useYubikey(device);
        }
    }

    /**
     * Handle a change notification for the result of using the YubiKey
     */
    private void onYubikeyResultChanged(KeyResult result)
    {
        try (result) {
            if (result != null) {
                stopUser(
                        ((result.itsPassword != null) ?
                         result.itsPassword.pass() : null), result.itsError);
            }
        }
    }

    /**
     * Stop interaction with the user
     */
    private void stopUser(Owner<PwsPassword>.Param password, Exception e)
    {
        if (itsTimer != null) {
            itsTimer.cancel();
        }
        if (itsUser != null) {
            itsUser.finish(password, e);
        }
    }

    /**
     * Result of using the YubiKey to calculate the password
     */
    private static class KeyResult implements Closeable
    {
        protected final Owner<PwsPassword> itsPassword;
        protected final Exception itsError;

        /**
         * Constructor
         */
        protected KeyResult(Owner<PwsPassword>.Param password, Exception error)
        {
            itsPassword = (password != null) ? password.use() : null;
            itsError = error;
        }

        @Override
        public void close()
        {
            if (itsPassword != null) {
                itsPassword.close();
            }
        }
    }

    /**
     * Logger for YubiKey libraries
     */
    private static class YubiLogger extends Logger
    {
        @Override
        protected void logDebug(@NonNull String message)
        {
            if (PasswdSafeUtil.DEBUG) {
                Log.d(TAG, "YubiKey log: " + message);
            }
        }

        @Override
        protected void logError(@NonNull String message,
                                @NonNull Throwable throwable)
        {
            Log.e(TAG, "YubiKey error: " + message, throwable);
        }
    }
}
