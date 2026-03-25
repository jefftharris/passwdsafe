/*
 * Copyright (©) 2016-2026 Jeff Harris <jefftharris@gmail.com>
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentResultListener;

import com.jefftharris.passwdsafe.R;

/**
 * Dialog to confirm a prompt
 */
public class ConfirmPromptDialog extends AppCompatDialogFragment
    implements CompoundButton.OnCheckedChangeListener,
               DialogInterface.OnCancelListener,
               DialogInterface.OnClickListener
{
    private static final String REQUEST_KEY = "ConfirmPromptDialog";

    private static final String ARG_CONFIRM = "confirm";
    private static final String ARG_CONFIRM_ARGS = "confirmArgs";
    private static final String ARG_NEUTRAL = "neutral";
    private static final String ARG_NEUTRAL_ARGS = "neutralArgs";
    private static final String ARG_PROMPT = "prompt";
    private static final String ARG_TITLE = "title";

    private static final Bundle CANCEL_ARGS = new Bundle();

    private CheckBox itsConfirmCb;
    private AlertDialog itsDialog;

    /**
     * Client for showing and checking result of the dialog
     */
    public static class Client extends DialogClient
    {
        /**
         * Constructor for a fragment
         */
        public <T extends Fragment & FragmentResultListener>
        Client(@NonNull T listener, @NonNull String id)
        {
            super(REQUEST_KEY, id, listener.getParentFragmentManager(),
                  listener, listener);
        }

        /**
         * Constructor for a fragment activity
         */
        public <T extends FragmentActivity & FragmentResultListener>
        Client(@NonNull T listener, @NonNull String id)
        {
            super(REQUEST_KEY, id, listener.getSupportFragmentManager(),
                  listener, listener);
        }

        /**
         * Show the dialog
         */
        public void show(String title,
                         String prompt,
                         String confirm,
                         Bundle confirmArgs)
        {
            show(title, prompt, confirm, confirmArgs, null, null);
        }

        /**
         * Show the dialog with a neutral option
         */
        public void show(String title,
                         String prompt,
                         String confirm,
                         Bundle confirmArgs,
                         String neutral,
                         Bundle neutralArgs)
        {
            ConfirmPromptDialog dialog = new ConfirmPromptDialog();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, title);
            args.putString(ARG_PROMPT, prompt);
            args.putString(ARG_CONFIRM, confirm);
            args.putBundle(ARG_CONFIRM_ARGS, confirmArgs);
            args.putString(ARG_NEUTRAL, neutral);
            args.putBundle(ARG_NEUTRAL_ARGS, neutralArgs);
            doShow(dialog, args);
        }
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = requireArguments();
        String titleStr = args.getString(ARG_TITLE);
        String promptStr = args.getString(ARG_PROMPT);
        String confirmStr = args.getString(ARG_CONFIRM);
        if (TextUtils.isEmpty(confirmStr)) {
            confirmStr = getString(R.string.ok);
        }
        String neutralStr = args.getString(ARG_NEUTRAL);

        Context ctx = requireContext();
        LayoutInflater factory = LayoutInflater.from(ctx);
        @SuppressLint("InflateParams")
        View dlgView = factory.inflate(R.layout.confirm_prompt, null);

        TextView confirmMsg = dlgView.findViewById(R.id.confirm_message);
        confirmMsg.setText(promptStr);
        itsConfirmCb = dlgView.findViewById(R.id.confirm);
        itsConfirmCb.setOnCheckedChangeListener(this);

        setCancelable(true);
        AlertDialog.Builder alert = new AlertDialog.Builder(ctx)
            .setTitle(titleStr)
            .setView(dlgView)
            .setPositiveButton(confirmStr, this)
            .setNegativeButton(R.string.cancel, this);
        if (!TextUtils.isEmpty(neutralStr)) {
            alert.setNeutralButton(neutralStr, this);
        }
        itsDialog = alert.create();
        return itsDialog;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        validate();
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        switch (which) {
        case AlertDialog.BUTTON_POSITIVE: {
            setResult(ARG_CONFIRM_ARGS);
            break;
        }
        case AlertDialog.BUTTON_NEGATIVE: {
            setResult(null);
            break;
        }
        case AlertDialog.BUTTON_NEUTRAL: {
            setResult(ARG_NEUTRAL_ARGS);
            break;
        }
        }
    }

    @Override
    public void onCheckedChanged(@NonNull CompoundButton buttonView,
                                 boolean isChecked)
    {
        validate();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog)
    {
        super.onCancel(dialog);
        setResult(null);
    }

    private void setResult(@Nullable String resultKey)
    {
        var args = requireArguments();
        var result = args.getBundle(resultKey);
        Client.setResult(requireArguments(),
                         (result != null) ? result : CANCEL_ARGS,
                         getParentFragmentManager());
    }

    /**
     * Validate the prompt
     */
    private void validate()
    {
        Button okBtn = itsDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        okBtn.setEnabled(itsConfirmCb.isChecked());
    }
}
