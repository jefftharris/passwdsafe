/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.test;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;

import com.jefftharris.passwdsafe.PasswdSafe;
import com.jefftharris.passwdsafe.R;

import org.hamcrest.Matcher;
import org.jetbrains.annotations.Contract;
import org.junit.Assert;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.close;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.DrawerMatchers.isClosed;
import static androidx.test.espresso.contrib.DrawerMatchers.isOpen;
import static androidx.test.espresso.matcher.ViewMatchers.hasChildCount;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.jefftharris.passwdsafe.test.util.ViewActions.waitId;
import static org.hamcrest.Matchers.allOf;

/**
 * Test utilities for the PasswdSafe activity and its fragments
 */
class PasswdSafeTestUtils
{
    /**
     * Validate the UI for a new file.  The file is left in read-only mode.
     */
    public static void validateOpenedEmptyFile(boolean newFile)
    {
        if (!newFile) {
            validateMenus(false);
            setWritable(true);
        }
        validateMenus(true);
        setWritable(false);
        validateMenus(false);

        onView(withId(R.id.content)).check(matches(isEnabled()));
        onView(allOf(withId(android.R.id.list),
                     withParent(withParent(withId(R.id.content))))).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
        onView(allOf(withId(android.R.id.empty),
                     withParent(withParent(withId(R.id.content))))).check(
                matches(withEffectiveVisibility(
                        ViewMatchers.Visibility.VISIBLE)));
    }

    /**
     * Close the open file
     */
    public static void closeFile(@NonNull ActivityScenario<PasswdSafe> scenario,
                                 boolean forResult)
    {
        onView(withId(R.id.menu_close))
                .check(matches(isEnabled()))
                .perform(click());
        Assert.assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        if (forResult) {
            Assert.assertEquals(Activity.RESULT_CANCELED,
                                scenario.getResult().getResultCode());
        }
    }

    /**
     * Validate the menus of an open file
     */
    private static void validateMenus(boolean writable)
    {
        // Validate nav drawer
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.START)))
                .perform(open());

        onView(withParent(withId(R.id.navigation_drawer)))
               .check(matches(hasChildCount(1 /*header*/ + 6 /*menu items*/)));

        for (int id : new int[]
                { R.string.writable, R.string.records,
                  R.string.password_policies, R.string.password_expiration,
                  R.string.preferences, R.string.about }) {
            onView(allOf(withText(id),
                         isDescendantOfA(withId(R.id.navigation_drawer))))
                    .check(matches(isEnabled()));
        }

        onView(withWritableSw())
                .check(matches(writable ? isChecked() : isNotChecked()));

        onView(withId(R.id.drawer_layout))
                .check(matches(isOpen(Gravity.START)))
                .perform(close());

        // Validate main menu
        onView(isRoot()).perform(waitId(R.id.menu_search,
                                        TimeUnit.SECONDS.toMillis(15)));
        onView(withId(R.id.menu_search))
                .check(matches(isEnabled()));
        if (writable) {
            onView(withId(R.id.menu_add))
                    .check(matches(isEnabled()));
            openActionBarOverflowOrOptionsMenu(
                    getInstrumentation().getTargetContext());
            onView(withText(R.string.file_operations))
                    .check(matches(isEnabled()));
            onView(withText(R.string.share_file))
                    .check(matches(isEnabled()));
            onView(withText(R.string.sort))
                    .check(matches(isEnabled()));
            onView(withText(R.string.close_file))
                    .check(matches(isEnabled()));
            pressBack();
        } else {
            onView(withId(R.id.menu_close))
                    .check(matches(isEnabled()));
            onView(withId(R.id.menu_add))
                    .check(doesNotExist());
            openActionBarOverflowOrOptionsMenu(
                    getInstrumentation().getTargetContext());
            onView(withText(R.string.share_file))
                    .check(matches(isEnabled()));
            onView(withText(R.string.sort))
                    .check(matches(isEnabled()));
            pressBack();
        }
    }

    /**
     * Set the writable state of the file.  The file must be in the opposite
     * state already.
     */
    private static void setWritable(boolean writable)
    {
        onView(withId(R.id.drawer_layout))
                .check(matches(isClosed(Gravity.START)))
                .perform(open());

        onView(withWritableSw())
                .check(matches(writable ? isNotChecked() : isChecked()))
                .perform(click());

        // Wait for main menu
        onView(isRoot()).perform(
                waitId(R.id.menu_search, TimeUnit.SECONDS.toMillis(15)));
        onView(withId(R.id.drawer_layout)).check(
                matches(isClosed(Gravity.START)));
    }

    /**
     * Get a matcher for the writable switch
     */
    @NonNull
    @Contract(" -> new")
    private static Matcher<View> withWritableSw()
    {
        return allOf(
                withId(R.id.switch_item),
                withParent(withParent(hasSibling(
                        allOf(withText(R.string.writable),
                              isDescendantOfA(withId(R.id.navigation_drawer))
                        )))
                ));
    }
}
