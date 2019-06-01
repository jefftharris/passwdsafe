/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Handler;
import androidx.core.app.ActivityCompat;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.core.os.CancellationSignal;

/**
 * Saved password manager implementation for Marshmallow and higher
 */
@TargetApi(23)
public final class SavedPasswordsMgrMarshmallow
{
    /**
     * Get the Marshmallow compatible fingerprint manager
     */
    public static SavedPasswordsMgr.FingerprintMgr getFingerprintMgr(
            Context ctx)
    {
        return new FingerprintMgr(ctx);
    }

    /**
     * The fingerprint manager which is compatible with Marshmallow.  Bridges
     * the compat API of the base class with the real FingerprintManager service
     * on Marshmallow and higher.
     */
    private static final class FingerprintMgr
            extends SavedPasswordsMgr.FingerprintMgr
    {
        private final FingerprintManager itsMgr;

        /**
         * Constructor
         */
        public FingerprintMgr(Context ctx)
        {
            if (ActivityCompat.checkSelfPermission(
                    ctx, Manifest.permission.USE_FINGERPRINT) ==
                PackageManager.PERMISSION_GRANTED) {
                itsMgr = ctx.getSystemService(FingerprintManager.class);
            } else {
                itsMgr = null;
            }
        }
        @Override
        public boolean isHardwareDetected()
        {
            //noinspection MissingPermission
            return (itsMgr != null) && itsMgr.isHardwareDetected();
        }

        @Override
        public boolean hasEnrolledFingerprints()
        {
            //noinspection MissingPermission
            return (itsMgr != null) && itsMgr.hasEnrolledFingerprints();
        }

        @Override
        public void authenticate(
                FingerprintManagerCompat.CryptoObject crypto,
                int flags,
                CancellationSignal cancel,
                FingerprintManagerCompat.AuthenticationCallback callback,
                Handler handler)
                throws IllegalArgumentException, IllegalStateException
        {
            if (itsMgr == null) {
                throw new IllegalStateException("No fingerprint manager");
            }

            FingerprintManager.CryptoObject cryptoObj =
                    new FingerprintManager.CryptoObject(crypto.getCipher());

            android.os.CancellationSignal cancelObj = null;
            if (cancel != null) {
                cancelObj = (android.os.CancellationSignal)
                        cancel.getCancellationSignalObject();
            }

            //noinspection MissingPermission
            itsMgr.authenticate(cryptoObj, cancelObj, flags,
                                new AuthenticationCallback(callback), handler);
        }

    }

    /**
     * Authentication callback class to bridge the compat and non-compat
     * fingerprint manager APIs
     */
    private static final class AuthenticationCallback
            extends FingerprintManager.AuthenticationCallback
    {
        private final FingerprintManagerCompat.AuthenticationCallback itsCb;

        /**
         * Constructor
         */
        public AuthenticationCallback(
                FingerprintManagerCompat.AuthenticationCallback cb)
        {
            itsCb = cb;
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString)
        {
            itsCb.onAuthenticationError(errorCode, errString);
        }

        @Override
        public void onAuthenticationFailed()
        {
            itsCb.onAuthenticationFailed();
        }

        @Override
        public void onAuthenticationHelp(int helpCode, CharSequence helpString)
        {
            itsCb.onAuthenticationHelp(helpCode, helpString);
        }

        @Override
        public void onAuthenticationSucceeded(
                FingerprintManager.AuthenticationResult result)
        {
            FingerprintManagerCompat.CryptoObject crypto =
                    new FingerprintManagerCompat.CryptoObject(
                            result.getCryptoObject().getCipher());

            itsCb.onAuthenticationSucceeded(
                    new FingerprintManagerCompat.AuthenticationResult(crypto));
        }
    }
}
