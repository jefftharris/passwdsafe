/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;

/**
 * Fragment for the navigation drawer of the file list activity
 */
public class FileListNavDrawerFragment
        extends AbstractNavDrawerFragment<FileListNavDrawerFragment.Listener>
        implements LoaderManager.LoaderCallbacks<Cursor>
{
    /** Listener interface for the owning activity */
    public interface Listener
    {
        /** Show the files */
        void showFiles();

        /** Show the sync files */
        void showSyncProviderFiles(Uri uri);

        /** Show the backup files */
        void showBackupFiles();

        /** Show the preferences */
        void showPreferences();

        /** Show the about dialog */
        void showAbout();
    }

    /** Mode of the navigation drawer */
    public enum Mode
    {
        /** Initial state */
        INIT,
        /** About */
        ABOUT,
        /** Files */
        FILES,
        /** Sync files */
        SYNC_FILES,
        /** Backup files */
        BACKUP_FILES,
        /** Preferences */
        PREFERENCES
    }

    private static final String PREF_SHOWN_DRAWER =
            "passwdsafe_navigation_drawer_shown";

    private int itsSelNavItem = -1;
    private Uri itsSelSyncFilesUri = null;
    private final SparseArray<Uri> itsProviders = new SparseArray<>();
    private int itsNoProvidersNavItem = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        return doCreateView(inflater, container,
                            R.layout.fragment_file_list_nav_drawer);
    }

    @Override
    public void onViewCreated(@NonNull View fragView, Bundle savedInstanceState)
    {
        super.onViewCreated(fragView, savedInstanceState);
        //LoaderManager.enableDebugLogging(true);
        LoaderManager.getInstance(this).initLoader(1, null, this);
    }

    /**
     * Users of this fragment must call this method to set up the navigation
     * drawer interactions.
     *
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(DrawerLayout drawerLayout)
    {
        doSetUp(drawerLayout, PREF_SHOWN_DRAWER);
        updateView(Mode.INIT, null);
    }

    /**
     * Update drawer for the fragments displayed in the activity
     */
    public void updateView(@NonNull Mode mode, Uri syncFilesUri)
    {
        Menu menu = getNavView().getMenu();
        boolean openDrawer = false;
        int selNavItem = -1;
        Uri selSyncFilesUri = null;
        switch (mode) {
        case INIT: {
            break;
        }
        case ABOUT: {
            selNavItem = R.id.menu_drawer_about;
            break;
        }
        case FILES: {
            // If the user hasn't 'learned' about the drawer, open it
            openDrawer = shouldOpenDrawer();
            selNavItem = R.id.menu_drawer_files;
            break;
        }
        case SYNC_FILES: {
            selSyncFilesUri = syncFilesUri;
            for (int i = 0; i < itsProviders.size(); ++i) {
                if (itsProviders.valueAt(i).equals(syncFilesUri)) {
                    selNavItem = itsProviders.keyAt(i);
                    break;
                }
            }
            break;
        }
        case BACKUP_FILES: {
            selNavItem = R.id.menu_drawer_backup_files;
            break;
        }
        case PREFERENCES: {
            selNavItem = R.id.menu_drawer_preferences;
            break;
        }
        }

        updateDrawerToggle(true, 0);

        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            int itemId = item.getItemId();
            if (selNavItem == -1) {
                item.setChecked(false);
            } else if (selNavItem == itemId) {
                item.setChecked(true);
            }
        }
        itsSelNavItem = selNavItem;
        itsSelSyncFilesUri = selSyncFilesUri;

        openDrawer(openDrawer);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem)
    {
        closeDrawer();

        Listener listener = getListener();
        int navItem = menuItem.getItemId();
        if (itsSelNavItem != navItem) {
            if (navItem == R.id.menu_drawer_about) {
                listener.showAbout();
            } else if (navItem == R.id.menu_drawer_backup_files) {
                listener.showBackupFiles();
            } else if (navItem == R.id.menu_drawer_files) {
                listener.showFiles();
            } else if (navItem == R.id.menu_drawer_preferences) {
                listener.showPreferences();
            } else {
                Uri providerUri = itsProviders.get(navItem);
                if (providerUri != null) {
                    listener.showSyncProviderFiles(providerUri);
                } else if (itsNoProvidersNavItem != -1) {
                    PasswdSafeUtil.startMainActivity(
                            PasswdSafeUtil.SYNC_PACKAGE, getContext());
                }
            }
        }

        return true;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        return new PasswdCursorLoader(
                requireContext(), PasswdSafeContract.Providers.CONTENT_URI,
                PasswdSafeContract.Providers.PROJECTION,
                null, null, PasswdSafeContract.Providers.PROVIDER_SORT_ORDER);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor)
    {
        if (PasswdCursorLoader.checkResult(loader, getActivity())) {
            updateProviders(cursor);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader)
    {
        onLoadFinished(loader, null);
    }

    /**
     * Update the list of providers
     */
    private void updateProviders(Cursor cursor)
    {
        Menu menu = getNavView().getMenu();

        Uri currUri = itsProviders.get(itsSelNavItem);
        if (currUri == null) {
            currUri = itsSelSyncFilesUri;
        }
        if (currUri != null) {
            itsSelNavItem = -1;
        }
        for (int i = 0; i < itsProviders.size(); ++i) {
            menu.removeItem(itsProviders.keyAt(i));
        }
        itsProviders.clear();
        if (itsNoProvidersNavItem != -1) {
            menu.removeItem(itsNoProvidersNavItem);
            itsNoProvidersNavItem = -1;
        }

        if (cursor != null)
        {
            int nextProviderId = Menu.FIRST;
            for (boolean more = cursor.moveToFirst(); more;
                 more = cursor .moveToNext()) {
                String typestr = cursor.getString(
                        PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
                ProviderType type = ProviderType.valueOf(typestr);
                String acct =
                        PasswdSafeContract.Providers.getDisplayName(cursor);
                MenuItem item = menu.add(R.id.menu_group_main,
                                         nextProviderId, 10, acct);
                type.setIcon(item);
                updateMenuIcon(item);
                item.setCheckable(true);

                long id = cursor.getLong(
                        PasswdSafeContract.Providers.PROJECTION_IDX_ID);
                Uri uri = ContentUris.withAppendedId(
                        PasswdSafeContract.Providers.CONTENT_URI, id);
                if (uri.equals(currUri)) {
                    item.setChecked(true);
                    itsSelNavItem = nextProviderId;
                }
                itsProviders.put(nextProviderId++, uri);
            }

            if ((nextProviderId == Menu.FIRST) &&
                SyncProviderFragment.checkProvider(requireContext())) {
                MenuItem item = menu.add(R.id.menu_group_main,
                                         nextProviderId, 10,
                                         R.string.select_accounts);
                item.setIcon(R.drawable.ic_passwdsafe_sync_dark);
                itsNoProvidersNavItem = nextProviderId;
            }
        }
    }

    /**
     * Update the look of a menu item icon to match static items
     */
    private static void updateMenuIcon(@NonNull MenuItem item)
    {
        var icon = item.getIcon();
        if (icon != null) {
            Drawable d = icon.mutate();
            d.setAlpha(138 /*54%*/);
            item.setIcon(d);
        }
    }
}
