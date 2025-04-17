/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import com.jefftharris.passwdsafe.file.PasswdFileDataUser;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;

/**
 * Base fragment for accessing password file data
 */
public abstract class AbstractPasswdSafeFileDataFragment
        <ListenerT extends AbstractPasswdSafeFileDataFragment.Listener>
        extends Fragment
        implements MenuProvider
{
    /**
     * Listener interface for owning activity
     */
    public interface Listener
    {
        /** Use the file data */
        @Nullable
        <RetT> RetT useFileData(PasswdFileDataUser<RetT> user);

        /** Is the navigation drawer closed */
        boolean isNavDrawerClosed();
    }

    private ListenerT itsListener;

    @Override
    public void onAttach(@NonNull Context ctx)
    {
        super.onAttach(ctx);
        //noinspection unchecked
        itsListener = (ListenerT)ctx;
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        itsListener = null;
    }

    @Override
    public final void onCreateMenu(@NonNull Menu menu,
                                   @NonNull MenuInflater inflater)
    {
        if ((itsListener != null) && itsListener.isNavDrawerClosed()) {
            doOnCreateMenu(menu, inflater);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem)
    {
        return false;
    }

    /**
     * Enable the options menu for the fragment
     */
    protected void enableMenu()
    {
        GuiUtils.enableOptionsMenu(this);
    }

    /**
     * Get the context listener
     */
    protected final ListenerT getListener()
    {
        return itsListener;
    }

    /**
     * Derived-class create options menu
     */
    protected void doOnCreateMenu(@NonNull Menu menu,
                                  @NonNull MenuInflater inflater)
    {
    }

    /**
     * Use the file data
     */
    @Nullable
    protected final <RetT> RetT useFileData(PasswdFileDataUser<RetT> user)
    {
        if (isAdded() && itsListener != null) {
            return itsListener.useFileData(user);
        }
        return null;
    }
}
