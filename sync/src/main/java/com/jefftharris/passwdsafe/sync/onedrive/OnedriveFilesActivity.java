/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
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
import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;

import java.util.ArrayList;
import java.util.List;


/**
 *  Activity for managing files synced from OneDrive
 */
public class OnedriveFilesActivity extends AbstractSyncedFilesActivity
{
    private final OnedriveProvider itsProvider;

    private static final String[] QUERY_SELECT =
            new String[]{"id", "name", "lastModifiedDateTime", "eTag",
                         "parentReference", "children", "folder", "file"};

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
        protected ListFilesTask(OnedriveProvider provider,
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
                // TODO: Need some mechanism for caching the graph client and
                //  the drive id for the activity
                itsProvider.useOneDriveService(client -> {
                    var providerClient = new OnedriveProviderClient(client);
                    var resp = OnedriveUtils
                            .getFilePathRequest(providerClient, params[0])
                            .children()
                            .get(requestCfg -> {
                                if (requestCfg.queryParameters != null) {
                                    requestCfg.queryParameters.select =
                                            QUERY_SELECT;
                                }
                            });

                    var pageIter =
                            new PageIterator.Builder<DriveItem,
                                    DriveItemCollectionResponse>()
                                    .client(client)
                                    .collectionPage(resp)
                                    .collectionPageFactory(
                                            DriveItemCollectionResponse::createFromDiscriminatorValue)
                                    .processPageItemCallback(item -> {
                                        files.add(
                                                new OnedriveProviderFile(item));
                                        return true;
                                    })
                                    .build();
                    pageIter.iterate();
                });
            } catch (Exception e) {
                result = Pair.create(null, e);
            }
            return result;
        }
    }
}
