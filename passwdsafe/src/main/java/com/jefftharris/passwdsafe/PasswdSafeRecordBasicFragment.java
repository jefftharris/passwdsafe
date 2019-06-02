/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.jefftharris.passwdsafe.lib.view.AbstractTextWatcher;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;
import com.jefftharris.passwdsafe.pref.PasswdTimeoutPref;
import com.jefftharris.passwdsafe.view.CopyField;
import com.jefftharris.passwdsafe.view.PasswdLocation;
import com.jefftharris.passwdsafe.view.TextInputUtils;

import org.pwsafe.lib.file.PwsRecord;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;


/**
 * Fragment for showing basic fields of a password record
 */
public class PasswdSafeRecordBasicFragment
        extends AbstractPasswdSafeRecordFragment
        implements View.OnClickListener,
                   View.OnLongClickListener,
                   CompoundButton.OnCheckedChangeListener
{
    /**
     * Password visibility option change
     */
    private enum PasswordVisibilityChange
    {
        INITIAL,
        TOGGLE,
        SEEK,
        SHOW_SUBSET
    }

    private static final Pattern SUBSET_SPLIT = Pattern.compile("[ ,;]+");
    private static final char[] SUBSET_CHARS =
            { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
              '-', ' ', ',', ';', '?' };

    private boolean itsIsPasswordShown = false;
    private String itsHiddenPasswordStr;
    private String itsSubsetErrorStr;
    private String itsTitle;
    private View itsBaseRow;
    private TextView itsBaseLabel;
    private TextView itsBase;
    private View itsGroupRow;
    private TextView itsGroup;
    private View itsUserRow;
    private TextView itsUser;
    private View itsPasswordRow;
    private TextView itsPassword;
    private Runnable itsPasswordHideRun;
    private SeekBar itsPasswordSeek;
    private CompoundButton itsPasswordSubsetBtn;
    private TextInputLayout itsPasswordSubsetInput;
    private TextView itsPasswordSubset;
    private View itsUrlRow;
    private TextView itsUrl;
    private View itsEmailRow;
    private TextView itsEmail;
    private View itsTimesRow;
    private View itsCreationTimeRow;
    private TextView itsCreationTime;
    private View itsLastModTimeRow;
    private TextView itsLastModTime;
    private View itsProtectedRow;
    private View itsReferencesRow;
    private ListView itsReferences;

    /**
     * Create a new instance of the fragment
     */
    public static PasswdSafeRecordBasicFragment newInstance(
            PasswdLocation location)
    {
        PasswdSafeRecordBasicFragment frag = new PasswdSafeRecordBasicFragment();
        frag.setArguments(createArgs(location));
        return frag;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        setHasOptionsMenu(true);
        View root = inflater.inflate(R.layout.fragment_passwdsafe_record_basic,
                                     container, false);
        itsBaseRow = root.findViewById(R.id.base_row);
        itsBaseRow.setOnClickListener(this);
        itsBaseLabel = root.findViewById(R.id.base_label);
        itsBase = root.findViewById(R.id.base);
        View baseBtn = root.findViewById(R.id.base_btn);
        baseBtn.setOnClickListener(this);
        itsGroupRow = root.findViewById(R.id.group_row);
        itsGroup = root.findViewById(R.id.group);
        itsUserRow = root.findViewById(R.id.user_row);
        itsUser = root.findViewById(R.id.user);
        itsPasswordRow = root.findViewById(R.id.password_row);
        itsPasswordRow.setOnClickListener(this);
        itsPassword = root.findViewById(R.id.password);
        itsPasswordSeek = root.findViewById(R.id.password_seek);
        itsPasswordSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener()
                {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                                                  boolean fromUser)
                    {
                        if (fromUser) {
                            updatePasswordShown(PasswordVisibilityChange.SEEK,
                                                progress, false);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar)
                    {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar)
                    {
                    }
                });
        itsPasswordSubsetBtn = root.findViewById(R.id.password_subset_btn);
        itsPasswordSubsetBtn.setOnCheckedChangeListener(this);
        itsPasswordSubsetBtn.setOnLongClickListener(this);
        itsPasswordSubsetInput = root.findViewById(R.id.password_subset_input);
        itsPasswordSubset = root.findViewById(R.id.password_subset);
        itsPasswordSubset.addTextChangedListener(new AbstractTextWatcher()
        {
            @Override
            public void afterTextChanged(Editable editable)
            {
                passwordSubsetChanged();
            }
        });
        itsPasswordSubset.setKeyListener(new NumberKeyListener()
        {
            @NonNull
            @Override
            protected char[] getAcceptedChars()
            {
                return SUBSET_CHARS;
            }

            @Override
            public int getInputType()
            {
                return InputType.TYPE_CLASS_NUMBER |
                       InputType.TYPE_NUMBER_FLAG_SIGNED;
            }
        });
        itsPasswordHideRun = () ->
                updatePasswordShown(PasswordVisibilityChange.INITIAL, 0, false);
        itsUrlRow = root.findViewById(R.id.url_row);
        itsUrl = root.findViewById(R.id.url);
        itsEmailRow = root.findViewById(R.id.email_row);
        itsEmail = root.findViewById(R.id.email);
        itsTimesRow = root.findViewById(R.id.times_row);
        itsCreationTimeRow = root.findViewById(R.id.creation_time_row);
        itsCreationTime = root.findViewById(R.id.creation_time);
        itsLastModTimeRow = root.findViewById(R.id.last_mod_time_row);
        itsLastModTime = root.findViewById(R.id.last_mod_time);
        itsProtectedRow = root.findViewById(R.id.protected_row);
        itsReferencesRow = root.findViewById(R.id.references_row);
        itsReferences = root.findViewById(R.id.references);
        itsReferences.setOnItemClickListener(
                (parent, view, position, id) -> showRefRec(false, position));

        registerForContextMenu(itsUserRow);
        registerForContextMenu(itsPasswordRow);
        updatePasswordShown(PasswordVisibilityChange.INITIAL, 0, false);

        return root;
    }

    @Override
    public void onPause()
    {
        super.onPause();
        itsPassword.removeCallbacks(itsPasswordHideRun);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu)
    {
        MenuItem item = menu.findItem(R.id.menu_toggle_password);
        if (item != null) {
            item.setTitle(
                    itsIsPasswordShown ?
                            R.string.hide_password : R.string.show_password);
            item.setEnabled(itsPasswordRow.getVisibility() == View.VISIBLE);
        }

        item = menu.findItem(R.id.menu_copy_url);
        if (item != null) {
            item.setVisible(itsUrlRow.getVisibility() == View.VISIBLE);
        }

        item = menu.findItem(R.id.menu_copy_email);
        if (item != null) {
            item.setVisible(itsEmailRow.getVisibility() == View.VISIBLE);
        }

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
        case R.id.menu_copy_user: {
            copyUser();
            return true;
        }
        case R.id.menu_copy_password: {
            copyPassword();
            return true;
        }
        case R.id.menu_copy_url: {
            copyUrl();
            return true;
        }
        case R.id.menu_copy_email: {
            copyEmail();
            return true;
        }
        case R.id.menu_toggle_password: {
            updatePasswordShown(PasswordVisibilityChange.TOGGLE, 0, false);
            return true;
        }
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, view, menuInfo);
        menu.setHeaderTitle(itsTitle);
        switch (view.getId()) {
        case R.id.user_row: {
            menu.add(PasswdSafe.CONTEXT_GROUP_RECORD_BASIC,
                     R.id.menu_copy_user, 0, R.string.copy_user);
            break;
        }
        case R.id.password_row: {
            menu.add(PasswdSafe.CONTEXT_GROUP_RECORD_BASIC,
                     R.id.menu_copy_password, 0, R.string.copy_password);
            break;
        }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        if (item.getGroupId() != PasswdSafe.CONTEXT_GROUP_RECORD_BASIC) {
            return super.onContextItemSelected(item);
        }

        switch (item.getItemId()) {
        case R.id.menu_copy_password: {
            copyPassword();
            return true;
        }
        case R.id.menu_copy_user: {
            copyUser();
            return true;
        }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId()) {
        case R.id.base_row:
        case R.id.base_btn: {
            showRefRec(true, 0);
            break;
        }
        case R.id.password_row: {
            updatePasswordShown(PasswordVisibilityChange.TOGGLE, 0, false);
            break;
        }
        }
    }

    @Override
    public boolean onLongClick(View v)
    {
        switch (v.getId()) {
        case R.id.password_subset_btn: {
            Toast.makeText(getContext(), R.string.password_subset,
                           Toast.LENGTH_SHORT).show();
            return true;
        }
        }
        return false;
    }

    @Override
    public void onCheckedChanged(CompoundButton btn, boolean checked)
    {
        switch (btn.getId()) {
        case R.id.password_subset_btn: {
            updatePasswordShown(PasswordVisibilityChange.SHOW_SUBSET,
                                0, checked);
            break;
        }
        }
    }

    @Override
    protected void doOnCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.fragment_passwdsafe_record_basic, menu);
    }

    @Override
    protected void doRefresh(@NonNull RecordInfo info)
    {
        PwsRecord ref = info.itsPasswdRec.getRef();
        PwsRecord recForPassword = info.itsRec;
        int hiddenId = R.string.hidden_password_normal;
        String url = null;
        String email = null;
        Date creationTime = null;
        Date lastModTime = null;
        switch (info.itsPasswdRec.getType()) {
        case NORMAL: {
            itsBaseRow.setVisibility(View.GONE);
            url = info.itsFileData.getURL(info.itsRec);
            email = info.itsFileData.getEmail(info.itsRec);
            creationTime = info.itsFileData.getCreationTime(info.itsRec);
            lastModTime = info.itsFileData.getLastModTime(info.itsRec);
            break;
        }
        case ALIAS: {
            itsBaseRow.setVisibility(View.VISIBLE);
            itsBaseLabel.setText(R.string.alias_base_record_header);
            itsBase.setText(info.itsFileData.getId(ref));
            hiddenId = R.string.hidden_password_alias;
            recForPassword = ref;
            url = info.itsFileData.getURL(info.itsRec);
            email = info.itsFileData.getEmail(info.itsRec);
            creationTime = info.itsFileData.getCreationTime(recForPassword);
            lastModTime = info.itsFileData.getLastModTime(recForPassword);
            break;
        }
        case SHORTCUT: {
            itsBaseRow.setVisibility(View.VISIBLE);
            itsBaseLabel.setText(R.string.shortcut_base_record_header);
            itsBase.setText(info.itsFileData.getId(ref));
            hiddenId = R.string.hidden_password_shortcut;
            recForPassword = ref;
            creationTime = info.itsFileData.getCreationTime(recForPassword);
            lastModTime = info.itsFileData.getLastModTime(recForPassword);
            break;
        }
        }

        itsTitle = info.itsFileData.getTitle(info.itsRec);
        setFieldText(itsGroup, itsGroupRow,
                     info.itsFileData.getGroup(info.itsRec));
        setFieldText(itsUser, itsUserRow,
                     info.itsFileData.getUsername(info.itsRec));

        itsHiddenPasswordStr = getString(hiddenId);
        String password = info.itsFileData.getPassword(recForPassword);
        setFieldText(itsPassword, itsPasswordRow,
                     ((password != null) ? itsHiddenPasswordStr : null));
        int passwordLen = (password != null) ? password.length() : 0;
        itsPasswordSeek.setMax(passwordLen);
        itsSubsetErrorStr = getString(R.string.password_subset_error,
                                      passwordLen);
        updatePasswordShown(PasswordVisibilityChange.INITIAL, 0, false);

        setFieldText(itsUrl, itsUrlRow, url);
        setFieldText(itsEmail, itsEmailRow, email);

        GuiUtils.setVisible(itsTimesRow,
                            (creationTime != null) || (lastModTime != null));
        setFieldDate(itsCreationTime, itsCreationTimeRow, creationTime);
        setFieldDate(itsLastModTime, itsLastModTimeRow, lastModTime);
        GuiUtils.setVisible(itsProtectedRow,
                            info.itsFileData.isProtected(info.itsRec));

        List<PwsRecord> references = info.itsPasswdRec.getRefsToRecord();
        boolean hasReferences = (references != null) && !references.isEmpty();
        if (hasReferences) {
            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(getActivity(),
                                       android.R.layout.simple_list_item_1);
            for (PwsRecord refRec: references) {
                adapter.add(info.itsFileData.getId(refRec));
            }
            itsReferences.setAdapter(adapter);
        } else {
            itsReferences.setAdapter(null);
        }
        GuiUtils.setListViewHeightBasedOnChildren(itsReferences);
        GuiUtils.setVisible(itsReferencesRow, hasReferences);

        requireActivity().invalidateOptionsMenu();
    }

    /**
     * Show a referenced record
     */
    private void showRefRec(final boolean baseRef, final int referencingPos)
    {
        PasswdLocation location = useRecordInfo(info -> {
            PwsRecord refRec = null;
            if (baseRef) {
                refRec = info.itsPasswdRec.getRef();
            } else {
                List<PwsRecord> refs = info.itsPasswdRec.getRefsToRecord();
                if ((referencingPos >= 0) && (referencingPos < refs.size())) {
                    refRec = refs.get(referencingPos);
                }
            }
            if (refRec == null) {
                return null;
            }

            return new PasswdLocation(refRec, info.itsFileData);
        });
        if (location != null) {
            getListener().changeLocation(location);
        }
    }

    /**
     * Update whether the password is shown
     */
    private void updatePasswordShown(PasswordVisibilityChange change,
                                     int progress,
                                     boolean showSubset)
    {
        String password = null;
        boolean seekShown = true;
        boolean subsetShown = false;

        switch (change) {
        case INITIAL: {
            itsIsPasswordShown = false;
            itsPasswordSeek.setProgress(0);
            break;
        }
        case TOGGLE: {
            itsIsPasswordShown = !itsIsPasswordShown;
            if (itsIsPasswordShown) {
                password = getPassword();
            }
            itsPasswordSeek.setProgress(
                    itsIsPasswordShown ? itsPasswordSeek.getMax() : 0);
            break;
        }
        case SEEK: {
            if (progress == 0) {
                itsIsPasswordShown = false;
                password = itsHiddenPasswordStr;
            } else {
                itsIsPasswordShown = true;
                password = getPassword();
                if ((password != null) && (progress < password.length())) {
                    password = password.substring(0, progress) + "…";
                }
            }
            break;
        }
        case SHOW_SUBSET: {
            itsPasswordSeek.setProgress(0);
            if (showSubset) {
                seekShown = false;
                subsetShown = true;
                itsIsPasswordShown = true;
                password = "";
                itsPasswordSubset.setText(null);
            } else {
                itsIsPasswordShown = false;
            }
            break;
        }
        }

        itsPasswordSubsetBtn.setChecked(subsetShown);
        GuiUtils.setVisible(itsPasswordSeek, seekShown);
        GuiUtils.setTextInputVisible(itsPasswordSubsetInput, subsetShown);
        if (subsetShown) {
            itsPasswordSubset.requestFocus();
        }
        Activity act = getActivity();
        GuiUtils.setKeyboardVisible(itsPasswordSubset, act, subsetShown);
        itsPassword.setText(
                (password != null) ? password : itsHiddenPasswordStr);
        TypefaceUtils.enableMonospace(itsPassword, itsIsPasswordShown, act);
        itsPassword.removeCallbacks(itsPasswordHideRun);
        if (itsIsPasswordShown) {
            SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
            PasswdTimeoutPref timeout =
                    Preferences.getPasswdVisibleTimeoutPref(prefs);
            switch (timeout) {
            case TO_15_SEC:
            case TO_30_SEC:
            case TO_1_MIN:
            case TO_5_MIN: {
                itsPassword.postDelayed(itsPasswordHideRun,
                                        timeout.getTimeout());
                break;
            }
            case TO_NONE: {
                break;
            }
            }
        }
        act.invalidateOptionsMenu();
    }

    /**
     * Handle a change in the password subset to show
     */
    private void passwordSubsetChanged()
    {
        String subset = itsPasswordSubset.getText().toString();
        String password = getPassword();
        if (password == null) {
            itsPassword.setText("");
            return;
        }
        int passwordLen = password.length();
        StringBuilder passwordSubset = new StringBuilder();
        boolean error = false;
        String[] tokens = TextUtils.split(subset, SUBSET_SPLIT);
        for (int i = 0; i < tokens.length; ++i) {
            String trimToken = tokens[i].trim();
            if ((trimToken.length() == 0) ||
                ((i == (tokens.length - 1)) && trimToken.equals("-"))) {
                continue;
            }
            int idx;
            try {
                idx = Integer.parseInt(trimToken);
            } catch (Exception e) {
                error = true;
                break;
            }
            char c;
            if ((idx > 0) && (idx <= passwordLen)) {
                c = password.charAt(idx - 1);
            } else if ((idx < 0) && (-idx <= passwordLen)) {
                c = password.charAt(passwordLen + idx);
            } else {
                error = true;
                break;
            }
            if (passwordSubset.length() > 0) {
                passwordSubset.append(" ");
            }
            passwordSubset.append(c);
        }

        TextInputUtils.setTextInputError(error ? itsSubsetErrorStr : null,
                                         itsPasswordSubsetInput);
        itsPassword.setText(passwordSubset.toString());
    }

    /**
     * Copy the user name to the clipboard
     */
    private void copyUser()
    {
        getListener().copyField(CopyField.USER_NAME, getLocation().getRecord());
    }

    /**
     * Copy the password to the clipboard
     */
    private void copyPassword()
    {
        getListener().copyField(CopyField.PASSWORD, getLocation().getRecord());
    }

    /**
     * Copy the URL to the clipboard
     */
    private void copyUrl()
    {
        getListener().copyField(CopyField.URL, getLocation().getRecord());
    }

    /**
     * Copy the email to the clipboard
     */
    private void copyEmail()
    {
        getListener().copyField(CopyField.EMAIL, getLocation().getRecord());
    }

    /**
     * Get the password
     */
    private @Nullable
    String getPassword()
    {
        return useRecordInfo(
                info -> info.itsPasswdRec.getPassword(info.itsFileData));
    }
}
