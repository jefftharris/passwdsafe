/*
 * Copyright (©) 2017-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

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
    private FilesViewModel itsFilesModel;

    /**
     * Constructor
     */
    public OnedriveFilesActivity()
    {
        super(ProviderType.ONEDRIVE, ProviderRemoteFile.PATH_SEPARATOR);
    }

    @Override
    protected void onCreate(Bundle args)
    {
        super.onCreate(args);
        itsFilesModel = new ViewModelProvider(this).get(FilesViewModel.class);
    }

    /**
     * Create a list files task
     */
    @Override
    protected AbstractListFilesTask createListFilesTask(
            Context ctx,
            AbstractListFilesTask.Callback cb)
    {
        return new ListFilesTask(itsFilesModel, ctx, cb);
    }


    /**
     * View model for the activity
     *
     * @noinspection WeakerAccess (must be public for ViewModel)
     */
    public static class FilesViewModel extends AndroidViewModel
    {
        public final OnedriveProvider itsProvider;
        public final MutableLiveData<OnedriveProviderClient> itsClientData;

        /** Constructor */
        public FilesViewModel(Application app)
        {
            super(app);
            itsProvider = (OnedriveProvider)
                    ProviderFactory.getProvider(ProviderType.ONEDRIVE, app);
            itsClientData = new MutableLiveData<>();
        }
    }


    /** Background task for listing files from OneDrive */
    private static class ListFilesTask extends AbstractListFilesTask
    {
        private final OnedriveProvider itsProvider;
        private final MutableLiveData<OnedriveProviderClient> itsActClientData;

        /** Constructor */
        protected ListFilesTask(@NonNull FilesViewModel filesModel,
                                @NonNull Context ctx,
                                @NonNull Callback cb)
        {
            super(ctx, cb);
            itsProvider = filesModel.itsProvider;
            itsActClientData = filesModel.itsClientData;
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
                final var activityClient = itsActClientData.getValue();
                itsProvider.useOneDriveService(client -> {
                    var providerClient = activityClient;
                    if (providerClient == null) {
                        providerClient = new OnedriveProviderClient(client);
                        itsActClientData.postValue(providerClient);
                    }

                    var resp = OnedriveUtils
                            .getFilePathRequest(providerClient, params[0])
                            .children()
                            .get(OnedriveUtils::updateGetChildrenRequest);

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
                }, (activityClient != null) ? activityClient.itsClient : null);
            } catch (Exception e) {
                result = Pair.create(null, e);
            }
            return result;
        }
    }
}
