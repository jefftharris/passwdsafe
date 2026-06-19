/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test;

import android.net.Uri;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jefftharris.passwdsafe.PasswdSafe;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.test.util.TestModeRule;
import com.jefftharris.passwdsafe.test.util.TestUtils;
import com.jefftharris.passwdsafe.test.util.TestV3FileRule;
import com.jefftharris.passwdsafe.test.util.YubikeyTestModeRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.jefftharris.passwdsafe.test.util.TestUtils.withTextInputError;

@RunWith(AndroidJUnit4.class)
public class PasswdSafeOpenYubikeyFileFragmentTest
{
    @Rule(order = 1)
    public TestModeRule itsTestMode = new TestModeRule();

    @Rule(order = 2)
    public YubikeyTestModeRule itsYubikeyTestMode = new YubikeyTestModeRule();

    @Rule(order = 3)
    public TestV3FileRule itsTestFile = new TestV3FileRule();

    @Rule(order = 4)
    public ActivityScenarioRule<PasswdSafe> itsActivityRule =
            new ActivityScenarioRule<>(PasswdSafeUtil.createOpenIntent(
                    Uri.fromFile(itsTestFile.getFile()), null));

    @Test
    public void testOpen()
    {
        PasswdSafeOpenFileFragmentTest.verifyInitialFields(itsTestFile, true);
        onView(withId(R.id.passwd_edit)).perform(replaceText("TEST123"));
        onView(withId(R.id.yubikey)).perform(click());

        TestUtils.clickButton(R.id.open);

        // No tests for showing progress bar.  A custom idling resource is used
        // to wait while the testing YubikeyMgr counts down and opens the file

        PasswdSafeTestUtils.validateOpenedEmptyFile(false);
        PasswdSafeTestUtils.closeFile(itsActivityRule.getScenario(), false);
    }

    @Test
    public void testEmptyPassword()
    {
        PasswdSafeOpenFileFragmentTest.verifyInitialFields(itsTestFile, true);
        onView(withId(R.id.passwd_edit)).perform(replaceText(""));
        onView(withId(R.id.yubikey)).perform(click());

        TestUtils.clickButton(R.id.open);

        // No tests for showing progress bar.  A custom idling resource is used
        // to wait while the testing YubikeyMgr counts down and opens the file

        onView(withId(R.id.passwd_input)).check(
                matches(withTextInputError("Invalid password")));
        onView(withId(R.id.passwd_edit)).check(matches(withText("")));
    }

    @Test
    public void testBadPassword()
    {
        PasswdSafeOpenFileFragmentTest.verifyInitialFields(itsTestFile, true);
        onView(withId(R.id.passwd_edit)).perform(
                replaceText(TestV3FileRule.PASSWD));
        onView(withId(R.id.yubikey)).perform(click());

        TestUtils.clickButton(R.id.open);

        // No tests for showing progress bar.  A custom idling resource is used
        // to wait while the testing YubikeyMgr counts down and opens the file

        onView(withId(R.id.passwd_input)).check(
                matches(withTextInputError("Invalid password")));
        onView(withId(R.id.passwd_edit)).check(
                matches(withText(TestV3FileRule.PASSWD)));
    }
}
