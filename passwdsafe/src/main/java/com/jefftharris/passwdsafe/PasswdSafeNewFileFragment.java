/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.jefftharris.passwdsafe.db.PasswdSafeDb;
import com.jefftharris.passwdsafe.db.RecentFilesDao;
import com.jefftharris.passwdsafe.file.PasswdFileData;
import com.jefftharris.passwdsafe.file.PasswdFileUri;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.AbstractTextWatcher;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TextInputUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.util.CountedBool;
import com.jefftharris.passwdsafe.view.PasswordVisibilityMenuHandler;

import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsPassword;


/**
 * Fragment for creating a new file
 */
public class PasswdSafeNewFileFragment
        extends AbstractPasswdSafeOpenNewFileFragment
        implements View.OnClickListener
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Handle when the file new is canceled */
        void handleFileNewCanceled();

        /** Handle when the file was successfully created */
        void handleFileNew(PasswdFileData fileData);

        /** Update the view for creating a new file */
        void updateViewFileNew();
    }

    private Listener itsListener;
    private String itsPsafe3Sfx;
    private TextView itsTitle;
    private TextInputLayout itsFileNameInput;
    private EditText itsFileName;
    private TextInputLayout itsPasswordInput;
    private EditText itsPassword;
    private TextInputLayout itsPasswordConfirmInput;
    private EditText itsPasswordConfirm;
    private Button itsOkBtn;
    private final CountedBool itsBackgroundDisable = new CountedBool();
    private final Validator itsValidator = new Validator();
    private boolean itsUseStorage = false;

    private static final String ARG_URI = "uri";

    private static final int CREATE_DOCUMENT_REQUEST = 0;

    private static final String TAG = "PasswdSafeNewFileFrag";

    /**
     * Create a new instance
     */
    @NonNull
    public static PasswdSafeNewFileFragment newInstance(Uri newFileUri)
    {
        PasswdSafeNewFileFragment fragment = new PasswdSafeNewFileFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, newFileUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            Uri uri = args.getParcelable(ARG_URI);
            itsUseStorage = (uri == null);
            setFileUri(uri);
            setDoResolveOnStart(!itsUseStorage);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        View rootView = inflater.inflate(R.layout.fragment_passwdsafe_new_file,
                                         container, false);
        setupView(rootView);

        itsPsafe3Sfx = getString(R.string.psafe3_ext);
        itsTitle = rootView.findViewById(R.id.title);
        if (itsUseStorage) {
            itsTitle.setText(R.string.new_file);
        }
        itsFileNameInput = rootView.findViewById(R.id.file_name_input);
        itsFileName = rootView.findViewById(R.id.file_name);
        itsFileName.setText(itsPsafe3Sfx);
        itsFileName.post(() -> itsFileName.setSelection(0));
        itsFileName.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after)
            {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count)
            {
            }

            @Override
            public void afterTextChanged(Editable s)
            {
                if (!s.toString().endsWith(itsPsafe3Sfx)) {
                    s.replace(0, s.length(), itsPsafe3Sfx);
                    itsFileName.setSelection(0);
                }
            }
        });
        itsValidator.registerTextView(itsFileName);

        Context ctx = getContext();
        itsPasswordInput = rootView.findViewById(R.id.password_input);
        itsPassword = rootView.findViewById(R.id.password);
        TypefaceUtils.setMonospace(itsPassword, ctx);
        itsValidator.registerTextView(itsPassword);
        itsPasswordInput.setTypeface(Typeface.DEFAULT);

        itsPasswordConfirmInput =
                rootView.findViewById(R.id.password_confirm_input);
        itsPasswordConfirm = rootView.findViewById(R.id.password_confirm);
        TypefaceUtils.setMonospace(itsPasswordConfirm, ctx);
        itsValidator.registerTextView(itsPasswordConfirm);
        itsPasswordConfirmInput.setTypeface(Typeface.DEFAULT);
        PasswordVisibilityMenuHandler.set(ctx, itsPassword, itsPasswordConfirm);

        itsOkBtn = rootView.findViewById(R.id.ok);
        itsOkBtn.setOnClickListener(this);
        setValid(false);

        GuiUtils.setupFormKeyboard(itsFileName, itsPasswordConfirm, itsOkBtn,
                                   getActivity());

        return rootView;
    }

    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsListener.updateViewFileNew();
        itsValidator.validate();
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        GuiUtils.clearEditText(itsPassword);
        GuiUtils.clearEditText(itsPasswordConfirm);
        itsValidator.unregisterTextView(itsPassword);
        itsValidator.unregisterTextView(itsPasswordConfirm);
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    @Override
    public void onClick(@NonNull View view)
    {
        if (view.getId() == R.id.ok) {
            GuiUtils.setKeyboardVisible(itsPassword, requireContext(), false);
            String fileName = itsFileName.getText().toString();
            if (itsUseStorage) {
                startActivityForResult(createNewFileIntent(fileName),
                                       CREATE_DOCUMENT_REQUEST);
            } else {
                try (Owner<PwsPassword> passwd =
                             PwsPassword.create(itsPassword.getText())) {
                    startTask(new NewTask(fileName, passwd.pass(), this));
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode) {
        case CREATE_DOCUMENT_REQUEST: {
            if (resultCode != Activity.RESULT_OK) {
                cancelFragment(true);
                break;
            }

            Context ctx = requireContext();
            Uri newUri = data.getData();
            String title = RecentFilesDao.getSafDisplayName(newUri, ctx);

            boolean checkPermissions = isCheckPermissions();
            if (!checkPermissions && (title == null)) {
                title = data.getStringExtra("__test_display_name");
            }

            String error = validateFileName(title);
            if (error != null) {
                ContentResolver cr = ctx.getContentResolver();
                ApiCompat.documentsContractDeleteDocument(cr, newUri);
                String fileError = getString(R.string.cannot_create_file,
                                             title);
                PasswdSafeUtil.showFatalMsg(
                        String.format("%s - %s", fileError, error),
                        getActivity());
                break;
            }

            if (checkPermissions) {
                RecentFilesDao.updateOpenedSafFile(
                        newUri, (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                 Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                        ctx);
            }

            if ((newUri != null) && !TextUtils.isEmpty(title)) {
                RecentFilesDao recentFilesDao =
                        PasswdSafeDb.get(ctx).accessRecentFiles();
                try {
                    recentFilesDao.insertOrUpdate(newUri, title);
                } catch (Exception e) {
                    Log.e(TAG, "Error saving recent file: " + newUri, e);
                }
            }
            setFileUri(newUri);
            itsFileName.setText(title);
            startResolve();
            break;
        }
        default: {
            super.onActivityResult(requestCode, resultCode, data);
            break;
        }
        }
    }

    /**
     * Derived-class handler for when the resolve task is finished
     */
    @Override
    protected final void doResolveTaskFinished()
    {
        if (itsUseStorage) {
            String fileName = itsFileName.getText().toString();
            try (Owner<PwsPassword> passwd =
                         PwsPassword.create(itsPassword.getText())) {
                startTask(new NewTask(fileName, passwd.pass(), this));
            }
        } else {
            int titleId = R.string.new_file;
            PasswdFileUri uri = getPasswdFileUri();
            PasswdFileUri.Type type =
                    (uri != null) ? uri.getType() : PasswdFileUri.Type.FILE;
            switch (type) {
            case FILE: {
                titleId = R.string.new_local_file;
                break;
            }
            case SYNC_PROVIDER: {
                if (uri.getSyncType() == null) {
                    PasswdSafeUtil.showFatalMsg("Unknown sync type",
                                                getActivity());
                    break;
                }
                switch (uri.getSyncType()) {
                case GDRIVE: {
                    titleId = R.string.new_drive_file;
                    break;
                }
                case DROPBOX: {
                    titleId = R.string.new_dropbox_file;
                    break;
                }
                case BOX: {
                    titleId = R.string.new_box_file;
                    break;
                }
                case ONEDRIVE: {
                    titleId = R.string.new_onedrive_file;
                    break;
                }
                case OWNCLOUD: {
                    titleId = R.string.new_owncloud_file;
                    break;
                }
                }
                break;
            }
            case EMAIL:
            case GENERIC_PROVIDER:
            case BACKUP: {
                break;
            }
            }
            itsTitle.setText(titleId);
        }
    }

    /**
     *  Derived-class handler when the fragment is canceled
     */
    @Override
    protected final void doCancelFragment(boolean userCancel)
    {
        GuiUtils.setKeyboardVisible(itsPasswordInput, requireContext(), false);
        if (userCancel && itsListener != null) {
            itsListener.handleFileNewCanceled();
        }
    }

    /**
     * Derived-class handler to enable/disable field controls during
     * background operations
     */
    @Override
    protected final void doSetFieldsEnabled(boolean enabled)
    {
        itsFileNameInput.setEnabled(enabled);
        itsPasswordInput.setEnabled(enabled);
        itsPasswordConfirmInput.setEnabled(enabled);
        itsBackgroundDisable.update(!enabled);
        itsValidator.validate();
    }

    /**
     * Create the intent for creating a new file document
     */
    private static @NonNull Intent createNewFileIntent(String fileName)
    {
        Intent createIntent = new Intent(
                DocumentsContractCompat.INTENT_ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        createIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // Create a file with the requested MIME type and name.
        createIntent.setType(PasswdSafeUtil.MIME_TYPE_PSAFE3);
        createIntent.putExtra(Intent.EXTRA_TITLE, fileName);
        return createIntent;
    }

    /**
     * Handle when the new task is finished
     */
    private void newTaskFinished(PasswdFileData result,
                                 Throwable error,
                                 PasswdFileUri fileUri)
    {
        if (result != null) {
            itsListener.handleFileNew(result);
        } else if (error != null) {
            PasswdSafeUtil.showFatalMsg(
                    error, getString(R.string.cannot_create_file, fileUri),
                    getActivity());
        } else {
            cancelFragment(false);
        }
    }

    /**
     * Validate the file name
     * @return error message if invalid; null if valid
     */
    @Nullable
    @Contract("null -> !null")
    private String validateFileName(String fileName)
    {
        if ((fileName == null) || !fileName.endsWith(itsPsafe3Sfx)) {
            return getString(R.string.invalid_file_name);
        }

        String fileNameBase = fileName.substring(
                0, fileName.length() - itsPsafe3Sfx.length());

        var ctx = requireContext();
        var error = PasswdFileUri.validateFileNameBase(fileNameBase, ctx);
        if (error != null) {
            return error;
        }

        if (!itsUseStorage) {
            PasswdFileUri uri = getPasswdFileUri();
            if (uri != null) {
                return uri.validateNewChild(fileNameBase, ctx);
            }
        }

        return null;
    }

    /**
     * Set whether the fields are valid
     */
    private void setValid(boolean valid)
    {
        itsOkBtn.setEnabled(valid && !itsBackgroundDisable.get());
    }

    /**
     *  Whether permissions should be checked
     */
    private static boolean isCheckPermissions()
    {
        return !PasswdSafeUtil.isTesting();
    }

    /**
     * Class to validate fields in the fragment
     */
    private class Validator
    {
        private final TextWatcher itsTextWatcher = new AbstractTextWatcher()
        {
            @Override
            public void afterTextChanged(Editable s)
            {
                validate();
            }
        };

        /**
         * Register a text view with the validator to revalidate on text change
         */
        protected void registerTextView(@NonNull TextView tv)
        {
            tv.addTextChangedListener(itsTextWatcher);
        }

        /**
         * Unregister a text view
         */
        protected void unregisterTextView(@NonNull TextView tv)
        {
            tv.removeTextChangedListener(itsTextWatcher);
        }

        /**
         * Validate the fragment
         */
        protected final void validate()
        {
            boolean isError;

            CharSequence fileName = itsFileName.getText();
            isError = TextInputUtils.setTextInputError(
                    validateFileName(fileName.toString()), itsFileNameInput);

            CharSequence passwd = itsPassword.getText();
            isError |= TextInputUtils.setTextInputError(
                    (passwd.length() == 0) ?
                            getString(R.string.empty_password) : null,
                    itsPasswordInput);

            CharSequence confirm = itsPasswordConfirm.getText();
            isError |= TextInputUtils.setTextInputError(
                    !TextUtils.equals(passwd, confirm) ?
                            getString(R.string.passwords_do_not_match) : null,
                    itsPasswordConfirmInput);

            setValid(!isError);
        }
    }

    /**
     * Background task for creating a new file
     */
    private static final class NewTask
            extends BackgroundTask<PasswdFileData, PasswdSafeNewFileFragment>
    {
        private final String itsFileName;
        private final PasswdFileUri itsFileUri;
        private final boolean itsUseStorage;
        private final Owner<PwsPassword> itsPassword;
        private PasswdFileUri itsNewFileUri;

        /**
         * Constructor
         */
        private NewTask(String fileName,
                        @NonNull Owner<PwsPassword>.Param passwd,
                        PasswdSafeNewFileFragment frag)
        {
            super(frag);
            itsFileName = fileName;
            itsFileUri = frag.getPasswdFileUri();
            itsPassword = passwd.use();
            itsUseStorage = frag.itsUseStorage;
        }

        @NonNull
        @Override
        protected PasswdFileData doInBackground() throws Exception
        {
            Context ctx = getContext();
            if (itsUseStorage) {
                itsNewFileUri = itsFileUri;
            } else {
                itsNewFileUri = itsFileUri.createNewChild(itsFileName, ctx);
            }
            PasswdFileData fileData = new PasswdFileData(itsNewFileUri);
            fileData.createNewFile(itsPassword.pass(), ctx);
            return fileData;
        }

        @Override
        protected void onTaskFinished(PasswdFileData result,
                                      Throwable error,
                                      @NonNull PasswdSafeNewFileFragment frag)
        {
            super.onTaskFinished(result, error, frag);
            try {
                frag.newTaskFinished(result, error, itsNewFileUri);
            } finally {
                itsPassword.close();
            }
        }
    }
}
