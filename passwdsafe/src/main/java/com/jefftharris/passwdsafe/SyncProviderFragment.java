/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;
import com.jefftharris.passwdsafe.util.ProviderSyncTask;

/**
 * The SyncProviderFragment allows the user to choose a sync provider
 */
public class SyncProviderFragment extends ListFragment
        implements LoaderCallbacks<Cursor>
{
    /** Listener interface for the owning activity */
    public interface Listener
    {
        /** Show the files for a provider's URI */
        void showSyncProviderFiles(Uri uri);

        /** Does the activity have a menu */
        @SuppressWarnings("SameReturnValue")
        boolean activityHasMenu();
    }

    private SimpleCursorAdapter itsProviderAdapter;
    private Listener itsListener;
    private final ProviderSyncTask itsSyncTask = new ProviderSyncTask();

    @Override
    public void onAttach(Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        if (itsListener.activityHasMenu()) {
            setHasOptionsMenu(true);
        }
        View rootView = inflater.inflate(R.layout.fragment_sync_provider,
                                         container, false);

        View launchBtn = rootView.findViewById(android.R.id.empty);
        launchBtn.setOnClickListener(
                v -> PasswdSafeUtil.startMainActivity(
                        PasswdSafeUtil.SYNC_PACKAGE, getActivity()));

        GuiUtils.setVisible(rootView, checkProvider(getContext()));
        return rootView;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        itsProviderAdapter = new SimpleCursorAdapter(
               getActivity(), R.layout.sync_provider_list_item, null,
               new String[] { PasswdSafeContract.Providers.COL_ACCT,
                              PasswdSafeContract.Providers.COL_TYPE,
                              PasswdSafeContract.Providers.COL_TYPE},
               new int[] { android.R.id.text1, android.R.id.text2, R.id.icon },
               0);
        itsProviderAdapter.setViewBinder((view, cursor, colIdx) -> {
            int id = view.getId();
            switch (id) {
            case android.R.id.text1: {
                String displayName =
                        PasswdSafeContract.Providers.getDisplayName(cursor);
                TextView tv = (TextView)view;
                tv.setText(displayName);
                return true;
            }
            case android.R.id.text2:
            case R.id.icon: {
                try {
                    String typeStr = cursor.getString(colIdx);
                    ProviderType type = ProviderType.valueOf(typeStr);
                    switch (id) {
                    case android.R.id.text2: {
                        type.setText((TextView)view);
                        break;
                    }
                    case R.id.icon: {
                        type.setIcon((ImageView)view);
                        break;
                    }
                    }
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            }
            return false;
        });
        setListAdapter(itsProviderAdapter);

        LoaderManager.getInstance(this).initLoader(0, null, this);
    }


    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }


    @Override
    public void onStop()
    {
        super.onStop();
        itsSyncTask.cancel();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_sync_provider, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_sync: {
            itsSyncTask.start(null, getContext());
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
        Cursor cursor = (Cursor)getListAdapter().getItem(position);
        if ((cursor == null) || (itsListener == null)) {
            return;
        }

        Uri uri = ContentUris.withAppendedId(
                PasswdSafeContract.Providers.CONTENT_URI, id);
        itsListener.showSyncProviderFiles(uri);
    }


    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        Uri uri = PasswdSafeContract.Providers.CONTENT_URI;
        return new PasswdCursorLoader(
                 getContext(), uri, PasswdSafeContract.Providers.PROJECTION,
                 null, null, PasswdSafeContract.Providers.PROVIDER_SORT_ORDER);
    }


    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor)
    {
        if (PasswdCursorLoader.checkResult(loader, getActivity())) {
            itsProviderAdapter.changeCursor(cursor);
        }
    }


    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader)
    {
        onLoadFinished(loader, null);
    }

    /**
     * Check whether the sync provider is present
     */
    public static boolean checkProvider(Context ctx)
    {
        ContentResolver res = ctx.getContentResolver();
        String type = res.getType(PasswdSafeContract.Providers.CONTENT_URI);
        return (type != null);
    }
}
