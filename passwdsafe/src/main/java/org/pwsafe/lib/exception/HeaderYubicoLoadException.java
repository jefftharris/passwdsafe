/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.exception;

/**
 * An exception class to indicate that the Yubico header field failed to load
 */
public class HeaderYubicoLoadException extends Exception
{
    /**
     * Constructor
     */
    public HeaderYubicoLoadException()
    {
    }
}
