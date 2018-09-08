/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
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
import android.os.Environment;
import android.support.test.espresso.DataInteraction;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;

import com.jefftharris.passwdsafe.BuildConfig;
import com.jefftharris.passwdsafe.FileListActivity;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.test.util.ChildCheckedViewAction;

import junit.framework.Assert;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.closeSoftKeyboard;
import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerActions.open;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.contrib.NavigationViewActions.navigateTo;
import static android.support.test.espresso.intent.Checks.checkNotNull;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.Intents.intending;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasCategories;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasData;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasType;
import static android.support.test.espresso.intent.matcher.IntentMatchers.toPackage;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.jefftharris.passwdsafe.test.util.RecyclerViewAssertions.hasRecyclerViewItemAtPosition;
import static com.jefftharris.passwdsafe.test.util.RecyclerViewAssertions.withRecyclerViewCount;
import static com.jefftharris.passwdsafe.test.util.TestUtils.withAdaptedData;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * UI tests for FileListActivity
 */
@RunWith(AndroidJUnit4.class)
public class FileListActivityTest
{
    public static final File DIR = Environment.getExternalStorageDirectory();
    private static final String TEST_FILE = "test.psafe3";

    @Rule
    public IntentsTestRule<FileListActivity> itsActivityRule =
            new IntentsTestRule<>(FileListActivity.class);

    @Test
    public void testLegacyFiles()
    {
        verifyDrawerClosed();
        setLegacyFileChooser(true);

        onView(withId(android.R.id.list));
        onTestFile(TEST_FILE).check(matches(anything()));

        onView(withId(android.R.id.list))
                .check(matches(withAdaptedData(withFileData(TEST_FILE))));
        onView(withId(android.R.id.list))
                .check(matches(not(withAdaptedData(withFileData("none.psafe3")))));
    }

    @Test
    public void testLegacyFileNav()
    {
        verifyDrawerClosed();
        setLegacyFileChooser(true);

        Assert.assertTrue(
                DIR.equals(new File("/storage/emulated/0")) ||
                DIR.equals(new File("/mnt/sdcard")));
        onView(withId(R.id.current_group_label))
                .check(matches(withText(DIR.getPath())));
        onView(withId(R.id.current_group_label))
                .perform(click());
        onView(withId(R.id.current_group_label))
                .check(matches(withText(DIR.getParentFile().getPath())));
        onView(withId(R.id.home))
                .perform(click());
        onView(withId(R.id.current_group_label))
                .check(matches(withText(DIR.getPath())));
    }

    @Test
    public void testLegacyLaunchFileNew()
    {
        verifyDrawerClosed();
        setLegacyFileChooser(true);

        onView(withId(R.id.menu_file_new))
                .perform(click());

        intended(allOf(
                hasAction(equalTo("com.jefftharris.passwdsafe.action.NEW")),
                toPackage("com.jefftharris.passwdsafe"),
                hasData("file://" + DIR.getAbsolutePath())));
    }

    @Test
    public void testNewLaunchFileNew()
    {
        verifyDrawerClosed();
        setLegacyFileChooser(false);

        onView(withId(R.id.menu_file_new))
                .perform(click());

        intended(allOf(
                hasAction(equalTo("com.jefftharris.passwdsafe.action.NEW")),
                toPackage("com.jefftharris.passwdsafe")));
     }

     @Test
     public void testNewFileOpen()
     {
         itsActivityRule.getActivity().setIsTesting(true);
         verifyDrawerClosed();
         setLegacyFileChooser(false);
         clearRecents();

         Uri fileUri = Uri.fromFile(new File(DIR, TEST_FILE));
         Intent openResponse =
                 new Intent().setData(fileUri)
                             .putExtra("__test_display_name", TEST_FILE);

         intending(allOf(
                 hasAction(equalTo(
                         DocumentsContractCompat.INTENT_ACTION_OPEN_DOCUMENT)),
                 hasCategories(
                         Collections.singleton(Intent.CATEGORY_OPENABLE)),
                 hasType("application/*")))
                 .respondWith(new Instrumentation.ActivityResult(
                         Activity.RESULT_OK, openResponse));

         onView(withId(R.id.fab))
                 .perform(click());
         intended(allOf(hasAction(PasswdSafeUtil.VIEW_INTENT),
                        toPackage("com.jefftharris.passwdsafe"),
                        hasData(fileUri)));

         closeSoftKeyboard();
         pressBack();

         onNewFileList()
                 .check(matches(withEffectiveVisibility(
                         ViewMatchers.Visibility.VISIBLE)));
         onNewFileList()
                 .check(withRecyclerViewCount(1));
         onNewFileList()
                 .check(hasRecyclerViewItemAtPosition(
                         0,
                         hasDescendant(allOf(withId(R.id.text),
                                             withText(TEST_FILE)))));

         onView(withId(R.id.empty))
                 .check(matches(withEffectiveVisibility(
                         ViewMatchers.Visibility.GONE)));
     }

