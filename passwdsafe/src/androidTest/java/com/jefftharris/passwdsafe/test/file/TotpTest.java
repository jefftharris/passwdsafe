/*
 * Copyright (©) 2025-2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.file;

import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.file.Totp;

import org.junit.Test;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

import java.util.Date;
import java.util.EnumMap;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for TOTP generator
 */
public final class TotpTest
{
    private static final long[] TIMES =
            {59, 1111111109L, 1111111111L, 1234567890L, 2000000000L,
             20000000000L};
    private static final String[] EXP_SHA1_6 =
            {"287082", "081804", "050471", "005924", "279037", "353130"};
    private static final String[] EXP_SHA1_8 =
            {"94287082", "07081804", "14050471", "89005924", "69279037",
             "65353130"};
    private static final String[] EXP_SHA256_6 =
            {"119246", "084774", "062674", "819424", "698825", "737706"};
    private static final String[] EXP_SHA256_8 =
            {"46119246", "68084774", "67062674", "91819424", "90698825",
             "77737706"};
    private static final String[] EXP_SHA512_6 =
            {"693936", "091201", "943326", "441116", "618901", "863826"};
    private static final String[] EXP_SHA512_8 =
            {"90693936", "25091201", "99943326", "93441116", "38618901",
             "47863826"};

