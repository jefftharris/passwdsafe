/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jefftharris.passwdsafe.file.PasswdFileData;
import com.jefftharris.passwdsafe.file.PasswdFileUri;
import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.AbstractTextWatcher;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.util.Pair;
import com.jefftharris.passwdsafe.util.YubiState;
import com.jefftharris.passwdsafe.view.ConfirmPromptDialog;
import com.jefftharris.passwdsafe.view.TextInputUtils;

import org.pwsafe.lib.exception.InvalidPassphraseException;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;


/**
 * Fragment for opening a file
 */
public class PasswdSafeOpenFileFragment
        extends AbstractPasswdSafeOpenNewFileFragment
        implements ConfirmPromptDialog.Listener,
                   View.OnClickListener, CompoundButton.OnCheckedChangeListener
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Handle when the file open is canceled */
        void handleFileOpenCanceled();

        /** Handle when the file was successfully opened */
        void handleFileOpen(PasswdFileData fileData, String recToOpen);

        /** Update the view for opening a file */
        void updateViewFileOpen();

        /** Is the navigation drawer closed */
        boolean isNavDrawerClosed();
    }

    /**
     * Phase of the UI
     */
    private enum Phase
    {
        INITIAL,
        RESOLVING,
        WAITING_PASSWORD,
        YUBIKEY,
        OPENING,
        SAVING_PASSWORD,
        FINISHED
    }

    /**
     * Type of change in the saved password
     */
    private enum SavePasswordChange
    {
        ADD,
        REMOVE,
        NONE
    }

    private Listener itsListener;
    private String itsRecToOpen;
    private TextView itsTitle;
    private TextInputLayout itsPasswordInput;
    private TextView itsPasswordEdit;
    private TextView itsSavedPasswordMsg;
    private int itsSavedPasswordTextColor;
    private TextView itsReadonlyMsg;
    private CheckBox itsSavePasswdCb;
    private CheckBox itsYubikeyCb;
    private Button itsViewBtn;
    private Button itsModifyBtn;
    private boolean itsIsOpenReadonly = true;
    private SavedPasswordsMgr itsSavedPasswordsMgr;
    private boolean itsIsPasswordSaved = false;
    private SavePasswordChange itsSaveChange = SavePasswordChange.NONE;
    private LoadSavedPasswordUser itsLoadSavedPasswordUser;
    private AddSavedPasswordUser itsAddSavedPasswordUser;
    private YubikeyMgr itsYubiMgr;
    private YubikeyMgr.User itsYubiUser;
    private YubiState itsYubiState = YubiState.UNAVAILABLE;
    private int itsYubiSlot = 2;
    private boolean itsIsYubikey = false;
    private String itsUserPassword;
    private int itsRetries = 0;
    private Phase itsPhase = Phase.INITIAL;
    private TextWatcher itsErrorClearingWatcher;

    private static final String ARG_URI = "uri";
    private static final String ARG_REC_TO_OPEN = "recToOpen";
    private static final String STATE_SLOT = "slot";
    private static final String TAG = "PasswdSafeOpenFileFragment";


    /**
     * Create a new instance
     */
    public static PasswdSafeOpenFileFragment newInstance(Uri fileUri,
                                                         String recToOpen)
    {
        PasswdSafeOpenFileFragment fragment = new PasswdSafeOpenFileFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, fileUri);
        args.putString(ARG_REC_TO_OPEN, recToOpen);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            setFileUri(args.getParcelable(ARG_URI));
            itsRecToOpen = args.getString(ARG_REC_TO_OPEN);
        }

        if (savedInstanceState == null) {
            itsYubiSlot = 2;
        } else {
            itsYubiSlot = savedInstanceState.getInt(STATE_SLOT, 2);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);

        View rootView = inflater.inflate(R.layout.fragment_passwdsafe_open_file,
                                         container, false);
        setupView(rootView);
        Context ctx = getContext();

        itsTitle = rootView.findViewById(R.id.file);
        itsPasswordInput = rootView.findViewById(R.id.passwd_input);
        itsPasswordEdit = rootView.findViewById(R.id.passwd_edit);
        TypefaceUtils.setMonospace(itsPasswordEdit, ctx);
        itsPasswordEdit.setEnabled(false);

        itsIsOpenReadonly = true;
        itsReadonlyMsg = rootView.findViewById(R.id.read_only_msg);
        GuiUtils.setVisible(itsReadonlyMsg, false);
        itsModifyBtn = rootView.findViewById(R.id.modify);
        itsModifyBtn.setOnClickListener(this);
        itsModifyBtn.setEnabled(false);
        itsViewBtn = rootView.findViewById(R.id.view);
        itsViewBtn.setOnClickListener(this);
        itsViewBtn.setEnabled(false);

        itsSavedPasswordMsg = rootView.findViewById(R.id.saved_password);
        itsSavedPasswordTextColor = itsSavedPasswordMsg.getCurrentTextColor();
        itsSavePasswdCb = rootView.findViewById(R.id.save_password);
        itsSavePasswdCb.setOnCheckedChangeListener(this);
        boolean saveAvailable = itsSavedPasswordsMgr.isAvailable();
        GuiUtils.setVisible(itsSavePasswdCb, saveAvailable);
        GuiUtils.setVisible(itsSavedPasswordMsg, false);

        itsYubiMgr = new YubikeyMgr();
        itsYubikeyCb = rootView.findViewById(R.id.yubikey);
        setVisibility(R.id.file_open_help_text, false, rootView);
        itsYubiState = itsYubiMgr.getState(getActivity());
        switch (itsYubiState) {
        case UNAVAILABLE: {
            GuiUtils.setVisible(itsYubikeyCb, false);
            break;
        }
        case DISABLED: {
            itsYubikeyCb.setEnabled(false);
            itsYubikeyCb.setText(R.string.yubikey_disabled);
            break;
        }
        case ENABLED: {
            break;
        }
        }
        setVisibility(R.id.yubi_progress_text, false, rootView);

        return rootView;
    }

    @Override
    public void onAttach(Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
        itsSavedPasswordsMgr = new SavedPasswordsMgr(ctx);
    }

    @Override
    public void onStart()
    {
        super.onStart();
        setPhase(Phase.RESOLVING);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsListener.updateViewFileOpen();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SLOT, itsYubiSlot);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (itsYubiMgr != null) {
            itsYubiMgr.onPause();
        }
        cancelSavedPasswordUsers();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if (itsYubiMgr != null) {
            itsYubiMgr.stop();
        }
        setPhase(Phase.INITIAL);
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
        itsSavedPasswordsMgr = null;
    }

    /** Handle a new intent */
    public void onNewIntent(Intent intent)
    {
        if (itsYubiMgr != null) {
            itsYubiMgr.handleKeyIntent(intent);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        if ((itsListener != null) && itsListener.isNavDrawerClosed()) {
            inflater.inflate(R.menu.fragment_passwdsafe_open_file, menu);

            switch (itsYubiState) {
            case ENABLED:
            case DISABLED: {
                break;
            }
            case UNAVAILABLE: {
                menu.setGroupVisible(R.id.menu_group_slots, false);
                break;
            }
            }

            MenuItem item;
            switch (itsYubiSlot) {
            case 2:
            default: {
                item = menu.findItem(R.id.menu_slot_2);
                itsYubiSlot = 2;
                break;
            }
            case 1: {
                item = menu.findItem(R.id.menu_slot_1);
                break;
            }
            }
            item.setChecked(true);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_file_open_help: {
            View root = getView();
            if (root != null) {
                View help = root.findViewById(R.id.file_open_help_text);
                help.setVisibility((help.getVisibility() == View.VISIBLE) ?
                                           View.GONE : View.VISIBLE);
                GuiUtils.setKeyboardVisible(itsPasswordEdit, requireContext(),
                                            false);
            }
            return true;
        }
        case R.id.menu_slot_1: {
            item.setChecked(true);
            itsYubiSlot = 1;
            return true;
        }
        case R.id.menu_slot_2: {
            item.setChecked(true);
            itsYubiSlot = 2;
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

    @Override
    public void onClick(View view)
    {
        int id = view.getId();
        switch (id) {
        case R.id.view:
        case R.id.modify: {
            itsIsOpenReadonly = (id == R.id.view);
            if (itsYubikeyCb.isChecked()) {
                setPhase(Phase.YUBIKEY);
            } else {
                setPhase(Phase.OPENING);
            }
            break;
        }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean isChecked)
    {
        switch (button.getId()) {
        case R.id.save_password: {
            if (itsSavePasswdCb.isChecked()) {
                Context ctx = getContext();
                SharedPreferences prefs = Preferences.getSharedPrefs(ctx);
                if (!Preferences.isFileSavedPasswordConfirm(prefs)) {
                    FragmentManager fragMgr = getFragmentManager();
                    if (fragMgr == null) {
                        break;
                    }
                    ConfirmPromptDialog dlg = ConfirmPromptDialog.newInstance(
                            getString(R.string.save_password_p),
                            getString(R.string.save_password_warning),
                            getString(R.string.save), null);
                    dlg.setTargetFragment(this, 0);
                    dlg.show(fragMgr, "saveConfirm");
                }
            }
            break;
        }
        }
    }

    @Override
    public void promptCanceled()
    {
        itsSavePasswdCb.setChecked(false);
    }

    @Override
    public void promptConfirmed(Bundle confirmArgs)
    {
        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        Preferences.setFileSavedPasswordConfirmed(prefs);
    }

    @Override
    protected final void doResolveTaskFinished()
    {
        doSetFieldsEnabled(itsViewBtn.isEnabled());
        setPhase(Phase.WAITING_PASSWORD);
    }

    /**
     *  Derived-class handler when the fragment is canceled
     */
    @Override
    protected final void doCancelFragment(boolean userCancel)
    {
        Context ctx = getContext();
        if (ctx != null) {
            GuiUtils.setKeyboardVisible(itsPasswordEdit, ctx, false);
        }
        if (userCancel && itsListener != null) {
            itsListener.handleFileOpenCanceled();
        }
    }

    /**
     * Derived-class handler to enable/disable field controls during
     * background operations
     */
    @Override
    protected final void doSetFieldsEnabled(boolean enabled)
    {
        itsPasswordEdit.setEnabled(enabled);
        itsViewBtn.setEnabled(enabled);

        boolean modifyEnabled = enabled;
        boolean savePasswdEnabled = enabled;
        PasswdFileUri passwdFileUri = getPasswdFileUri();
        if (enabled && (passwdFileUri != null)) {
            Pair<Boolean, Integer> rc = getPasswdFileUri().isWritable();
            modifyEnabled = rc.first;

            switch (passwdFileUri.getType()) {
            case EMAIL: {
                savePasswdEnabled = false;
                break;
            }
            case FILE:
            case SYNC_PROVIDER:
            case GENERIC_PROVIDER: {
                break;
            }
            }
        }
        itsModifyBtn.setEnabled(modifyEnabled);
        itsSavePasswdCb.setEnabled(savePasswdEnabled);

        switch (itsYubiState) {
        case ENABLED: {
            itsYubikeyCb.setEnabled(enabled);
            break;
        }
        case DISABLED: {
            itsYubikeyCb.setEnabled(false);
            break;
        }
        case UNAVAILABLE: {
            break;
        }
        }
    }

    /**
     * Set the UI phase
     */
    private void setPhase(Phase newPhase)
    {
        if (newPhase == itsPhase) {
            return;
        }

        PasswdSafeUtil.dbginfo(TAG, "setPhase: %s", newPhase);
        switch (itsPhase) {
        case RESOLVING: {
            exitResolvingPhase();
            break;
        }
        case WAITING_PASSWORD: {
            itsUserPassword = itsPasswordEdit.getText().toString();
            cancelSavedPasswordUsers();
            break;
        }
        case YUBIKEY: {
            View root = getView();
            setVisibility(R.id.yubi_progress_text, false,
                          Objects.requireNonNull(root));
            setProgressVisible(false, false);
            setFieldsDisabled(false);
            itsYubiMgr.stop();
            break;
        }
        case SAVING_PASSWORD: {
            setProgressVisible(false, false);
            setFieldsDisabled(false);
            cancelSavedPasswordUsers();
            break;
        }
        case INITIAL:
        case OPENING:
        case FINISHED: {
            break;
        }
        }

        itsPhase = newPhase;

        switch (itsPhase) {
        case WAITING_PASSWORD: {
            enterWaitingPasswordPhase();
            break;
        }
        case YUBIKEY: {
            itsYubiUser = new YubikeyUser();
            itsYubiMgr.start(itsYubiUser);
            View root = getView();
            setVisibility(R.id.yubi_progress_text, true, root);
            setProgressVisible(true, false);
            setFieldsDisabled(true);
            break;
        }
        case OPENING: {
            enterOpeningPhase();
            break;
        }
        case SAVING_PASSWORD: {
            setFieldsDisabled(true);
            setProgressVisible(true, false);
            break;
        }
        case FINISHED: {
            PasswdSafeIME.resetKeyboard();
            break;
        }
        case INITIAL:
        case RESOLVING: {
            break;
        }
        }
    }

    /**
     * Exit the resolving phase
     */
    private void exitResolvingPhase()
    {
        setTitle(R.string.open_file);

        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        PasswdFileUri uri = getPasswdFileUri();
        if (uri != null) {
            Pair<Boolean, Integer> rc = uri.isWritable();
            if (!rc.first && (rc.second != null)) {
                itsReadonlyMsg.setText(getString(rc.second));
                GuiUtils.setVisible(itsReadonlyMsg, true);
            }
        }

        switch (itsYubiState) {
        case ENABLED: {
            itsYubikeyCb.setChecked(Preferences.getFileOpenYubikeyPref(prefs));
            break;
        }
        case DISABLED:
        case UNAVAILABLE: {
            itsYubikeyCb.setChecked(false);
            break;
        }
        }
    }

    /**
     * Enter the waiting password phase
     */
    private void enterWaitingPasswordPhase()
    {
        PasswdFileUri fileUri = getPasswdFileUri();
        if (fileUri == null) {
            cancelFragment(false);
            return;
        }
        itsIsPasswordSaved = false;
        switch (fileUri.getType()) {
        case FILE:
        case SYNC_PROVIDER:
        case GENERIC_PROVIDER: {
            itsIsPasswordSaved =
                    itsSavedPasswordsMgr.isAvailable() &&
                    itsSavedPasswordsMgr.isSaved(getPasswdFileUri());
        }
        case EMAIL: {
            break;
        }
        }

        GuiUtils.setupFormKeyboard(itsIsPasswordSaved ? null : itsPasswordEdit,
                                   itsPasswordEdit, itsViewBtn, getContext());
        GuiUtils.setVisible(itsSavedPasswordMsg, itsIsPasswordSaved);
        if (itsIsPasswordSaved) {
            cancelSavedPasswordUsers();
            setProgressVisible(true, false);
            itsLoadSavedPasswordUser = new LoadSavedPasswordUser();
            itsIsPasswordSaved = itsSavedPasswordsMgr.startPasswordAccess(
                    getPasswdFileUri(), itsLoadSavedPasswordUser);
        } else {
            itsPasswordEdit.requestFocus();
            checkOpenDefaultFile();
        }
        itsSavePasswdCb.setChecked(itsIsPasswordSaved);
    }

    /**
     * Enter the opening phase
     */
    private void enterOpeningPhase()
    {
        setTitle(R.string.loading_file);
        TextInputUtils.setTextInputError(null, itsPasswordInput);

        SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
        Preferences.setFileOpenYubikeyPref(itsYubikeyCb.isChecked(), prefs);

        boolean doSave = itsSavePasswdCb.isChecked();
        if (itsIsPasswordSaved && !doSave) {
            itsSaveChange = SavePasswordChange.REMOVE;
        } else if (!itsIsPasswordSaved && doSave) {
            itsSaveChange = SavePasswordChange.ADD;
        } else {
            itsSaveChange = SavePasswordChange.NONE;
        }

        Owner<PwsPassword> passwd =
                new Owner<>(new PwsPassword(itsPasswordEdit.getText()));
        try {
            startTask(new OpenTask(passwd.pass(), itsIsOpenReadonly, this));
        } finally {
            passwd.close();
        }
    }

    /**
     * Handle when the open task is finished
     */
    private void openTaskFinished(OpenResult result, Throwable error)
    {
        if (result != null) {
            switch (itsSaveChange) {
            case ADD: {
                if (result.itsKeygenError != null) {
                    String msg = getString(
                            R.string.password_save_canceled_key_error,
                            result.itsKeygenError.toString());
                    PasswdSafeUtil.showErrorMsg(msg,
                                                new ActContext(getContext()));
                    break;
                }

                cancelSavedPasswordUsers();
                itsAddSavedPasswordUser = new AddSavedPasswordUser(result);
                itsSavedPasswordsMgr.startPasswordAccess(
                        getPasswdFileUri(), itsAddSavedPasswordUser);
                setPhase(Phase.SAVING_PASSWORD);
                break;
            }
            case REMOVE: {
                itsSavedPasswordsMgr.removeSavedPassword(getPasswdFileUri());
                finishFileOpen(result.itsFileData);
                break;
            }
            case NONE: {
                finishFileOpen(result.itsFileData);
                break;
            }
            }
        } else if (error != null) {
            if (((error instanceof IOException) &&
                 TextUtils.equals(error.getMessage(), "Invalid password")) ||
                (error instanceof InvalidPassphraseException)) {
                if (itsRetries++ < 5) {
                    TextInputUtils.setTextInputError(
                            getString(R.string.invalid_password),
                            itsPasswordInput);

                    if (itsErrorClearingWatcher == null) {
                        itsErrorClearingWatcher = new ErrorClearingWatcher();
                        itsPasswordEdit.addTextChangedListener(
                                itsErrorClearingWatcher);
                    }
                } else {
                    PasswdSafeUtil.showFatalMsg(
                            getString(R.string.invalid_password), getActivity(),
                            false);
                }
            } else {
                PasswdSafeUtil.showFatalMsg(error, error.toString(),
                                            getActivity());
            }
            setPhase(Phase.WAITING_PASSWORD);
        } else {
            cancelFragment(false);
        }
    }

    /**
     *  Finish the file open fragment
     */
    private void finishFileOpen(PasswdFileData fileData)
    {
        setPhase(Phase.FINISHED);
        itsListener.handleFileOpen(fileData, itsRecToOpen);
    }

    /**
     * Set the title
     */
    private void setTitle(int label)
    {
        String title;
        PasswdFileUri passwdFileUri = getPasswdFileUri();
        if (passwdFileUri != null) {
            title = passwdFileUri.getIdentifier(getActivity(), true);
        } else {
            title = "";
        }
        //noinspection ConstantConditions
        if (PasswdSafeApp.DEBUG_AUTO_FILE != null) {
            title += " - AUTOOPEN!!!!!";
        }
        itsTitle.setText(getResources().getString(label, title));
    }

    /**
     * Cancel saved password operations
     */
    private void cancelSavedPasswordUsers()
    {
        if (itsLoadSavedPasswordUser != null) {
            itsLoadSavedPasswordUser.cancel();
            itsLoadSavedPasswordUser = null;
        }

        if (itsAddSavedPasswordUser != null) {
            itsAddSavedPasswordUser.cancel();
            itsAddSavedPasswordUser = null;
        }
    }

    /**
     * Check for opening default file
     */
    @SuppressLint("SetTextI18n")
    private void checkOpenDefaultFile()
    {
        //noinspection ConstantConditions
        if ((PasswdSafeApp.DEBUG_AUTO_FILE != null) &&
            (getFileUri().getPath().equals(PasswdSafeApp.DEBUG_AUTO_FILE))) {
            itsYubikeyCb.setChecked(false);
            itsPasswordEdit.setText("test123");
            itsModifyBtn.performClick();
        }
    }

    /**
     * Set visibility of a field
     */
    private static void setVisibility(int id, boolean visible, View parent)
    {
        View v = parent.findViewById(id);
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Result of opening a file
     */
    private static class OpenResult
    {
        public final PasswdFileData itsFileData;
        public final Exception itsKeygenError;

        /**
         * Constructor
         */
        public OpenResult(PasswdFileData fileData, Exception keygenError)
        {
            itsFileData = fileData;
            itsKeygenError = keygenError;
        }
    }

    /**
     * Background task for opening the file
     */
    private static final class OpenTask
            extends BackgroundTask<OpenResult, PasswdSafeOpenFileFragment>
    {
        private final PasswdFileUri itsFileUri;
        private final Owner<PwsPassword> itsPassword;
        private final boolean itsItsIsReadOnly;
        private final boolean itsIsYubikey;
        private final SavePasswordChange itsSaveChange;
        private final SavedPasswordsMgr itsSavedPasswordsMgr;

        /**
         * Constructor
         */
        public OpenTask(Owner<PwsPassword>.Param passwd,
                        boolean itsIsReadOnly,
                        PasswdSafeOpenFileFragment frag)
        {
            super(frag);
            itsFileUri = frag.getPasswdFileUri();
            itsPassword = passwd.use();
            itsItsIsReadOnly = itsIsReadOnly;
            itsIsYubikey = frag.itsIsYubikey;
            itsSaveChange = frag.itsSaveChange;
            itsSavedPasswordsMgr = frag.itsSavedPasswordsMgr;
        }

        @Override
        protected OpenResult doInBackground() throws Exception
        {
            PasswdFileData fileData = new PasswdFileData(itsFileUri);
            fileData.setYubikey(itsIsYubikey);
            fileData.load(itsPassword.pass(), itsItsIsReadOnly, getContext());

            Exception keygenError = null;
            switch (itsSaveChange) {
            case ADD: {
                if (Looper.myLooper() == null) {
                    Looper.prepare();
                }
                try {
                    itsSavedPasswordsMgr.generateKey(itsFileUri);
                } catch (InvalidAlgorithmParameterException |
                        NoSuchAlgorithmException | NoSuchProviderException |
                        IOException e) {
                    keygenError = e;
                }
                break;
            }
            case REMOVE:
            case NONE: {
                break;
            }
            }

            return new OpenResult(fileData, keygenError);
        }

        @Override
        protected void onTaskFinished(OpenResult result,
                                      Throwable error,
                                      @NonNull PasswdSafeOpenFileFragment frag)
        {
            super.onTaskFinished(result, error, frag);
            try {
                frag.openTaskFinished(result, error);
            } finally {
                itsPassword.close();
            }
        }
    }

    /**
     * User of the YubikeyMgr
     */
    private class YubikeyUser implements YubikeyMgr.User
    {
        @Override
        public Activity getActivity()
        {
            return PasswdSafeOpenFileFragment.this.getActivity();
        }

        @Override
        public String getUserPassword()
        {
            return itsUserPassword;
        }

        @Override
        public void finish(String password, Exception e)
        {
            boolean haveUser = (itsYubiUser != null);
            itsYubiUser = null;
            if (password != null) {
                itsIsYubikey = true;
                itsPasswordEdit.setText(password);
                setPhase(Phase.OPENING);
            } else if (e != null) {
                Activity act = getActivity();
                PasswdSafeUtil.showFatalMsg(
                        e, act.getString(R.string.yubikey_error), act);
            } else if (haveUser) {
                setPhase(Phase.WAITING_PASSWORD);
            }
        }

        @Override
        public int getSlotNum()
        {
            return itsYubiSlot;
        }

        @Override
        public void timerTick(int totalTime, int remainingTime)
        {
            ProgressBar progress = getProgress();
            progress.setMax(totalTime);
            progress.setProgress(remainingTime);
        }
    }

    /**
     * How a saved password access is finished
     */
    private enum SavedPasswordFinish
    {
        /** Success */
        SUCCESS,
        /** Fragment canceled, no delayed operations should be added */
        FRAGMENT_CANCEL,
        /** Timeout */
        TIMEOUT,
        /** Error */
        ERROR
    }

    /**
     * Base user for accessing a saved password
     */
    private abstract class AbstractSavedPasswordUser
            extends SavedPasswordsMgr.User
    {
        protected final String itsTag;
        private final int itsStartMsgId;
        private final CountDownTimer itsCancelTimer;
        private Runnable itsPendingAction;
        private boolean itsIsCancelTimeout = false;
        private boolean itsIsFinished = false;
        private boolean itsIsIgnoreErrorAfterCancel = false;

        /**
         * Constructor
         */
        protected AbstractSavedPasswordUser(int startMsgId, String tag)
        {
            itsTag = tag;
            itsStartMsgId = startMsgId;
            itsCancelTimer = new CountDownTimer(30 * 1000, 1 * 1000)
            {
                @Override
                public void onTick(long millisUntilFinished)
                {
                    ProgressBar progress = getProgress();
                    progress.setMax(30);
                    progress.setProgress((int)millisUntilFinished / 1000);
                }

                @Override
                public void onFinish()
                {
                    getProgress().setProgress(0);
                    itsIsCancelTimeout = true;
                    AbstractSavedPasswordUser.this.cancel();
                }
            };
        }

        @Override
        public final void cancel()
        {
            cancelPendingAction();
            super.cancel();
        }

        @Override
        public final void onAuthenticationError(int errMsgId,
                                                CharSequence errString)
        {
            if (itsIsIgnoreErrorAfterCancel) {
                itsIsIgnoreErrorAfterCancel = false;
            } else {
                PasswdSafeUtil.dbginfo(itsTag, "error: %s", errString);
                finish(SavedPasswordFinish.ERROR, errString);
            }
        }

        @Override
        public final void onAuthenticationFailed()
        {
            PasswdSafeUtil.dbginfo(itsTag, "failed");
            setNotificationMsg(getString(R.string.fingerprint_not_recognized),
                               itsStartMsgId);
        }

        @Override
        public final void onAuthenticationHelp(int helpMsgId,
                                               CharSequence helpString)
        {
            PasswdSafeUtil.dbginfo(itsTag, "help: %s", helpString);
            setNotificationMsg(helpString, itsStartMsgId);
        }

        @Override
        protected final void onStart()
        {
            PasswdSafeUtil.dbginfo(itsTag, "onStart");
            itsCancelTimer.start();
            itsSavedPasswordMsg.setTextColor(itsSavedPasswordTextColor);
            itsSavedPasswordMsg.setText(itsStartMsgId);
            GuiUtils.setVisible(itsSavedPasswordMsg, true);
        }

        @Override
        public final void onCancel()
        {
            PasswdSafeUtil.dbginfo(itsTag, "onCancel");
            SavedPasswordFinish finishMode;
            if (itsIsCancelTimeout) {
                itsIsIgnoreErrorAfterCancel = true;
                finishMode = SavedPasswordFinish.TIMEOUT;
            } else {
                finishMode = SavedPasswordFinish.FRAGMENT_CANCEL;
            }
            finish(finishMode, getString(R.string.canceled));
        }

        /**
         * Finish access to the saved passwords
         */
        protected final void finish(SavedPasswordFinish finishMode,
                                    CharSequence msg)
        {
            if (itsIsFinished) {
                return;
            }
            itsIsFinished = true;
            itsCancelTimer.cancel();

            GuiUtils.setVisible(itsSavedPasswordMsg, true);
            itsSavedPasswordMsg.setText(msg);
            int textColor = itsSavedPasswordTextColor;
            boolean resolve = false;
            switch (finishMode) {
            case SUCCESS: {
                textColor = R.attr.textColorGreen;
                resolve = true;
                break;
            }
            case ERROR: {
                textColor = R.attr.textColorError;
                resolve = true;
                break;
            }
            case FRAGMENT_CANCEL:
            case TIMEOUT: {
                break;
            }
            }

            if (resolve) {
                Context ctx = getContext();
                if (ctx != null) {
                    TypedValue value = new TypedValue();
                    ctx.getTheme().resolveAttribute(textColor, value,
                                                    true);
                    textColor = value.data;
                }
            }
            itsSavedPasswordMsg.setTextColor(textColor);
            handleFinish(finishMode);

            switch (finishMode) {
            case FRAGMENT_CANCEL: {
                cancelPendingAction();
                break;
            }
            case SUCCESS:
            case ERROR:
            case TIMEOUT: {
                break;
            }
            }
        }

        /**
         * Derived-class callback for finishing access to the saved passwords
         */
        protected abstract void handleFinish(SavedPasswordFinish finishMode);

        /**
         * Perform a delayed action
         */
        protected final void doDelayed(final Runnable action)
        {
            cancelPendingAction();
            itsPendingAction = () -> {
                action.run();
                itsPendingAction = null;
                itsSavedPasswordMsg.setTextColor(itsSavedPasswordTextColor);
            };
            itsSavedPasswordMsg.postDelayed(itsPendingAction, 2000);
        }

        /**
         * Temporarily set a notification message for the user
         */
        private void setNotificationMsg(CharSequence msg, final int baseMsgId)
        {
            itsSavedPasswordMsg.setText(msg);
            doDelayed(() -> itsSavedPasswordMsg.setText(baseMsgId));
        }

        /**
         * Cancel a pending action
         */
        private void cancelPendingAction()
        {
            if (itsPendingAction != null) {
                itsSavedPasswordMsg.removeCallbacks(itsPendingAction);
                itsPendingAction = null;
            }
        }
    }

    /**
     * User for loading a saved password
     */
    private final class LoadSavedPasswordUser extends AbstractSavedPasswordUser
    {
        /**
         * Constructor
         */
        public LoadSavedPasswordUser()
        {
            super(R.string.touch_sensor_to_load_saved_password,
                  "LoadSavedPasswordUser");
        }

        @Override
        public void onAuthenticationSucceeded(
                FingerprintManagerCompat.AuthenticationResult result)
        {
            if (itsSavedPasswordsMgr == null) {
                onCancel();
                return;
            }
            PasswdSafeUtil.dbginfo(itsTag, "success");
            Cipher cipher = result.getCryptoObject().getCipher();
            try {
                String password = itsSavedPasswordsMgr.loadSavedPassword(
                        getPasswdFileUri(), cipher);
                itsPasswordEdit.setText(password);
                finish(SavedPasswordFinish.SUCCESS,
                       getString(R.string.password_loaded));
            } catch (IllegalBlockSizeException | BadPaddingException |
                    IOException e) {
                String msg = "Error using cipher: " + e;
                Log.e(itsTag, msg, e);
                onAuthenticationError(0, msg);
            }
        }

        @Override
        protected boolean isEncrypt()
        {
            return false;
        }

        @Override
        protected void handleFinish(SavedPasswordFinish finishMode)
        {
            setProgressVisible(false, false);
            switch (finishMode) {
            case SUCCESS: {
                doDelayed(() -> {
                    itsLoadSavedPasswordUser = null;
                    if (itsYubikeyCb.isChecked()) {
                        setPhase(Phase.YUBIKEY);
                    } else {
                        setPhase(Phase.OPENING);
                    }
                });
                break;
            }
            case ERROR:
            case FRAGMENT_CANCEL:
            case TIMEOUT: {
                itsLoadSavedPasswordUser = null;
                break;
            }
            }
        }
    }

    /**
     * User for adding a saved password
     */
    private final class AddSavedPasswordUser extends AbstractSavedPasswordUser
    {
        private final OpenResult itsOpenResult;

        /**
         * Constructor
         */
        public AddSavedPasswordUser(OpenResult result)
        {
            super(R.string.touch_sensor_to_save_the_password,
                  "AddSavedPasswordUser");
            itsOpenResult = result;
        }

        @Override
        public void onAuthenticationSucceeded(
                FingerprintManagerCompat.AuthenticationResult result)
        {
            if (itsSavedPasswordsMgr == null) {
                onCancel();
                return;
            }
            PasswdSafeUtil.dbginfo(itsTag, "success");
            Cipher cipher = result.getCryptoObject().getCipher();
            try {
                itsSavedPasswordsMgr.addSavedPassword(
                        getPasswdFileUri(), itsUserPassword,
                        Objects.requireNonNull(cipher));
                finish(SavedPasswordFinish.SUCCESS,
                       getString(R.string.password_saved));
            } catch (Exception e) {
                String msg = "Error using cipher: " + e;
                Log.e(itsTag, msg, e);
                onAuthenticationError(0, msg);
            }
        }

        @Override
        protected boolean isEncrypt()
        {
            return true;
        }

        @Override
        protected void handleFinish(SavedPasswordFinish finishMode)
        {
            switch (finishMode) {
            case SUCCESS: {
                doDelayed(() -> {
                    itsAddSavedPasswordUser = null;
                    finishFileOpen(itsOpenResult.itsFileData);
                });
                break;
            }
            case ERROR:
            case TIMEOUT: {
                doDelayed(() -> {
                    itsAddSavedPasswordUser = null;
                    setPhase(Phase.WAITING_PASSWORD);
                });
                break;
            }
            case FRAGMENT_CANCEL: {
                itsAddSavedPasswordUser = null;
                break;
            }
            }
        }
    }

    /**
     * Text watcher to clear the invalid password message
     */
    private final class ErrorClearingWatcher extends AbstractTextWatcher
    {
        @Override
        public void afterTextChanged(Editable s)
        {
            TextInputUtils.setTextInputError(null, itsPasswordInput);
            itsPasswordEdit.removeTextChangedListener(itsErrorClearingWatcher);
            itsErrorClearingWatcher = null;
        }
    }
}
