/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.jefftharris.passwdsafe.lib.AboutUtils;

/**
 *  The about dialog
 */
public class AboutDialog extends AppCompatDialogFragment
        implements DialogInterface.OnClickListener
{
    /** Create a new instance */
    public static AboutDialog newInstance()
    {
        AboutDialog frag = new AboutDialog();
        Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.DialogFragment#onCreateDialog(android.os.Bundle)
     */
    @SuppressLint("InflateParams")
    @Override
    public @NonNull
    Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Activity act = requireActivity();
        LayoutInflater factory = LayoutInflater.from(act);
        View detailsView = factory.inflate(R.layout.fragment_about, null);

        String licenses = AboutUtils.getLicenses(
                requireContext(), "license-PasswdSafe.txt",
                "license-android.txt", "license-AndroidAssetStudio.txt",
                "license-MaterialIcons.txt", "license-icons.txt",
                "license-box.txt", "license-onedrive.txt");
        String name = AboutUtils.updateAboutFields(detailsView, licenses, act);

        AlertDialog.Builder builder = new AlertDialog.Builder(act)
            .setTitle(name)
            .setIcon(R.drawable.ic_action_info_dark)
            .setView(detailsView)
            .setPositiveButton(R.string.close, this);
        return builder.create();
    }

    /** Handle a click on the dialog button */
    public void onClick(DialogInterface dialog, int which)
    {
        dialog.dismiss();
    }

}