     @Test
     public void testNewClearRecents()
     {
         verifyDrawerClosed();
         setLegacyFileChooser(false);
         clearRecents();
     }

    @Test
    public void testAbout()
    {
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.START)))
                .perform(open());

        onView(withId(R.id.navigation_drawer))
                .perform(navigateTo(R.id.menu_drawer_about));

        onView(withId(R.id.version))
                .check(matches(withText(BuildConfig.VERSION_NAME + " (DEBUG)")));
    }

    private static void clearRecents()
    {
        openActionBarOverflowOrOptionsMenu(
                getInstrumentation().getTargetContext());
        onView(withText(R.string.clear_recent))
                .perform(click());

        onNewFileList()
                .check(matches(withEffectiveVisibility(
                        ViewMatchers.Visibility.VISIBLE)));
        onNewFileList()
                .check(withRecyclerViewCount(0));
        onView(withId(R.id.empty))
                .check(matches(withEffectiveVisibility(
                        ViewMatchers.Visibility.VISIBLE)));
    }

    /**
     * Set whether the legacy file chooser is used
     */
    private static void setLegacyFileChooser(boolean showLegacy)
    {
        verifyDrawerClosed();

        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.START)))
                .perform(open());
        onView(withId(R.id.navigation_drawer))
                .perform(navigateTo(R.id.menu_drawer_preferences));

        onView(withId(R.id.list))
                .perform(RecyclerViewActions.actionOnItem(
                        hasDescendant(allOf(withId(android.R.id.title),
                                            withText(R.string.files))),
                        click()));

        ChildCheckedViewAction legacyCheckAction =
                new ChildCheckedViewAction(android.R.id.checkbox, showLegacy);
        onView(withId(R.id.list))
                .perform(RecyclerViewActions.actionOnItem(
                        hasDescendant(
                                allOf(withId(android.R.id.title),
                                      withText(R.string.legacy_file_chooser))),
                        legacyCheckAction));
        pressBack();
        if (legacyCheckAction.isPrevChecked() == showLegacy) {
            pressBack();
        }
    }

    /**
     * Test on a file in the list
     */
    private static DataInteraction onTestFile(
            @SuppressWarnings("SameParameterValue") String fileTitle)
    {
        return onData(allOf(is(instanceOf(HashMap.class)),
                            withFileData(fileTitle)))
                .inAdapterView(withId(android.R.id.list));
    }

    /**
     * Test on the file list in the new file chooser
     */
    private static ViewInteraction onNewFileList()
    {
        return onView(allOf(withId(R.id.files),
                            instanceOf(RecyclerView.class)));
    }

    /**
     * Match with a FileData entry matching title text
     */
    private static Matcher<Object> withFileData(String expectedText)
    {
        // use preconditions to fail fast when a test is creating an
        // invalid matcher.
        checkNotNull(expectedText);
        return withFileData(equalTo(expectedText));
    }

    /**
     * Match with a FileData entry matching a title
     */
    private static Matcher<Object> withFileData(
            final Matcher<String> titleMatcher)
    {
        // use preconditions to fail fast when a test is creating an
        // invalid matcher.
        checkNotNull(titleMatcher);
        return new BoundedMatcher<Object, Map>(Map.class)
        {
            @Override
            public boolean matchesSafely(Map data)
            {
                return hasEntry(equalTo("title"), hasToString(titleMatcher))
                        .matches(data);
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("with file data: ");
                titleMatcher.describeTo(description);
            }
        };
    }

    /**
     * Verify the nav drawer is closed
     */
    private static void verifyDrawerClosed()
    {
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.START)));
    }
}
