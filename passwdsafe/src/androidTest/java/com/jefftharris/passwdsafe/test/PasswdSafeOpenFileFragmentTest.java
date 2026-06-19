/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jefftharris.passwdsafe.PasswdSafe;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.test.util.TestModeRule;
import com.jefftharris.passwdsafe.test.util.TestUtils;
import com.jefftharris.passwdsafe.test.util.TestV3FileRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.jefftharris.passwdsafe.test.util.TestUtils.withTextInputError;
import static com.jefftharris.passwdsafe.test.util.ViewActions.setChecked;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.emptyString;

/**
 * UI test for PasswdSafeOpenFileFragment
 */
@RunWith(AndroidJUnit4.class)
public class PasswdSafeOpenFileFragmentTest
{
    @Rule(order=1)
    public TestModeRule itsTestMode = new TestModeRule();

    @Rule(order=2)
    public TestV3FileRule itsTestFile = new TestV3FileRule();

    @Rule(order=3)
    public ActivityScenarioRule<PasswdSafe> itsActivityRule =
            new ActivityScenarioRule<>(PasswdSafeUtil.createOpenIntent(
                    Uri.fromFile(itsTestFile.getFile()), null));

    @Test
    public void testOpen()
    {
        verifyInitialFields(itsTestFile, false);
        onView(withId(R.id.passwd_edit))
                .perform(replaceText(TestV3FileRule.PASSWD));
        TestUtils.clickButton(R.id.open);
        PasswdSafeTestUtils.validateOpenedEmptyFile(false);
        PasswdSafeTestUtils.closeFile(itsActivityRule.getScenario(), false);
    }

    @Test
    public void testEmptyPassword()
    {
        verifyInitialFields(itsTestFile, false);
        TestUtils.clickButton(R.id.open);
        onView(withId(R.id.passwd_input))
                .check(matches(withTextInputError("Invalid password")));
        onView(withId(R.id.passwd_edit)).check(matches(withText("")));
    }

    @Test
    public void testBadPassword()
    {
        verifyInitialFields(itsTestFile, false);
        onView(withId(R.id.passwd_edit))
                .perform(replaceText(TestV3FileRule.PASSWD + "BAD"));
        TestUtils.clickButton(R.id.open);
        onView(withId(R.id.passwd_input))
                .check(matches(withTextInputError("Invalid password")));
        onView(withId(R.id.passwd_edit)).check(
                matches(withText(TestV3FileRule.PASSWD + "BAD")));
    }

    /**
     * Validate the initial fields for the open fragment
     */
    public static void verifyInitialFields(@NonNull TestV3FileRule testFile,
                                           boolean hasYubikey)
    {
        onView(withId(R.id.file))
                .check(matches(withText("Open " + testFile.getFileName())));
        onView(withId(R.id.passwd_edit))
                .check(matches(withText(emptyString())));

        onView(allOf(withId(R.id.save_password),
                     withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
                .check(matches(isNotChecked()));

        var yubikeyVis = hasYubikey ? ViewMatchers.Visibility.VISIBLE :
                         ViewMatchers.Visibility.GONE;
        onView(withId(R.id.yubikey)).check(
                matches(withEffectiveVisibility(yubikeyVis)));
        if (hasYubikey) {
            // Ensure previous test with yubikey doesn't affect current test
            onView(withId(R.id.yubikey)).perform(setChecked(false));
        }

        onView(withId(R.id.yubikey_debugging)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.yubikey_nfc_disabled)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.yubikey_error)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.read_only_msg)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.saved_password)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.yubi_progress_text)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(withId(R.id.progress)).check(matches(withEffectiveVisibility(
                ViewMatchers.Visibility.INVISIBLE)));
        onView(withId(R.id.open)).check(matches(withEffectiveVisibility(
                ViewMatchers.Visibility.VISIBLE)));
    }
}
