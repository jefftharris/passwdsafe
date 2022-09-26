/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;

import java.util.Locale;

/**
 * Fragment to show the sync logs
 */
public class SyncLogsFragment extends ListFragment
{
    private static final int LOADER_LOGS = 0;
    private static final String STATE_SHOW_ALL = "showAll";

    private boolean itsIsShowAll = false;
    private SimpleCursorAdapter itsLogsAdapter;
    private LoaderCallbacks<Cursor> itsLogsCbs;
    private int itsSelItemPos = -1;


    /* (non-Javadoc)
     * @see android.support.v4.app.ListFragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);

        itsIsShowAll = (savedInstanceState != null) &&
                       savedInstanceState.getBoolean(STATE_SHOW_ALL, false);

        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Override
    public void onViewCreated(@NonNull View fragView, Bundle savedInstanceState)
    {
        super.onViewCreated(fragView, savedInstanceState);

        itsLogsAdapter = new SimpleCursorAdapter(
                requireContext(), R.layout.listview_sync_log_item, null,
                new String[] { PasswdSafeContract.SyncLogs.COL_START,
                               PasswdSafeContract.SyncLogs.COL_LOG,
                               PasswdSafeContract.SyncLogs.COL_STACK},
                new int[] { R.id.title, R.id.log, R.id.stack }, 0);

        itsLogsAdapter.setViewBinder((view, cursor, colIdx) -> {
            switch (colIdx) {
            case PasswdSafeContract.SyncLogs.PROJECTION_IDX_START: {
                long start = cursor.getLong(
                        PasswdSafeContract.SyncLogs.PROJECTION_IDX_START);
                long end = cursor.getLong(
                        PasswdSafeContract.SyncLogs.PROJECTION_IDX_END);
                String acct = cursor.getString(
                        PasswdSafeContract.SyncLogs.PROJECTION_IDX_ACCT);
                TextView tv = (TextView)view;

                String str = String.format(
                        Locale.US, "%s (%ds) - %s",
                        Utils.formatDate(start, getActivity()),
                        (end - start) / 1000, acct);
                tv.setText(str);
                return true;
            }
            case PasswdSafeContract.SyncLogs.PROJECTION_IDX_LOG: {
                int flags = cursor.getInt(
                        PasswdSafeContract.SyncLogs.PROJECTION_IDX_FLAGS);
                String log = cursor.getString(
                        PasswdSafeContract.SyncLogs.PROJECTION_IDX_LOG);

                StringBuilder str = new StringBuilder();
                if ((flags &
                     PasswdSafeContract.SyncLogs.FLAGS_IS_MANUAL) != 0) {
                    str.append(getString(R.string.manual));
                } else {
                    str.append(getString(R.string.automatic));
                }

                str.append(", ");
                if ((flags &
                     PasswdSafeContract.SyncLogs.FLAGS_IS_NOT_CONNECTED) != 0) {
                    str.append(getString(R.string.network_not_connected));
                } else {
                    str.append(getString(R.string.network_connected));
                }

                if (log.length() != 0) {
                    str.append("\n");
                }
                str.append(log);
                TextView tv = (TextView)view;
                tv.setText(str.toString());
                return true;
            }
            case PasswdSafeContract.SyncLogs.PROJECTION_IDX_STACK: {
                boolean checked =
                        getListView().isItemChecked(cursor.getPosition());
                String stack;
                if (checked) {
                    stack = cursor.getString(
                            PasswdSafeContract.SyncLogs.PROJECTION_IDX_STACK);
                } else {
                    stack = null;
                }

                GuiUtils.setVisible(view, checked && !TextUtils.isEmpty(stack));
                ((TextView)view).setText(stack);
                return true;
            }
            }
            return false;
        });

        setListAdapter(itsLogsAdapter);
        setEmptyText(getString(R.string.no_logs));
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        itsLogsCbs = new LoaderCallbacks<>()
        {
            @NonNull
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args)
            {
                String selection = itsIsShowAll ? null :
                    PasswdSafeContract.SyncLogs.DEFAULT_SELECTION;
                return new PasswdCursorLoader(
                        requireContext(),
                        PasswdSafeContract.SyncLogs.CONTENT_URI,
                        PasswdSafeContract.SyncLogs.PROJECTION,
                        selection, null,
                        PasswdSafeContract.SyncLogs.START_SORT_ORDER);
            }

            @Override
            public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                       Cursor cursor)
            {
                if (PasswdCursorLoader.checkResult(loader, getActivity())) {
                    itsLogsAdapter.changeCursor(cursor);
                }
            }

            @Override
            public void onLoaderReset(@NonNull Loader<Cursor> loader)
            {
                onLoadFinished(loader, null);
            }
        };
        LoaderManager.getInstance(this).initLoader(LOADER_LOGS,
                                                   null, itsLogsCbs);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOW_ALL, itsIsShowAll);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onCreateOptionsMenu(android.view.Menu, android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_sync_logs, menu);
        super.onCreateOptionsMenu(menu, inflater);

        MenuItem item = menu.findItem(R.id.menu_show_all);
        item.setChecked(itsIsShowAll);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.Fragment#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menu_show_all) {
            itsIsShowAll = !item.isChecked();
            item.setChecked(itsIsShowAll);
            LoaderManager.getInstance(this).restartLoader(LOADER_LOGS, null,
                                                          itsLogsCbs);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView l, @NonNull View v, int pos, long id)
    {
        if (l.isItemChecked(pos)) {
            if (pos == itsSelItemPos) {
                l.setItemChecked(pos, false);
                itsSelItemPos = -1;
            } else {
                itsSelItemPos = pos;
            }
        }
        l.invalidateViews();
    }
}
