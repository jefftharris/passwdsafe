/*
 * Copyright (©) 2024-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.crypto;

import org.pwsafe.lib.Log;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC implementation. Currently uses default digester provider.
 *
 * @author Glen Smith
 * @author Jeff Harris
 */
public class HmacPws
{
    private final Mac itsMac;

    public HmacPws(byte[] key) throws InvalidKeyException
    {
        try {
            // Use the default provider's HMAC SHA-256
            itsMac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            var keye = new InvalidKeyException("No algorithm", e);
            Log.getInstance(HmacPws.class.getName()).error(keye);
            throw keye;
        }
        itsMac.init(new SecretKeySpec(key, itsMac.getAlgorithm()));
    }

    public final void digest(byte[] incoming)
    {
        itsMac.update(incoming);
    }

    public final byte[] doFinal()
    {
        return itsMac.doFinal();
    }
}
