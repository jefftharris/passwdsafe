/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.jefftharris.passwdsafe.file.PasswdFileDataUser;
import com.jefftharris.passwdsafe.file.PasswdRecordFilter;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.util.Pair;
import com.jefftharris.passwdsafe.view.CopyField;
import com.jefftharris.passwdsafe.view.DialogActivity;
import com.jefftharris.passwdsafe.view.PasswdFileDataView;
import com.jefftharris.passwdsafe.view.PasswdLocation;
import com.jefftharris.passwdsafe.view.PasswdRecordListData;

import org.pwsafe.lib.file.PwsRecord;

import java.util.List;

public class LauncherRecordShortcuts extends DialogActivity
        implements PasswdSafeListFragment.Listener,
                   SharedPreferences.OnSharedPreferenceChangeListener
{
    /**
     * Intent flag to apply a filter to show records that have no aliases
     * referencing them
     */
    public static final String FILTER_NO_ALIAS = "filterNoAlias";

    /**
     * Intent flag to apply a filter to show records that have no shortcuts
     * referencing them
     */
    public static final String FILTER_NO_SHORTCUT = "filterNoShortcut";

    private enum Mode
    {
        SHORTCUT,
        CHOOSE_RECORD
    }

    private static final String TAG = "LauncherRecordShortcuts";

    private final PasswdFileDataView itsFileDataView = new PasswdFileDataView();
    private PasswdLocation itsLocation = new PasswdLocation();
    private Mode itsMode;
    private TextView itsFile;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = Preferences.getSharedPrefs(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        itsFile = findViewById(R.id.file);
        itsFileDataView.onAttach(this, prefs);

        Intent intent = getIntent();
        switch (String.valueOf(intent.getAction())) {
        case Intent.ACTION_CREATE_SHORTCUT: {
            setTitle(R.string.shortcut_record);
            itsMode = Mode.SHORTCUT;
            break;
        }
        case PasswdSafeApp.CHOOSE_RECORD_INTENT: {
            setTitle(R.string.choose_record);
            itsMode = Mode.CHOOSE_RECORD;
            GuiUtils.setVisible(itsFile, false);
            break;
        }
        default: {
            finish();
            return;
        }
        }

        int options = PasswdRecordFilter.OPTS_DEFAULT;
        if (intent.getBooleanExtra(FILTER_NO_ALIAS, false)) {
            options |= PasswdRecordFilter.OPTS_NO_ALIAS;
        }
        if (intent.getBooleanExtra(FILTER_NO_SHORTCUT, false)) {
            options |= PasswdRecordFilter.OPTS_NO_SHORTCUT;
        }
        if (options != PasswdRecordFilter.OPTS_DEFAULT) {
            itsFileDataView.setRecordFilter(new PasswdRecordFilter(null,
                                                                   options));
        }

        if (savedInstanceState == null) {
            FragmentManager fragMgr = getSupportFragmentManager();
            FragmentTransaction txn = fragMgr.beginTransaction();
            txn.replace(R.id.contents,
                        PasswdSafeListFragment.newInstance(itsLocation, true));
            txn.commit();
        }
    }

    @Override
    protected @LayoutRes int getViewLayoutId()
    {
        return R.layout.activity_launcher_record_shortcuts;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        itsFileDataView.clearFileData();
        String fileTitle = PasswdSafeFileDataFragment.useOpenFileData(
                fileData -> {
                    itsFileDataView.setFileData(fileData);
                    return fileData.getUri().getIdentifier(
                            LauncherRecordShortcuts.this, true);
                });
        if (fileTitle != null) {
            itsFile.setText(fileTitle);
        } else {
            itsFile.setText(R.string.no_records_open_file);
            GuiUtils.setVisible(findViewById(R.id.contents), false);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        itsFileDataView.clearFileData();
    }

    @Override
    public void onDestroy()
    {
        SharedPreferences prefs = Preferences.getSharedPrefs(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        itsFileDataView.onDetach();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs,
                                          @Nullable String key)
    {
        if (itsFileDataView.handleSharedPreferenceChanged(prefs, key)) {
            PasswdSafeFileDataFragment.useOpenFileData(
                    (PasswdFileDataUser<Void>)fileData -> {
                        itsFileDataView.refreshFileData(fileData);
                        return null;
                    });
        }
    }

    @Override
    public void copyField(CopyField field, String recUuid)
    {
        // Not supported
    }

    @Override
    public boolean isCopySupported()
    {
        return false;
    }

    @Override
    public void changeLocation(PasswdLocation location)
    {
        PasswdSafeUtil.dbginfo(TAG, "changeLocation: %s", location);
        if (location.isRecord()) {
            selectRecord(location.getRecord());
        } else if (!itsLocation.equals(location)) {
            FragmentManager fragMgr = getSupportFragmentManager();
            FragmentTransaction txn = fragMgr.beginTransaction();
            txn.setTransition(FragmentTransaction.TRANSIT_NONE);
            txn.replace(R.id.contents,
                        PasswdSafeListFragment.newInstance(location, true));
            txn.addToBackStack(null);
            txn.commit();
        }
    }

    @Override
    public List<PasswdRecordListData> getBackgroundRecordItems(
            boolean incRecords, boolean incGroups)
    {
        return itsFileDataView.getRecords(incRecords, incGroups);
    }

    @Override
    public void updateViewList(PasswdLocation location)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateViewList: %s", location);
        itsLocation = location;
        itsFileDataView.setCurrGroups(itsLocation.getGroups());

        FragmentManager fragMgr = getSupportFragmentManager();
        Fragment contentsFrag = fragMgr.findFragmentById(R.id.contents);
        if (contentsFrag instanceof PasswdSafeListFragment) {
            ((PasswdSafeListFragment)contentsFrag).updateLocationView(
                    itsLocation, PasswdSafeListFragment.Mode.ALL);
        }
    }

    @Override
    public boolean activityHasMenu()
    {
        return false;
    }

    @Override
    public void showRecordPreferences()
    {
    }

    @Override
    public boolean isNavDrawerClosed()
    {
        return true;
    }

    /**
     * Select the given record and return a result
     */
    private void selectRecord(final String uuid)
    {
        switch (itsMode) {
        case SHORTCUT: {
            Pair<Uri, String> rc = PasswdSafeFileDataFragment.useOpenFileData(
                    fileData -> {
                        PwsRecord rec = fileData.getRecord(uuid);
                        String title = fileData.getTitle(rec);
                        return new Pair<>(fileData.getUri().getUri(), title);
                    });
            if (rc != null) {
                Intent intent = LauncherFileShortcuts.createShortcutIntent(
                        "launcher-record", rc.second, rc.first, uuid, this);
                setResult(RESULT_OK, intent);
            }
            break;
        }
        case CHOOSE_RECORD: {
            Intent intent = new Intent();
            intent.putExtra(PasswdSafeApp.RESULT_DATA_UUID, uuid);
            setResult(RESULT_OK, intent);
            break;
        }
        }
        finish();
    }
}
