/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.lib.view.GuiUtils;
import com.jefftharris.passwdsafe.lib.view.TypefaceUtils;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PasswdHistory
{
    public static class Entry implements Comparable<Entry>
    {
        private Date itsDate;
        private final String itsPasswd;

        protected Entry(Date date, String passwd)
        {
            itsDate = date;
            itsPasswd = passwd;
        }

        protected Date getDate()
        {
            return itsDate;
        }

        protected void setDate(Date date)
        {
            itsDate = date;
        }

        public String getPasswd()
        {
            return itsPasswd;
        }

        public int compareTo(@NonNull Entry arg0)
        {
            // Sort descending
            return -itsDate.compareTo(arg0.itsDate);
        }

        @Override
        @NonNull
        public String toString()
        {
            return itsPasswd + " [" + itsDate + "]";
        }
    }

    public static final int MAX_SIZE_MIN = 0;
    public static final int MAX_SIZE_MAX = 255;

    private boolean itsIsEnabled;
    private int itsMaxSize;
    // Sorted with newest entry first
    private final List<Entry> itsPasswds = new ArrayList<>();

    public PasswdHistory(@NonNull String historyStr)
        throws IllegalArgumentException
    {
        int historyLen = historyStr.length();
        if (historyLen < 5) {
            throw new IllegalArgumentException(
                "Field length (" + historyLen + ") too short: " + 5);
        }

        itsIsEnabled = historyStr.charAt(0) != '0';
        itsMaxSize = Integer.parseInt(historyStr.substring(1, 3), 16);
        if (itsMaxSize > 255) {
            throw new IllegalArgumentException(
                "Invalid max size: " + itsMaxSize);
        }

        int numEntries = Integer.parseInt(historyStr.substring(3, 5), 16);
        if (numEntries > 255) {
            throw new IllegalArgumentException(
                "Invalid numEntries: " + numEntries);
        }

        int pos = 5;
        while (pos < historyLen) {
            if (pos + 12 >= historyLen) {
                throw new IllegalArgumentException(
                    "Field length (" + historyLen + ") too short: " +
                    (pos + 12));
            }

            long date = Long.parseLong(historyStr.substring(pos, pos + 8), 16);
            int passwdLen =
                Integer.parseInt(historyStr.substring(pos + 8, pos + 12), 16);
            pos += 12;

            if (pos + passwdLen > historyLen) {
                throw new IllegalArgumentException(
                    "Field length (" + historyLen + ") too short: " +
                    (pos + passwdLen));
            }

            String passwd = historyStr.substring(pos, pos + passwdLen);
            itsPasswds.add(new Entry(new Date(date * 1000L), passwd));
            pos += passwdLen;
        }
        Collections.sort(itsPasswds);
    }

    public PasswdHistory()
    {
        itsIsEnabled = true;
        itsMaxSize = 5;
    }

    public boolean isEnabled()
    {
        return itsIsEnabled;
    }

    public void setEnabled(boolean enabled)
    {
        itsIsEnabled = enabled;
    }

    public int getMaxSize()
    {
        return itsMaxSize;
    }

    public void setMaxSize(int maxSize)
    {
        if (maxSize < 0) {
            return;
        }
        itsMaxSize = maxSize;
    }

    public void adjustEntriesToMaxSize()
    {
        while (itsMaxSize < itsPasswds.size()) {
            // Remove oldest
            itsPasswds.remove(itsPasswds.size() - 1);
        }
    }

    public List<Entry> getPasswds()
    {
        return itsPasswds;
    }

    /// Add the password and the date it was last modified to the history
    public void addPasswd(String passwd, Date passwdDate)
    {
        if (itsIsEnabled && (itsMaxSize > 0)) {
            if (itsPasswds.size() == itsMaxSize) {
                // Remove oldest
                itsPasswds.remove(itsPasswds.size() - 1);
            }
            if (passwdDate == null) {
                passwdDate = new Date();
            }

            // Adjust times of current entries if they are later than the
            // date for the new password.  Ensure the same relative ordering
            // is kept.
            Date maxDate = passwdDate;
            for (Entry entry: itsPasswds) {
                if (entry.getDate().compareTo(maxDate) >= 0) {
                    entry.setDate(maxDate);
                    maxDate = new Date(maxDate.getTime() - 1*1000);
                }
            }

            itsPasswds.add(new Entry(passwdDate, passwd));
            Collections.sort(itsPasswds);
        }
    }

    @Override
    @NonNull
    public String toString()
    {
        StringBuilder strbld = new StringBuilder();
        String str = String.format(Locale.US, "%1d%02x%02x",
                                   isEnabled() ? 1 : 0,
                                   itsMaxSize, itsPasswds.size());
        strbld.append(str);

        for (Entry entry : itsPasswds) {
            String passwd = entry.getPasswd();
            str = String.format(Locale.US, "%08x%04x",
                                (int)(entry.getDate().getTime() / 1000),
                                passwd.length());
            strbld.append(str);
            strbld.append(passwd);
        }

        return strbld.toString();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o)
    {
        if (super.equals(o)) {
            return true;
        } else if (!(o instanceof PasswdHistory hist)) {
            return false;
        } else {
            if ((itsIsEnabled != hist.itsIsEnabled) ||
                (itsMaxSize != hist.itsMaxSize) ||
                (itsPasswds.size() != hist.itsPasswds.size())) {
                return false;
            } else {
                for (int i = 0; i < itsPasswds.size(); ++i) {
                    Entry e1 = itsPasswds.get(i);
                    Entry e2 = hist.itsPasswds.get(i);
                    if (!e1.getPasswd().equals(e2.getPasswd()) ||
                        !e1.getDate().equals(e2.getDate())) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    /**
     * Are two histories equal
     */
    public static boolean isEqual(PasswdHistory hist1, PasswdHistory hist2)
    {
        //noinspection SimplifiableIfStatement
        if (((hist1 == null) && (hist2 != null)) ||
            ((hist1 != null) && (hist2 == null))) {
            return false;
        }

        return (hist1 == null) || hist1.equals(hist2);
    }

    /**
     * Create a list adapter to show a history
     */
    @NonNull
    @Contract("_, _, _, _ -> new")
    public static ListAdapter createAdapter(@NonNull PasswdHistory history,
                                            boolean enabled,
                                            boolean hasContextMenu,
                                            Context ctx)
    {
        ArrayList<PasswdHistory.Entry> entries =
                new ArrayList<>(history.getPasswds());
        return new HistoryAdapter(entries, enabled, hasContextMenu, ctx);
    }


    /**
     * List adapter to show history entries
     */
    private static class HistoryAdapter extends ArrayAdapter<Entry>
    {
        private final boolean itsIsEnabled;
        private final boolean itsHasContextMenu;
        private final LayoutInflater itsInflater;

        /**
         * Constructor
         */
        protected HistoryAdapter(ArrayList<Entry> entries,
                                 boolean enabled,
                                 boolean hasContextMenu,
                                 Context ctx)
        {
            super(ctx, R.layout.passwd_history_list_item, entries);
            itsIsEnabled = enabled;
            itsHasContextMenu = hasContextMenu;
            itsInflater = LayoutInflater.from(ctx);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                            @NonNull ViewGroup parent)
        {
            ViewHolder itemViews;
            Context ctx = getContext();
            if (convertView == null) {
                convertView = itsInflater.inflate(
                        R.layout.passwd_history_list_item, parent, false);
                itemViews = new ViewHolder(convertView, itsIsEnabled,
                                           itsHasContextMenu, ctx);
                convertView.setTag(itemViews);
            } else {
                itemViews = (ViewHolder)convertView.getTag();
            }

            Entry entry = getItem(position);
            if (entry != null) {
                itemViews.update(entry, ctx);
            }
            return convertView;
        }

        /**
         * View holder class for fields in each entry's layout
         */
        private static class ViewHolder implements View.OnClickListener
        {
            private final TextView itsPassword;
            private final TextView itsDate;

            /**
             * Constructor
             */
            protected ViewHolder(@NonNull View view,
                                 boolean enabled,
                                 boolean hasContextMenu,
                                 Context ctx)
            {
                itsPassword = view.findViewById(R.id.password);
                TypefaceUtils.setMonospace(itsPassword, ctx);
                itsDate = view.findViewById(R.id.date);

                view.setEnabled(enabled);
                itsPassword.setEnabled(enabled);
                itsDate.setEnabled(enabled);

                View menuBtn = view.findViewById(R.id.item_menu);
                if (hasContextMenu) {
                    menuBtn.setEnabled(enabled);
                    menuBtn.setOnClickListener(this);
                } else {
                    GuiUtils.setVisible(menuBtn, false);
                }
            }

            /**
             * Update the layout fields with values from the entry
             */
            protected void update(@NonNull Entry entry, Context ctx)
            {
                itsPassword.setText(entry.getPasswd());
                itsDate.setText(Utils.formatDate(entry.getDate(), ctx));
            }

            @Override
            public void onClick(@NonNull View v)
            {
                v.showContextMenu();
            }
        }
    }
}
