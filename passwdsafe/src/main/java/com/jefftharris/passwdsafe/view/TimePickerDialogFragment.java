/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.util.Calendar;

/**
 * Dialog to pick a time
 */
public class TimePickerDialogFragment extends DialogFragment
        implements TimePickerDialog.OnTimeSetListener
{
    public static final String REQUEST_KEY = "TimePickerDialogFragment";

    private static final String ARG_HOUR_OF_DAY = "hourOfDay";

    private static final String ARG_MINUTE = "minute";

    /**
     * Create a new instance
     */
    @NonNull
    public static TimePickerDialogFragment newInstance(int hourOfDay,
                                                       int minute)
    {
        TimePickerDialogFragment frag = new TimePickerDialogFragment();
        frag.setArguments(createBundle(hourOfDay, minute));
        return frag;
    }

    /**
     * Set the fragment listener for results
     */
    public static <T extends Fragment & FragmentResultListener>
    void setListener(@NonNull T listener)
    {
        var fragMgr = listener.getParentFragmentManager();
        fragMgr.setFragmentResultListener(REQUEST_KEY, listener, listener);
    }

    /**
     * Update a date from the fragment result
     */
    public static void updateDateFromResult(@NonNull Bundle result,
                                            @NonNull Calendar date)
    {
        DatePickerDialogFragment.setResultField(result, ARG_HOUR_OF_DAY, date,
                                                Calendar.HOUR_OF_DAY);
        DatePickerDialogFragment.setResultField(result, ARG_MINUTE, date,
                                                Calendar.MINUTE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = requireArguments();
        Calendar now = Calendar.getInstance();
        int hourOfDay =
                args.getInt(ARG_HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        int minute = args.getInt(ARG_MINUTE, now.get(Calendar.MINUTE));

        return new TimePickerDialog(getContext(), this, hourOfDay, minute,
                                    DateFormat.is24HourFormat(getContext()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute)
    {
        var fragMgr = getParentFragmentManager();
        fragMgr.setFragmentResult(REQUEST_KEY, createBundle(hourOfDay, minute));
    }

    @NonNull
    private static Bundle createBundle(int hourOfDay,
                                       int minute)
    {
        Bundle args = new Bundle();
        args.putInt(ARG_HOUR_OF_DAY, hourOfDay);
        args.putInt(ARG_MINUTE, minute);
        return args;
    }
}
