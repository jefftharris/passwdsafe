/*
 * Copyright (©) 2015-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jefftharris.passwdsafe.file.PasswdFileData;

import org.jetbrains.annotations.Contract;
import org.pwsafe.lib.file.PwsRecord;

import java.util.ArrayList;

/**
 * The PasswdLocation class encapsulates a location within a password file
 */
public class PasswdLocation implements Parcelable
{
    public static final Parcelable.Creator<PasswdLocation> CREATOR =
            new Parcelable.Creator<>()
            {
                @NonNull
                @Contract("_ -> new")
                public PasswdLocation createFromParcel(Parcel in)
                {
                    return new PasswdLocation(in);
                }

                @NonNull
                @Contract(value = "_ -> new", pure = true)
                public PasswdLocation[] newArray(int size)
                {
                    return new PasswdLocation[size];
                }
            };

    private final ArrayList<String> itsGroups = new ArrayList<>();
    private final String itsRecord;

    /** Default constructor */
    public PasswdLocation()
    {
        itsRecord = null;
    }

    /** Constructor with groups and a specific record */
    private PasswdLocation(ArrayList<String> groups, String record)
    {
        if (groups != null) {
            itsGroups.addAll(groups);
        }
        itsRecord = record;
    }

    /** Constructor from a password record */
    public PasswdLocation(PwsRecord rec, @NonNull PasswdFileData fileData)
    {
        String group = fileData.getGroup(rec);
        if (!TextUtils.isEmpty(group)) {
            PasswdFileData.splitGroup(group, itsGroups);
        }
        itsRecord = fileData.getUUID(rec);
    }

    /** Constructor from a group */
    public PasswdLocation(String group)
    {
        if (!TextUtils.isEmpty(group)) {
            PasswdFileData.splitGroup(group, itsGroups);
        }
        itsRecord = null;
    }

    /** Constructor from a parcel */
    private PasswdLocation(@NonNull Parcel parcel)
    {
        parcel.readStringList(itsGroups);
        byte hasRecord = parcel.readByte();
        if (hasRecord != 0) {
            itsRecord = parcel.readString();
        } else {
            itsRecord = null;
        }
    }

    /** Get the location's groups */
    public ArrayList<String> getGroups()
    {
        return itsGroups;
    }

    /** Get the path string for the groups */
    public String getGroupPath()
    {
        return TextUtils.join(" / ", itsGroups);
    }

    /** Get the path string for the group as stored in a record */
    @Nullable
    public String getRecordGroup()
    {
        if (itsGroups.isEmpty()) {
            return null;
        }
        return TextUtils.join(".", itsGroups);
    }

    /** Does the location represent a record */
    public boolean isRecord()
    {
        return itsRecord != null;
    }

    /** Get the location's specific record; null for no record */
    public String getRecord()
    {
        return itsRecord;
    }

    /** Get a new location with a selected record */
    public PasswdLocation selectRecord(String record)
    {
        return new PasswdLocation(itsGroups, record);
    }

    /** Get a new location with a child group selected */
    public PasswdLocation selectGroup(String group)
    {
        PasswdLocation loc = new PasswdLocation(itsGroups, null);
        loc.itsGroups.add(group);
        return loc;
    }

    /** Get a new location with one group popped from the list */
    public PasswdLocation popGroup()
    {
        PasswdLocation loc = new PasswdLocation(itsGroups, null);
        if (!loc.itsGroups.isEmpty()) {
            loc.itsGroups.remove(loc.itsGroups.size() - 1);
        }
        return loc;
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags)
    {
        parcel.writeStringList(itsGroups);
        parcel.writeByte((byte)((itsRecord != null) ? 1 : 0));
        if (itsRecord != null) {
            parcel.writeString(itsRecord);
        }
    }

    /** Are the locations' groups equal */
    public boolean equalGroups(@NonNull PasswdLocation loc)
    {
        return itsGroups.equals(loc.itsGroups);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PasswdLocation location)) {
            return false;
        }
        return equalGroups(location) &&
               TextUtils.equals(itsRecord, location.itsRecord);
    }

    @Override
    @NonNull
    public String toString()
    {
        return String.format("{rec: %s, groups: %s}",
                             itsRecord, getGroupPath());
    }
}
