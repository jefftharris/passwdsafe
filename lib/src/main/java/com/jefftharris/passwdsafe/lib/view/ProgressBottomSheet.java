/*
 * Copyright (©) 2023 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.lib.view;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.R;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Progress dialog shown as a modal bottom sheet
 */
public class ProgressBottomSheet extends BottomSheetDialogFragment
        implements View.OnClickListener
{
    private ProgressBar itsProgress;
    private TextView itsMsg;
    private View itsErrorCopyBtn;
    private View itsErrorCloseBtn;
    private TextView itsErrorMsg;
    private Throwable itsError;

    /**
     * Create an instance of the progress bottom sheet
     */
    public static ProgressBottomSheet newInstance(String msg)
    {
        ProgressBottomSheet frag = new ProgressBottomSheet();
        frag.setCancelable(false);

        Bundle args = new Bundle();
        args.putString("msg", msg);
        frag.setArguments(args);
        return frag;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState)
    {
        Bundle args = requireArguments();
        View root = inflater.inflate(R.layout.fragment_progress_bottom_sheet,
                                     container, false);

        itsProgress = root.findViewById(R.id.progress);
        itsMsg = root.findViewById(R.id.msg);
        itsMsg.setText(args.getString("msg"));
        itsErrorMsg = root.findViewById(R.id.error_msg);
        GuiUtils.setVisible(itsErrorMsg, false);

        itsErrorCopyBtn = root.findViewById(R.id.copy);
        GuiUtils.setVisible(itsErrorCopyBtn, false);
        itsErrorCopyBtn.setOnClickListener(this);

        itsErrorCloseBtn = root.findViewById(R.id.close);
        GuiUtils.setVisible(itsErrorCloseBtn, false);
        itsErrorCloseBtn.setOnClickListener(this);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        setupRoundedBottomSheet(view);
    }

    /**
     * Show an error from the progress task
     */
    public void showError(Throwable error)
    {
        itsError = error;

        GuiUtils.setVisible(itsProgress, false);
        itsMsg.setText(requireContext().getString(R.string.error_fmt,
                                                  itsMsg.getText()));

        GuiUtils.setVisible(itsErrorMsg, true);
        GuiUtils.setVisible(itsErrorCopyBtn, true);
        GuiUtils.setVisible(itsErrorCloseBtn, true);

        StringBuilder msg = new StringBuilder();
        while (error != null) {
            if (msg.length() != 0) {
                msg.append("\n");
            }
            msg.append(error.getMessage());
            error = error.getCause();
        }
        itsErrorMsg.setText(msg.toString());

        setCancelable(true);
    }

    @Override
    public void onClick(View v)
    {
        int viewId = v.getId();
        if (viewId == R.id.copy) {
            if (itsError != null) {
                StringWriter writer = new StringWriter();
                itsError.printStackTrace(new PrintWriter(writer));
                ApiCompat.copyToClipboard(writer.toString(), true,
                                          requireContext());
            }
        } else if (viewId == R.id.close) {
            requireActivity().finish();
        }
    }

    /**
     * Setup the style for the bottom sheet to use rounded corners
     */
    private void setupRoundedBottomSheet(@NonNull View view)
    {
        var act = requireActivity();
        var a = new TypedValue();
        act.getTheme()
           .resolveAttribute(android.R.attr.colorBackground, a, true);
        var background = ((ViewGroup)view.getParent()).getBackground().mutate();
        if (background instanceof ColorDrawable) {
            ((ColorDrawable)background).setColor(a.data);
        } else if (background instanceof GradientDrawable) {
            ((GradientDrawable)background).setColor(a.data);
        } else if (background instanceof ShapeDrawable) {
            ((ShapeDrawable)background).getPaint().setColor(a.data);
        }
    }
}
