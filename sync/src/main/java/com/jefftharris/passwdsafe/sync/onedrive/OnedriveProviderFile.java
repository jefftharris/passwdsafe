/*
 * Copyright (©) 2015-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.microsoft.graph.models.DriveItem;

import java.util.Locale;
import java.util.Objects;

/**
 *  Abstraction of an OneDrive remote file
 */
public class OnedriveProviderFile implements ProviderRemoteFile
{
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
        var parent = itsItem.getParentReference();
        if (parent != null) {
            var parentPath = Objects.requireNonNull(parent.getPath());
            // Per MS docs, path relative to root starts after the first colon
            var pos = parentPath.indexOf(':');
            if (pos >= 0) {
                builder.encodedPath(parentPath.substring(pos + 1));
            }
        }
        builder.appendPath(itsItem.getName());
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
    @NonNull
    public String getTitle()
    {
        return Objects.requireNonNull(itsItem.getName());
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
        var modtime = Objects.requireNonNull(itsItem.getLastModifiedDateTime());
        return modtime.toInstant().toEpochMilli();
    }

    /**
     * Get the file's hash code
     */
    @Override
    @NonNull
    public String getHash()
    {
        // TODO: sha256?
        var file = itsItem.getFile();
        return Objects.requireNonNull(
                (file != null) ? Objects
                        .requireNonNull(file.getHashes())
                        .getSha1Hash() :
                itsItem.getETag());
    }

    /**
     * Is the file a folder
     */
    @Override
    public boolean isFolder()
    {
        return (itsItem.getFolder() != null);
    }

    /**
     * Get a debugging string for the file
     */
    @Override
    public String toDebugString()
    {
        long modtime = getModTime();
        return String.format(
                Locale.US,
                "{name: %s, parent: %s, id: %s, folder: %b, remid: %s, " +
                "mod: %tc(%d)}",
                itsItem.getName(),
                (itsItem.getParentReference() != null) ?
                        itsItem.getParentReference().getPath() : "null",
                itsItem.getId(), itsRemoteId,
                (itsItem.getFolder() != null),
                modtime, modtime);
    }
}
