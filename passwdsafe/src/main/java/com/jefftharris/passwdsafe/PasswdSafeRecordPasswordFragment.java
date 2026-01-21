/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;


import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.jefftharris.passwdsafe.file.HeaderPasswdPolicies;
import com.jefftharris.passwdsafe.file.PasswdExpiration;
import com.jefftharris.passwdsafe.file.PasswdHistory;
import com.jefftharris.passwdsafe.file.PasswdPolicy;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.view.PasswdLocation;
import com.jefftharris.passwdsafe.view.PasswdPolicyView;

import org.pwsafe.lib.file.PwsRecord;

import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;


/**
 * Fragment for showing password-specific fields of a password record
 */
public class PasswdSafeRecordPasswordFragment
        extends AbstractPasswdSafeRecordFragment
        implements View.OnClickListener
{
    private View itsPolicyRow;
    private PasswdPolicyView itsPolicy;
    private View itsTotpRow;
    private TextView itsTotpSecretKey;
    private TextView itsTotpAlgo;
    private TextView itsTotpDigits;
    private TextView itsTotpTimeStep;
    private TextView itsTotpTimeStart;
    private DateFormat itsGmtDateFormat = null;
    private View itsPasswordTimesRow;
    private View itsExpirationTimeRow;
    private TextView itsExpirationTime;
    private View itsExpirationIntervalRow;
    private TextView itsExpirationInterval;
    private View itsPasswordModTimeRow;
    private TextView itsPasswordModTime;
    private CheckBox itsHistoryEnabledCb;
    private TextView itsHistoryMaxSizeLabel;
    private TextView itsHistoryMaxSize;
    private ListView itsHistory;
    private PasswdSafeRecordTotpViewModel itsViewModel;


    /**
     * Create a new instance of the fragment
     */
    @NonNull
    public static PasswdSafeRecordPasswordFragment newInstance(
            PasswdLocation location)
    {
        PasswdSafeRecordPasswordFragment frag =
                new PasswdSafeRecordPasswordFragment();
        frag.setArguments(createArgs(location));
        return frag;

    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        itsViewModel = new ViewModelProvider(requireActivity()).get(
                PasswdSafeRecordTotpViewModel.class);
        itsViewModel.getConfig().observe(this, this::onTotpChanged);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        View root = inflater.inflate(
                R.layout.fragment_passwdsafe_record_password, container, false);
        itsPolicyRow = root.findViewById(R.id.policy_row);
        itsPolicy = root.findViewById(R.id.policy);
        itsPolicy.setGenerateEnabled(false);
        itsTotpRow = root.findViewById(R.id.totp_row);
        itsTotpRow.setOnClickListener(this);
        itsTotpSecretKey = root.findViewById(R.id.totp_key);
        itsTotpAlgo = root.findViewById(R.id.totp_algo);
        itsTotpDigits = root.findViewById(R.id.totp_num_digits);
        itsTotpTimeStep = root.findViewById(R.id.totp_time_step);
        itsTotpTimeStart = root.findViewById(R.id.totp_time_start);
        itsPasswordTimesRow = root.findViewById(R.id.password_times_row);
        itsExpirationTimeRow = root.findViewById(R.id.expiration_time_row);
        itsExpirationTime = root.findViewById(R.id.expiration_time);
        itsExpirationIntervalRow =
                root.findViewById(R.id.expiration_interval_row);
        itsExpirationInterval = root.findViewById(R.id.expiration_interval);
        itsPasswordModTimeRow = root.findViewById(R.id.password_mod_time_row);
        itsPasswordModTime = root.findViewById(R.id.password_mod_time);
        itsHistoryEnabledCb = root.findViewById(R.id.history_enabled);
        itsHistoryEnabledCb.setClickable(false);
        itsHistoryMaxSizeLabel = root.findViewById(R.id.history_max_size_label);
        itsHistoryMaxSize = root.findViewById(R.id.history_max_size);
        itsHistory = root.findViewById(R.id.history);
        itsHistory.setEnabled(false);
        return root;
    }

    @Override
    public void onPause()
    {
        super.onPause();
        itsViewModel.setTotp(null);
    }

    @Override
    public void onClick(@NonNull View view)
    {
        int id = view.getId();
        if (id == R.id.totp_row) {
            itsViewModel.updateConfigShown(
                    PasswdSafeRecordTotpViewModel.VisibiltyChange.TOGGLE);
        }
    }

    @Override
    protected void doRefresh(@NonNull RecordInfo info)
    {
        PasswdPolicy policy = null;
        String policyLoc = null;
        PasswdExpiration passwdExpiry = null;
        Date lastModTime = null;
        PasswdHistory history = null;
        final var rec = info.rec();
        PwsRecord recForPassword = rec;
        final var fileData = info.fileData();
        switch (info.passwdRec().getType()) {
        case NORMAL: {
            policy = info.passwdRec().getPasswdPolicy();
            if (policy == null) {
                PasswdSafeApp app =
                        (PasswdSafeApp)requireActivity().getApplication();
                policy = app.getDefaultPasswdPolicy();
                policyLoc = getString(R.string.default_policy);
            } else if (policy.getLocation() ==
                       PasswdPolicy.Location.RECORD_NAME) {
                HeaderPasswdPolicies hdrPolicies =
                        fileData.getHdrPasswdPolicies();
                String policyName = policy.getName();
                if (hdrPolicies != null) {
                    policy = hdrPolicies.getPasswdPolicy(policyName);
                }
                if (policy != null) {
                    policyLoc = getString(R.string.database_policy, policyName);
                }
            } else {
                policyLoc = getString(R.string.record);
            }
            passwdExpiry = fileData.getPasswdExpiry(rec);
            lastModTime = fileData.getPasswdLastModTime(rec);
            history = fileData.getPasswdHistory(rec);
            break;
        }
        case ALIAS: {
            recForPassword = info.passwdRec().getRef();
            passwdExpiry = fileData.getPasswdExpiry(recForPassword);
            lastModTime = fileData.getPasswdLastModTime(recForPassword);
            history = fileData.getPasswdHistory(recForPassword);
            break;
        }
        case SHORTCUT: {
            recForPassword = info.passwdRec().getRef();
            break;
        }
        }

        String expiryIntStr = null;
        if ((passwdExpiry != null) && passwdExpiry.isRecurring()) {
            int val = passwdExpiry.interval();
            if (val != 0) {
                expiryIntStr = getResources().getQuantityString(
                        R.plurals.interval_days, val, val);
            }
        }

        if (policy != null) {
            itsPolicy.showLocation(policyLoc);
            itsPolicy.showPolicy(policy, -1);
        }
        GuiUtils.setVisible(itsPolicyRow, policy != null);

        try (var totp = fileData.getTotp(recForPassword)) {
            itsViewModel.setTotp((totp != null) ? totp.pass() : null);
        }

        setFieldDate(itsExpirationTime, itsExpirationTimeRow,
                     (passwdExpiry != null) ? passwdExpiry.expiration() : null);
        setFieldText(itsExpirationInterval, itsExpirationIntervalRow,
                     expiryIntStr);
        setFieldDate(itsPasswordModTime, itsPasswordModTimeRow, lastModTime);
        //noinspection ConstantConditions
        GuiUtils.setVisible(itsPasswordTimesRow,
                            (passwdExpiry != null) || (lastModTime != null) ||
                            (expiryIntStr != null));

        boolean historyExists = (history != null);
        boolean historyEnabled = false;
        String historyMaxSize;
        if (historyExists) {
            historyEnabled = history.isEnabled();
            historyMaxSize = Integer.toString(history.getMaxSize());
            itsHistory.setAdapter(PasswdHistory.createAdapter(history, true,
                                                              false,
                                                              getActivity()));
        } else {
            historyMaxSize = getString(R.string.n_a);
            itsHistory.setAdapter(null);
        }
        GuiUtils.setListViewHeightBasedOnChildren(itsHistory);
        GuiUtils.setCheckedNoAnim(itsHistoryEnabledCb, historyEnabled);
        itsHistoryEnabledCb.setEnabled(historyExists);
        itsHistoryMaxSize.setText(historyMaxSize);
        GuiUtils.setVisible(itsHistoryMaxSize, historyExists);
        GuiUtils.setVisible(itsHistoryMaxSizeLabel, historyExists);
    }

    /**
     * Handle a change in TOTP configuration
     */
    private void onTotpChanged(
            @Nullable PasswdSafeRecordTotpViewModel.Config totpConfig)
    {
        if (totpConfig == null) {
            return;
        }

        Activity act = requireActivity();
        String algo = null;
        String digits = null;
        String timeStep = null;
        String timeStart = null;
        try (var totp = totpConfig.getTotp()) {
            if (totp != null) {
                var totpVal = totp.get();
                GuiUtils.setVisible(itsTotpRow, true);
                var status = totpVal.getStatus();
                switch (status) {
                case OK -> {
                    boolean isShown = totpConfig.isShown();
                    if (isShown) {
                        try (var secretKey = totpVal.getSecretKey()) {
                            secretKey.get().setInto(itsTotpSecretKey);
                        }
                    } else {
                        itsTotpSecretKey.setText(
                                R.string.hidden_password_normal);
                    }
                    TypefaceUtils.enableMonospace(itsTotpSecretKey, isShown,
                                                  act);
                    algo = totpVal.getHash().toString();
                    digits = Integer.toString(totpVal.getNumDigits());
                    timeStep = Long.toString(totpVal.getTimeStep());
                    if (itsGmtDateFormat == null) {
                        itsGmtDateFormat = DateFormat.getDateTimeInstance();
                        itsGmtDateFormat.setTimeZone(
                                TimeZone.getTimeZone("GMT"));
                    }
                    timeStart = itsGmtDateFormat.format(
                            new Date(totpVal.getTimeStart() * 1000));
                }
                case INVALID_ALGORITHM,
                     INVALID_NUM_DIGITS,
                     INVALID_SECRET_KEY,
                     INVALID_TIME_STEP -> {
                    itsTotpSecretKey.setText(
                            getString(R.string.error_fmt, status));
                    TypefaceUtils.enableMonospace(itsTotpSecretKey, false, act);
                }
                }
            } else {
                GuiUtils.setVisible(itsTotpRow, false);
                itsTotpSecretKey.setText(null);
            }
        }

        itsTotpAlgo.setText(algo);
        itsTotpDigits.setText(digits);
        itsTotpTimeStep.setText(timeStep);
        itsTotpTimeStart.setText(timeStart);
    }
}
