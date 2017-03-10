/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test;

import android.content.Intent;
import android.net.Uri;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.jefftharris.passwdsafe.PasswdSafe;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasFocus;
import static android.support.test.espresso.matcher.ViewMatchers.isEnabled;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withParent;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.jefftharris.passwdsafe.test.util.TestUtils.withTextInputError;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;

/**
 * UI test for creating a new file
 */
@RunWith(AndroidJUnit4.class)
public class NewFileTest
{
    private static final File DIR = new File("/storage/emulated/0");

    @Rule
    public ActivityTestRule<PasswdSafe> itsActivityRule =
            new ActivityTestRule<PasswdSafe>(PasswdSafe.class)
            {
                @Override
                protected Intent getActivityIntent()
                {
                    return PasswdSafeUtil.createNewFileIntent(
                            Uri.fromFile(DIR));
                }
            };

    @Test
    public void testInitialState()
    {
        onFileNameView()
                .check(matches(allOf(withText(".psafe3"), hasFocus())));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(equalTo("Empty file name"))));

        onView(withId(R.id.password))
                .check(matches(withText(isEmptyString())));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(equalTo("Empty password"))));

        onView(withId(R.id.password_confirm))
                .check(matches(withText(isEmptyString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));

        onView(withId(R.id.cancel))
                .check(matches(isEnabled()));
        onView(withId(R.id.ok))
                .check(matches(not(isEnabled())));
    }

    @Test
    public void testExistingFile()
    {
        Assert.assertTrue(new File(DIR, "test.psafe3").exists());
        Assert.assertTrue(!new File(DIR, "ZZZtest.psafe3").exists());

        onFileNameView()
                .perform(replaceText("ZZZtest.psafe3"));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));

        onView(allOf(withId(R.id.file_name),
                     withParent(withParent(withId(R.id.file_name_input)))))
                .perform(replaceText("test.psafe3"));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(equalTo("File exists"))));
    }

    @Test
    public void testFileName()
    {
        onFileNameView()
                .check(matches(withText(".psafe3")));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(equalTo("Empty file name"))));

        onFileNameView()
                .perform(replaceText("ZZZtest.psafe3"));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));

        for (char c: "1234567890abcxyzABCXYZ".toCharArray()) {
            onFileNameView().perform(replaceText("ZZZ" + c + "test.psafe3"));
            onView(withId(R.id.file_name_input))
                    .check(matches(withTextInputError(isEmptyOrNullString())));
        }

        for (char c: "`~!@#$%^&*()_+-={}[]|\\;:'\"<>,./?".toCharArray()) {
            onFileNameView()
                    .perform(replaceText("ZZZ" + c + "test.psafe3"));
            onView(withId(R.id.file_name_input))
                    .check(matches(
                            withTextInputError(equalTo("Invalid file name"))));
        }
    }

    @Test
    public void testFileNameSuffix()
    {
        onFileNameView()
                .check(matches(withText(".psafe3")));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(equalTo("Empty file name"))));

        onView(allOf(withId(R.id.file_name),
                     withParent(withParent(withId(R.id.file_name_input)))))
                .perform(replaceText(".psafe"));

        onView(allOf(withId(R.id.file_name),
                     withParent(withParent(withId(R.id.file_name_input)))))
                .check(matches(withText(".psafe3")));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(equalTo("Empty file name"))));
    }

    @Test
    public void testPassword()
    {
        // Check initial with valid file name
        Assert.assertTrue(!new File(DIR, "ZZZtest.psafe3").exists());
        onFileNameView()
                .perform(replaceText("ZZZtest.psafe3"));
        onView(withId(R.id.file_name_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.password))
                .check(matches(withText(isEmptyString())));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(equalTo("Empty password"))));
        onView(withId(R.id.password_confirm))
                .check(matches(withText(isEmptyString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.ok))
                .check(matches(not(isEnabled())));

        onView(withId(R.id.password))
                .perform(replaceText("test123"));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.password_confirm))
                .check(matches(withText(isEmptyString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(
                        withTextInputError(equalTo("Passwords do not match"))));
        onView(withId(R.id.ok))
                .check(matches(not(isEnabled())));

        onView(withId(R.id.password_confirm))
                .perform(replaceText("test123"));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.ok))
                .check(matches(isEnabled()));

        onView(withId(R.id.password_confirm))
                .perform(replaceText("test1234"));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(
                        withTextInputError(equalTo("Passwords do not match"))));
        onView(withId(R.id.ok))
                .check(matches(not(isEnabled())));

        onView(withId(R.id.password))
                .perform(replaceText("test1234"));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.password_confirm_input))
                .check(matches(withTextInputError(isEmptyOrNullString())));
        onView(withId(R.id.ok))
                .check(matches(isEnabled()));

        onView(withId(R.id.password))
                .perform(replaceText(""));
        onView(withId(R.id.password_input))
                .check(matches(withTextInputError(equalTo("Empty password"))));
        onView(withId(R.id.password_confirm_input))
                .check(matches(
                        withTextInputError(equalTo("Passwords do not match"))));
        onView(withId(R.id.ok))
                .check(matches(not(isEnabled())));
    }

    /**
     * Test with the file name text view
     */
    private static ViewInteraction onFileNameView()
    {
        return onView(allOf(
                withId(R.id.file_name),
                withParent(withParent(withId(R.id.file_name_input)))));
    }
}
