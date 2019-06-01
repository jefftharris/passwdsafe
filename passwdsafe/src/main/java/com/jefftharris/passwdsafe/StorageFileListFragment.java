/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;

import java.util.List;

/**
 *  The StorageFileListFragment fragment allows the user to open files using
 *  the storage access framework on Kitkat and higher
 */
@TargetApi(19)
public final class StorageFileListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
                   View.OnClickListener,
                   StorageFileListOps
{
    /** Listener interface for the owning activity */
    public interface Listener
    {
        /** Open a file */
        void openFile(Uri uri, String fileName);

        /** Does the activity have a menu */
        boolean activityHasMenu();

        /** Update the view for a list of files */
        void updateViewFiles();
    }

    private static final String TAG = "StorageFileListFragment";

    private static final int OPEN_RC = 1;

    private static final int LOADER_FILES = 0;

    private Listener itsListener;
    private RecentFilesDb itsRecentFilesDb;
    private ManagedRef<RecentFilesDb> itsRecentFilesDbRef;
    private View itsEmptyText;
    private StorageFileListAdapter itsFilesAdapter;
    private int itsFileIcon;

    @Override
    public void onAttach(Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;

        Resources.Theme theme = ctx.getTheme();
        TypedValue attr = new TypedValue();
        theme.resolveAttribute(R.attr.drawablePasswdsafe, attr, true);
        itsFileIcon = attr.resourceId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        itsRecentFilesDb = new RecentFilesDb(requireActivity());
        itsRecentFilesDbRef = new ManagedRef<>(itsRecentFilesDb);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        if (itsListener.activityHasMenu()) {
            setHasOptionsMenu(true);
        }

        View rootView = inflater.inflate(R.layout.fragment_storage_file_list,
                                         container, false);

        itsEmptyText = rootView.findViewById(R.id.empty);
        GuiUtils.setVisible(itsEmptyText, false);

        itsFilesAdapter = new StorageFileListAdapter(this);
        RecyclerView files = rootView.findViewById(R.id.files);
        files.setAdapter(itsFilesAdapter);

        ItemTouchHelper.SimpleCallback swipeCb =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT |
                                                      ItemTouchHelper.RIGHT)
        {
            @Override
            public boolean onMove(RecyclerView recyclerView,
                                  RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target)
            {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder,
                                 int direction)
            {
                removeFile(((StorageFileListHolder)viewHolder).getUri());
            }
        };
        ItemTouchHelper swipeHelper = new ItemTouchHelper(swipeCb);
        swipeHelper.attachToRecyclerView(files);

        View fab = rootView.findViewById(R.id.fab);
        fab.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        LoaderManager lm = getLoaderManager();
        lm.initLoader(LOADER_FILES, null, this);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        LoaderManager lm = getLoaderManager();
        lm.restartLoader(LOADER_FILES, null, this);
        itsListener.updateViewFiles();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        itsRecentFilesDb = null;
        itsRecentFilesDbRef.clear();
        itsRecentFilesDbRef = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case OPEN_RC: {
            PasswdSafeUtil.dbginfo(TAG, "onActivityResult open %d: %s",
                                   resultCode, data);
            if ((resultCode == Activity.RESULT_OK) && (data != null)) {
                openUri(data);
            }
            break;
        }
        default: {
            super.onActivityResult(requestCode, resultCode, data);
            break;
        }
        }
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
        case R.id.fab: {
            startOpenFile();
            break;
        }
        }
    }

    @Override
    public void storageFileClicked(String uristr, String title)
    {
        Uri uri = Uri.parse(uristr);
        openUri(uri, title);
    }

    @Override
    public int getStorageFileIcon()
    {
        return itsFileIcon;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_storage_file_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_file_open: {
            startOpenFile();
            return true;
        }
        case R.id.menu_file_new: {
            startActivity(new Intent(PasswdSafeUtil.NEW_INTENT));
            return true;
        }
        case R.id.menu_clear_recent: {
            try {
                Context ctx = getContext();
                if (ctx == null) {
                    return true;
                }
                List<Uri> recentUris = itsRecentFilesDb.clear();
                if (isCheckPermissions()) {
                    ContentResolver cr = ctx.getContentResolver();
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    for (Uri uri : recentUris) {
                        ApiCompat.releasePersistableUriPermission(cr, uri,
                                                                  flags);
                    }

                    List<Uri> permUris =
                            ApiCompat.getPersistedUriPermissions(cr);
                    for (Uri permUri : permUris) {
                        ApiCompat.releasePersistableUriPermission(cr, permUri,
                                                                  flags);
                    }
                }

                getLoaderManager().restartLoader(LOADER_FILES, null, this);
            } catch (Exception e) {
                PasswdSafeUtil.showFatalMsg(e, "Clear recent error",
                                            getActivity());
            }
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }


    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle)
    {
        return new FileLoader(itsRecentFilesDbRef, requireContext());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> cursorLoader,
                               Cursor cursor)
    {
        GuiUtils.setVisible(itsEmptyText,
                            (cursor == null) || (cursor.getCount() == 0));
        itsFilesAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> cursorLoader)
    {
        onLoadFinished(cursorLoader, null);
    }

    /** Start the intent to open a file */
    private void startOpenFile()
    {
        Intent intent = new Intent(
                DocumentsContractCompat.INTENT_ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("application/*");

        startActivityForResult(intent, OPEN_RC);
    }


    /** Open a password file URI from an intent */
    private void openUri(Intent openIntent)
    {
        Uri uri = openIntent.getData();
        int flags = openIntent.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        Context ctx = requireContext();
        String title = RecentFilesDb.getSafDisplayName(uri, ctx);
        if (isCheckPermissions()) {
            RecentFilesDb.updateOpenedSafFile(uri, flags, ctx);
        } else {
            if (title == null) {
                title = openIntent.getStringExtra("__test_display_name");
            }
        }
        if (title != null) {
            openUri(uri, title);
        }
    }


    /** Open a password file URI */
    private void openUri(Uri uri, String title)
    {
        PasswdSafeUtil.dbginfo(TAG, "openUri %s: %s", uri, title);

        try {
            itsRecentFilesDb.insertOrUpdateFile(uri, title);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting recent file", e);
        }

        itsListener.openFile(uri, title);
    }

    /**
     * Remove a recent file entry
     */
    private void removeFile(String uristr)
    {
        try {
            Uri uri = Uri.parse(uristr);
            itsRecentFilesDb.removeUri(uri);

            if (isCheckPermissions()) {
                ContentResolver cr = requireContext().getContentResolver();
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                ApiCompat.releasePersistableUriPermission(cr, uri, flags);
            }

            getLoaderManager().restartLoader(LOADER_FILES, null, this);
        } catch (Exception e) {
            PasswdSafeUtil.showFatalMsg(e, "Remove recent file error",
                                        requireActivity());
        }
    }

    /**
     *  Whether permissions should be checked
     */
    private static boolean isCheckPermissions()
    {
        return !PasswdSafeUtil.isTesting();
    }

    /**
     * Background file loader
     */
    private static final class FileLoader extends AsyncTaskLoader<Cursor>
    {
        private final ManagedRef<RecentFilesDb> itsRecentFilesDb;

        /**
         * Constructor
         */
        public FileLoader(ManagedRef<RecentFilesDb> recentFilesDb,
                          Context ctx)
        {
            super(ctx.getApplicationContext());
            itsRecentFilesDb = recentFilesDb;
        }

        /** Handle when the loader is reset */
        @Override
        protected void onReset()
        {
            super.onReset();
            onStopLoading();
        }

        /** Handle when the loader is started */
        @Override
        protected void onStartLoading()
        {
            forceLoad();
        }

        /** Handle when the loader is stopped */
        @Override
        protected void onStopLoading()
        {
            cancelLoad();
        }

        /** Load the files in the background */
        @Override
        public Cursor loadInBackground()
        {
            PasswdSafeUtil.dbginfo(TAG, "loadInBackground");

            RecentFilesDb recentFilesDb = itsRecentFilesDb.get();
            if (recentFilesDb == null) {
                return null;
            }

            if (isCheckPermissions()) {
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                ContentResolver cr = getContext().getContentResolver();
                List<Uri> permUris = ApiCompat.getPersistedUriPermissions(cr);
                for (Uri permUri : permUris) {
                    PasswdSafeUtil.dbginfo(TAG, "Checking persist perm %s",
                                           permUri);
                    Cursor cursor = null;
                    try {
                        cursor = cr.query(permUri, null, null, null, null);
                        if ((cursor != null) && (cursor.moveToFirst())) {
                            ApiCompat.takePersistableUriPermission(cr, permUri,
                                                                   flags);
                        } else {
                            ApiCompat.releasePersistableUriPermission(cr,
                                                                      permUri,
                                                                      flags);
                            recentFilesDb.removeUri(permUri);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "File remove error: " + permUri, e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            }

            try {
                return recentFilesDb.queryFiles();
            } catch (Exception e) {
                Log.e(TAG, "Files load error", e);
            }
            return null;
        }
    }
}
