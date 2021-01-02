/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;
import com.jefftharris.passwdsafe.util.ProviderSyncTask;

/**
 * The SyncProviderFilesFragment shows the list of files for a provider
 */
public class SyncProviderFilesFragment extends ListFragment
{
    /** Listener interface for the owning activity */
    public interface Listener
    {
        /** Open a file */
        void openFile(Uri uri, String fileName);

        /** Create a new file */
        void createNewFile(Uri dirUri);

        /** Update the view for a list of sync files */
        void updateViewSyncFiles(Uri syncFilesUri);
    }

    private static final String TAG = "SyncProviderFilesFrag";
    private static final int LOADER_TITLE = 0;
    private static final int LOADER_FILES = 1;

    private Uri itsProviderUri;
    private Uri itsFilesUri;
    private SimpleCursorAdapter itsProviderAdapter;
    private Listener itsListener;
    private final ProviderSyncTask itsSyncTask = new ProviderSyncTask();


    /** Create a new instance of the fragment */
    public static SyncProviderFilesFragment newInstance(Uri providerUri)
    {
        SyncProviderFilesFragment frag = new SyncProviderFilesFragment();
        Bundle args = new Bundle();
        args.putString("providerUri", providerUri.toString());
        frag.setArguments(args);
        return frag;
    }


    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        itsProviderUri = Uri.parse(args.getString("providerUri"));
        itsFilesUri = itsProviderUri.buildUpon().appendPath(
                PasswdSafeContract.Files.TABLE).build();
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.ListFragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_sync_provider_files,
                                container, false);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        itsProviderAdapter = new SimpleCursorAdapter(
               requireContext(), R.layout.sync_provider_file_list_item, null,
               new String[] { PasswdSafeContract.Files.COL_TITLE,
                              PasswdSafeContract.Files.COL_MOD_DATE,
                              PasswdSafeContract.Files.COL_FOLDER },
               new int[] { R.id.title, R.id.mod_date, R.id.folder },
               0);

        itsProviderAdapter.setViewBinder((view, cursor, colIdx) -> {
            switch (colIdx) {
            case PasswdSafeContract.Files.PROJECTION_IDX_MOD_DATE: {
                long modDate = cursor.getLong(colIdx);
                TextView tv = (TextView)view;
                tv.setText(Utils.formatDate(modDate, getActivity()));
                return true;
            }
            case PasswdSafeContract.Files.PROJECTION_IDX_FOLDER: {
                String folder = cursor.getString(colIdx);
                if (TextUtils.isEmpty(folder)) {
                    view.setVisibility(View.GONE);
                } else {
                    view.setVisibility(View.VISIBLE);
                    ((TextView)view).setText(folder);
                }
                return true;
            }
            }
            return false;
        });

        setListAdapter(itsProviderAdapter);

        LoaderManager lm = LoaderManager.getInstance(this);
        lm.initLoader(LOADER_TITLE, null, new LoaderCallbacks<Cursor>()
            {
                @NonNull
                @Override
                public Loader<Cursor> onCreateLoader(int id, Bundle args)
                {
                    return new PasswdCursorLoader(
                            requireContext(), itsProviderUri,
                            PasswdSafeContract.Providers.PROJECTION,
                            null, null, null);
                }

                @Override
                public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                           Cursor cursor)
                {
                    if (!PasswdCursorLoader.checkResult(loader,
                                                        getActivity())) {
                        return;
                    }
                    View view = getView();
                    if (view == null) {
                        return;
                    }
                    String str;
                    ImageView icon = view.findViewById(R.id.icon);
                    if ((cursor != null) && cursor.moveToFirst()) {
                        str = PasswdSafeContract.Providers.getDisplayName(cursor);
                        String typeStr = cursor.getString(
                            PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
                        try {
                            ProviderType type = ProviderType.valueOf(typeStr);
                            type.setIcon(icon);
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Unknown provider type", e);
                        }
                    } else {
                        str = getString(R.string.sync_account_deleted);
                    }
                    TextView tv = view.findViewById(R.id.title);
                    tv.setText(str);
                }

                @Override
                public void onLoaderReset(@NonNull Loader<Cursor> loader)
                {
                }
            });

        lm.initLoader(LOADER_FILES, null, new LoaderCallbacks<Cursor>()
            {
                 @NonNull
                 @Override
                 public Loader<Cursor> onCreateLoader(int id, Bundle args)
                 {
                     return new PasswdCursorLoader(
                             requireContext(), itsFilesUri,
                             PasswdSafeContract.Files.PROJECTION,
                             PasswdSafeContract.Files.NOT_DELETED_SELECTION,
                             null, PasswdSafeContract.Files.TITLE_SORT_ORDER);
                 }

                 @Override
                 public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                            Cursor cursor)
                 {
                     if (PasswdCursorLoader.checkResult(loader,
                                                        getActivity())) {
                         itsProviderAdapter.changeCursor(cursor);
                     }
                 }

                 @Override
                 public void onLoaderReset(@NonNull Loader<Cursor> loader)
                 {
                     onLoadFinished(loader, null);
                 }
            });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsListener.updateViewSyncFiles(itsProviderUri);
    }

    @Override
    public void onStop()
    {
        super.onStop();
        itsSyncTask.cancel();
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu, android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_sync_provider_files, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_sync_files) {
            itsSyncTask.start(itsProviderUri, requireContext());
            return true;
        } else if (itemId == R.id.menu_file_new) {
            itsListener.createNewFile(itsFilesUri);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.ListFragment#onListItemClick(android.widget.ListView, android.view.View, int, long)
     */
    @Override
    public void onListItemClick(@NonNull ListView l,
                                @NonNull View v,
                                int position,
                                long id)
    {
        Cursor cursor = (Cursor)requireListAdapter().getItem(position);
        if ((cursor == null) || (itsListener == null)) {
            return;
        }

        Uri uri = ContentUris.withAppendedId(itsFilesUri, id);
        PasswdSafeUtil.dbginfo(TAG, "Open provider uri %s", uri);
        itsListener.openFile(
                uri,
                cursor.getString(PasswdSafeContract.Files.PROJECTION_IDX_TITLE));
    }
}
