/*
 * Copyright (©) 2019-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.rule.IntentsRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.jefftharris.passwdsafe.PasswdSafe;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.test.util.TestModeRule;
import com.jefftharris.passwdsafe.test.util.TestUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.Callable;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasType;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;

/**
 * UI test for creating a new file
 */
@RunWith(AndroidJUnit4.class)
public class PasswdSafeNewFileTest
{
    private static final File FILE = FileListActivityTest.FILE;

    @Rule(order=1)
    public TestModeRule itsTestMode = new TestModeRule();

    @Rule(order=2)
    public IntentsRule itsIntentsRule = new IntentsRule();

    @Before
    public void setup()
    {
        if (FILE.exists()) {
            Assert.assertTrue(FILE.delete());
        }
    }

    @After
    @SuppressWarnings("EmptyMethod")
    public void teardown()
    {
    }

    @Test
    public void testNewFile() throws Exception
    {
        if (!ApiCompat.supportsExternalFilesDirs()) {
            return;
        }

        doTestNewFile(Uri.fromFile(FileListActivityTest.DIR), null);
    }

    @Test
    public void testNewFileSaf() throws Exception
    {
        doTestNewFile(null, () -> {
            Uri fileUri = Uri.fromFile(FILE);
            Intent newResponse = new Intent()
                    .setData(fileUri)
                    .putExtra("__test_display_name", FILE.getName());
            intending(allOf(hasAction(
                                    equalTo(DocumentsContractCompat.INTENT_ACTION_CREATE_DOCUMENT)),
                            hasCategories(Collections.singleton(
                                    Intent.CATEGORY_OPENABLE)),
                            hasType("application/psafe3"),
                            hasExtra(Intent.EXTRA_TITLE,
                                     FILE.getName()))).respondWith(
                    new Instrumentation.ActivityResult(Activity.RESULT_OK,
                                                       newResponse));
            Assert.assertTrue(FILE.createNewFile());
            return null;
        });
    }

    /**
     * Test creating and opening a new file
     */
    private static void doTestNewFile(Uri newFileDir,
                                      Callable<Void> setupNewFile)
            throws Exception
    {
        try {
            try (ActivityScenario<PasswdSafe> scenario =
                         ActivityScenario.launchActivityForResult(
                    PasswdSafeUtil.createNewFileIntent(newFileDir))) {
                fillNewFileForm();

                if (setupNewFile != null) {
                    setupNewFile.call();
                }

                TestUtils.clickButton(R.id.ok);
                Assert.assertTrue(FILE.exists());

                // Verify new file UI
                PasswdSafeTestUtils.validateOpenedEmptyFile(true);
                PasswdSafeTestUtils.closeFile(scenario, true);
            }

            Intents.release();
            Intents.init();
            Intent openIntent =
                    PasswdSafeUtil.createOpenIntent(Uri.fromFile(FILE), null);

            try (ActivityScenario<PasswdSafe> scenario =
                         ActivityScenario.launchActivityForResult(openIntent)) {
                scenario.onActivity(
                        activity -> Assert.assertFalse(activity.isFinishing()));

                // Open file
                onView(withId(R.id.file))
                        .check(matches(withText("Open " + FILE.getName())));
                onView(withId(R.id.passwd_edit))
                        .perform(replaceText("test123"));
                TestUtils.clickButton(R.id.open);

                // Verify open file UI
                PasswdSafeTestUtils.validateOpenedEmptyFile(false);
                PasswdSafeTestUtils.closeFile(scenario, true);
            }
        } finally {
            Assert.assertTrue(FILE.delete());
        }
    }

    /**
     * Fill in the new file form
     */
    private static void fillNewFileForm()
    {
        PasswdSafeNewFileFragmentTest.onFileNameView()
                .perform(replaceText(FILE.getName()));
        onView(withId(R.id.password))
                .perform(replaceText("test123"));
        onView(withId(R.id.password_confirm))
                .perform(replaceText("test123"));
    }
}
