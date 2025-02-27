/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * Abstract fragment for the navigation drawer of an activity
 */
public abstract class AbstractNavDrawerFragment<ListenerT> extends Fragment
        implements NavigationView.OnNavigationItemSelectedListener
{
    /** Per the design guidelines, you should show the drawer on launch until
     * the user manually expands it. This shared preference tracks this. */
    private static final String PREF_USER_LEARNED_DRAWER =
            "passwdsafe_navigation_drawer_learned";

    /** Counter for how often the drawer is forced open for user to see
     *  changes */
    private static final int NUM_EXPECTED_SHOWN_DRAWER = 1;

    private ActionBarDrawerToggle itsDrawerToggle;
    private DrawerLayout itsDrawerLayout;
    private NavigationView itsNavView;
    private View itsFragmentContainerView;
    private boolean itsFromSavedInstanceState;
    private boolean itsUserLearnedDrawer;
    private boolean itsInitShowDrawer = false;
    private ListenerT itsListener;

    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        //noinspection unchecked
        itsListener = (ListenerT)ctx;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = Preferences.getSharedPrefs(getContext());
        itsUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);

        if (savedInstanceState != null) {
            itsFromSavedInstanceState = true;
        }
    }

    @Override
    public void onViewCreated(@NonNull View viewFrag, Bundle savedInstanceState)
    {
        super.onViewCreated(viewFrag, savedInstanceState);
        setHasOptionsMenu(true);

        ViewCompat.setOnApplyWindowInsetsListener(
                viewFrag,
                (v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    // Apply the insets as a margin to the view. Apply
                    // whichever insets are appropriate to the layout. You can
                    // also update the view padding if that's more appropriate.
                    var mlp = (ViewGroup.MarginLayoutParams)v.getLayoutParams();
                    mlp.topMargin = insets.top;
                    mlp.leftMargin = insets.left;
                    mlp.bottomMargin = insets.bottom;
                    mlp.rightMargin = insets.right;
                    v.setLayoutParams(mlp);

                    // Return CONSUMED to keep the insets from passing down
                    // to descendant views
                    return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        // Forward the new configuration the drawer toggle component.
        itsDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu,
                                    @NonNull MenuInflater inflater)
    {
        // If the drawer is open, show the global app actions in the action bar
        if (itsDrawerLayout != null && isDrawerOpen()) {
            inflater.inflate(R.menu.global, menu);
            ActionBar actionBar = getActionBar();
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.app_name);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        return itsDrawerToggle.onOptionsItemSelected(item) ||
               super.onOptionsItemSelected(item);
    }

    /**
     * Users of this fragment must call this method to set up the navigation
     * drawer interactions.
     */
    protected void doSetUp(DrawerLayout drawerLayout,
                           String drawerOpenPref)
    {
        itsFragmentContainerView =
                requireActivity().findViewById(R.id.navigation_drawer);
        itsDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer
        // opens
        itsDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow,
                                        GravityCompat.START);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        itsDrawerToggle = new ActionBarDrawerToggle(
                getActivity(), itsDrawerLayout,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)
        {
            @Override
            public void onDrawerClosed(View drawerView)
            {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }

                requireActivity().invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView)
            {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) {
                    return;
                }

                if (!itsUserLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to
                    // prevent auto-showing the navigation drawer automatically
                    // in the future.
                    itsUserLearnedDrawer = true;
                    SharedPreferences sp =
                            Preferences.getSharedPrefs(getContext());
                    sp.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true)
                      .apply();
                }

                requireActivity().invalidateOptionsMenu();
            }
        };

        itsDrawerLayout.addDrawerListener(itsDrawerToggle);

        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        if (PasswdSafeUtil.isTesting()) {
            prefs.edit()
                 .putInt(drawerOpenPref, NUM_EXPECTED_SHOWN_DRAWER)
                 .putBoolean(PREF_USER_LEARNED_DRAWER, true)
                 .apply();
            itsUserLearnedDrawer = true;
            itsInitShowDrawer = false;
        } else {
            int shown = prefs.getInt(drawerOpenPref, 0);
            if (shown != NUM_EXPECTED_SHOWN_DRAWER) {
                prefs.edit().putInt(drawerOpenPref, NUM_EXPECTED_SHOWN_DRAWER)
                     .apply();
                itsInitShowDrawer = true;
            }
        }
    }

    /** Is the drawer open */
    public boolean isDrawerOpen()
    {
        return (itsDrawerLayout != null) &&
               itsDrawerLayout.isDrawerOpen(itsFragmentContainerView);
    }

    /** Is the drawer enabled */
    public boolean isDrawerEnabled()
    {
        return itsDrawerToggle.isDrawerIndicatorEnabled();
    }

    /** Call from activity's onPostCreate callback */
    public void onPostCreate()
    {
        itsDrawerToggle.syncState();
    }

    /**
     * Common implementation for onCreateView
     */
    protected View doCreateView(@NonNull LayoutInflater inflater,
                                ViewGroup container,
                                int layoutId)
    {
        View fragView = inflater.inflate(layoutId, container, false);
        itsNavView = (NavigationView)fragView;
        itsNavView.setNavigationItemSelectedListener(this);
        return fragView;
    }

    /**
     * Get the activity listener
     */
    protected ListenerT getListener()
    {
        return itsListener;
    }

    /**
     * Get the navigation view
     */
    protected NavigationView getNavView()
    {
        return itsNavView;
    }

    /**
     * Should the drawer be opened automatically
     */
    protected final boolean shouldOpenDrawer()
    {
        if (itsInitShowDrawer) {
            itsInitShowDrawer = false;
            return true;
        }
        return !itsUserLearnedDrawer && !itsFromSavedInstanceState;
    }

    /**
     * Possibly open the drawer
     */
    protected void openDrawer(boolean openDrawer)
    {
        if (openDrawer) {
            itsDrawerLayout.openDrawer(itsFragmentContainerView);
        }
    }

    /**
     * Close the drawer
     */
    protected void closeDrawer()
    {
        itsDrawerLayout.closeDrawers();
    }

    /**
     * Update the drawer toggle
     */
    protected void updateDrawerToggle(boolean enabled, int upIndicator)
    {
        itsDrawerToggle.setDrawerIndicatorEnabled(enabled);
        if (upIndicator == 0) {
            itsDrawerToggle.setHomeAsUpIndicator(null);
        } else {
            itsDrawerToggle.setHomeAsUpIndicator(upIndicator);
        }
    }

    /** Get the action bar */
    private ActionBar getActionBar()
    {
        return ((AppCompatActivity) requireActivity()).getSupportActionBar();
    }
}
