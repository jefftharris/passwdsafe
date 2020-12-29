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
import android.os.Looper;
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
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricConstants;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.textfield.TextInputLayout;
import com.jefftharris.passwdsafe.file.PasswdFileData;
import com.jefftharris.passwdsafe.file.PasswdFileUri;
import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.AbstractTextWatcher;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TextInputUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.util.Pair;
import com.jefftharris.passwdsafe.util.YubiState;
import com.jefftharris.passwdsafe.view.ConfirmPromptDialog;

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
    private EditText itsPasswordEdit;
    private TextView itsSavedPasswordMsg;
    private int itsSavedPasswordTextColor;
    private TextView itsReadonlyMsg;
    private CheckBox itsSavePasswdCb;
    private CheckBox itsYubikeyCb;
    private Button itsOpenBtn;
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
    private Owner<PwsPassword> itsOpenPassword;
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
        Context ctx = requireContext();

        itsTitle = rootView.findViewById(R.id.file);
        itsPasswordInput = rootView.findViewById(R.id.passwd_input);
        itsPasswordEdit = rootView.findViewById(R.id.passwd_edit);
        TypefaceUtils.setMonospace(itsPasswordEdit, ctx);
        itsPasswordEdit.setEnabled(false);

        itsReadonlyMsg = rootView.findViewById(R.id.read_only_msg);
        GuiUtils.setVisible(itsReadonlyMsg, false);
        itsOpenBtn = rootView.findViewById(R.id.open);
        itsOpenBtn.setOnClickListener(this);
        itsOpenBtn.setEnabled(false);

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
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
        itsSavedPasswordsMgr = new SavedPasswordsMgr(ctx);
        itsSavedPasswordsMgr.attach(this);
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
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if (itsYubiMgr != null) {
            itsYubiMgr.stop();
        }
        setOpenPassword(null);
        setPhase(Phase.INITIAL);
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
        if (itsSavedPasswordsMgr != null) {
            itsSavedPasswordsMgr.detach();
            itsSavedPasswordsMgr = null;
        }
    }

    /** Handle a new intent */
    public void onNewIntent(Intent intent)
    {
        if (itsYubiMgr != null) {
            itsYubiMgr.handleKeyIntent(intent);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu,
                                    @NonNull MenuInflater inflater)
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
        case R.id.open: {
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
        doSetFieldsEnabled(itsOpenBtn.isEnabled());
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
        itsOpenBtn.setEnabled(enabled);

        boolean savePasswdEnabled = enabled;
        PasswdFileUri passwdFileUri = getPasswdFileUri();
        if (enabled && (passwdFileUri != null)) {
            switch (passwdFileUri.getType()) {
            case EMAIL:
            case BACKUP: {
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
            try (Owner<PwsPassword> password =
                         PwsPassword.create(itsPasswordEdit)) {
                setOpenPassword(password.pass());
            }
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
            setFieldsDisabled(false);
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
            View root = requireView();
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
            break;
        }
        case FINISHED: {
            PasswdSafeIME.resetKeyboard();
            break;
        }
        case INITIAL: {
            setOpenPassword(null);
            GuiUtils.clearEditText(itsPasswordEdit);
            break;
        }
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
            break;
        }
        case EMAIL:
        case BACKUP: {
            break;
        }
        }

        GuiUtils.setupFormKeyboard(itsIsPasswordSaved ? null : itsPasswordEdit,
                                   itsPasswordEdit, itsOpenBtn, getContext());
        GuiUtils.setVisible(itsSavedPasswordMsg, itsIsPasswordSaved);
        if (itsIsPasswordSaved) {
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

        startTask(new OpenTask(itsOpenPassword.pass(), this));
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
            itsOpenBtn.performClick();
        }
    }

    /**
     * Set the password used to open a file
     */
    private void setOpenPassword(Owner<PwsPassword>.Param password)
    {
        if (itsOpenPassword != null) {
            itsOpenPassword.close();
            itsOpenPassword = null;
        }
        if (password != null) {
            itsOpenPassword = password.use();
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
        protected final PasswdFileData itsFileData;
        protected final Exception itsKeygenError;

        /**
         * Constructor
         */
        protected OpenResult(PasswdFileData fileData, Exception keygenError)
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
        private final boolean itsIsYubikey;
        private final SavePasswordChange itsSaveChange;
        private final SavedPasswordsMgr itsSavedPasswordsMgr;

        /**
         * Constructor
         */
        protected OpenTask(Owner<PwsPassword>.Param passwd,
                           PasswdSafeOpenFileFragment frag)
        {
            super(frag);
            itsFileUri = frag.getPasswdFileUri();
            itsPassword = passwd.use();
            itsIsYubikey = frag.itsIsYubikey;
            itsSaveChange = frag.itsSaveChange;
            itsSavedPasswordsMgr = frag.itsSavedPasswordsMgr;
        }

        @Override
        protected OpenResult doInBackground() throws Exception
        {
            PasswdFileData fileData = new PasswdFileData(itsFileUri);
            fileData.setYubikey(itsIsYubikey);
            fileData.load(itsPassword.pass(), getContext());

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

        @Override @CheckResult
        public Owner<PwsPassword> getUserPassword()
        {
            return itsOpenPassword.pass().use();
        }

        @Override
        public void finish(Owner<PwsPassword>.Param password, Exception e)
        {
            boolean haveUser = (itsYubiUser != null);
            itsYubiUser = null;
            if (password != null) {
                itsIsYubikey = true;
                setOpenPassword(password);
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
        private boolean itsIsFinished = false;

        /**
         * Constructor
         */
        protected AbstractSavedPasswordUser(String tag)
        {
            itsTag = tag;
        }

        @Override
        public final void onAuthenticationError(int errorCode,
                                                @NonNull CharSequence errString)
        {
            PasswdSafeUtil.dbginfo(itsTag, "error: %s", errString);
            switch (errorCode) {
            case BiometricConstants.ERROR_CANCELED:
            case BiometricConstants.ERROR_USER_CANCELED:
            case BiometricConstants.ERROR_NEGATIVE_BUTTON: {
                Context ctx = getContext();
                if (ctx != null) {
                    errString = ctx.getString(R.string.canceled);
                }
                break;
            }
            case BiometricConstants.ERROR_HW_NOT_PRESENT:
            case BiometricConstants.ERROR_HW_UNAVAILABLE:
            case BiometricConstants.ERROR_LOCKOUT_PERMANENT:
            case BiometricConstants.ERROR_LOCKOUT:
            case BiometricConstants.ERROR_NO_BIOMETRICS:
            case BiometricConstants.ERROR_NO_DEVICE_CREDENTIAL:
            case BiometricConstants.ERROR_NO_SPACE:
            case BiometricConstants.ERROR_TIMEOUT:
            case BiometricConstants.ERROR_UNABLE_TO_PROCESS:
            case BiometricConstants.ERROR_VENDOR: {
                break;
            }
            }
            finish(SavedPasswordFinish.ERROR, errString);
        }

        @Override
        public final void onAuthenticationFailed()
        {
            PasswdSafeUtil.dbginfo(itsTag, "failed");
        }

        /**
         * Finish access to the saved passwords
         */
        protected final void finish(SavedPasswordFinish finishMode,
                                    @NonNull CharSequence msg)
        {
            if (itsIsFinished) {
                return;
            }
            itsIsFinished = true;

            GuiUtils.setVisible(itsSavedPasswordMsg, true);
            itsSavedPasswordMsg.setText(msg);
            int textColor = itsSavedPasswordTextColor;
            switch (finishMode) {
            case SUCCESS: {
                textColor = R.attr.textColorGreen;
                break;
            }
            case ERROR: {
                textColor = R.attr.colorError;
                break;
            }
            }

            Context ctx = getContext();
            if (ctx != null) {
                TypedValue value = new TypedValue();
                ctx.getTheme().resolveAttribute(textColor, value, true);
                textColor = value.data;
            }
            itsSavedPasswordMsg.setTextColor(textColor);
            handleFinish(finishMode);
        }

        /**
         * Derived-class callback for finishing access to the saved passwords
         */
        protected abstract void handleFinish(SavedPasswordFinish finishMode);
    }

    /**
     * User for loading a saved password
     */
    private final class LoadSavedPasswordUser extends AbstractSavedPasswordUser
    {
        /**
         * Constructor
         */
        protected LoadSavedPasswordUser()
        {
            super("LoadSavedPasswordUser");
            setFieldsDisabled(true);
        }

        @Override
        public void onAuthenticationSucceeded(
                @NonNull BiometricPrompt.AuthenticationResult result)
        {
            BiometricPrompt.CryptoObject resultCrypto =
                    result.getCryptoObject();
            Cipher cipher =
                    (resultCrypto != null) ? resultCrypto.getCipher() : null;
            if ((itsSavedPasswordsMgr == null) || (cipher == null)) {
                return;
            }
            PasswdSafeUtil.dbginfo(itsTag, "success");
            try (Owner<PwsPassword> password =
                         itsSavedPasswordsMgr.loadSavedPassword(
                                 getPasswdFileUri(), cipher)) {
                password.get().setInto(itsPasswordEdit);
                finish(SavedPasswordFinish.SUCCESS,
                       getString(R.string.password_loaded));
            } catch (IllegalBlockSizeException | BadPaddingException |
                    IOException e) {
                String msg = "Error using cipher: " + e;
                Log.e(itsTag, msg, e);
                onAuthenticationError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                                      msg);
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
            setFieldsDisabled(false);
            itsLoadSavedPasswordUser = null;
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
        protected AddSavedPasswordUser(OpenResult result)
        {
            super("AddSavedPasswordUser");
            itsOpenResult = result;
        }

        @Override
        public void onAuthenticationSucceeded(
                @NonNull BiometricPrompt.AuthenticationResult result)
        {
            BiometricPrompt.CryptoObject resultCrypto =
                    result.getCryptoObject();
            Cipher cipher =
                    (resultCrypto != null) ? resultCrypto.getCipher() : null;
            if ((itsSavedPasswordsMgr == null) || (cipher == null)) {
                return;
            }
            PasswdSafeUtil.dbginfo(itsTag, "success");
            try (Owner<PwsPassword> savePassword =
                         PwsPassword.create(itsPasswordEdit)) {
                itsSavedPasswordsMgr.addSavedPassword(
                        getPasswdFileUri(), savePassword.pass(), cipher);
                finish(SavedPasswordFinish.SUCCESS,
                       getString(R.string.password_saved));
            } catch (Exception e) {
                String msg = "Error using cipher: " + e;
                Log.e(itsTag, msg, e);
                onAuthenticationError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                                      msg);
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
            itsAddSavedPasswordUser = null;
            switch (finishMode) {
            case SUCCESS: {
                finishFileOpen(itsOpenResult.itsFileData);
                break;
            }
            case ERROR: {
                setPhase(Phase.WAITING_PASSWORD);
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
