/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-1 message digest implementation.  A thin wrapper around the built-in
 * provider's SHA-1
 */
public class SHA1
{
    private final MessageDigest itsSha1;

    /**
     * Default constructor.
     */
    public SHA1()
    {
        itsSha1 = getSha();
    }

    /**
     * Clears all data.
     */
    public void clear()
    {
        itsSha1.reset();
    }

    /**
     * Adds a portion of a byte array to the digest.
     *
     * @param data the data to add
     */
    public void update(byte[] data,
                       @SuppressWarnings("SameParameterValue") int nOfs,
                       int nLen)
    {
        itsSha1.update(data, nOfs, nLen);
    }

    /**
     * Finishes the digest and retrieves the value.
     *
     * @return the digst bytes
     */
    public byte[] getDigest()
    {
        return itsSha1.digest();
    }

    /**
     * Get the default provider's SHA-1 digester
     */
    private static MessageDigest getSha()
    {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
}
