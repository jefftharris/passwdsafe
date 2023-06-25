/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.util;

/**
 * Save of the saved password when opening a file
 */
public enum SavedPasswordState
{
    UNKNOWN,
    NOT_AVAILABLE,
    AVAILABLE,
    LOADED_SUCCESS,
    LOADED_FAILURE
}
