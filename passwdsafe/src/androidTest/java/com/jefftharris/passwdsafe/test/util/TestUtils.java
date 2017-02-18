/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

/**
 * Test utilities
 */
public class TestUtils
{
    public static byte[] hexToBytes(String s)
    {
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte)((Character.digit(s.charAt(i*2), 16) << 4) |
                              Character.digit(s.charAt(i*2+1), 16));
        }
        return bytes;
    }
}
