/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import com.jefftharris.passwdsafe.lib.view.AbstractDialogClickListener;
import com.jefftharris.passwdsafe.sync.R;

/**
 * The AccountChooserDlg allows the user to choose an account of a given type
 */
public class AccountChooserDlg extends DialogFragment
{
    private static final String TAG = "AccountChooserDlg";

    /** Create a new instance of the dialog */
    public static AccountChooserDlg newInstance(
            @SuppressWarnings("SameParameterValue") String accountType,
            int requestCode, String noAccountsMsg)
    {
        AccountChooserDlg dialog = new AccountChooserDlg();
        Bundle args = new Bundle();
        args.putString("accountType", accountType);
        args.putInt("requestCode", requestCode);
        args.putString("noAccountsMsg", noAccountsMsg);
        dialog.setArguments(args);
        return dialog;
    }


    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = requireArguments();
        String accountType = args.getString("accountType");

        Activity act = requireActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle(R.string.choose_account);

        AccountManager acctMgr = AccountManager.get(act);
        Account[] accts;
        if (ActivityCompat.checkSelfPermission(
                act, Manifest.permission.GET_ACCOUNTS) ==
            PackageManager.PERMISSION_GRANTED) {
            accts = acctMgr.getAccountsByType(accountType);
        } else {
            accts = new Account[0];
        }
        if (accts.length > 0) {
            final String[] names = new String[accts.length];
            for (int i = 0; i < accts.length; ++i) {
                names[i] = accts[i].name;
            }
            builder.setItems(names,
                             (dialog, which) ->
                                     onAccountSelected(names[which]));
        } else {
            builder.setMessage(args.getString("noAccountsMsg"));
        }

        AbstractDialogClickListener clickListener =
                new AbstractDialogClickListener()
        {
            @Override
            public void onCancelClicked()
            {
                onAccountSelected(null);
            }
        };
        builder.setNegativeButton(R.string.cancel, clickListener);
        builder.setOnCancelListener(clickListener);

        return builder.create();
    }


    /** Handle a selected account */
    private void onAccountSelected(String accountName)
    {
        Bundle args = requireArguments();
        int requestCode = args.getInt("requestCode");

        int result;
        Intent intent = new Intent();
        if (accountName != null) {
            intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName);
            result = Activity.RESULT_OK;
        } else {
            result = Activity.RESULT_CANCELED;
        }

        PendingIntent pendIntent = requireActivity().createPendingResult(
                requestCode, intent, PendingIntent.FLAG_ONE_SHOT);
        try {
            pendIntent.send(result);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "intent send failed", e);
        }
    }
}
