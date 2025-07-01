/*
 * Copyright (c) 2008-2009 David Muller <roxon@users.sourceforge.net>.
 * Copyright (©) 2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package org.pwsafe.lib.exception;

import java.io.Serial;

/**
 * A generic exception.
 *
 * @author Kevin Preece
 */
public class PasswordSafeException extends Exception
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     */
    public PasswordSafeException(String arg0)
    {
        super(arg0);
    }
}
