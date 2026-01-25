/*
 * Copyright (©) 2023-2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.pwsafe.lib.Util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests for org.pwsafe.lib.Util class
 */
public final class UtilTest
{
    @Test
    public void testIntToByteArray()
    {
        int expected = 0x12345678;
        byte[] buf = new byte[4];
        Util.putIntToByteArray(buf, expected, 0);
        assertArrayEquals(new byte[]{0x78, 0x56, 0x34, 0x12}, buf);

        int value = Util.getIntFromByteArray(buf, 0);
        assertEquals(expected, value);
    }

    @Test
    public void testMillisToByteArray()
    {
        long expected = 0x12345678L * 1000L;
        byte[] buf = new byte[4];
        Util.putMillisToByteArray(buf, expected, 0);
        assertArrayEquals(new byte[]{0x78, 0x56, 0x34, 0x12}, buf);

        doTestMillisToByteArray(new byte[]{0, 0, 0, 0}, 0x00);
        doTestMillisToByteArray(new byte[]{0x78, 0, 0, 0}, 0x78);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0, 0}, 0x5678);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0}, 0x345678);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0x12}, 0x12345678);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0x12},
                                0x2112345678L);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0x12},
                                0x432112345678L);
        /* Tests skipped due to millisecond overflow converting from seconds
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0x12},
                                0x65432112345678L);
        doTestMillisToByteArray(new byte[]{0x78, 0x56, 0x34, 0x12},
                                0x8765432112345678L);
         */

        long value = Util.getMillisFromByteArray(buf, 0);
        assertEquals(expected, value);

        // 0-8
        doTestMillisToByteArray(0, new byte[]{});
        doTestMillisToByteArray(0x78, new byte[]{0x78});
        doTestMillisToByteArray(0x5678, new byte[]{0x78, 0x56});
        doTestMillisToByteArray(0x345678, new byte[]{0x78, 0x56, 0x34});
        doTestMillisToByteArray(0x12345678, new byte[]{0x78, 0x56, 0x34, 0x12});
        doTestMillisToByteArray(0x2112345678L,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21});
        doTestMillisToByteArray(0x432112345678L,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21, 0x43});
        doTestMillisToByteArray(0x65432112345678L,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21, 0x43,
                                           0x65});
        doTestMillisToByteArray(0x8765432112345678L,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21, 0x43,
                                           0x65, (byte)0x87});

        // > 8
        doTestMillisToByteArray(0,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21, 0x43,
                                           0x65, (byte)0x87, (byte)0x99});
        doTestMillisToByteArray(0,
                                new byte[]{0x78, 0x56, 0x34, 0x12, 0x21, 0x43,
                                           0x65, (byte)0x87, (byte)0x99,
                                           (byte)0xaa});
    }

    private static void doTestMillisToByteArray(@NonNull byte[] expectedBuf,
                                                long valueSecs)
    {
        byte[] valueBuf = new byte[]{0, 0, 0, 0};
        Util.putMillisToByteArray(valueBuf, valueSecs * 1000, 0);
        assertEquals(4, valueBuf.length);
        assertEquals(4, expectedBuf.length);
        assertArrayEquals(expectedBuf, valueBuf);
    }

    private static void doTestMillisToByteArray(long expectedSecs,
                                                @NonNull byte[] valueBuf)
    {
        long value = Util.getMillisFromByteArray(valueBuf, 0);
        assertEquals(expectedSecs * 1000, value);
    }

    @Test
    public void testShortToByteArray()
    {
        short expected = 0x1234;
        byte[] buf = new byte[2];
        Util.putShortToByteArray(buf, expected, 0);
        assertArrayEquals(new byte[]{0x34, 0x12}, buf);

        short value = Util.getShortFromByteArray(buf, 0);
        assertEquals(expected, value);
    }
}
