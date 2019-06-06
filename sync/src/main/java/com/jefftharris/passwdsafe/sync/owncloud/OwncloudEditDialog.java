/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.owncloud;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.jefftharris.passwdsafe.sync.ProviderSyncFreqPref;
import com.jefftharris.passwdsafe.sync.R;
import com.jefftharris.passwdsafe.sync.lib.DialogValidator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Dialog to edit an ownCloud account
 */
public class OwncloudEditDialog extends DialogFragment
        implements DialogInterface.OnClickListener
{
    public interface Listener
    {
        /** Handle changed settings for ownCloud */
        void handleOwncloudSettingsChanged(Uri providerUri,
                                           String url,
                                           ProviderSyncFreqPref freq);
    }

    private Uri itsProviderUri;
    private Listener itsListener;
    private TextView itsUrlEdit;
    private Spinner itsSyncInterval;
    private DialogValidator itsValidator;

    /** Create an instance of the dialog */
    public static OwncloudEditDialog newInstance(Uri providerUri,
                                                 String url,
                                                 int syncFreq)
    {
        OwncloudEditDialog dialog = new OwncloudEditDialog();
        Bundle args = new Bundle();
        args.putParcelable("providerUri", providerUri);
        args.putString("url", url);
        args.putInt("syncFreq", syncFreq);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    @SuppressLint("InflateParams")
    public @NonNull
    Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = Objects.requireNonNull(getArguments());
        itsProviderUri = args.getParcelable("providerUri");
        String url = args.getString("url");
        int syncFreq = args.getInt("syncFreq");

        Activity act = getActivity();
        LayoutInflater factory = LayoutInflater.from(act);
        View view = factory.inflate(R.layout.fragment_owncloud_edit_dialog,
                                    null);
        itsUrlEdit = view.findViewById(R.id.url);
        itsSyncInterval = view.findViewById(R.id.owncloud_interval);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.owncloud)
               .setView(view)
               .setPositiveButton(android.R.string.ok, this)
               .setNegativeButton(android.R.string.cancel, this);
        AlertDialog dialog = builder.create();
        itsValidator = new DialogValidator.AlertValidator(dialog, view)
        {
            @Override
            protected String doValidation()
            {
                try {
                    new URI(itsUrlEdit.getText().toString());
                } catch (URISyntaxException e) {
                    return e.getMessage();
                }
                return null;
            }
        };

        // Must set text before registering view so validation isn't
        // triggered right away
        itsUrlEdit.setText(url);
        itsValidator.registerTextView(itsUrlEdit);

        ProviderSyncFreqPref freq = ProviderSyncFreqPref.freqValueOf(syncFreq);
        itsSyncInterval.setSelection(freq.getDisplayIdx());

        return dialog;
    }

    @Override
    public void onAttach(Context ctx)
    {
        super.onAttach(ctx);
        itsListener = (Listener)ctx;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsValidator.reset();
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    /** Handle a click on the dialog button */
    public void onClick(DialogInterface dialog, int which)
    {
        if ((which == AlertDialog.BUTTON_POSITIVE) && (itsListener != null)) {
            int freqPos = itsSyncInterval.getSelectedItemPosition();
            ProviderSyncFreqPref freq =
                    ProviderSyncFreqPref.displayValueOf(freqPos);

            itsListener.handleOwncloudSettingsChanged(
                    itsProviderUri, itsUrlEdit.getText().toString(), freq);
        }
        dialog.dismiss();
    }
}
