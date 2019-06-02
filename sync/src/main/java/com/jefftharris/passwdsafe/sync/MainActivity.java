/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import androidx.annotation.NonNull;
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
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;
import com.jefftharris.passwdsafe.sync.dropbox.DropboxFilesActivity;
import com.jefftharris.passwdsafe.sync.lib.AccountSyncFreqUpdateTask;
import com.jefftharris.passwdsafe.sync.lib.AccountUpdateTask;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.Provider;
import com.jefftharris.passwdsafe.sync.lib.RemoveAccountTask;
import com.jefftharris.passwdsafe.sync.lib.SyncResults;
import com.jefftharris.passwdsafe.sync.onedrive.OnedriveFilesActivity;
import com.jefftharris.passwdsafe.sync.owncloud.OwncloudEditDialog;
import com.jefftharris.passwdsafe.sync.owncloud.OwncloudFilesActivity;
import com.jefftharris.passwdsafe.sync.owncloud.OwncloudProvider;
import com.microsoft.identity.common.internal.providers.oauth2.AuthorizationStrategy;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements LoaderCallbacks<Cursor>,
                   MainActivityProviderOps,
                   SyncUpdateHandler,
                   AccountUpdateTask.Listener,
                   OwncloudEditDialog.Listener
{
    private static final String TAG = "MainActivity";

    private static final int DROPBOX_LINK_RC = 1;
    private static final int BOX_AUTH_RC = 2;
    private static final int ONEDRIVE_LINK_RC =
            AuthorizationStrategy.BROWSER_FLOW;
    private static final int OWNCLOUD_LINK_RC = 4;
    private static final int PERMISSIONS_RC = 5;
    private static final int APP_SETTINGS_RC = 6;
    private static final int GDRIVE_PLAY_LINK_RC = 7;
    private static final int GDRIVE_PLAY_SERVICES_ERROR_RC = 8;

    private static final int LOADER_PROVIDERS = 0;

    private static final int MENU_BIT_HAS_GDRIVE = 0;
    private static final int MENU_BIT_HAS_DROPBOX = 1;
    private static final int MENU_BIT_HAS_BOX = 2;
    private static final int MENU_BIT_HAS_ONEDRIVE = 3;
    private static final int MENU_BIT_HAS_OWNCLOUD = 4;

    private DynamicPermissionMgr itsPermissionMgr;
    private MainActivityProviderAdapter itsAccountsAdapter;
    private final BitSet itsMenuOptions = new BitSet();
    private GDriveState itsGDriveState = GDriveState.OK;
    private final SparseArray<Uri> itsAccountLinkUris = new SparseArray<>();
    private boolean itsDropboxPendingAcctLink = false;
    private NewAccountTask itsNewAccountTask = null;
    private final List<AccountUpdateTask> itsUpdateTasks = new ArrayList<>();
    private boolean itsIsRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");

        itsPermissionMgr = new DynamicPermissionMgr(
                Manifest.permission.GET_ACCOUNTS, this,
                PERMISSIONS_RC, APP_SETTINGS_RC, PasswdSafeUtil.SYNC_PACKAGE,
                R.id.reload, R.id.app_settings);
        View noPermGroup = findViewById(R.id.no_permission_group);
        GuiUtils.setVisible(noPermGroup, !itsPermissionMgr.checkPerms());

        // Check the state of Google Play services
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int rc = googleApi.isGooglePlayServicesAvailable(this);
        if (rc != ConnectionResult.SUCCESS) {
            googleApi.showErrorDialogFragment(this, rc,
                                              GDRIVE_PLAY_SERVICES_ERROR_RC);
        }

        itsAccountsAdapter = new MainActivityProviderAdapter(this);
        RecyclerView accounts = findViewById(R.id.accounts);
        accounts.setAdapter(itsAccountsAdapter);
        accounts.setNestedScrollingEnabled(false);

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
                    DROPBOX_LINK_RC, Activity.RESULT_OK,
                    null, getAccountLinkUri(DROPBOX_LINK_RC));
        }

        if (itsNewAccountTask != null) {
            itsNewAccountTask.startTask(this, this);
            itsNewAccountTask = null;
        }
        reloadProviders();
        SyncApp.get(this).setSyncUpdateHandler(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case BOX_AUTH_RC: {
            itsNewAccountTask = getBoxProvider().finishAccountLink(
                    requestCode, resultCode, data,
                    getAccountLinkUri(BOX_AUTH_RC));
            break;
        }
        case ONEDRIVE_LINK_RC: {
            itsNewAccountTask = getOnedriveProvider().finishAccountLink(
                    requestCode, resultCode, data,
                    getAccountLinkUri(ONEDRIVE_LINK_RC));
            break;
        }
        case OWNCLOUD_LINK_RC: {
            itsNewAccountTask = getOwncloudProvider().finishAccountLink(
                    requestCode, resultCode, data,
                    getAccountLinkUri(OWNCLOUD_LINK_RC));
            break;
        }
        case GDRIVE_PLAY_LINK_RC: {
            itsNewAccountTask = getGDrivePlayProvider().finishAccountLink(
                    requestCode, resultCode, data,
                    getAccountLinkUri(GDRIVE_PLAY_LINK_RC));
            break;
        }
        case GDRIVE_PLAY_SERVICES_ERROR_RC: {
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
        if (!itsPermissionMgr.handlePermissionsResult(requestCode,
                                                      grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions,
                                             grantResults);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    /** Prepare the Screen's standard options menu to be displayed. */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        MenuItem item = menu.findItem(R.id.menu_add);
        item.setEnabled(itsPermissionMgr.hasPerms());

        setProviderMenuEnabled(menu, R.id.menu_add_box,
                               MENU_BIT_HAS_BOX);
        setProviderMenuEnabled(menu, R.id.menu_add_dropbox,
                               MENU_BIT_HAS_DROPBOX);
        setProviderMenuEnabled(menu, R.id.menu_add_google_drive,
                               MENU_BIT_HAS_GDRIVE);
        setProviderMenuEnabled(menu, R.id.menu_add_onedrive,
                               MENU_BIT_HAS_ONEDRIVE);
        setProviderMenuEnabled(menu, R.id.menu_add_owncloud,
                               MENU_BIT_HAS_OWNCLOUD);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_about: {
            showAbout();
            return true;
        }
        case R.id.menu_logs: {
            Intent intent = new Intent();
            intent.setClass(this, SyncLogsActivity.class);
            startActivity(intent);
            return true;
        }
        case R.id.menu_preferences: {
            Intent intent = new Intent();
            intent.setClass(this, PreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        case R.id.menu_add_box: {
            onBoxChoose(null);
            return true;
        }
        case R.id.menu_add_dropbox: {
            onDropboxChoose(null);
            return true;
        }
        case R.id.menu_add_google_drive: {
            onGdriveChoose(null);
            return true;
        }
        case R.id.menu_add_onedrive: {
            onOnedriveChoose(null);
            return true;
        }
        case R.id.menu_add_owncloud: {
            onOwncloudChoose(null);
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }


    /** Button onClick handler to launch PasswdSafe */
    @SuppressWarnings({"UnusedParameters", "unused"})
    public void onLaunchPasswdSafeClick(View view)
    {
        PasswdSafeUtil.startMainActivity("com.jefftharris.passwdsafe", this);
    }


    /** Handler to choose a Google Drive account */
    private void onGdriveChoose(Uri currProviderUri)
    {
        Provider driveProvider = getGDrivePlayProvider();
        try {
            driveProvider.startAccountLink(this, GDRIVE_PLAY_LINK_RC);
            itsAccountLinkUris.put(GDRIVE_PLAY_LINK_RC, currProviderUri);
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
            dbxProvider.startAccountLink(this, DROPBOX_LINK_RC);
            itsDropboxPendingAcctLink = true;
            itsAccountLinkUris.put(DROPBOX_LINK_RC, currProviderUri);
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
            boxProvider.startAccountLink(this, BOX_AUTH_RC);
            itsAccountLinkUris.put(BOX_AUTH_RC, currProviderUri);
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
            onedriveProvider.startAccountLink(this, ONEDRIVE_LINK_RC);
            itsAccountLinkUris.put(ONEDRIVE_LINK_RC, currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "OneDrive startAccountLink failed", e);
            onedriveProvider.unlinkAccount();
        }
    }


    /** Handler to choose an ownCloud account */
    private void onOwncloudChoose(Uri currProviderUri)
    {
        Provider owncloudProvider = getOwncloudProvider();
        try {
            owncloudProvider.startAccountLink(this, OWNCLOUD_LINK_RC);
            itsAccountLinkUris.put(OWNCLOUD_LINK_RC, currProviderUri);
        } catch (Exception e) {
            Log.e(TAG, "ownCloud startAccountLink failed", e);
            owncloudProvider.unlinkAccount();
        }
    }


    /** Handle changed settings for ownCloud */
    @Override
    public void handleOwncloudSettingsChanged(Uri providerUri,
                                              String url,
                                              ProviderSyncFreqPref freq)
    {
        getOwncloudProvider().setSettings(url);
        updateProviderSyncFreq(providerUri, freq);
        reloadProviders();
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
                    itsMenuOptions.set(MENU_BIT_HAS_OWNCLOUD);
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
        if (provider.isAccountAuthorized()) {
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
                onOwncloudChoose(providerUri);
                break;
            }
            }
        }
    }

    @Override
    public void handleProviderChooseFiles(ProviderType type, Uri providerUri)
    {
        Class chooseActivity = null;
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
        case OWNCLOUD: {
            chooseActivity = OwncloudFilesActivity.class;
            uriKey = OwncloudFilesActivity.INTENT_PROVIDER_URI;
            break;
        }
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
    public void handleProviderEditDialog(ProviderType type,
                                         Uri providerUri,
                                         ProviderSyncFreqPref freq)
    {
        switch (type) {
        case OWNCLOUD: {
            String url = getOwncloudProvider().getUrl().toString();
            DialogFragment dialog = OwncloudEditDialog.newInstance(
                    providerUri, url, freq.getFreq());
            dialog.show(getSupportFragmentManager(), null);
            break;
        }
        case BOX:
        case DROPBOX:
        case GDRIVE:
        case ONEDRIVE: {
            break;
        }
        }
    }

    @Override
    public void updateProviderSyncFreq(final Uri providerUri,
                                       ProviderSyncFreqPref freq)
    {
        new AccountSyncFreqUpdateTask(providerUri, freq, this)
                .startTask(this, this);
    }

    @Override
    public SyncResults getProviderSyncResults(ProviderType type)
    {
        Provider provider = getProvider(type);
        return (provider != null) ? provider.getSyncResults() : null;
    }

    @Override
    public CharSequence getProviderWarning(ProviderType type)
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
            case PENDING_AUTH: {
                warning = getText(R.string.gdrive_state_pending_auth);
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
            boolean authorized = getOwncloudProvider().isAccountAuthorized();
            if (!authorized) {
                warning = getText(R.string.owncloud_auth_required);
            }
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

    /** Get the ownCloud provider */
    private OwncloudProvider getOwncloudProvider()
    {
        return (OwncloudProvider)
                ProviderFactory.getProvider(ProviderType.OWNCLOUD, this);
    }

    /** Get the OneDrive provider */
    private Provider getOnedriveProvider()
    {
        return ProviderFactory.getProvider(ProviderType.ONEDRIVE, this);
    }

    /**
     * Get the provider
     */
    private Provider getProvider(ProviderType type)
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
            return getOwncloudProvider();
        }
        }
        return null;
    }

    /** Update a menu item based on the presence of a provider */
    private void setProviderMenuEnabled(Menu menu, int id, int hasProviderBit)
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
    protected static class ClearPromptDlg extends DialogFragment
    {
        /** Create an instance of the dialog */
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
