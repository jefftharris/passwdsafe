/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;


import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.jefftharris.passwdsafe.file.PasswdNotes;
import com.jefftharris.passwdsafe.view.PasswdLocation;

import org.pwsafe.lib.file.PwsRecord;

import java.util.List;


/**
 * Fragment showing a password record
 */
public class PasswdSafeRecordFragment
        extends AbstractPasswdSafeLocationFragment
                        <PasswdSafeRecordFragment.Listener>
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
            extends AbstractPasswdSafeFileDataFragment.Listener
    {
        /** Edit the record */
        void editRecord(PasswdLocation location);

        /** Delete the record */
        void deleteRecord(PasswdLocation location, String title);

        /** Update the view for a record */
        void updateViewRecord(PasswdLocation location);

        /** Find similar records */
        void filterSimilarRecords(PasswdLocation location);
    }

    private boolean itsCanEdit = false;
    private boolean itsCanDelete = false;
    private boolean itsHasNotes = false;
    private String itsTitle;
    private TabLayout itsTabs;

    /** Last selected tab to restore across records */
    private static int itsLastSelectedTab = 0;

    /**
     * Create a new instance
     */
    @NonNull
    public static PasswdSafeRecordFragment newInstance(PasswdLocation location)
    {
        PasswdSafeRecordFragment frag = new PasswdSafeRecordFragment();
        frag.setArguments(createArgs(location));
        return frag;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        View root = inflater.inflate(R.layout.fragment_passwdsafe_record,
                                     container, false);

        final ViewPager viewPager = root.findViewById(R.id.viewpager);
        viewPager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener()
                {
                    @Override
                    public void onPageSelected(int position)
                    {
                        itsLastSelectedTab = position;
                    }
                });
        viewPager.setAdapter(new FragmentPagerAdapter(
                getChildFragmentManager(),
                FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT)
        {
            @NonNull
            @Override
            public Fragment getItem(int position)
            {
                switch (position) {
                case 0: {
                    break;
                }
                case 1: {
                    return PasswdSafeRecordPasswordFragment.newInstance(
                            getLocation());
                }
                case 2: {
                    return PasswdSafeRecordNotesFragment.newInstance(
                            getLocation());
                }
                }
                return PasswdSafeRecordBasicFragment.newInstance(
                        getLocation());
            }

            @Override
            public int getCount()
            {
                return 3;
            }

            @Override
            public CharSequence getPageTitle(int position)
            {
                return switch (position) {
                    case 0 -> getString(R.string.basic);
                    case 1 -> getString(R.string.password);
                    case 2 -> getString(R.string.notes);
                    default -> null;
                };
            }
        });
        viewPager.setCurrentItem(itsLastSelectedTab);

        itsTabs = root.findViewById(R.id.tabs);
        itsTabs.post(() -> {
            if (!isAdded()) {
                return;
            }
            itsTabs.setupWithViewPager(viewPager);
            updateNotesTab();
        });

        return root;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        getListener().updateViewRecord(getLocation());
        refresh();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu)
    {
        MenuItem item = menu.findItem(R.id.menu_edit);
        if (item != null) {
            item.setVisible(itsCanEdit);
        }

        item = menu.findItem(R.id.menu_delete);
        if (item != null) {
            item.setVisible(itsCanDelete);
        }

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_edit) {
            Listener listener = getListener();
            if (listener != null) {
                listener.editRecord(getLocation());
            }
            return true;
        } else if (itemId == R.id.menu_find_similar) {
            Listener listener = getListener();
            if (listener != null) {
                listener.filterSimilarRecords(getLocation());
            }
            return true;
        } else if (itemId == R.id.menu_delete) {
            Listener listener = getListener();
            if (listener != null) {
                listener.deleteRecord(getLocation(), itsTitle);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void doOnCreateOptionsMenu(Menu menu,
                                         @NonNull MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_passwdsafe_record, menu);
    }

    /**
     * Refresh the view
     */
    private void refresh()
    {
        useRecordInfo((RecordInfoUser<Void>)info -> {
            itsCanEdit = info.itsFileData.canEdit();
            itsTitle = info.itsFileData.getTitle(info.itsRec);
            boolean isProtected = info.itsFileData.isProtected(info.itsRec);
            List<PwsRecord> refs = info.itsPasswdRec.getRefsToRecord();
            boolean hasRefs = (refs != null) && !refs.isEmpty();
            itsCanDelete = itsCanEdit && !hasRefs && !isProtected;

            switch (info.itsPasswdRec.getType()) {
            case NORMAL:
            case ALIAS: {
                PasswdNotes notes = info.itsFileData.getNotes(info.itsRec,
                                                              getContext());
                itsHasNotes = !TextUtils.isEmpty(notes.getNotes());
                break;
            }
            case SHORTCUT: {
                itsHasNotes = false;
                break;
            }
            }
            return null;
        });
        updateNotesTab();
        requireActivity().invalidateOptionsMenu();
    }

    /**
     * Update the notes tab
     */
    private void updateNotesTab()
    {
        TabLayout.Tab tab =
                (itsTabs.getTabCount() >= 3) ? itsTabs.getTabAt(2) : null;
        if (tab != null) {
            String title = getString(R.string.notes);
            if (itsHasNotes) {
                title += " *";
            }
            tab.setText(title);
        }
    }
}
