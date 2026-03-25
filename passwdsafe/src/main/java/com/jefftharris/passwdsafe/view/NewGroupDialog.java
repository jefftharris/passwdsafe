/*
 * Copyright (©) 2016-2026 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.view.AbstractDialogClickListener;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;

/**
 * Dialog to select a new group
 */
public class NewGroupDialog extends AppCompatDialogFragment
{
    public static final String ARG_GROUP = "group";

    private static final String REQUEST_KEY = "NewGroupDialog";

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
            super(REQUEST_KEY, id, listener.getParentFragmentManager(),
                  listener, listener);
        }

        /**
         * Show the dialog
         */
        public void show()
        {
            doShow(new NewGroupDialog(), new Bundle());
        }
    }

    @Override
    public @NonNull
    Dialog onCreateDialog(Bundle savedInstanceState)
    {
        LayoutInflater factory = getLayoutInflater();
        final View view = factory.inflate(R.layout.new_group, null);
        AbstractDialogClickListener dlgClick =
                new AbstractDialogClickListener()
                {
                    @Override
                    public void onOkClicked(DialogInterface dialog)
                    {
                        EditText newGroup = view.findViewById(R.id.new_group);
                        setResult(newGroup.getText().toString());
                    }

                    @Override
                    public void onCancelClicked()
                    {
                        setResult(null);
                    }
                };

        Context ctx = requireContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(PasswdSafeUtil.getAppTitle(ctx))
                .setView(view)
                .setPositiveButton(R.string.ok, dlgClick)
                .setNegativeButton(R.string.cancel, dlgClick)
                .setOnCancelListener(dlgClick);
        final AlertDialog alertDialog = builder.create();
        TextView tv = view.findViewById(R.id.new_group);
        GuiUtils.setupFormKeyboard(tv, tv, getContext(), () -> {
            Button btn = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (btn.isEnabled()) {
                btn.performClick();
            }
        });
        return alertDialog;
    }

    private void setResult(@Nullable String group)
    {
        var result = new Bundle();
        result.putString(ARG_GROUP, group);
        Client.setResult(requireArguments(), result,
                         getParentFragmentManager());
    }
}
