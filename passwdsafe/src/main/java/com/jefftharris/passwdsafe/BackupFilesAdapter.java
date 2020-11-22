/*
 * Copyright (©) 2020 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jefftharris.passwdsafe.db.BackupFile;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.Utils;

/**
 * A recycler view adapter for backup files
 */
public class BackupFilesAdapter
        extends ListAdapter<BackupFile, BackupFilesAdapter.ViewHolder>
{
    private SelectionTracker<Long> itsSelTracker;

    private static final String TAG = "BackupFilesAdapter";

    /**
     * Constructor
     */
    public BackupFilesAdapter()
    {
        super(new BackupFileDiff());
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext())
                                  .inflate(R.layout.backup_file_list_item,
                                           parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position)
    {
        BackupFile backup = getItem(position);
        boolean selected = (itsSelTracker != null) &&
                           itsSelTracker.isSelected((long)position);
        PasswdSafeUtil.dbginfo(TAG, "bind view pos %d, sel %b",
                               position, selected);
        holder.bind(backup, selected);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    /**
     * Create a selection lookup for a view
     */
    public ItemDetailsLookup<Long> createItemLookup(
            final RecyclerView recyclerView)
    {
        return new ItemDetailsLookup<Long>()
        {
            @Nullable
            @Override
            public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e)
            {
                View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
                if (view != null) {
                    return ((ViewHolder)recyclerView.getChildViewHolder(view))
                            .createItemDetails();
                }
                return null;
            }
        };
    }

    /**
     * Set the adapter's selection tracker
     */
    public void setSelectionTracker(SelectionTracker<Long> selTracker)
    {
        itsSelTracker = selTracker;
    }

    /**
     * View holder for displaying a backup file
     */
    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        private final TextView itsText;
        private final TextView itsModDate;

        /**
         * Constructor
         */
        public ViewHolder(View view)
        {
            super(view);
            itsText = view.findViewById(R.id.text);
            itsModDate = view.findViewById(R.id.mod_date);
        }

        /**
         * Bind a backup file to the view
         */
        public void bind(BackupFile backup, boolean selected)
        {
            itemView.setActivated(selected);

            itsText.setText(backup.title);
            itsText.requestLayout();

            itsModDate.setText(
                    Utils.formatDate(backup.date, itemView.getContext()));
        }

        /**
         * Create the item details for tracking selection
         */
        public ItemDetailsLookup.ItemDetails<Long> createItemDetails()
        {
            return new ItemDetailsLookup.ItemDetails<Long>() {

                @Override
                public int getPosition()
                {
                    return getAdapterPosition();
                }

                @Override
                public Long getSelectionKey()
                {
                    return getItemId();
                }
            };
        }
    }

    /**
     * BackupFile difference callbacks
     */
    private static class BackupFileDiff
            extends DiffUtil.ItemCallback<BackupFile>
    {

        @Override
        public boolean areItemsTheSame(@NonNull BackupFile oldItem,
                                       @NonNull BackupFile newItem)
        {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull BackupFile oldItem,
                                          @NonNull BackupFile newItem)
        {
            return oldItem.equals(newItem);
        }
    }
}