    private static final EnumMap<Totp.Hash, String> TEST_KEYS;
    static {
        TEST_KEYS = new EnumMap<>(Totp.Hash.class);
        TEST_KEYS.put(Totp.Hash.SHA1, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        TEST_KEYS.put(Totp.Hash.SHA256, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                                        "GEZDGNBVGY3TQOJQGEZA====");
        TEST_KEYS.put(Totp.Hash.SHA512,
                      "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                      "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                      "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA=");
    }

    private static final EnumMap<Totp.Hash, String> TEST_KEYS_NO_PAD;
    static {
        TEST_KEYS_NO_PAD = new EnumMap<>(Totp.Hash.class);
        TEST_KEYS_NO_PAD.put(Totp.Hash.SHA1,
                             "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        TEST_KEYS_NO_PAD.put(Totp.Hash.SHA256,
                             "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                             "GEZDGNBVGY3TQOJQGEZA");
        TEST_KEYS_NO_PAD.put(Totp.Hash.SHA512,
                             "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                             "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                             "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA");
    }

    @Test
    public void testSha1()
    {
        try (var secretKey = createSecretKey(Totp.Hash.SHA1)) {
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA1, 6,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA1_6, Totp.Hash.SHA1, 6, totp);
            }
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA1, 8,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA1_8, Totp.Hash.SHA1, 8, totp);
            }
        }
    }

    @Test
    public void testSha256()
    {
        try (var secretKey = createSecretKey(Totp.Hash.SHA256)) {
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA256, 6,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA256_6, Totp.Hash.SHA256, 6, totp);
            }
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA256, 8,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA256_8, Totp.Hash.SHA256, 8, totp);
            }
        }
    }

    @Test
    public void testSha512()
    {
        try (var secretKey = createSecretKey(Totp.Hash.SHA512)) {
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA512, 6,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA512_6, Totp.Hash.SHA512, 6, totp);
            }
            try (var totp = new Totp(secretKey.pass(), Totp.Hash.SHA512, 8,
                                     Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                assertTotps(EXP_SHA512_8, Totp.Hash.SHA512, 8, totp);
            }
        }
    }

    @Test
    public void testTimeStep() throws InterruptedException
    {
        final int STEPS = 3;
        for (var hash : Totp.Hash.values()) {
            try (var secretKey = createSecretKey(hash);
                 var totp = new Totp(secretKey.pass(), hash, 8, STEPS,
                                     Totp.T0)) {
                doTestTimeStep(totp);
            }
        }
    }

    @Test
    public void testExtraPadding()
    {
        final Date TEST_DATE = new Date(0);
        for (var hash : Totp.Hash.values()) {
            StringBuilder key = new StringBuilder(
                    Objects.requireNonNull(TEST_KEYS_NO_PAD.get(hash)));
            Owner<PwsPassword> value = null;
            try {
                for (int i = 0; i < 10; ++i) {
                    try (var secretKey = PwsPassword.create(key.toString());
                         var totp = new Totp(secretKey.pass(), hash,
                                             Totp.DEFAULT_NUM_DIGITS,
                                             Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                        assertEquals(Totp.Status.OK, totp.getStatus());
                        if (value == null) {
                            value = totp.generate(TEST_DATE);
                            assertNotNull(value);
                        } else {
                            assertNotNull(value);
                            try (var newValue = totp.generate(TEST_DATE)) {
                                assertNotNull(newValue);
                                assertEquals(value.get(), newValue.get());
                            }
                        }
                    }
                    key.append("=");
                }
                assertNotNull(value);
            } finally {
                if (value != null) {
                    value.close();
                }
            }
        }
    }

    @Test
    public void testExtraKeyChars()
    {
        final Date TEST_DATE = new Date();
        for (var hash : Totp.Hash.values()) {
            try (var baseSecretKey = createSecretKey(hash);
                 var baseTotp = new Totp(baseSecretKey.pass(), hash,
                                         Totp.DEFAULT_NUM_DIGITS,
                                         Totp.DEFAULT_TIME_STEP, Totp.T0);
                 var baseValue = baseTotp.generate(TEST_DATE)) {
                assertNotNull(baseValue);
                var baseKey = TEST_KEYS.get(hash);
                assertNotNull(baseKey);
                for (var s: new String[]{" ", "-", "  ", " -", "- ", "--"}) {
                    StringBuilder key = new StringBuilder();
                    for (int idx = 0; idx < baseKey.length(); ++idx) {
                        key.append(s);
                        key.append(baseKey.charAt(idx));
                    }
                    key.append(s);

                    try (var secretKey = PwsPassword.create(key.toString());
                         var totp = new Totp(secretKey.pass(), hash,
                                             Totp.DEFAULT_NUM_DIGITS,
                                             Totp.DEFAULT_TIME_STEP, Totp.T0);
                         var value = totp.generate(TEST_DATE);
                         var totpSecretKey = totp.getSecretKey()) {

                        assertEquals(Totp.Status.OK, totp.getStatus());
                        assertNotNull(value);
                        assertEquals(baseValue.get(), value.get());
                        assertEquals(key.toString(),
                                     totpSecretKey.get().unprotectAsString());
                    }
                }
            }
        }
    }

    @Test
    public void testLenientKey()
    {
        for (var key: new String[] {"22", "2222", "22222", "2222222"}) {
            try (var secretKey = PwsPassword.create(key.toCharArray())) {
                for (var hash : Totp.Hash.values()) {
                    try (var totp = new Totp(secretKey.pass(), hash,
                                             Totp.DEFAULT_NUM_DIGITS,
                                             Totp.DEFAULT_TIME_STEP, Totp.T0);
                         var value = totp.generate()) {
                        assertEquals(Totp.Status.OK, totp.getStatus());
                        assertNotNull(value);
                    }
                }
            }
        }
    }

    @Test
    public void testEquals()
    {
        try (var secretKey = createSecretKey(Totp.Hash.SHA1);
             var totp = new Totp(secretKey.pass(), Totp.Hash.SHA1,
                                 Totp.DEFAULT_NUM_DIGITS,
                                 Totp.DEFAULT_TIME_STEP, Totp.T0)) {

            // Hash equality
            for (var testHash : Totp.Hash.values()) {
                try (var testKey = createSecretKey(testHash);
                     var testTotp = new Totp(testKey.pass(), testHash,
                                             totp.getNumDigits(),
                                             (int)totp.getTimeStep(),
                                             totp.getTimeStart())) {
                    if (testHash == Totp.Hash.SHA1) {
                        assertEquals(totp, testTotp);
                    } else {
                        assertNotEquals(totp, testTotp);
                    }
                }
            }

            // Secret key equality
            for (var testKeyExtra: new String[]{"", "-", " ", "="}) {
                try (var testSecretKey = PwsPassword.create(
                        TEST_KEYS.get(Totp.Hash.SHA1) + testKeyExtra);
                     var testTotp = new Totp(testSecretKey.pass(),
                                             totp.getHash(),
                                             totp.getNumDigits(),
                                             (int)totp.getTimeStep(),
                                             totp.getTimeStart())) {
                    if (testKeyExtra.length() == 0) {
                        assertEquals(totp, testTotp);
                    } else {
                        assertNotEquals(totp, testTotp);
                    }
                }
            }

            // Num digits equality
            for (var numDigits : new int[]{5, 6, 7, 8, totp.getNumDigits()}) {
                try (var testTotp = new Totp(secretKey.pass(), Totp.Hash.SHA1,
                                             numDigits, (int)totp.getTimeStep(),
                                             totp.getTimeStart())) {
                    if (numDigits == totp.getNumDigits()) {
                        assertEquals(totp, testTotp);
                    } else {
                        assertNotEquals(totp, testTotp);
                    }
                }
            }

            // Time step equality
            for (long timeStep = totp.getTimeStep() - 10;
                 timeStep < totp.getTimeStep() + 10; ++timeStep) {
                try (var testTotp = new Totp(secretKey.pass(), Totp.Hash.SHA1,
                                             totp.getNumDigits(), (int)timeStep,
                                             totp.getTimeStart())) {
                    if (timeStep == totp.getTimeStep()) {
                        assertEquals(totp, testTotp);
                    } else {
                        assertNotEquals(totp, testTotp);
                    }
                }
            }

            // Time start equality
            for (long timeStart = totp.getTimeStart() - 10;
                 timeStart < totp.getTimeStart() + 10; ++timeStart) {
                try (var testTotp = new Totp(secretKey.pass(), Totp.Hash.SHA1,
                                             totp.getNumDigits(),
                                             (int)totp.getTimeStep(),
                                             timeStart)) {
                    if (timeStart == totp.getTimeStart()) {
                        assertEquals(totp, testTotp);
                    } else {
                        assertNotEquals(totp, testTotp);
                    }
                }
            }
        }
    }

    @Test
    public void testInvalidNumDigits()
    {
        for (var num : new int[]{-2, -1, 0, 9, 10}) {
            for (var hash : Totp.Hash.values()) {
                try (var secretKey = createSecretKey(hash);
                     var totp = new Totp(secretKey.pass(), hash, num,
                                         Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                    assertEquals(Totp.Status.INVALID_NUM_DIGITS,
                                 totp.getStatus());
                }
            }
        }
    }

    @Test
    public void testInvalidSecretKey()
    {
        for (var key : new String[]{"", " ", "  ", "-", " -", "- ", "!",
                                    "123"}) {
            try (var secretKey = PwsPassword.create(key.toCharArray())) {
                for (var hash : Totp.Hash.values()) {
                    try (var totp = new Totp(secretKey.pass(), hash, 6,
                                             Totp.DEFAULT_TIME_STEP, Totp.T0)) {
                        assertEquals(Totp.Status.INVALID_SECRET_KEY,
                                     totp.getStatus());
                    }
                }
            }
        }
    }

    @Test
    public void testInvalidTimeStep()
    {
        for (var timeStep: new int[] {-100, -1, 0}) {
            for (var hash: Totp.Hash.values()) {
                try (var secretKey = createSecretKey(hash);
                     var totp = new Totp(secretKey.pass(), hash, 6, timeStep,
                                         Totp.T0)) {
                    assertEquals(Totp.Status.INVALID_TIME_STEP,
                                 totp.getStatus());
                }
            }
        }
    }

    @NonNull
    private static Owner<PwsPassword> createSecretKey(@NonNull Totp.Hash hash)
    {
        var key = TEST_KEYS.get(hash);
        assertNotNull(key);
        return PwsPassword.create(key.toCharArray());
    }

    private static void assertTotps(@NonNull String[] expecteds,
                                    @NonNull Totp.Hash hash,
                                    int numDigits,
                                    @NonNull Totp totp)
    {
        assertEquals(Totp.Status.OK, totp.getStatus());
        assertTrue(totp.getSecretKey().get().equals(TEST_KEYS.get(hash)));
        assertEquals(hash, totp.getHash());
        assertEquals(numDigits, totp.getNumDigits());
        assertEquals(30, totp.getTimeStep());
        assertEquals(0, totp.getTimeStart());
        assertEquals(expecteds.length, TotpTest.TIMES.length);
        for (int i = 0; i < TotpTest.TIMES.length; ++i) {
            try (var value = totp.generate(
                    new Date(TotpTest.TIMES[i] * 1000))) {
                assertNotNull(value);
                assertTrue(value.get().equals(expecteds[i]));
            }
        }
    }

    private static void doTestTimeStep(@NonNull Totp totp)
            throws InterruptedException
    {
        assertEquals(Totp.Status.OK, totp.getStatus());

        @SuppressWarnings("unchecked")
        var vals = (Owner<PwsPassword>[]) new Owner[3];
        try {
            int valsIdx = 0;
            final int SLEEP_MS = 500;

            // Validate three unique values are taken for the time steps and two
            // loops per second.  Iterate enough for 2*(time steps) + 1 for
            // three values no matter when in a second the code starts.
            for (int i = 0; i < totp.getTimeStep() * (1000 / SLEEP_MS) * 2 + 1;
                 ++i) {
                try (var val = totp.generate()) {
                    String msg =
                            String.format("val: %s, valsIdx: %d", val, valsIdx);

                    assertNotNull(msg, val);
                    for (int idx = 0; idx < vals.length; ++idx) {
                        if (idx < valsIdx) {
                            assertNotNull(msg, vals[idx]);
                            assertNotEquals(msg, vals[idx].get(), val.get());
                        } else if (idx > valsIdx) {
                            assertNull(msg, vals[idx]);
                        }
                    }

                    if (vals[valsIdx] == null) {
                        //noinspection resource (stored in array)
                        vals[valsIdx] = val.pass().use();
                        assertEquals(msg, 0, i);
                        assertEquals(msg, 0, valsIdx);
                    } else if (!vals[valsIdx].get().equals(val.get())) {
                        ++valsIdx;
                        assertTrue(msg, valsIdx < vals.length);
                        assertNull(msg, vals[valsIdx]);
                        //noinspection resource (stored in array)
                        vals[valsIdx] = val.pass().use();
                    }
                }

                //noinspection BusyWait
                Thread.sleep(SLEEP_MS);
            }

            assertEquals(vals.length - 1, valsIdx);
            for (var val : vals) {
                assertNotNull(val);
            }
        } finally {
            for (var val : vals) {
                if (val != null) {
                    val.close();
                }
            }
        }
    }
}
