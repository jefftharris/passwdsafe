/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.DatePicker;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import java.util.Calendar;

/**
 * Dialog to pick a date
 */
public class DatePickerDialogFragment extends DialogFragment
        implements DatePickerDialog.OnDateSetListener
{
    public static final String REQUEST_KEY = "DatePickerDialogFragment";

    private static final String ARG_YEAR = "year";

    private static final String ARG_MONTH_OF_YEAR = "monthOfYear";

    private static final String ARG_DAY_OF_MONTH = "dayOfMonth";

    /**
     * Create a new instance
     */
    @NonNull
    public static DatePickerDialogFragment newInstance(int year,
                                                       int monthOfYear,
                                                       int dayOfMonth)
    {
        DatePickerDialogFragment frag = new DatePickerDialogFragment();
        frag.setArguments(createBundle(year, monthOfYear, dayOfMonth));
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
        setResultField(result, ARG_YEAR, date, Calendar.YEAR);
        setResultField(result, ARG_MONTH_OF_YEAR, date, Calendar.MONTH);
        setResultField(result, ARG_DAY_OF_MONTH, date, Calendar.DAY_OF_MONTH);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = requireArguments();
        Calendar now = Calendar.getInstance();
        int year = args.getInt(ARG_YEAR, now.get(Calendar.YEAR));
        int monthOfYear =
                args.getInt(ARG_MONTH_OF_YEAR, now.get(Calendar.MONTH));
        int dayOfMonth =
                args.getInt(ARG_DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));

        return new DatePickerDialog(requireContext(), this, year, monthOfYear,
                                    dayOfMonth);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear,
                          int dayOfMonth)
    {
        var fragMgr = getParentFragmentManager();
        fragMgr.setFragmentResult(REQUEST_KEY,
                                  createBundle(year, monthOfYear, dayOfMonth));
    }

    private static void setResultField(@NonNull Bundle result,
                                       @NonNull String key,
                                       @NonNull Calendar date,
                                       int field)
    {
        date.set(field, result.getInt(key, date.get(field)));
    }

    @NonNull
    private static Bundle createBundle(int year,
                                       int monthOfYear,
                                       int dayOfMonth)
    {
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, year);
        args.putInt(ARG_MONTH_OF_YEAR, monthOfYear);
        args.putInt(ARG_DAY_OF_MONTH, dayOfMonth);
        return args;
    }
}
