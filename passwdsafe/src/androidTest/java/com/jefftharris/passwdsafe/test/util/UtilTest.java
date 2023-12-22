/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

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

        long value = Util.getMillisFromByteArray(buf, 0);
        assertEquals(expected, value);
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
