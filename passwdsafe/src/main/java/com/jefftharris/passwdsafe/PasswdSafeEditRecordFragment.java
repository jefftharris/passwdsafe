/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jefftharris.passwdsafe.view.PasswdLocation;


/**
 * Fragment for editing a password record
 */
public class PasswdSafeEditRecordFragment extends Fragment
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Update the view for editing a record */
        void updateViewEditRecord(PasswdLocation location);
    }

    private PasswdLocation itsLocation;
    private Listener itsListener;

    /**
     * Create a new instance
     */
    public static PasswdSafeEditRecordFragment newInstance(
            PasswdLocation location)
    {
        PasswdSafeEditRecordFragment frag = new PasswdSafeEditRecordFragment();
        Bundle args = new Bundle();
        args.putParcelable("location", location);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        PasswdLocation location;
        if (args != null) {
            location = args.getParcelable("location");
        } else {
            location = new PasswdLocation();
        }
        itsLocation = location;
    }

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        itsListener = (Listener)activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View rootView = inflater.inflate(
                R.layout.fragment_passwdsafe_edit_record, container, false);

        return rootView;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        itsListener.updateViewEditRecord(itsLocation);
        refresh();
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    /**
     * Refresh the view
     */
    private void refresh()
    {
    }
}
