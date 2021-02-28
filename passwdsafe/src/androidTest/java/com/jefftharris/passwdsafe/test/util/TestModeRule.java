/*
 * Copyright (©) 2021 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test.util;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

import org.junit.rules.ExternalResource;

/**
 * Testing rule to set test mode in the app
 */
public class TestModeRule extends ExternalResource
{
    @Override
    protected void before()
    {
        PasswdSafeUtil.setIsTesting(true);
    }

    @Override
    protected void after()
    {
        PasswdSafeUtil.setIsTesting(false);
    }
}
