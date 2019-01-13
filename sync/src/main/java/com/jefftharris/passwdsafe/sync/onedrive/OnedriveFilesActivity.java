/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.content.Context;
import android.util.Pair;

import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.ProviderFactory;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncedFilesActivity;
import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.microsoft.graph.extensions.DriveItem;
import com.microsoft.graph.extensions.IDriveItemRequestBuilder;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 *  Activity for managing files synced from OneDrive
 */
public class OnedriveFilesActivity extends AbstractSyncedFilesActivity
{
    private final OnedriveProvider itsProvider;

    private static final List<Option> QUERY_OPTIONS = Arrays.asList(
            new QueryOption("$expand", "children"),
            new QueryOption("$select", "id,name,lastModifiedDateTime,eTag," +
                                       "parentReference,children,folder,file"));

    /**
     * Constructor
     */
    public OnedriveFilesActivity()
    {
        super(ProviderType.ONEDRIVE);
        itsProvider = (OnedriveProvider)
                ProviderFactory.getProvider(ProviderType.ONEDRIVE, this);
    }

    /**
     * Create a list files task
     */
    @Override
    protected AbstractListFilesTask createListFilesTask(
            Context ctx,
            AbstractListFilesTask.Callback cb)
    {
        return new ListFilesTask(itsProvider, ctx, cb);
    }


    /** Background task for listing files from OneDrive */
    private static class ListFilesTask extends AbstractListFilesTask
    {
        private final OnedriveProvider itsProvider;


        /** Constructor */
        public ListFilesTask(OnedriveProvider provider,
                             Context ctx, Callback cb)
        {
            super(ctx, cb);
            itsProvider = provider;
        }


        @Override
        protected Pair<List<ProviderRemoteFile>, Exception>
        doInBackground(String... params)
        {
            List<ProviderRemoteFile> files = new ArrayList<>();
            Pair<List<ProviderRemoteFile>, Exception> result =
                    Pair.create(files, null);
            if (itsProvider == null) {
                return result;
            }

            try {
                itsProvider.useOneDriveService(client -> {
                    IDriveItemRequestBuilder rootRequest =
                            OnedriveUtils.getFilePathRequest(client, params[0]);
                    DriveItem item =
                            rootRequest.buildRequest(QUERY_OPTIONS).get();
                    if (item.children != null) {
                        for (DriveItem child: item.children.getCurrentPage()) {
                            files.add(new OnedriveProviderFile(child));
                        }
                    }
                });
            } catch (Exception e) {
                result = Pair.create(null, e);
            }
            return result;
        }
    }
}
