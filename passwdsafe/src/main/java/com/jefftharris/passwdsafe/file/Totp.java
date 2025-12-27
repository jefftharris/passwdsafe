/*
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.util.Pair;

import org.apache.commons.codec.CodecPolicy;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.Util;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP generator. Generates one-time passwords following RFC 6238.
 */
public class Totp implements AutoCloseable
{
    public enum Status
    {
        OK,
        INVALID_ALGORITHM,
        INVALID_NUM_DIGITS,
        INVALID_SECRET_KEY,
        INVALID_TIME_STEP
    }

    ///  Hash function to use for TOTP values
    public enum Hash
    {
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512");

        private final String itsAlgorithm;

        Hash(String algorithm)
        {
            itsAlgorithm = algorithm;
        }
    }

    public static final int DEFAULT_NUM_DIGITS = 6;
    public static final int DEFAULT_TIME_STEP = 30;
    public static final long T0 = 0;

    private static final int[] DIGITS_POWER
            // 0  1   2    3     4      5       6        7         8
            = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    private final @NonNull Status itsStatus;
    private final @NonNull Owner<PwsPassword> itsSecretKey;
    private final @Nullable Mac itsHmac;
    private final int itsNumDigits;
    /// Time step in seconds
    private final long itsTimeStep;
    /// Time start in seconds
    private final long itsTimeStart;

    /**
     * Constructor
     */
    public Totp(@NonNull Owner<PwsPassword>.Param secretKeyParam,
                @NonNull Hash hash,
                int numDigits,
                int timeStep,
                long timeStart)
    {
        itsSecretKey = secretKeyParam.use();
        var init = init(itsSecretKey.get(), hash, numDigits, timeStep);
        itsStatus = init.first();
        itsHmac = init.second();
        itsNumDigits = numDigits;
        itsTimeStep = timeStep;
        itsTimeStart = timeStart;
    }

    /**
     * Get the TOTP status
     */
    @NonNull
    public Status getStatus()
    {
        return itsStatus;
    }

    /**
     * Get the time step in seconds
     */
    public long getTimeStep()
    {
        return itsTimeStep;
    }

    /**
     * Generate a TOTP value for the current time
     */
    @Nullable
    @CheckResult
    public Owner<PwsPassword> generate()
    {
        return generate(new Date());
    }

    /**
     * Generate a TOTP value for the given time
     */
    @Nullable
    @CheckResult
    public Owner<PwsPassword> generate(@NonNull Date time)
    {
        switch (itsStatus) {
        case OK -> {
        }
        case INVALID_ALGORITHM,
             INVALID_NUM_DIGITS,
             INVALID_SECRET_KEY,
             INVALID_TIME_STEP -> {
            return null;
        }
        }

        if (itsHmac == null) {
            return null;
        }

        var t = TimeUnit.MILLISECONDS.toSeconds(time.getTime()) - itsTimeStart;
        var steps = t / itsTimeStep;
        var stepsStr = String.format("%016X", steps);

        try {
            var stepsBytes = Hex.decodeHex(stepsStr);
            var hash = itsHmac.doFinal(stepsBytes);

            // Put selected bytes into result int according to RFC4226
            int offset = hash[hash.length - 1] & 0xf;
            int binary = ((hash[offset] & 0x7f) << 24) |
                         ((hash[offset + 1] & 0xff) << 16) |
                         ((hash[offset + 2] & 0xff) << 8) |
                         (hash[offset + 3] & 0xff);
            int otp = binary % DIGITS_POWER[itsNumDigits];

            // Convert to string using RFC6238 errata #7271 for constant-time
            var ret = Long.toString(10000000000L + otp);
            return PwsPassword.create(ret.substring(11- itsNumDigits));
        } catch (DecoderException e) {
            return null;
        }
    }

    /**
     * Close the TOTP and clear any stored values
     */
    @Override
    public void close()
    {
        itsSecretKey.close();
    }

    /**
     * Finalize the object
     */
    @Override
    protected void finalize() throws Throwable
    {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Initialize the overall status and MAC
     */
    @NonNull
    @Contract("_, _, _, _ -> new")
    private static Pair<Status, Mac> init(@NonNull PwsPassword secretKey,
                                          @NonNull Hash hash,
                                          int numDigits,
                                          int timeStep)
    {
        if ((numDigits <= 0) || (numDigits >= DIGITS_POWER.length)) {
            return new Pair<>(Status.INVALID_NUM_DIGITS, null);
        }

        if (timeStep <= 0) {
            return new Pair<>(Status.INVALID_TIME_STEP, null);
        }

        byte[] secretBytes = null;
        byte[] keyBytes = null;
        try {
            try {
                if (secretKey.length() == 0) {
                    return new Pair<>(Status.INVALID_SECRET_KEY, null);
                }
                var base32 = Base32
                        .builder()
                        .setDecodingPolicy(CodecPolicy.STRICT)
                        .get();
                secretBytes = secretKey.getBytes("US-ASCII");
                if (!base32.isInAlphabet(secretBytes, true)) {
                    return new Pair<>(Status.INVALID_SECRET_KEY, null);
                }

                keyBytes = base32.decode(secretBytes);
            } catch (UnsupportedEncodingException | RuntimeException e) {
                return new Pair<>(Status.INVALID_SECRET_KEY, null);
            }

            try {
                var hmac = Mac.getInstance(hash.itsAlgorithm);
                var key = new SecretKeySpec(keyBytes, "RAW");
                hmac.init(key);
                return new Pair<>(Status.OK, hmac);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                return new Pair<>(Status.INVALID_ALGORITHM, null);
            }
        } finally {
            if (keyBytes != null) {
                Util.clearArray(keyBytes);
            }
            if (secretBytes != null) {
                Util.clearArray(secretBytes);
            }
        }
    }
}
