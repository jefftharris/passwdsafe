/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.CustomOnBackCallback;

public class LauncherFileShortcuts extends AppCompatActivity
        implements FileListFragment.Listener,
                   StorageFileListFragment.Listener,
                   SyncProviderFragment.Listener,
                   SyncProviderFilesFragment.Listener
{
    public static final String EXTRA_IS_DEFAULT_FILE = "isDefFile";

    private static final String TAG = "LauncherFileShortcuts";

    private Boolean itsIsStorageFrag = null;
    private boolean itsIsDefaultFile = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        PasswdSafeApp.setupDialogTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher_file_shortcuts);

        FragmentManager fragMgr = getSupportFragmentManager();
        FragmentTransaction txn = fragMgr.beginTransaction();
        txn.replace(R.id.sync, new SyncProviderFragment());
        txn.commit();

        if (savedInstanceState == null) {
            setFileChooseFrag();
        }

        Intent intent = getIntent();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            finish();
            return;
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackCallback());

       itsIsDefaultFile = intent.getBooleanExtra(EXTRA_IS_DEFAULT_FILE, false);
        if (itsIsDefaultFile) {
            setTitle(R.string.default_file_to_open);
        } else {
            setTitle(R.string.shortcut_file);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setFileChooseFrag();
    }

    @Override
    public void openFile(Uri uri, String fileName)
    {
        if (itsIsDefaultFile || (uri != null)) {
            Intent intent;
            if (uri != null) {
                intent = createShortcutIntent("launcher-file", fileName, uri,
                                              null, this);
            } else {
                intent = new Intent();
            }

            setResult(RESULT_OK, intent);
        }

        finish();
    }

    @Override
    public void createNewFile(Uri dirUri)
    {
    }

    @Override
    public void showSyncProviderFiles(Uri uri)
    {
        FragmentManager fragMgr = getSupportFragmentManager();
        Fragment syncFrag = fragMgr.findFragmentById(R.id.sync);

        SyncProviderFilesFragment syncFilesFrag =
                SyncProviderFilesFragment.newInstance(uri);

        FragmentTransaction txn = fragMgr.beginTransaction();
        if (syncFrag != null) {
            txn.remove(syncFrag);
        }
        txn.replace(R.id.files, syncFilesFrag);
        txn.addToBackStack(null);
        txn.commit();
    }

    @Override
    public boolean activityHasMenu()
    {
        return false;
    }

    @Override
    public boolean activityHasNoneItem()
    {
        return itsIsDefaultFile;
    }

    @Override
    public boolean appHasFilePermission()
    {
        return false;
    }

    @Override
    public void updateViewFiles()
    {
    }

    @Override
    public void updateViewSyncFiles(Uri syncFilesUri)
    {
    }

    /**
     * Create a intent for a launcher shortcut to open a file and optionally
     * a record
     */
    @NonNull
    public static Intent createShortcutIntent(@NonNull String id,
                                              @NonNull String label,
                                              @NonNull Uri uri,
                                              @Nullable String uuid,
                                              @NonNull Context ctx)
    {
        ShortcutInfoCompat info =
                new ShortcutInfoCompat.Builder(ctx, id)
                        .setShortLabel(label)
                        .setIcon(IconCompat.createWithResource(
                                ctx,
                                R.mipmap.ic_launcher_passwdsafe))
                        .setIntent(PasswdSafeUtil.createOpenIntent(uri, uuid))
                        .build();
        return ShortcutManagerCompat.createShortcutResultIntent(ctx, info);
    }

    /**
     * Set the file chooser fragment
     */
    private void setFileChooseFrag()
    {
        SharedPreferences prefs = Preferences.getSharedPrefs(this);
        boolean storageFrag =
                ((ApiCompat.SDK_VERSION >= ApiCompat.SDK_KITKAT) &&
                 !Preferences.getFileLegacyFileChooserPref(prefs));
        if ((itsIsStorageFrag == null) || (itsIsStorageFrag != storageFrag)) {
            PasswdSafeUtil.dbginfo(TAG, "setFileChooseFrag storage %b",
                                   storageFrag);
            Fragment frag;
            if (storageFrag) {
                frag = new StorageFileListFragment();
            } else {
                frag = new FileListFragment();
            }
            FragmentManager fragMgr = getSupportFragmentManager();
            FragmentTransaction txn = fragMgr.beginTransaction();
            txn.replace(R.id.files, frag);
            txn.commit();
            itsIsStorageFrag = storageFrag;
        }
    }

    /**
     * Custom on-back handler
     */
    private final class OnBackCallback extends CustomOnBackCallback
    {
        @Override
        @Nullable
        protected Activity performCustomOnBack()
        {
            PasswdSafeUtil.dbginfo(TAG, "performCustomOnBack");

            FragmentManager mgr = getSupportFragmentManager();
            Fragment frag = mgr.findFragmentById(R.id.files);
            boolean handled = (frag instanceof FileListFragment) &&
                              frag.isVisible() &&
                              ((FileListFragment) frag).doBackPressed();

            return handled ? null : LauncherFileShortcuts.this;
        }
    }
}
