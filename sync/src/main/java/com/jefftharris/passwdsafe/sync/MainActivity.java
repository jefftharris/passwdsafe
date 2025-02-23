/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.jefftharris.passwdsafe.lib.AboutUtils;
import com.jefftharris.passwdsafe.lib.DynamicPermissionMgr;
import com.jefftharris.passwdsafe.lib.GenericProviderNaming;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;
import com.jefftharris.passwdsafe.sync.dropbox.DropboxFilesActivity;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncedFilesActivity;
import com.jefftharris.passwdsafe.sync.lib.AccountSyncFreqUpdateTask;
import com.jefftharris.passwdsafe.sync.lib.AccountUpdateTask;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.Preferences;
import com.jefftharris.passwdsafe.sync.lib.Provider;
import com.jefftharris.passwdsafe.sync.lib.RemoveAccountTask;
import com.jefftharris.passwdsafe.sync.lib.SyncResults;
import com.jefftharris.passwdsafe.sync.onedrive.OnedriveFilesActivity;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements LoaderCallbacks<Cursor>,
                   MainActivityProviderOps,
                   SyncUpdateHandler,
                   AccountUpdateTask.Listener,
                   View.OnClickListener
{
    private static final String TAG = "MainActivity";

    private static final int LOADER_PROVIDERS = 0;

    private static final int MENU_BIT_HAS_GDRIVE = 0;
    private static final int MENU_BIT_HAS_DROPBOX = 1;
    private static final int MENU_BIT_HAS_BOX = 2;
    private static final int MENU_BIT_HAS_ONEDRIVE = 3;

    private DynamicPermissionMgr itsPermissionMgr;
    private MainActivityProviderAdapter itsAccountsAdapter;
    private final BitSet itsMenuOptions = new BitSet();
    private GDriveState itsGDriveState = GDriveState.OK;
    private final SparseArray<Uri> itsAccountLinkUris = new SparseArray<>();
    private boolean itsDropboxPendingAcctLink = false;
    private boolean itsOnedrivePendingAcctLink = false;
    private NewAccountTask<?> itsNewAccountTask = null;
    private final List<AccountUpdateTask> itsUpdateTasks = new ArrayList<>();
    private boolean itsIsRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");

        itsPermissionMgr = new DynamicPermissionMgr(
                this, ActivityRequest.PERMISSIONS, ActivityRequest.APP_SETTINGS,
                PasswdSafeUtil.SYNC_PACKAGE, R.id.reload, R.id.app_settings);
        itsPermissionMgr.addPerm(DynamicPermissionMgr.PERM_POST_NOTIFICATIONS,
                                 false);
        View noPermGroup = findViewById(R.id.no_permission_group);
        GuiUtils.setVisible(noPermGroup, !itsPermissionMgr.checkPerms());

        // Check the state of Google Play services
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int rc = googleApi.isGooglePlayServicesAvailable(this);
        if (rc != ConnectionResult.SUCCESS) {
            googleApi.showErrorDialogFragment(
                    this, rc, ActivityRequest.GDRIVE_PLAY_SERVICES_ERROR);
        }

        itsAccountsAdapter = new MainActivityProviderAdapter(this);
        RecyclerView accounts = findViewById(R.id.accounts);
        accounts.setAdapter(itsAccountsAdapter);
        accounts.setNestedScrollingEnabled(false);

        View passwdSafe = findViewById(R.id.passwd_safe);
        passwdSafe.setOnClickListener(this);

        LoaderManager lm = LoaderManager.getInstance(this);
        lm.initLoader(LOADER_PROVIDERS, null, this);

        if (BuildConfig.DEBUG) {
            CheckBox success = findViewById(R.id.force_sync_failure);
            GuiUtils.setVisible(success, true);
            success.setChecked(SyncApp.get(this).isForceSyncFailure());
            success.setOnCheckedChangeListener(
                    (buttonView, isChecked) ->
                            SyncApp.get(this).setIsForceSyncFailure(isChecked));
        }

        SharedPreferences prefs = Preferences.getSharedPrefs(this);
        Preferences.clearShowGDriveFileMigrationPref(prefs);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onStart()
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        if (AboutUtils.checkShowNotes(this)) {
            showAbout();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        itsIsRunning = true;
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onPause()
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        itsIsRunning = false;
        for (AccountUpdateTask task: new ArrayList<>(itsUpdateTasks)) {
            task.cancelTask();
        }
        itsUpdateTasks.clear();
        SyncApp.get(this).setSyncUpdateHandler(null);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onResumeFragments()
     */
    @Override
    protected void onResumeFragments()
    {
        super.onResumeFragments();
        if (itsDropboxPendingAcctLink) {
            itsDropboxPendingAcctLink = false;
            itsNewAccountTask = getDbxProvider().finishAccountLink(
                    this, ActivityRequest.DROPBOX_LINK, Activity.RESULT_OK,
                    null, getAccountLinkUri(ActivityRequest.DROPBOX_LINK));
        }

        if (itsOnedrivePendingAcctLink) {
            itsOnedrivePendingAcctLink = false;
            itsNewAccountTask = getOnedriveProvider().finishAccountLink(
                    this, ActivityRequest.ONEDRIVE_LINK, Activity.RESULT_OK,
                    null, getAccountLinkUri(ActivityRequest.ONEDRIVE_LINK));
        }

        if (itsNewAccountTask != null) {
            itsNewAccountTask.startTask(this, this);
            itsNewAccountTask = null;
        }
        reloadProviders();
        SyncApp.get(this).setSyncUpdateHandler(this);

        for (ProviderType type: ProviderType.values()) {
            ProviderFactory.getProvider(type, this).onResume();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case ActivityRequest.BOX_AUTH: {
            itsNewAccountTask = getBoxProvider().finishAccountLink(
                    this, requestCode, resultCode, data,
                    getAccountLinkUri(ActivityRequest.BOX_AUTH));
            break;
        }
        case ActivityRequest.GDRIVE_PLAY_LINK:
        case ActivityRequest.GDRIVE_PLAY_LINK_PERMS: {
            itsNewAccountTask = getGDrivePlayProvider().finishAccountLink(
                    this, requestCode, resultCode, data,
                    getAccountLinkUri(requestCode));
            break;
        }
        case ActivityRequest.GDRIVE_PLAY_SERVICES_ERROR: {
            break;
        }
        default: {
            if (!itsPermissionMgr.handleActivityResult(requestCode)) {
                super.onActivityResult(requestCode, resultCode, data);
            }
            break;
        }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        if (!itsPermissionMgr.handlePermissionsResult(
                requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions,
                                             grantResults);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.activity_main, menu);

        GenericProviderNaming.setAddProviderMenuItem(menu, R.id.menu_add_box,
                                                     ProviderType.BOX);
        GenericProviderNaming.setAddProviderMenuItem(menu,
                                                     R.id.menu_add_dropbox,
                                                     ProviderType.DROPBOX);
        GenericProviderNaming.setAddProviderMenuItem(menu,
                                                     R.id.menu_add_google_drive,
                                                     ProviderType.GDRIVE);
        GenericProviderNaming.setAddProviderMenuItem(menu,
                                                     R.id.menu_add_onedrive,
                                                     ProviderType.ONEDRIVE);

        return true;
    }

    /** Prepare the Screen's standard options menu to be displayed. */
    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu)
    {
        MenuItem item = menu.findItem(R.id.menu_add);
        item.setEnabled(itsPermissionMgr.hasRequiredPerms());

        setProviderMenuEnabled(menu, R.id.menu_add_box,
                               MENU_BIT_HAS_BOX);
        setProviderMenuEnabled(menu, R.id.menu_add_dropbox,
                               MENU_BIT_HAS_DROPBOX);
        setProviderMenuEnabled(menu, R.id.menu_add_google_drive,
                               MENU_BIT_HAS_GDRIVE);
        setProviderMenuEnabled(menu, R.id.menu_add_onedrive,
                               MENU_BIT_HAS_ONEDRIVE);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_about) {
            showAbout();
            return true;
        } else if (itemId == R.id.menu_logs) {
            Intent intent = new Intent();
            intent.setClass(this, SyncLogsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_preferences) {
            Intent intent = new Intent();
            intent.setClass(this, PreferencesActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_add_box) {
            onBoxChoose(null);
            return true;
        } else if (itemId == R.id.menu_add_dropbox) {
            onDropboxChoose(null);
            return true;
        } else if (itemId == R.id.menu_add_google_drive) {
            onGdriveChoose(null);
            return true;
        } else if (itemId == R.id.menu_add_onedrive) {
            onOnedriveChoose(null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onClick(@NonNull View v)
    {
        int id = v.getId();
        if (id == R.id.passwd_safe) {
            PasswdSafeUtil.startMainActivity(PasswdSafeUtil.PACKAGE, this);
        }
    }


    /** Handler to choose a Google Drive account */
    private void onGdriveChoose(Uri currProviderUri)
    {
        Provider driveProvider = getGDrivePlayProvider();
        try {
            driveProvider.startAccountLink(this,
                                           ActivityRequest.GDRIVE_PLAY_LINK);
            itsAccountLinkUris.put(ActivityRequest.GDRIVE_PLAY_LINK,
                                   currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "onGDrivePlayChoose failed", e);
            driveProvider.unlinkAccount();
        }
    }


    /** Handler to choose a Dropbox account */
    private void onDropboxChoose(Uri currProviderUri)
    {
        Provider dbxProvider = getDbxProvider();
        try {
            dbxProvider.startAccountLink(this, ActivityRequest.DROPBOX_LINK);
            itsDropboxPendingAcctLink = true;
            itsAccountLinkUris.put(ActivityRequest.DROPBOX_LINK,
                                   currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "startDropboxLink failed", e);
            dbxProvider.unlinkAccount();
        }
    }


    /** Handler to choose a Box account */
    private void onBoxChoose(Uri currProviderUri)
    {
        Provider boxProvider = getBoxProvider();
        try {
            boxProvider.startAccountLink(this, ActivityRequest.BOX_AUTH);
            itsAccountLinkUris.put(ActivityRequest.BOX_AUTH, currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "Box startAccountLink failed", e);
            boxProvider.unlinkAccount();
        }
    }


    /** Handler to choose an OneDrive account */
    private void onOnedriveChoose(Uri currProviderUri)
    {
        Provider onedriveProvider = getOnedriveProvider();
        try {
            onedriveProvider.startAccountLink(this,
                                              ActivityRequest.ONEDRIVE_LINK);
            itsOnedrivePendingAcctLink = true;
            itsAccountLinkUris.put(ActivityRequest.ONEDRIVE_LINK,
                                   currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "OneDrive startAccountLink failed", e);
            onedriveProvider.unlinkAccount();
        }
    }


    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        return new PasswdCursorLoader(
                this, PasswdSafeContract.Providers.CONTENT_URI,
                PasswdSafeContract.Providers.PROJECTION,
                null, null, PasswdSafeContract.Providers.PROVIDER_SORT_ORDER);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor)
    {
        if (!PasswdCursorLoader.checkResult(loader, this)) {
            return;
        }
        boolean hasAccounts = false;
        itsMenuOptions.clear();
        for (boolean more = (cursor != null) && cursor.moveToFirst(); more;
                more = cursor.moveToNext()) {
            hasAccounts = true;
            String typeStr = cursor.getString(
                    PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
            try {
                ProviderType type = ProviderType.valueOf(typeStr);
                switch (type) {
                case GDRIVE: {
                    itsMenuOptions.set(MENU_BIT_HAS_GDRIVE);
                    break;
                }
                case DROPBOX: {
                    itsMenuOptions.set(MENU_BIT_HAS_DROPBOX);
                    break;
                }
                case BOX: {
                    itsMenuOptions.set(MENU_BIT_HAS_BOX);
                    break;
                }
                case ONEDRIVE: {
                    itsMenuOptions.set(MENU_BIT_HAS_ONEDRIVE);
                    break;
                }
                case OWNCLOUD: {
                    break;
                }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unknown type: " + typeStr);
            }
        }

        GuiUtils.setVisible(findViewById(R.id.no_accounts_msg), !hasAccounts);
        invalidateOptionsMenu();

        itsAccountsAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader)
    {
        onLoadFinished(loader, null);
    }

    @Override
    public void handleProviderSync(ProviderType type, Uri providerUri)
    {
        Provider provider = getProvider(type);
        if (provider == null) {
            return;
        }
        CharSequence warning = getProviderWarning(type);
        if (provider.isAccountAuthorized() && TextUtils.isEmpty(warning)) {
            provider.requestSync(true);
        } else {
            switch (type) {
            case GDRIVE: {
                onGdriveChoose(providerUri);
                break;
            }
            case DROPBOX: {
                onDropboxChoose(providerUri);
                break;
            }
            case BOX: {
                onBoxChoose(providerUri);
                break;
            }
            case ONEDRIVE: {
                onOnedriveChoose(providerUri);
                break;
            }
            case OWNCLOUD: {
                break;
            }
            }
        }
    }

    @Override
    public void handleProviderChooseFiles(@NonNull ProviderType type,
                                          Uri providerUri)
    {
        Class<? extends AbstractSyncedFilesActivity> chooseActivity = null;
        String uriKey = null;
        switch (type) {
        case DROPBOX: {
            chooseActivity = DropboxFilesActivity.class;
            uriKey = DropboxFilesActivity.INTENT_PROVIDER_URI;
            break;
        }
        case ONEDRIVE: {
            chooseActivity = OnedriveFilesActivity.class;
            uriKey = OnedriveFilesActivity.INTENT_PROVIDER_URI;
            break;
        }
        case OWNCLOUD:
        case BOX:
        case GDRIVE: {
            break;
        }
        }
        if (chooseActivity == null) {
            return;
        }
        Intent intent = new Intent();
        intent.putExtra(uriKey, providerUri);
        intent.setClass(this, chooseActivity);
        startActivity(intent);
    }

    @Override
    public void handleProviderDelete(Uri providerUri)
    {
        if (providerUri != null) {
            DialogFragment prompt = ClearPromptDlg.newInstance(providerUri);
            prompt.show(getSupportFragmentManager(), null);
        }
    }

    @Override
    public void updateProviderSyncFreq(final Uri providerUri,
                                       ProviderSyncFreqPref freq)
    {
        new AccountSyncFreqUpdateTask(providerUri, freq, this)
                .startTask(this, this);
    }

    @Nullable
    @Override
    public SyncResults getProviderSyncResults(ProviderType type)
    {
        Provider provider = getProvider(type);
        return (provider != null) ? provider.getSyncResults() : null;
    }

    @Override
    @Nullable
    public CharSequence getProviderWarning(@NonNull ProviderType type)
    {
        CharSequence warning = null;
        switch (type) {
        case GDRIVE: {
            switch (itsGDriveState) {
            case OK: {
                break;
            }
            case AUTH_REQUIRED: {
                warning = getText(R.string.gdrive_state_auth_required);
                break;
            }
            }
            break;
        }
        case DROPBOX: {
            boolean authorized = getDbxProvider().isAccountAuthorized();
            if (!authorized) {
                warning = getText(R.string.dropbox_acct_unlinked);
            }
            break;
        }
        case BOX: {
            boolean authorized = getBoxProvider().isAccountAuthorized();
            if (!authorized) {
                warning = getText(R.string.box_acct_unlinked);
            }
            break;
        }
        case ONEDRIVE: {
            boolean authorized = getOnedriveProvider().isAccountAuthorized();
            if (!authorized) {
                warning = getText(R.string.onedrive_acct_unlinked);
            }
            break;
        }
        case OWNCLOUD: {
            break;
        }
        }
        return warning;
    }

    @Override
    public boolean isActivityRunning()
    {
        return itsIsRunning;
    }

    @Override
    public void openUrl(Uri url, @StringRes int titleId)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, getString(titleId)));
        }
    }

    @Override
    public void updateGDriveState(GDriveState state)
    {
        itsGDriveState = state;
        reloadProviders();
    }

    @Override
    public void updateProviderState()
    {
        reloadProviders();
    }

    /**
     * Notification the task is starting
     */
    @Override
    public final void notifyUpdateStarted(AccountUpdateTask task)
    {
        itsUpdateTasks.add(task);
    }

    /**
     * Notification the task is finished
     */
    @Override
    public final void notifyUpdateFinished(AccountUpdateTask task)
    {
        itsUpdateTasks.remove(task);
    }

    /** Remove an account */
    private void removeAccount(Uri currAcct)
    {
        new RemoveAccountTask(currAcct, this).startTask(this, this);
    }

    /** Get the Google Drive provider */
    private Provider getGDrivePlayProvider()
    {
        return ProviderFactory.getProvider(ProviderType.GDRIVE, this);
    }

    /** Get the Dropbox provider */
    private Provider getDbxProvider()
    {
        return ProviderFactory.getProvider(ProviderType.DROPBOX, this);
    }

    /** Get the Box provider */
    private Provider getBoxProvider()
    {
        return ProviderFactory.getProvider(ProviderType.BOX, this);
    }

    /** Get the OneDrive provider */
    private Provider getOnedriveProvider()
    {
        return ProviderFactory.getProvider(ProviderType.ONEDRIVE, this);
    }

    /**
     * Get the provider
     */
    @Nullable
    private Provider getProvider(@NonNull ProviderType type)
    {
        switch (type) {
        case GDRIVE: {
            return getGDrivePlayProvider();
        }
        case DROPBOX: {
            return getDbxProvider();
        }
        case BOX: {
            return getBoxProvider();
        }
        case ONEDRIVE: {
            return getOnedriveProvider();
        }
        case OWNCLOUD: {
            return ProviderFactory.getProvider(ProviderType.OWNCLOUD, this);
        }
        }
        return null;
    }

    /** Update a menu item based on the presence of a provider */
    private void setProviderMenuEnabled(@NonNull Menu menu,
                                        int id,
                                        int hasProviderBit)
    {
        MenuItem item = menu.findItem(id);
        item.setEnabled(!itsMenuOptions.get(hasProviderBit));
    }

    /**
     * Show the about dialog
     */
    private void showAbout()
    {
        AboutDialog dlg = AboutDialog.newInstance();
        dlg.show(getSupportFragmentManager(), "AboutDialog");
    }

    /**
     * Reload the providers
     */
    private void reloadProviders()
    {
        LoaderManager lm = LoaderManager.getInstance(this);
        lm.restartLoader(LOADER_PROVIDERS, null, this);
    }

    /**
     * Get the provider URI cached during the account link
     */
    private Uri getAccountLinkUri(int requestCode)
    {
        Uri uri = itsAccountLinkUris.get(requestCode);
        itsAccountLinkUris.remove(requestCode);
        return uri;
    }

    /** Dialog to prompt when an account is cleared */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
    public static class ClearPromptDlg extends DialogFragment
    {
        /** Create an instance of the dialog */
        @NonNull
        protected static ClearPromptDlg newInstance(Uri currAcct)
        {
            ClearPromptDlg dlg = new ClearPromptDlg();
            Bundle args = new Bundle();
            args.putParcelable("currAcct", currAcct);
            dlg.setArguments(args);
            return dlg;
        }

        /* (non-Javadoc)
         * @see android.support.v4.app.DialogFragment#onCreateDialog(android.os.Bundle)
         */
        @Override
        public @NonNull
        Dialog onCreateDialog(Bundle savedInstanceState)
        {
            Bundle args = getArguments();
            final Uri currAcct =
                    (args != null) ? args.getParcelable("currAcct") : null;

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(getActivity());
            builder
            .setMessage(R.string.remove_account)
            .setPositiveButton(
                    android.R.string.yes,
                    (dialog, which) -> {
                        MainActivity act = (MainActivity)getActivity();
                        if (act != null) {
                            act.removeAccount(currAcct);
                        }
                    })
            .setNegativeButton(android.R.string.no, null);
            return builder.create();
        }
    }
}
