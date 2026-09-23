/*
 * Copyright (©) 2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.jefftharris.passwdsafe.PasswdSafeRecordTotpViewModel;
import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.file.Totp;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.AbstractDialogClickListener;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;

import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;

/**
 * The AuthCodeEditDialog class enapsulates the functionality for editing an
 * authentication code
 */
public class AuthCodeEditDialog extends AppCompatDialogFragment
        implements CompoundButton.OnCheckedChangeListener
{
    private enum Result
    {
        OK,
        DELETE
    }

    private static final String REQUEST_KEY = "AuthCodeEditDialog";
    private static final String TAG = REQUEST_KEY;

    private PasswdSafeRecordTotpViewModel itsRecordTotpViewModel;
    private AuthCodeViewModel itsViewModel;
    private EditText itsSecretKey;
    private CheckBox itsAllowDelete;
    private TextView itsWarning;
    private Validator itsValidator;


    /**
     * Client for showing and checking result of the dialog
     */
    public static class Client extends DialogClient
    {
        /**
         * Constructor for a fragment
         */
        public <T extends Fragment & FragmentResultListener> Client(
                @NonNull T listener, @NonNull String id)
        {
            // Use child frag manager for dialog vs typical fragment parent
            // manager
            super(REQUEST_KEY, id, listener.getChildFragmentManager(), listener,
                  listener);
        }

        /**
         * Show the dialog
         */
        public void show()
        {
            Bundle args = new Bundle();
            doShow(new AuthCodeEditDialog(), args);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        itsRecordTotpViewModel =
                new ViewModelProvider(requireParentFragment()).get(
                        PasswdSafeRecordTotpViewModel.class);
        itsRecordTotpViewModel
                .getConfig()
                .observe(this, this::onTotpConfigChanged);
        itsViewModel = new ViewModelProvider(this).get(AuthCodeViewModel.class);
        itsViewModel.getEditTotp().observe(this, this::onEditTotpChanged);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
    {
        var ctx = requireContext();
        var layoutFactory = LayoutInflater.from(ctx);
        @SuppressLint("InflateParams") var view =
                layoutFactory.inflate(R.layout.auth_code_edit, null);
        createView(view, ctx);

        AbstractDialogClickListener dlgClick = new AbstractDialogClickListener()
        {
            @Override
            public void onOkClicked(@NonNull DialogInterface dialog)
            {
                dialog.dismiss();
                setResult(Result.OK);
            }

            @Override
            public void onNeutralClicked(@NonNull DialogInterface dialog)
            {
                dialog.dismiss();
                setResult(Result.DELETE);
            }
        };

        AlertDialog.Builder alert = new AlertDialog.Builder(ctx)
                .setTitle(R.string.edit_authentication_code)
                .setView(view)
                .setPositiveButton(R.string.ok, dlgClick)
                .setNegativeButton(R.string.cancel, dlgClick)
                .setNeutralButton(R.string.delete, dlgClick)
                .setOnCancelListener(dlgClick);
        AlertDialog dialog = alert.create();

        PasswdSafeUtil.dbginfo(TAG, "onCreateDialog new validator");
        itsValidator = new Validator(dialog, view, ctx);
        itsValidator.registerTextView(itsSecretKey);

        return dialog;
    }

    @Override
    public void onCheckedChanged(@NonNull CompoundButton buttonView,
                                 boolean isChecked)
    {
        int buttonId = buttonView.getId();
        if (buttonId == R.id.allow_delete) {
            if (itsValidator != null) {
                itsValidator.validate();
            }
        }
    }

    private void createView(@NonNull View view, @NonNull Context ctx)
    {
        itsSecretKey = view.findViewById(R.id.totp_secret_key);
        TypefaceUtils.setMonospace(itsSecretKey, ctx);

        itsAllowDelete = view.findViewById(R.id.allow_delete);
        itsAllowDelete.setChecked(false);
        itsAllowDelete.setOnCheckedChangeListener(this);

        itsWarning = view.findViewById(R.id.warning);

        setInitTotpView();
    }

    @SuppressLint("SetTextI18n")
    private void setInitTotpView()
    {
        try {
            setValidatorPaused(true);

            var editTotp = itsViewModel.getEditTotp().getValue();
            if (editTotp != null) {
                var totpVal = editTotp.get();
                try (var secretKey = totpVal.getSecretKey()) {
                    secretKey.get().setInto(itsSecretKey);
                }
            } else {
                GuiUtils.clearEditText(itsSecretKey);
            }
        } finally {
            setValidatorPaused(false);
        }
    }

    private void onTotpConfigChanged(
            @Nullable PasswdSafeRecordTotpViewModel.Config config)
    {
        try (var totp = (config != null) ? config.getTotp() : null) {
            PasswdSafeUtil.dbginfo(TAG, "onTotpChanged config: hastotp %b",
                                   (totp != null));
            boolean init =
                    itsViewModel.handleTotpConfigChanged(Owner.maybePass(totp));
            if (init) {
                setInitTotpView();
            }
        }
    }

    private void onEditTotpChanged(@Nullable Owner<Totp> editTotp)
    {
        PasswdSafeUtil.dbginfo(TAG, "onEditTotpChanged: hastotp %b",
                               (editTotp != null));
        GuiUtils.setVisible(itsWarning, false);
        itsWarning.setText(null);
    }

    @Nullable
    private Owner<Totp> getUpdatedTotp()
    {
        try (var secretKeyVal = PwsPassword.create(itsSecretKey)) {
            if (secretKeyVal.get().length() == 0) {
                return null;
            }

            var hash = Totp.Hash.SHA1;
            int numDigits = Totp.DEFAULT_NUM_DIGITS;
            long timeStep = Totp.DEFAULT_TIME_STEP;
            long timeStart = Totp.T0;

            return new Owner<>(
                    new Totp(secretKeyVal.pass(), hash, numDigits, timeStep,
                             timeStart));
        }
    }

    private void setValidatorPaused(boolean paused)
    {
        if (itsValidator != null) {
            itsValidator.setPaused(paused);
        }
    }

    @Nullable
    private String validateTotp()
    {
        try (var editTotp = getUpdatedTotp()) {
            itsViewModel.setEditTotp(Owner.maybePass(editTotp));
            if (editTotp != null) {
                var status = editTotp.get().getStatus();
                switch (status) {
                case OK -> {
                }
                case INVALID_SECRET_KEY -> {
                    return getString(R.string.authentication_key_invalid);
                }
                case INVALID_NUM_DIGITS -> {
                    return getString(
                            R.string.authentication_num_digits_invalid);
                }
                case INVALID_TIME_STEP -> {
                    return getString(R.string.authentication_time_step_invalid);
                }
                case INVALID_TIME_START -> {
                    return getString(
                            R.string.authentication_time_start_invalid);
                }
                case INVALID_ALGORITHM -> {
                    return getString(R.string.unknown_hash_algorithm);
                }
                }
            }
        } catch (Exception e) {
            itsViewModel.setEditTotp(null);
            return e.getMessage();
        }

        return null;
    }

    private void setResult(@NonNull Result result)
    {
        switch (result) {
        case OK -> itsRecordTotpViewModel.setTotp(
                Owner.maybePass(itsViewModel.getEditTotp().getValue()));
        case DELETE -> itsRecordTotpViewModel.setTotp(null);
        }

        Client.setResult(requireArguments(), new Bundle(),
                         getParentFragmentManager());
    }

    public static class AuthCodeViewModel extends ViewModel
    {
        private static final String TAG = "AuthCodeEditDialogVM";

        private final CloseableLiveData<Owner<Totp>> itsEditTotp =
                new CloseableLiveData<>(null);
        private boolean itsIsEditInit = false;

        private boolean handleTotpConfigChanged(
                @Nullable Owner<Totp>.Param configTotp)
        {
            PasswdSafeUtil.dbginfo(TAG, "totpConfigChanged init %b, " +
                                        "configTotp %b",
                                   itsIsEditInit,
                                   (configTotp != null));
            if (!itsIsEditInit) {
                itsIsEditInit = true;
                setEditTotp(configTotp);
                return true;
            } else {
                return false;
            }
        }

        private LiveData<Owner<Totp>> getEditTotp()
        {
            return itsEditTotp;
        }

        private void setEditTotp(@Nullable Owner<Totp>.Param editTotp)
        {
            PasswdSafeUtil.dbginfo(TAG, "setTotp totp %b -> %b",
                                   itsEditTotp.getValue() != null,
                                   editTotp != null);
            var currTotp = itsEditTotp.getValue();
            if (currTotp != null) {
                if (editTotp != null) {
                    try (var editTotpVal = editTotp.use()) {
                        if (!currTotp.get().equals(editTotpVal.get())) {
                            itsEditTotp.setValue(editTotpVal.pass().use());
                        }
                    }
                } else {
                    itsEditTotp.setValue(null);
                }
            } else {
                if (editTotp != null) {
                    itsEditTotp.setValue(editTotp.use());
                }
            }
        }

        @Override
        protected void onCleared()
        {
            super.onCleared();
            itsEditTotp.close();
        }
    }

    /**
     * Validator
     */
    private class Validator extends DialogValidator.AlertCompatValidator
            implements AdapterView.OnItemSelectedListener

    {
        /**
         * Constructor
         */
        private Validator(AlertDialog dlg, View view, Context ctx)
        {
            super(dlg, view, ctx);

        }

        @Override
        protected String doValidation()
        {
            return validateTotp();
        }

        @Override
        protected boolean doCheckNeutralEnabled(boolean valid)
        {
            return super.doCheckNeutralEnabled(valid) &&
                   itsAllowDelete.isChecked();
        }

        @Override
        public void onItemSelected(AdapterView<?> parent,
                                   View view,
                                   int position,
                                   long id)
        {
            validate();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent)
        {
        }
    }
}
