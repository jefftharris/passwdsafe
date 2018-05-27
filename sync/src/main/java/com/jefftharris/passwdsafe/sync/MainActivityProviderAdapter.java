/*
 * Copyright (©) 2018 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.database.Cursor;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.jefftharris.passwdsafe.lib.view.CursorRecyclerViewAdapter;

/**
 * RecyclerView adapter class for providers in the MainActivity
 */
class MainActivityProviderAdapter
        extends CursorRecyclerViewAdapter<MainActivityProviderHolder>
{
    private final MainActivityProviderOps itsProviderOps;

    /**
     * Constructor
     */
    public MainActivityProviderAdapter(MainActivityProviderOps ops)
    {
        itsProviderOps = ops;
    }

    @Override
    protected void onBindViewHolder(MainActivityProviderHolder holder,
                                    Cursor item)
    {
        holder.updateView(item);
    }

    @NonNull
    @Override
    public MainActivityProviderHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.recyclerview_item_provider,
                         parent, false);
        return new MainActivityProviderHolder(v, itsProviderOps);
    }
}
