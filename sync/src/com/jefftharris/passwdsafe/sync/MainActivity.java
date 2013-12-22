/*
 * Copyright (©) 2013 Jeff Harris <jefftharris@gmail.com> All rights reserved.
 * Use of the code is allowed under the Artistic License 2.0 terms, as specified
 * in the LICENSE file distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.jefftharris.passwdsafe.lib.AboutDialog;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.ReleaseNotesDialog;
import com.jefftharris.passwdsafe.sync.lib.Provider;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;

public class MainActivity extends FragmentActivity
        implements LoaderCallbacks<Cursor>, SyncUpdateHandler
{
    private static final String TAG = "MainActivity";

    private static final int CHOOSE_ACCOUNT_RC = 0;
    private static final int DROPBOX_LINK_RC = 1;

    private static final int LOADER_PROVIDERS = 0;

    private Account itsGdriveAccount = null;
    private Uri itsGdriveUri = null;
    private Uri itsDropboxUri = null;

    private NewAccountInfo itsNewAccount = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate");

        Spinner freqSpin = (Spinner)findViewById(R.id.gdrive_interval);
        freqSpin.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id)
            {
                onGdriveFreqChanged(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });

        freqSpin = (Spinner)findViewById(R.id.dropbox_interval);
        freqSpin.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id)
            {
                onDropboxFreqChanged(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent)
            {
            }
        });

        // Check the state of Google Play services
        int rc = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (rc != ConnectionResult.SUCCESS) {
            Dialog dlg = GooglePlayServicesUtil.getErrorDialog(rc, this, 0);
            dlg.show();
        }

        updateGdriveAccount(null);
        updateDropboxAccount(null);
        LoaderManager lm = getSupportLoaderManager();
        lm.initLoader(LOADER_PROVIDERS, null, this);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onStart()
     */
    @Override
    protected void onStart()
    {
        super.onStart();
        ReleaseNotesDialog.checkNotes(this);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onDestroy()
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onPause()
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        SyncApp.get(this).setSyncUpdateHandler(null);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onResumeFragments()
     */
    @Override
    protected void onResumeFragments()
    {
        super.onResumeFragments();
        if (itsNewAccount != null) {
            setAccount(itsNewAccount.itsCurrAccountUri,
                       itsNewAccount.itsAccount,
                       itsNewAccount.itsProviderType);
            itsNewAccount = null;
        }
        LoaderManager lm = getSupportLoaderManager();
        lm.restartLoader(LOADER_PROVIDERS, null, this);
        SyncApp.get(this).setSyncUpdateHandler(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case CHOOSE_ACCOUNT_RC:
            if (data != null) {
                Bundle b = data.getExtras();
                String accountName =
                        b.getString(AccountManager.KEY_ACCOUNT_NAME);
                Log.i(TAG, "Selected account: " + accountName);
                if (accountName != null && accountName.length() > 0) {
                    itsNewAccount = new NewAccountInfo(ProviderType.GDRIVE,
                                                       accountName,
                                                       itsGdriveUri);
                }
            }
            break;
        case DROPBOX_LINK_RC: {
            String acctName = getDbxProvider().finishAccountLink();
            itsNewAccount = new NewAccountInfo(ProviderType.DROPBOX, acctName,
                                               itsDropboxUri);
            break;
        }
        default: {
            super.onActivityResult(requestCode, resultCode, data);
            break;
        }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.activity_main, menu);

        MenuItem item = menu.findItem(R.id.menu_about);
        MenuItemCompat.setShowAsAction(item,
                                       MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        item = menu.findItem(R.id.menu_logs);
        MenuItemCompat.setShowAsAction(item,
                                       MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_about: {
            AboutDialog dlg = new AboutDialog();
            dlg.show(getSupportFragmentManager(), "AboutDialog");
            return true;
        }
        case R.id.menu_logs: {
            Intent intent = new Intent();
            intent.setClass(this, SyncLogsActivity.class);
            startActivity(intent);
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }


    /** Button onClick handler to launch PasswdSafe */
    public void onLaunchPasswdSafeClick(View view)
    {
        PasswdSafeUtil.startMainActivity("com.jefftharris.passwdsafe", this);
    }


    /** Button onClick handler to choose a GDrive account */
    public void onGdriveChoose(View view)
    {
        Intent intent = AccountPicker.newChooseAccountIntent(
                itsGdriveAccount, null,
                new String[] { SyncDb.GDRIVE_ACCOUNT_TYPE },
                true, null, null, null, null);
        try {
            startActivityForResult(intent, CHOOSE_ACCOUNT_RC);
        } catch (ActivityNotFoundException e) {
            String msg = getString(R.string.google_acct_not_available);
            Log.e(TAG, msg, e);
            PasswdSafeUtil.showErrorMsg(msg, this);
        }
    }


    /** Button onClick handler to sync a GDrive account */
    public void onGdriveSync(View view)
    {
        if (itsGdriveAccount != null) {
            ApiCompat.requestManualSync(itsGdriveAccount,
                                        PasswdSafeContract.CONTENT_URI, this);
        }
    }


    /** Button onClick handler to clear a GDrive account */
    public void onGdriveClear(View view)
    {
        DialogFragment prompt = ClearPromptDlg.newInstance(itsGdriveUri,
                                                           ProviderType.GDRIVE);
        prompt.show(getSupportFragmentManager(), null);
    }


    /** GDrive sync frequency spinner changed */
    private void onGdriveFreqChanged(int pos)
    {
        ProviderSyncFreqPref freq = ProviderSyncFreqPref.displayValueOf(pos);
        new AccountTask(freq, itsGdriveUri, ProviderType.GDRIVE);
    }

    /** Button onClick handler to choose a Dropbox account */
    public void onDropboxChoose(View view)
    {
        Provider dbxProvider = getDbxProvider();
        try {
            dbxProvider.startAccountLink(this, DROPBOX_LINK_RC);
        } catch (Exception e) {
            Log.e(TAG, "startDropboxLink failed", e);
            dbxProvider.unlinkAccount();
        }
    }


    /** Button onClick handler to sync a Dropbox account */
    public void onDropboxSync(View view)
    {
        getDbxProvider().requestSync(true);
    }


    /** Button onClick handler to clear a Dropbox account */
    public void onDropboxClear(View view)
    {
        DialogFragment prompt =
                ClearPromptDlg.newInstance(itsDropboxUri, ProviderType.DROPBOX);
        prompt.show(getSupportFragmentManager(), null);
    }


    /** Dropbox sync frequency spinner changed */
    private void onDropboxFreqChanged(int pos)
    {
        ProviderSyncFreqPref freq = ProviderSyncFreqPref.displayValueOf(pos);
        new AccountTask(freq, itsDropboxUri, ProviderType.DROPBOX);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onCreateLoader(int, android.os.Bundle)
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        return new CursorLoader(this, PasswdSafeContract.Providers.CONTENT_URI,
                                PasswdSafeContract.Providers.PROJECTION,
                                null, null, null);
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onLoadFinished(android.support.v4.content.Loader, java.lang.Object)
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
    {
        boolean hasGdrive = false;
        boolean hasDropbox = false;
        for (boolean more = cursor.moveToFirst(); more;
                more = cursor.moveToNext()) {
            String typeStr = cursor.getString(
                    PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
            try {
                ProviderType type = ProviderType.valueOf(typeStr);
                switch (type) {
                case GDRIVE: {
                    hasGdrive = true;
                    updateGdriveAccount(cursor);
                    break;
                }
                case DROPBOX: {
                    hasDropbox = true;
                    updateDropboxAccount(cursor);
                    break;
                }
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unknown type: " + typeStr);
            }
        }
        if (!hasGdrive) {
            updateGdriveAccount(null);
        }
        if (!hasDropbox) {
            updateDropboxAccount(null);
        }
    }

    /* (non-Javadoc)
     * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onLoaderReset(android.support.v4.content.Loader)
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader)
    {
        updateGdriveAccount(null);
        updateDropboxAccount(null);
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.SyncApp.SyncUpdateHandler#updateGDriveState(com.jefftharris.passwdsafe.sync.SyncApp.SyncUpdateHandler.GDriveState)
     */
    @Override
    public void updateGDriveState(GDriveState state)
    {
        TextView warning = (TextView)findViewById(R.id.gdrive_sync_warning);
        switch (state) {
        case OK: {
            warning.setVisibility(View.GONE);
            break;
        }
        case AUTH_REQUIRED: {
            warning.setVisibility(View.VISIBLE);
            warning.setText(R.string.gdrive_state_auth_required);
            break;
        }
        case PENDING_AUTH: {
            warning.setVisibility(View.VISIBLE);
            warning.setText(R.string.gdrive_state_pending_auth);
            break;
        }
        }
    }

    /** Update the UI when the GDrive account is changed */
    private final void updateGdriveAccount(Cursor cursor)
    {
        View chooseBtn = findViewById(R.id.gdrive_choose);
        TextView acctView = (TextView)findViewById(R.id.gdrive_acct);
        View btns = findViewById(R.id.gdrive_controls);
        if (cursor != null) {
            long id = cursor.getLong(
                    PasswdSafeContract.Providers.PROJECTION_IDX_ID);
            String acct = cursor.getString(
                    PasswdSafeContract.Providers.PROJECTION_IDX_ACCT);
            int freqVal = cursor.getInt(
                    PasswdSafeContract.Providers.PROJECTION_IDX_SYNC_FREQ);
            ProviderSyncFreqPref freq =
                    ProviderSyncFreqPref.freqValueOf(freqVal);
            GoogleAccountManager acctMgr = new GoogleAccountManager(this);
            itsGdriveAccount = acctMgr.getAccountByName(acct);
            itsGdriveUri = ContentUris.withAppendedId(
                    PasswdSafeContract.Providers.CONTENT_URI, id);

            View freqSpinLabel = findViewById(R.id.gdrive_interval_label);
            Spinner freqSpin = (Spinner)findViewById(R.id.gdrive_interval);
            freqSpin.setSelection(freq.getDisplayIdx());
            View syncBtn = findViewById(R.id.gdrive_sync);
            chooseBtn.setVisibility(View.GONE);
            acctView.setVisibility(View.VISIBLE);
            btns.setVisibility(View.VISIBLE);

            boolean haveAccount = (itsGdriveAccount != null);
            if (haveAccount) {
                acctView.setText(getString(R.string.account_label,
                                           itsGdriveAccount.name));
            } else {
                acctView.setText(getString(R.string.account_not_exists_label,
                                           acct));
            }
            freqSpin.setEnabled(haveAccount);
            freqSpinLabel.setEnabled(haveAccount);
            syncBtn.setEnabled(haveAccount);
        } else {
            itsGdriveAccount = null;
            itsGdriveUri = null;
            chooseBtn.setVisibility(View.VISIBLE);
            acctView.setVisibility(View.GONE);
            btns.setVisibility(View.GONE);
        }
    }

    /** Update the UI when the Dropbox account is changed */
    private final void updateDropboxAccount(Cursor cursor)
    {
        View chooseBtn = findViewById(R.id.dropbox_choose);
        TextView acctView = (TextView)findViewById(R.id.dropbox_acct);
        View btns = findViewById(R.id.dropbox_controls);
        if (cursor != null) {
            long id = cursor.getLong(
                    PasswdSafeContract.Providers.PROJECTION_IDX_ID);
            String acct = PasswdSafeContract.Providers.getDisplayName(cursor);
            int freqVal = cursor.getInt(
                    PasswdSafeContract.Providers.PROJECTION_IDX_SYNC_FREQ);
            ProviderSyncFreqPref freq =
                    ProviderSyncFreqPref.freqValueOf(freqVal);
            itsDropboxUri = ContentUris.withAppendedId(
                    PasswdSafeContract.Providers.CONTENT_URI, id);
            boolean authorized = getDbxProvider().isAccountAuthorized();

            View freqSpinLabel = findViewById(R.id.dropbox_interval_label);
            Spinner freqSpin = (Spinner)findViewById(R.id.dropbox_interval);
            freqSpin.setSelection(freq.getDisplayIdx());
            View syncBtn = findViewById(R.id.dropbox_sync);
            View acctWarning = findViewById(R.id.dropbox_acct_unlink);
            chooseBtn.setVisibility(View.GONE);
            acctView.setVisibility(View.VISIBLE);
            btns.setVisibility(View.VISIBLE);
            acctWarning.setVisibility(authorized ? View.GONE : View.VISIBLE);

            acctView.setText(getString(R.string.account_label, acct));
            freqSpin.setEnabled(true);
            freqSpinLabel.setEnabled(true);
            syncBtn.setEnabled(authorized);
        } else {
            itsDropboxUri = null;
            chooseBtn.setVisibility(View.VISIBLE);
            acctView.setVisibility(View.GONE);
            btns.setVisibility(View.GONE);
        }
    }

    /** Set the new account to use with the app */
    private void setAccount(Uri currAcct, String newAcct, ProviderType acctType)
    {
        new AccountTask(currAcct, newAcct, acctType);
    }

    /** Get the Dropbox provider */
    private Provider getDbxProvider()
    {
        return ProviderFactory.getProvider(ProviderType.DROPBOX, this);
    }

    /** Dialog to prompt when an account is cleared */
    public static class ClearPromptDlg extends DialogFragment
    {
        /** Create an instance of the dialog */
        public static ClearPromptDlg newInstance(Uri currAcct,
                                                 ProviderType providerType)
        {
            ClearPromptDlg dlg = new ClearPromptDlg();
            Bundle args = new Bundle();
            args.putParcelable("currAcct", currAcct);
            args.putString("providerType", providerType.name());
            dlg.setArguments(args);
            return dlg;
        }

        /* (non-Javadoc)
         * @see android.support.v4.app.DialogFragment#onCreateDialog(android.os.Bundle)
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            Bundle args = getArguments();
            final Uri currAcct = args.getParcelable("currAcct");
            final ProviderType providerType =
                    ProviderType.fromString(args.getString("providerType"));

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(getActivity());
            builder
            .setMessage(R.string.remove_account)
            .setPositiveButton(android.R.string.yes,
                               new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    MainActivity act = (MainActivity)getActivity();
                    act.setAccount(currAcct, null, providerType);
                }
            })
            .setNegativeButton(android.R.string.no, null);
            return builder.create();
        }
    }

    /** The type of async account task */
    private enum AccountTaskType
    {
        ADD_REMOVE,
        UPDATE_SYNC_FREQ
    }

    /** Async task to set the account */
    private class AccountTask extends AsyncTask<Void, Void, Void>
    {
        private final AccountTaskType itsType;
        private final Uri itsCurrAccount;
        private ProgressFragment itsProgressFrag;
        private final String itsNewAccount;
        private final ProviderType itsProviderType;
        private final ProviderSyncFreqPref itsUpdateFreq;

        /** Constructor for add/remove */
        public AccountTask(Uri currAcct, String newAcct, ProviderType type)
        {
            itsType = AccountTaskType.ADD_REMOVE;
            itsCurrAccount = currAcct;
            itsNewAccount = newAcct;
            itsProviderType = type;
            itsUpdateFreq = null;
            String msg = getString((newAcct == null) ?
                    R.string.removing_account : R.string.adding_account);
            init(msg);
        }

        /** Constructor for updating the sync frequency */
        public AccountTask(ProviderSyncFreqPref freq,
                           Uri currAccount, ProviderType type)
        {
            itsType = AccountTaskType.UPDATE_SYNC_FREQ;
            itsCurrAccount = currAccount;
            itsNewAccount = null;
            itsProviderType = type;
            itsUpdateFreq = freq;
            init(getString(R.string.updating_account));
        }

        /** Initialize the task */
        private void init(String msg)
        {
            itsProgressFrag = ProgressFragment.newInstance(msg);
            itsProgressFrag.show(getSupportFragmentManager(), null);
            execute();
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Void doInBackground(Void... params)
        {
            try {
                ContentResolver cr = MainActivity.this.getContentResolver();

                switch (itsType) {
                case ADD_REMOVE: {
                    // Stop syncing for the previously selected account.
                    if (itsCurrAccount != null) {
                        cr.delete(itsCurrAccount, null, null);
                    }

                    if (itsNewAccount != null) {
                        ContentValues values = new ContentValues();
                        values.put(PasswdSafeContract.Providers.COL_ACCT,
                                   itsNewAccount);
                        values.put(PasswdSafeContract.Providers.COL_TYPE,
                                   itsProviderType.name());
                        cr.insert(PasswdSafeContract.Providers.CONTENT_URI,
                                  values);
                    }
                    break;
                }
                case UPDATE_SYNC_FREQ: {
                    ContentValues values = new ContentValues();
                    values.put(PasswdSafeContract.Providers.COL_SYNC_FREQ,
                               itsUpdateFreq.getFreq());
                    cr.update(itsCurrAccount, values, null, null);
                    break;
                }
                }
            } catch (Exception e) {
                Log.e(TAG, "Account update error", e);
            }
            return null;
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Void arg)
        {
            super.onPostExecute(arg);
            itsProgressFrag.dismiss();
        }
    }

    /** Information for a new account */
    private static class NewAccountInfo
    {
        public final ProviderType itsProviderType;
        public final String itsAccount;
        public final Uri itsCurrAccountUri;

        /** Constructor */
        public NewAccountInfo(ProviderType type, String acct, Uri currAcctUri)
        {
            itsProviderType = type;
            itsAccount = acct;
            itsCurrAccountUri = currAcctUri;
        }
    }
}
