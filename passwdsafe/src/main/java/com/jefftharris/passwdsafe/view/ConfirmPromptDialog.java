/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
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
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.jefftharris.passwdsafe.R;

/**
 * Dialog to confirm a prompt
 */
public class ConfirmPromptDialog extends AppCompatDialogFragment
    implements CompoundButton.OnCheckedChangeListener,
               DialogInterface.OnCancelListener,
               DialogInterface.OnClickListener
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Handle when a prompt was confirmed or the neutral action selected
         */
        void promptConfirmed(Bundle confirmArgs);

        /** Handle when a prompt was canceled */
        void promptCanceled();
    }

    private static final String ARG_CONFIRM = "confirm";
    private static final String ARG_CONFIRM_ARGS = "confirmArgs";
    private static final String ARG_NEUTRAL = "neutral";
    private static final String ARG_NEUTRAL_ARGS = "neutralArgs";
    private static final String ARG_PROMPT = "prompt";
    private static final String ARG_TITLE = "title";

    private CheckBox itsConfirmCb;
    private AlertDialog itsDialog;
    private Listener itsListener;

    /**
     * Create a new instance
     */
    public static ConfirmPromptDialog newInstance(String title,
                                                  String prompt,
                                                  String confirm,
                                                  Bundle confirmArgs)
    {
        return newInstance(title, prompt, confirm, confirmArgs, null, null);
    }

    /**
     * Create a new instance with a neutral option
     */
    public static ConfirmPromptDialog newInstance(String title,
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
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public @NonNull Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = getArguments();
        String titleStr = args.getString(ARG_TITLE);
        String promptStr = args.getString(ARG_PROMPT);
        String confirmStr = args.getString(ARG_CONFIRM);
        if (TextUtils.isEmpty(confirmStr)) {
            confirmStr = getString(R.string.ok);
        }
        String neutralStr = args.getString(ARG_NEUTRAL);

        Context ctx = getContext();
        LayoutInflater factory = LayoutInflater.from(ctx);
        @SuppressLint("InflateParams")
        View dlgView = factory.inflate(R.layout.confirm_prompt, null);

        itsConfirmCb = (CheckBox)dlgView.findViewById(R.id.confirm);
        itsConfirmCb.setOnCheckedChangeListener(this);

        setCancelable(true);
        AlertDialog.Builder alert = new AlertDialog.Builder(ctx)
            .setTitle(titleStr)
            .setMessage(promptStr)
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
    public void onAttach(Context ctx)
    {
        super.onAttach(ctx);
        Fragment frag = getTargetFragment();
        if (frag != null) {
            itsListener = (Listener)frag;
        } else {
            itsListener = (Listener)ctx;
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        validate();
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        if (itsListener == null) {
            return;
        }

        switch (which) {
        case AlertDialog.BUTTON_POSITIVE: {
            itsListener.promptConfirmed(
                    getArguments().getBundle(ARG_CONFIRM_ARGS));
            break;
        }
        case AlertDialog.BUTTON_NEGATIVE: {
            itsListener.promptCanceled();
            break;
        }
        case AlertDialog.BUTTON_NEUTRAL: {
            itsListener.promptConfirmed(
                    getArguments().getBundle(ARG_NEUTRAL_ARGS));
            break;
        }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    {
        validate();
    }

    @Override
    public void onCancel(DialogInterface dialog)
    {
        super.onCancel(dialog);
        if (itsListener != null) {
            itsListener.promptCanceled();
        }
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
