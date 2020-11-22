/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Context;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StableIdKeyProvider;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.RecyclerView;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * A fragment for backup files
 */
public class BackupFilesFragment extends Fragment
{
    // TODO: cleanup menus
    // TODO: delete all
    // TODO: delete selected
    // TODO: restore
    // TODO: share? after open?
    // TODO: open read-only
    // TODO: Update db for no URL permission - clear URL but keep file, allow share/open
    // TODO: Update for no file - remove entry
    // TODO: max entries global and/or per file URL
    // TODO: label noting backups use temp files which can be cleared, etc.
    // TODO: translations

    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Update the view for the backup files */
        void updateViewBackupFiles();
    }

    private Listener itsListener;
    private BackupFilesModel itsBackupFiles;
    private BackupFilesAdapter itsBackupFilesAdapter;
    private SelectionTracker<Long> itsSelTracker;
    private ActionMode itsActionMode;
    private static final String TAG = "BackupFilesFragment";

    /**
     * Create a new instance
     */
    public static BackupFilesFragment newInstance()
    {
        return new BackupFilesFragment();
    }

    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        itsBackupFiles =
                new ViewModelProvider(this).get(BackupFilesModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        View rootView =
                inflater.inflate(R.layout.fragment_backup_files_list, container,
                                 false);

        RecyclerView files = rootView.findViewById(R.id.files);
        itsBackupFilesAdapter = new BackupFilesAdapter();
        files.setAdapter(itsBackupFilesAdapter);
        itsBackupFiles.getBackupFiles().observe(
                getViewLifecycleOwner(),
                backupFiles -> itsBackupFilesAdapter.submitList(backupFiles));

        itsSelTracker = new SelectionTracker.Builder<>(
                "backup-file-selection",
                files,
                new StableIdKeyProvider(files),
                itsBackupFilesAdapter.createItemLookup(files),
                StorageStrategy.createLongStorage())
                .withSelectionPredicate(
                        SelectionPredicates.createSelectSingleAnything())
                .withOnItemActivatedListener((item, e) -> {
                    Long key = item.getSelectionKey();
                    if (key != null) {
                        itsSelTracker.select(key);
                    }
                    return true;
                })
                .build();
        itsBackupFilesAdapter.setSelectionTracker(itsSelTracker);
        itsSelTracker.addObserver(new SelectionTracker.SelectionObserver<Long>()
        {
            @Override
            public void onSelectionChanged()
            {
                super.onSelectionChanged();
                onSelChanged(itsSelTracker.hasSelection());
            }
        });

        return rootView;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsListener.updateViewBackupFiles();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        itsSelTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);
        itsSelTracker.onRestoreInstanceState(savedInstanceState);
        onSelChanged(itsSelTracker.hasSelection());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_backup_files, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Handle a change in the selection state
     */
    private void onSelChanged(boolean hasSelection)
    {
        PasswdSafeUtil.dbginfo(TAG, "Selection: %b", hasSelection);
        if (itsSelTracker.hasSelection() && (itsActionMode == null)) {
            itsActionMode = requireActivity().startActionMode(
                    new ActionModeCallback());
        } else if (!itsSelTracker.hasSelection() && (itsActionMode != null)) {
            itsActionMode.finish();
        }
    }

    /**
     * Action mode callbacks
     */
    private class ActionModeCallback implements ActionMode.Callback
    {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.fragment_backup_file, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            itsSelTracker.clearSelection();
            itsActionMode = null;
        }
    }
}
