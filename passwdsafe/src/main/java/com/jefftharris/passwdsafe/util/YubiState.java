/*
 * Copyright (©) 2014-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.util;

/**
 * State of YubiKey support
 */
public enum YubiState
{
    /// State is not known
    UNKNOWN,
    /// USB and NFC are both unavailable on the device
    UNAVAILABLE,

    /// USB is not available, NFC is enabled
    USB_DISABLED_NFC_ENABLED,
    /// USB is not available, NFC is disabled
    USB_DISABLED_NFC_DISABLED,

    /// USB is available, NFC is unavailable
    USB_ENABLED_NFC_UNAVAILABLE,
    /// USB is available, NFC is disabled
    USB_ENABLED_NFC_DISABLED,

    /// Both USB and NFC are available
    ENABLED;

    /**
     * Are either USB or NFC enabled
     */
    public boolean isEnabled()
    {
        return switch (this) {
            case USB_DISABLED_NFC_ENABLED,
                 USB_ENABLED_NFC_UNAVAILABLE,
                 USB_ENABLED_NFC_DISABLED,
                 ENABLED -> true;
            case UNKNOWN,
                 UNAVAILABLE,
                 USB_DISABLED_NFC_DISABLED -> false;
        };
    }

    /**
     * Is USB enabled
     */
    public boolean isUsbEnabled()
    {
        return switch (this) {
            case USB_ENABLED_NFC_UNAVAILABLE,
                 USB_ENABLED_NFC_DISABLED,
                 ENABLED -> true;
            case UNKNOWN,
                 UNAVAILABLE,
                 USB_DISABLED_NFC_ENABLED,
                 USB_DISABLED_NFC_DISABLED -> false;
        };
    }
}
