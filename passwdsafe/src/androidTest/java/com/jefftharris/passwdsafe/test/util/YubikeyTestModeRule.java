/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.jefftharris.passwdsafe.YubikeyViewModel;

import org.junit.rules.ExternalResource;

/**
 * Testing rule to set Yubikey test mode in the app
 */
public class YubikeyTestModeRule extends ExternalResource
{
    private final CountingIdlingResource itsIdleRsrc =
            new CountingIdlingResource("YubikeyTestModeRule");

    @Override
    protected void before()
    {
        IdlingRegistry.getInstance().register(itsIdleRsrc);
        YubikeyViewModel.setTesting(itsIdleRsrc);
    }

    @Override
    protected void after()
    {
        YubikeyViewModel.setTesting(null);
        IdlingRegistry.getInstance().unregister(itsIdleRsrc);
    }
}
