/*
 * Copyright (©) 2015 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.net.Uri;

import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.microsoft.graph.extensions.DriveItem;

import java.util.Locale;

/**
 *  Abstraction of an OneDrive remote file
 */
public class OnedriveProviderFile implements ProviderRemoteFile
{
    private static final String DRIVE_ROOT_PATH = "/drive/root:";

    private final DriveItem itsItem;
    private final String itsRemoteId;
    private final String itsPath;

    /**
     * Constructor
     */
    public OnedriveProviderFile(DriveItem item)
    {
        itsItem = item;
        Uri.Builder builder = new Uri.Builder();
        if (itsItem.parentReference != null) {
            builder.encodedPath(
                    itsItem.parentReference.path.substring(
                            DRIVE_ROOT_PATH.length()));
        }
        builder.appendPath(itsItem.name);
        Uri uri = builder.build();
        itsPath = uri.getPath();
        String remoteId = uri.getEncodedPath();
        if (remoteId != null) {
            remoteId = remoteId.toLowerCase();
        }
        itsRemoteId = remoteId;
    }

    /**
     * Get the file's remote identifier
     */
    @Override
    public String getRemoteId()
    {
        return itsRemoteId;
    }

    /**
     * Get the file's path for display
     */
    @Override
    public String getDisplayPath()
    {
        return itsPath;
    }

    /**
     * Get the file's title
     */
    @Override
    public String getTitle()
    {
        return itsItem.name;
    }

    /**
     * Get the file's folder
     */
    @Override
    public String getFolder()
    {
        int pos = itsPath.lastIndexOf(PATH_SEPARATOR);
        if (pos >= 0) {
            return itsPath.substring(0, pos);
        } else {
            return itsPath;
        }
    }

    /**
     * Get the file's modification time
     */
    @Override
    public long getModTime()
    {
        return itsItem.lastModifiedDateTime.getTimeInMillis();
    }

    /**
     * Get the file's hash code
     */
    @Override
    public String getHash()
    {
        return (itsItem.file != null) ?
                itsItem.file.hashes.sha1Hash : itsItem.eTag;
    }

    /**
     * Is the file a folder
     */
    @Override
    public boolean isFolder()
    {
        return (itsItem.folder != null);
    }

    /**
     * Get a debugging string for the file
     */
    @Override
    public String toDebugString()
    {
        long modtime = itsItem.lastModifiedDateTime.getTimeInMillis();
        return String.format(
                Locale.US,
                "{name: %s, parent: %s, id: %s, folder: %b, remid: %s, " +
                "mod: %tc(%d)}",
                itsItem.name,
                (itsItem.parentReference != null) ?
                        itsItem.parentReference.path : "null",
                itsItem.id, itsRemoteId,
                (itsItem.folder != null),
                modtime, modtime);
    }
}
