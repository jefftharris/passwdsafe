/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.file.PasswdExpiryFilter;
import com.jefftharris.passwdsafe.file.PasswdFileDataUser;
import com.jefftharris.passwdsafe.file.PasswdFileUri;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.util.Pair;
import com.jefftharris.passwdsafe.view.ConfirmPromptDialog;
import com.jefftharris.passwdsafe.view.DatePickerDialogFragment;

import java.util.Calendar;
import java.util.Date;

/**
 * Fragment for password expiration information
 */
public class PasswdSafeExpirationsFragment
        extends AbstractPasswdSafeFileDataFragment
                        <PasswdSafeExpirationsFragment.Listener>
        implements AdapterView.OnItemClickListener,
                   CompoundButton.OnCheckedChangeListener,
                   ConfirmPromptDialog.Listener,
                   DatePickerDialogFragment.Listener
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
            extends AbstractPasswdSafeFileDataFragment.Listener
    {
        /** Update the view for expiration info */
        void updateViewExpirations();

        /** Set the expiration record filter */
        void setRecordExpiryFilter(PasswdExpiryFilter filter, Date customDate);
    }

    private static final String TAG = "PasswdSafeExpirationsFragment";

    private CheckBox itsEnableExpiryNotifs;

    /**
     * Create a new instance
     */
    public static PasswdSafeExpirationsFragment newInstance()
    {
        return new PasswdSafeExpirationsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState)
    {
        View root = inflater.inflate(R.layout.fragment_passwdsafe_expirations,
                                     container, false);

        itsEnableExpiryNotifs = root.findViewById(R.id.enable_expiry_notifs);
        itsEnableExpiryNotifs.setOnCheckedChangeListener(this);
        ListView expirations = root.findViewById(R.id.expirations);
        expirations.setOnItemClickListener(this);

        return root;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        getListener().updateViewExpirations();
        refresh();
    }

    @Override
    public void onItemClick(AdapterView<?> list, View view, int pos, long id)
    {
        PasswdExpiryFilter filter = PasswdExpiryFilter.fromIdx(pos);
        PasswdSafeUtil.dbginfo(TAG, "Filter %s", filter);
        switch (filter) {
        case EXPIRED:
        case TODAY:
        case IN_A_WEEK:
        case IN_TWO_WEEKS:
        case IN_A_MONTH:
        case IN_A_YEAR:
        case ANY: {
            getListener().setRecordExpiryFilter(filter, null);
            break;
        }
        case CUSTOM: {
            Calendar now = Calendar.getInstance();
            DatePickerDialogFragment picker =
                    DatePickerDialogFragment.newInstance(
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH));
            picker.setTargetFragment(this, 0);
            picker.show(getParentFragmentManager(), "datePicker");
            break;
        }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean isChecked)
    {
        PasswdSafeUtil.dbginfo(TAG, "onCheckedChanged checked %b", isChecked);
        if (button.getId() == R.id.enable_expiry_notifs) {
            if (isChecked) {
                ConfirmPromptDialog dialog = ConfirmPromptDialog.newInstance(
                        getString(R.string.expiration_notifications),
                        getString(R.string.expiration_notifications_warning),
                        getString(R.string.enable), null);
                dialog.setTargetFragment(this, 0);
                dialog.show(getParentFragmentManager(), "expiry");
            } else {
                setExpiryNotif(false);
            }
        }
    }

    @Override
    public void handleDatePicked(int year, int monthOfYear, int dayOfMonth)
    {
        Listener listener = getListener();
        if (listener == null) {
            return;
        }
        Calendar date = Calendar.getInstance();
        date.set(year, monthOfYear, dayOfMonth, 0, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        date.add(Calendar.DAY_OF_MONTH, 1);
        listener.setRecordExpiryFilter(PasswdExpiryFilter.CUSTOM,
                                       date.getTime());
    }

    @Override
    public void promptConfirmed(Bundle confirmArgs)
    {
        setExpiryNotif(true);
    }

    @Override
    public void promptCanceled()
    {
        refresh();
    }

    @Override
    protected void doOnCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    }

    /**
     * Refresh the view
     */
    private void refresh()
    {
        Pair<Boolean, Boolean> rc = getListener().useFileData(fileData -> {
            PasswdFileUri uri = fileData.getUri();
            boolean enabled = NotificationMgr.notifSupported(uri);
            boolean checked = false;
            if (enabled) {
                NotificationMgr notifyMgr = getNotifyMgr();
                checked = notifyMgr.hasPasswdExpiryNotif(uri);
            }
            return new Pair<>(enabled, checked);
        });
        if (rc != null) {
            // Disable listener to set state
            itsEnableExpiryNotifs.setOnCheckedChangeListener(null);
            itsEnableExpiryNotifs.setEnabled(rc.first);
            itsEnableExpiryNotifs.setChecked(rc.second);
            itsEnableExpiryNotifs.setOnCheckedChangeListener(this);
        }
    }

    /**
     * Set whether expiration notifications are enabled
     */
    private void setExpiryNotif(final boolean enabled)
    {
        getListener().useFileData((PasswdFileDataUser<Void>)fileData -> {
            NotificationMgr notifyMgr = getNotifyMgr();
            notifyMgr.setPasswdExpiryNotif(fileData, enabled);
            return null;
        });
        refresh();
    }

    /**
     * Get the notification manager
     */
    private NotificationMgr getNotifyMgr()
    {
        PasswdSafeApp app = (PasswdSafeApp)requireActivity().getApplication();
        return app.getNotifyMgr();
    }
}
