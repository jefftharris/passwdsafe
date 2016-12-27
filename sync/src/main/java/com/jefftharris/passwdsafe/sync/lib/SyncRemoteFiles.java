/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.support.v4.util.LongSparseArray;

import java.util.HashMap;
import java.util.Map;

/**
 * The SyncRemoteFiles class encapsulates remote files gathered during a sync
 */
public class SyncRemoteFiles
{
    /// Map from remote file id to remote file
    private final Map<String, ProviderRemoteFile> itsRemoteFiles =
            new HashMap<>();

    /// Map from db file id to remote file for new local files
    private final LongSparseArray<ProviderRemoteFile> itsRemoteFilesForNew =
            new LongSparseArray<>();

    /// Map from db file id to updated remote file id
    private final LongSparseArray<String> itsUpdatedRemoteIds =
            new LongSparseArray<>();

    /**
     * Constructor
     */
    public SyncRemoteFiles()
    {
    }

    /**
     * Get a remote file by remote ID
     */
    public ProviderRemoteFile getRemoteFile(String remoteId)
    {
        return itsRemoteFiles.get(remoteId);
    }

    /**
     * Get the existing remote files
     */
    public Iterable<ProviderRemoteFile> getRemoteFiles()
    {
        return itsRemoteFiles.values();
    }

    /**
     * Add an existing remote file
     */
    public void addRemoteFile(ProviderRemoteFile remoteFile)
    {
        itsRemoteFiles.put(remoteFile.getRemoteId(), remoteFile);
    }

    /**
     * Get a remote file for the file id of a new local file
     */
    public ProviderRemoteFile getRemoteFileForNew(long dbFileId)
    {
        return itsRemoteFilesForNew.get(dbFileId);
    }

    /**
     * Add a remote file for the file id of a new local file
     */
    public void addRemoteFileForNew(long dbFileId,
                                    ProviderRemoteFile remoteFile)
    {
        itsRemoteFilesForNew.put(dbFileId, remoteFile);
    }

    /**
     * Are there any updated remote ids
     */
    public boolean hasUpdatedRemoteIds()
    {
        return itsUpdatedRemoteIds.size() > 0;
    }

    /**
     * Get an updated remote id for a file
     */
    public String getUpdatedRemoteId(long dbFileId)
    {
        return itsUpdatedRemoteIds.get(dbFileId);
    }

    /**
     * Add an updated remote id for a file
     */
    public void addUpdatedRemoteId(long dbFileId, String remoteId)
    {
        itsUpdatedRemoteIds.put(dbFileId, remoteId);
    }
}
