/*
 * Copyright (©) 2019 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.jefftharris.passwdsafe.lib.ManagedTask;
import com.jefftharris.passwdsafe.lib.ManagedTasks;
import com.jefftharris.passwdsafe.lib.view.AbstractTextWatcher;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TextInputUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.sync.R;

/**
 * Activity to add an ownCloud account
 */
public class OwncloudAccountActivity extends AppCompatActivity
        implements View.OnClickListener
{
    private final ManagedTasks itsTasks = new ManagedTasks();
    private TextInputLayout itsUrlInput;
    private EditText itsUrl;
    private TextInputLayout itsUsernameInput;
    private EditText itsUsername;
    private TextInputLayout itsPasswordInput;
    private EditText itsPassword;
    private TextView itsMessage;
    private View itsProgress;
    private Button itsOkButton;
    private final Validator itsValidator = new Validator();

    // TODO: do connection check
    // TODO: resource strings

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owncloud_account);

        itsUrlInput = findViewById(R.id.url_input);
        itsUrl = findViewById(R.id.url);
        itsValidator.registerTextView(itsUrl);
        itsUsernameInput = findViewById(R.id.username_input);
        itsUsername = findViewById(R.id.username);
        itsValidator.registerTextView(itsUsername);
        itsPasswordInput = findViewById(R.id.password_input);
        itsPassword = findViewById(R.id.password);
        TypefaceUtils.setMonospace(itsPassword, this);
        itsValidator.registerTextView(itsPassword);
        itsMessage = findViewById(R.id.message);
        itsProgress = findViewById(R.id.progress);
        GuiUtils.setVisible(itsProgress, false);
        itsOkButton = findViewById(R.id.ok);
        itsOkButton.setOnClickListener(this);

        GuiUtils.setupFormKeyboard(null, itsPassword, this, this::okClicked);
        itsValidator.validate();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        itsTasks.cancelTasks();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        GuiUtils.clearEditText(itsPassword);
        itsValidator.unregisterTextView(itsUrl);
        itsValidator.unregisterTextView(itsUsername);
        itsValidator.unregisterTextView(itsPassword);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
        case R.id.ok: {
            okClicked();
            break;
        }
        }
    }

    private void okClicked()
    {
        GuiUtils.setKeyboardVisible(itsPassword, this, false);
        itsTasks.startTask(new ConnectionCheck(this));
    }

    private void setValid(boolean valid)
    {
        itsOkButton.setEnabled(valid);
    }

    private void connectionCheckStarted()
    {
        itsMessage.setText("Checking...");
        setFieldsForCheck(true);
    }

    private void connectionCheckFinished(Throwable error, ConnectionCheck task)
    {
        itsTasks.taskFinished(task);
        setFieldsForCheck(false);
        if (error == null) {
            itsMessage.setText("");
        } else {
            itsMessage.setText(error.toString());
        }
    }

    private void setFieldsForCheck(boolean checkRunning)
    {
        itsUrlInput.setEnabled(!checkRunning);
        itsUsernameInput.setEnabled(!checkRunning);
        itsPasswordInput.setEnabled(!checkRunning);
        itsOkButton.setEnabled(!checkRunning);
        GuiUtils.setVisible(itsProgress, checkRunning);
    }

    private class Validator extends AbstractTextWatcher
    {
        /**
         * Register a text view with the validator to revalidate on text change
         */
        protected void registerTextView(TextView tv)
        {
            tv.addTextChangedListener(this);
        }

        /**
         * Unregister a text view
         */
        protected void unregisterTextView(TextView tv)
        {
            tv.removeTextChangedListener(this);
        }

        @Override
        public void afterTextChanged(Editable s)
        {
            validate();
        }

        protected final void validate()
        {
            boolean isError;

            // TODO: resource strings for errors
            CharSequence url = itsUrl.getText();
            isError = TextInputUtils.setTextInputError(
                    TextUtils.isEmpty(url) ?
                            "Empty URL" : null,
                    itsUrlInput);

            isError |= TextInputUtils.setTextInputError(
                    TextUtils.isEmpty(itsUsername.getText()) ?
                            "Empty username" : null,
                    itsUsernameInput);

            isError |= TextInputUtils.setTextInputError(
                    TextUtils.isEmpty(itsPassword.getText()) ?
                            "Empty password" : null,
                    itsPasswordInput);

            setValid(!isError);
        }
    }

    private static class ConnectionCheck
            extends ManagedTask<Boolean, OwncloudAccountActivity>
    {
        protected ConnectionCheck(OwncloudAccountActivity act)
        {
            super(act, act);
        }

        @Override
        protected void onTaskStarted(@NonNull OwncloudAccountActivity frag)
        {
            frag.connectionCheckStarted();
        }

        @Override
        protected void onTaskFinished(Boolean result, Throwable error,
                                      @NonNull OwncloudAccountActivity frag)
        {
            frag.connectionCheckFinished(error, this);
        }

        @Override
        protected Boolean doInBackground() throws Throwable
        {
            Thread.sleep(5000);
            return null;
        }
    }
}
