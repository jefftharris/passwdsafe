/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
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
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.jefftharris.passwdsafe.file.PasswdFileUri;
import com.jefftharris.passwdsafe.file.PasswdPolicy;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.pref.FileBackupPref;
import com.jefftharris.passwdsafe.pref.FileTimeoutPref;
import com.jefftharris.passwdsafe.pref.PasswdExpiryNotifPref;
import com.jefftharris.passwdsafe.pref.PasswdTimeoutPref;
import com.jefftharris.passwdsafe.pref.RecordFieldSortPref;
import com.jefftharris.passwdsafe.pref.RecordSortOrderPref;
import com.jefftharris.passwdsafe.pref.ThemePref;
import com.jefftharris.passwdsafe.view.ConfirmPromptDialog;

import org.pwsafe.lib.file.PwsFile;

import java.io.File;
import java.util.Objects;

/**
 * Fragment for PasswdSafe preferences
 */
public class PreferencesFragment extends PreferenceFragmentCompat
        implements ConfirmPromptDialog.Listener
{
    /** Listener interface for owning activity */
    public interface Listener
    {
        /** Update the view for preferences */
        void updateViewPreferences();
    }

    public static final String SCREEN_RECORD = "recordOptions";

    /** Action confirmed via ConfirmPromptDialog */
    private enum ConfirmAction
    {
        CLEAR_ALL_NOTIFS,
        CLEAR_ALL_SAVED
    }

    private static final int REQUEST_DEFAULT_FILE = 0;
    private static final int REQUEST_CLEAR_ALL_NOTIFS = 1;
    private static final int REQUEST_CLEAR_ALL_SAVED = 2;

    private static final String CONFIRM_ARG_ACTION = "action";

    private static final String TAG = "PreferencesFragment";

    private Listener itsListener;
    private Screen itsScreen;

    /**
     * Create a new instance
     */
    @NonNull
    public static PreferencesFragment newInstance(String key)
    {
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
        PreferencesFragment frag = new PreferencesFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        if (ctx instanceof Listener) {
            itsListener = (Listener)ctx;
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String key)
    {
        setPreferencesFromResource(R.xml.preferences, key);
        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        Resources res = getResources();

        if ((key == null) || key.equals("top_prefs")) {
            itsScreen = new RootScreen(prefs, res);
        } else if (key.equals("fileOptions")) {
            itsScreen = new FilesScreen(prefs, res);
        } else if (key.equals("passwordOptions")) {
            itsScreen = new PasswordScreen(prefs, res);
        } else if (key.equals(SCREEN_RECORD)) {
            itsScreen = new RecordScreen(prefs, res);
        } else {
            PasswdSafeUtil.showFatalMsg("Unknown preferences screen: " + key,
                                        getActivity());
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        prefs.registerOnSharedPreferenceChangeListener(itsScreen);
        if (itsListener != null) {
            itsListener.updateViewPreferences();
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        prefs.unregisterOnSharedPreferenceChangeListener(itsScreen);
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (!itsScreen.onActivityResult(requestCode, resultCode, data))
        {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void promptCanceled()
    {
    }

    @Override
    public void promptConfirmed(Bundle confirmArgs)
    {
        ConfirmAction action;
        try {
            action = ConfirmAction.valueOf(
                    confirmArgs.getString(CONFIRM_ARG_ACTION));
        } catch (Exception e) {
            return;
        }
        itsScreen.promptConfirmed(action);
    }

    /**
     * Find a non-null preference
     */
    @NonNull
    private <T extends Preference> T requirePreference(String key)
    {
        return Objects.requireNonNull(findPreference(key));
    }

    /**
     * Hide a preference
     */
    private void hidePreference(String key)
    {
        Preference pref = requirePreference(key);
        pref.setEnabled(false);
        pref.setVisible(false);
    }

    /**
     * A screen of preferences
     */
    private static abstract class Screen
        implements Preference.OnPreferenceClickListener,
                   SharedPreferences.OnSharedPreferenceChangeListener
    {
        /**
         * Handle an activity result
         * @return true if handled; false otherwise
         */
        protected boolean onActivityResult(int requestCode, int resultCode,
                                           Intent data)
        {
            return false;
        }

        /**
         * Handle a confirmed dialog prompt
         */
        protected void promptConfirmed(ConfirmAction action)
        {
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference preference)
        {
            return false;
        }
    }

    /**
     * The root screen of preferences
     */
    private final class RootScreen extends Screen
    {
        private final ListPreference itsThemePref;
        private final EditTextPreference itsDebugTagsPref;

        /**
         * Constructor
         */
        private RootScreen(SharedPreferences prefs, Resources res)
        {
            itsThemePref = requirePreference(Preferences.PREF_DISPLAY_THEME);
            itsThemePref.setEntries(ThemePref.getDisplayNames(res));
            itsThemePref.setEntryValues(ThemePref.getValues());
            updateThemePrefSummary(prefs);

            Preference pref =
                    findPreference(Preferences.PREF_DISPLAY_VIBRATE_KEYBOARD);
            if (pref != null) {
                pref.setVisible(ApiCompat.hasVibrator(requireContext()));
            }

            pref = findPreference(
                    Preferences.PREF_DISPLAY_SHOW_UNTRUSTED_EXTERNAL);
            if (pref != null) {
                pref.setVisible(ApiCompat.supportsExternalDisplays());
            }

            itsDebugTagsPref = requirePreference(Preferences.PREF_DEBUG_TAGS);
            itsDebugTagsPref.setDialogMessage(R.string.logging_tags_desc);
            itsDebugTagsPref.setDefaultValue(Preferences.PREF_DEBUG_TAGS_DEF);
            onSharedPreferenceChanged(prefs, Preferences.PREF_DEBUG_TAGS);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              @Nullable String key)
        {
            boolean updateTheme = false;
            boolean updateDebugTags = false;
            if (key == null) {
                updateTheme = true;
                updateDebugTags = true;
            } else {
                switch (key) {
                case Preferences.PREF_DEBUG_TAGS: {
                    updateDebugTags = true;
                    break;
                }
                case Preferences.PREF_DISPLAY_THEME: {
                    updateTheme = true;
                    break;
                }
                }
            }
            if (updateTheme) {
                updateThemePrefSummary(prefs);
                requireActivity().recreate();
            }
            if (updateDebugTags) {
                String val = Preferences.getDebugTagsPref(prefs);
                itsDebugTagsPref.setSummary(val);
            }
        }

        private void updateThemePrefSummary(SharedPreferences prefs)
        {
            ThemePref pref = Preferences.getDisplayTheme(prefs);
            Resources res = getResources();
            itsThemePref.setSummary(pref.getDisplayName(res));
        }
    }

    /**
     * The screen of file preferences
     */
    private final class FilesScreen extends Screen
    {
        private final EditTextPreference itsFileDirPref;
        private final Preference itsDefFilePref;
        private final ListPreference itsFileClosePref;
        private final ListPreference itsFileBackupPref;

        /**
         * Constructor
         */
        private FilesScreen(SharedPreferences prefs, Resources res)
        {
            itsFileDirPref = requirePreference(Preferences.PREF_FILE_DIR);
            itsFileDirPref.setDefaultValue(Preferences.PREF_FILE_DIR_DEF);

            itsDefFilePref = requirePreference(Preferences.PREF_DEF_FILE);
            itsDefFilePref.setOnPreferenceClickListener(this);

            itsFileClosePref =
                    requirePreference(Preferences.PREF_FILE_CLOSE_TIMEOUT);
            itsFileClosePref.setEntries(FileTimeoutPref.getDisplayNames(res));
            itsFileClosePref.setEntryValues(FileTimeoutPref.getValues());

            itsFileBackupPref = requirePreference(Preferences.PREF_FILE_BACKUP);
            itsFileBackupPref.setEntries(FileBackupPref.getDisplayNames(res));
            itsFileBackupPref.setEntryValues(FileBackupPref.getValues());

            onSharedPreferenceChanged(prefs, Preferences.PREF_FILE_DIR);
            onSharedPreferenceChanged(prefs, Preferences.PREF_DEF_FILE);
            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_FILE_CLOSE_TIMEOUT);
            onSharedPreferenceChanged(prefs, Preferences.PREF_FILE_BACKUP);

            if (!ApiCompat.supportsExternalFilesDirs()) {
                hidePreference(Preferences.PREF_FILE_DIR);
                hidePreference(Preferences.PREF_FILE_LEGACY_FILE_CHOOSER);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              @Nullable String key)
        {
            boolean updateFileDir = false;
            boolean updateDefFile = false;
            boolean updateCloseTimeout = false;
            boolean updateBackup = false;
            if (key == null) {
                updateFileDir = true;
                updateDefFile = true;
                updateCloseTimeout = true;
                updateBackup = true;
            } else {
                switch (key) {
                case Preferences.PREF_FILE_DIR: {
                    updateFileDir = true;
                    break;
                }
                case Preferences.PREF_DEF_FILE: {
                    updateDefFile = true;
                    break;
                }
                case Preferences.PREF_FILE_CLOSE_TIMEOUT: {
                    updateCloseTimeout = true;
                    break;
                }
                case Preferences.PREF_FILE_BACKUP: {
                    updateBackup = true;
                    break;
                }
                }
            }
            if (updateFileDir) {
                File pref = Preferences.getFileDirPref(prefs);
                if (TextUtils.isEmpty(pref.toString())) {
                    pref = new File(Preferences.PREF_FILE_DIR_DEF);
                    itsFileDirPref.setText(pref.toString());
                }
                if (!TextUtils.equals(pref.toString(),
                                      itsFileDirPref.getText())) {
                    itsFileDirPref.setText(pref.toString());
                }
                itsFileDirPref.setSummary(pref.toString());
            }
            if (updateDefFile) {
                new DefaultFileResolver(
                        Preferences.getDefFilePref(prefs),
                        this, PreferencesFragment.this).execute();
            }
            if (updateCloseTimeout) {
                FileTimeoutPref pref =
                        Preferences.getFileCloseTimeoutPref(prefs);
                itsFileClosePref.setSummary(
                        pref.getDisplayName(getResources()));
            }
            if (updateBackup) {
                FileBackupPref pref = Preferences.getFileBackupPref(prefs);
                itsFileBackupPref.setSummary(
                        pref.getDisplayName(getResources()));
            }
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference preference)
        {
            switch (preference.getKey()) {
            case Preferences.PREF_DEF_FILE: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT, null,
                                           getContext(),
                                           LauncherFileShortcuts.class);
                intent.putExtra(LauncherFileShortcuts.EXTRA_IS_DEFAULT_FILE,
                                true);
                startActivityForResult(intent, REQUEST_DEFAULT_FILE);
                return true;
            }
            }
            return false;
        }

        @Override
        public boolean onActivityResult(int requestCode, int resultCode,
                                        Intent data)
        {
            switch (requestCode) {
            case REQUEST_DEFAULT_FILE: {
                if (resultCode != Activity.RESULT_OK) {
                    return true;
                }
                Intent val =
                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
                Uri uri = (val != null) ? val.getData() : null;
                setDefFilePref((uri != null) ? uri.toString() : null);
                return true;
            }
            default: {
                return false;
            }
            }
        }

        /**
         * Set the default file preference
         */
        private void setDefFilePref(String prefVal)
        {
            SharedPreferences prefs = itsDefFilePref.getSharedPreferences();
            if (prefs != null) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(Preferences.PREF_DEF_FILE, prefVal);
                editor.apply();
                onSharedPreferenceChanged(prefs, Preferences.PREF_DEF_FILE);
            }
        }
    }

    /**
     * The screen of password preferences
     */
    private final class PasswordScreen extends Screen
    {
        private final ListPreference itsPasswdVisibleTimeoutPref;
        private final ListPreference itsPasswdEncPref;
        private final ListPreference itsPasswdExpiryNotifPref;
        private final EditTextPreference itsPasswdDefaultSymsPref;

        /**
         * Constructor
         */
        private PasswordScreen(SharedPreferences prefs, Resources res)
        {
            itsPasswdVisibleTimeoutPref =
                    requirePreference(Preferences.PREF_PASSWD_VISIBLE_TIMEOUT);
            itsPasswdVisibleTimeoutPref.setEntries(
                    PasswdTimeoutPref.getDisplayNames(res));
            itsPasswdVisibleTimeoutPref.setEntryValues(
                    PasswdTimeoutPref.getValues());

            itsPasswdEncPref = requirePreference(Preferences.PREF_PASSWD_ENC);
            String[] charsets =
                    PwsFile.ALL_PASSWORD_CHARSETS.toArray(new String[0]);
            itsPasswdEncPref.setEntries(charsets);
            itsPasswdEncPref.setEntryValues(charsets);
            itsPasswdEncPref.setDefaultValue(Preferences.PREF_PASSWD_ENC_DEF);

            itsPasswdExpiryNotifPref =
                    requirePreference(Preferences.PREF_PASSWD_EXPIRY_NOTIF);
            itsPasswdExpiryNotifPref.setEntries(
                    PasswdExpiryNotifPref.getDisplayNames(res));
            itsPasswdExpiryNotifPref.setEntryValues(
                    PasswdExpiryNotifPref.getValues());

            itsPasswdDefaultSymsPref =
                    requirePreference(Preferences.PREF_PASSWD_DEFAULT_SYMS);
            itsPasswdDefaultSymsPref.setDialogMessage(
                    getString(R.string.default_symbols_empty_pref,
                              PasswdPolicy.SYMBOLS_DEFAULT));
            itsPasswdDefaultSymsPref.setDefaultValue(
                    PasswdPolicy.SYMBOLS_DEFAULT);

            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_PASSWD_VISIBLE_TIMEOUT);
            onSharedPreferenceChanged(prefs, Preferences.PREF_PASSWD_ENC);
            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_PASSWD_EXPIRY_NOTIF);
            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_PASSWD_DEFAULT_SYMS);

            Preference clearNotifsPref =
                    requirePreference(Preferences.PREF_PASSWD_CLEAR_ALL_NOTIFS);
            clearNotifsPref.setOnPreferenceClickListener(this);
            Preference clearAllSavedPref =
                    requirePreference(Preferences.PREF_PASSWD_CLEAR_ALL_SAVED);
            clearAllSavedPref.setOnPreferenceClickListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              @Nullable String key)
        {
            boolean updateVisibleTimeout = false;
            boolean updateEnc = false;
            boolean updateExpiryNotif = false;
            boolean updateDefaultSyms = false;
            if (key == null) {
                updateVisibleTimeout = true;
                updateEnc = true;
                updateExpiryNotif = true;
                updateDefaultSyms = true;
            } else {
                switch (key) {
                case Preferences.PREF_PASSWD_VISIBLE_TIMEOUT: {
                    updateVisibleTimeout = true;
                    break;
                }
                case Preferences.PREF_PASSWD_ENC: {
                    updateEnc = true;
                    break;
                }
                case Preferences.PREF_PASSWD_EXPIRY_NOTIF: {
                    updateExpiryNotif = true;
                    break;
                }
                case Preferences.PREF_PASSWD_DEFAULT_SYMS: {
                    updateDefaultSyms = true;
                    break;
                }
                }
            }
            if (updateVisibleTimeout) {
                PasswdTimeoutPref pref =
                        Preferences.getPasswdVisibleTimeoutPref(prefs);
                itsPasswdVisibleTimeoutPref.setSummary(
                        pref.getDisplayName(getResources()));
            }
            if (updateEnc) {
                itsPasswdEncPref.setSummary(
                        Preferences.getPasswordEncodingPref(prefs));
            }
            if (updateExpiryNotif) {
                PasswdExpiryNotifPref pref =
                        Preferences.getPasswdExpiryNotifPref(prefs);
                Resources res = getResources();
                itsPasswdExpiryNotifPref.setSummary(pref.getDisplayName(res));
            }
            if (updateDefaultSyms) {
                String val = Preferences.getPasswdDefaultSymbolsPref(prefs);
                itsPasswdDefaultSymsPref.setSummary(
                        getString(R.string.symbols_used_by_default, val));
            }
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference preference)
        {
            switch (preference.getKey()) {
            case Preferences.PREF_PASSWD_CLEAR_ALL_NOTIFS: {
                Activity act = requireActivity();
                PasswdSafeApp app = (PasswdSafeApp)act.getApplication();
                Bundle confirmArgs = new Bundle();
                confirmArgs.putString(CONFIRM_ARG_ACTION,
                                      ConfirmAction.CLEAR_ALL_NOTIFS.name());
                DialogFragment dlg = app.getNotifyMgr().createClearAllPrompt(
                        act, confirmArgs);
                dlg.setTargetFragment(PreferencesFragment.this,
                                      REQUEST_CLEAR_ALL_NOTIFS);
                dlg.show(getParentFragmentManager(), "clearNotifsConfirm");
                return true;
            }
            case Preferences.PREF_PASSWD_CLEAR_ALL_SAVED: {
                Bundle confirmArgs = new Bundle();
                confirmArgs.putString(CONFIRM_ARG_ACTION,
                                      ConfirmAction.CLEAR_ALL_SAVED.name());
                ConfirmPromptDialog dlg = ConfirmPromptDialog.newInstance(
                        getString(R.string.clear_all_saved_passwords),
                        getString(R.string.erase_all_saved_passwords),
                        getString(R.string.clear), confirmArgs);
                dlg.setTargetFragment(PreferencesFragment.this,
                                      REQUEST_CLEAR_ALL_SAVED);
                dlg.show(getParentFragmentManager(), "clearSavedConfirm");
                return true;
            }
            }
            return false;
        }

        @Override
        public void promptConfirmed(@NonNull ConfirmAction action)
        {
            switch (action) {
            case CLEAR_ALL_NOTIFS: {
                Activity act = requireActivity();
                PasswdSafeApp app = (PasswdSafeApp)act.getApplication();
                app.getNotifyMgr().handleClearAllConfirmed();
                break;
            }
            case CLEAR_ALL_SAVED: {
                SavedPasswordsMgr passwdMgr =
                        new SavedPasswordsMgr(requireContext());
                passwdMgr.removeAllSavedPasswords();
                break;
            }
            }
        }
    }

    /**
     * The screen of record preferences
     */
    private final class RecordScreen extends Screen
    {
        private final ListPreference itsRecordSortOrderPref;
        private final ListPreference itsRecordFieldSortPref;

        /**
         * Constructor
         */
        private RecordScreen(SharedPreferences prefs, Resources res)
        {
            itsRecordSortOrderPref =
                    requirePreference(Preferences.PREF_RECORD_SORT_ORDER);
            itsRecordSortOrderPref.setEntries(
                    RecordSortOrderPref.getDisplayNames(res));
            itsRecordSortOrderPref.setEntryValues(
                    RecordSortOrderPref.getValues());

            itsRecordFieldSortPref =
                    requirePreference(Preferences.PREF_RECORD_FIELD_SORT);
            itsRecordFieldSortPref.setEntries(
                    RecordFieldSortPref.getDisplayNames(res));
            itsRecordFieldSortPref.setEntryValues(
                    RecordFieldSortPref.getValues());

            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_RECORD_SORT_ORDER);
            onSharedPreferenceChanged(prefs,
                                      Preferences.PREF_RECORD_FIELD_SORT);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              @Nullable String key)
        {
            boolean updateSortOrder = false;
            boolean updateFieldSort = false;
            if (key == null) {
                updateSortOrder = true;
                updateFieldSort = true;
            } else {
                switch (key) {
                case Preferences.PREF_RECORD_SORT_ORDER: {
                    updateSortOrder = true;
                    break;
                }
                case Preferences.PREF_RECORD_FIELD_SORT: {
                    updateFieldSort = true;
                    break;
                }
                }
            }
            if (updateSortOrder) {
                RecordSortOrderPref pref =
                        Preferences.getRecordSortOrderPref(prefs);
                Resources res = getResources();
                itsRecordSortOrderPref.setSummary(pref.getDisplayName(res));
            }
            if (updateFieldSort) {
                RecordFieldSortPref pref =
                        Preferences.getRecordFieldSortPref(prefs);
                Resources res = getResources();
                itsRecordFieldSortPref.setSummary(pref.getDisplayName(res));
            }
        }
    }

    /**
     * Background task to resolve the default file URI and set the
     * preference's summary
     */
    private static class DefaultFileResolver
            extends AsyncTask<Void, Void, PasswdFileUri>
    {
        private final ManagedRef<FilesScreen> itsScreen;
        private final ManagedRef<Fragment> itsFrag;
        private PasswdFileUri.Creator itsUriCreator;

        /**
         * Constructor
         */
        protected DefaultFileResolver(Uri fileUri,
                                      FilesScreen screen,
                                      Fragment fragment)
        {
            itsScreen = new ManagedRef<>(screen);
            itsFrag = new ManagedRef<>(fragment);
            if (fileUri != null) {
                itsUriCreator = new PasswdFileUri.Creator(
                        fileUri, fragment.getContext());
            }
        }

        @Override
        protected final void onPreExecute()
        {
            super.onPreExecute();
            if (itsUriCreator != null) {
                itsUriCreator.onPreExecute();
            }
        }

        @Nullable
        @Override
        protected PasswdFileUri doInBackground(Void... params)
        {
            try {
                return (itsUriCreator != null) ?
                        itsUriCreator.finishCreate() : null;
            } catch (Throwable e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(PasswdFileUri result)
        {
            FilesScreen screen = itsScreen.get();
            Fragment frag = itsFrag.get();
            if ((screen == null) || (frag == null) || !frag.isResumed()) {
                return;
            }
            String summary;
            if (result == null) {
                summary = frag.getString(R.string.none);
                if (itsUriCreator != null) {
                    Throwable resolveEx = itsUriCreator.getResolveEx();
                    if (resolveEx != null) {
                        Log.e(TAG, "Error resolving default file",
                              resolveEx);
                        summary = frag.getString(
                                R.string.file_not_found_perm_denied);
                        screen.setDefFilePref(null);
                    }
                }
            } else {
                summary = result.getIdentifier(frag.getContext(), false);
            }
            screen.itsDefFilePref.setSummary(summary);
        }
    }
}
