/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.box;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.box.androidsdk.content.BoxApiFolder;
import com.box.androidsdk.content.BoxApiSearch;
import com.box.androidsdk.content.BoxApiUser;
import com.box.androidsdk.content.BoxConstants;
import com.box.androidsdk.content.BoxException;
import com.box.androidsdk.content.models.BoxError;
import com.box.androidsdk.content.models.BoxFile;
import com.box.androidsdk.content.models.BoxFolder;
import com.box.androidsdk.content.models.BoxItem;
import com.box.androidsdk.content.models.BoxIteratorItems;
import com.box.androidsdk.content.models.BoxJsonObject;
import com.box.androidsdk.content.models.BoxSession;
import com.box.androidsdk.content.models.BoxUser;
import com.box.androidsdk.content.requests.BoxRequestsFolder;
import com.box.androidsdk.content.requests.BoxRequestsSearch;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.sync.lib.AbstractLocalToRemoteSyncOper;
import com.jefftharris.passwdsafe.sync.lib.AbstractRemoteToLocalSyncOper;
import com.jefftharris.passwdsafe.sync.lib.AbstractRmSyncOper;
import com.jefftharris.passwdsafe.sync.lib.DbFile;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.ProviderRemoteFile;
import com.jefftharris.passwdsafe.sync.lib.ProviderSyncer;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;
import com.jefftharris.passwdsafe.sync.lib.SyncRemoteFiles;

import java.util.List;

/**
 * The BoxSyncer class encapsulates a Box sync operation
 */
public class BoxSyncer extends ProviderSyncer<BoxSession>
{
    private static final String[] FILE_FIELDS = new String[] {
            BoxFile.FIELD_ID, BoxFile.FIELD_TYPE, BoxFile.FIELD_NAME,
            BoxFile.FIELD_PATH_COLLECTION, BoxFile.FIELD_MODIFIED_AT,
            BoxFile.FIELD_ITEM_STATUS, BoxFile.FIELD_SIZE,
            BoxFile.FIELD_SHA1 };

    private static final String TAG = "BoxSyncer";

    /** Constructor */
    public BoxSyncer(BoxSession client, DbProvider provider,
                     SyncConnectivityResult connResult,
                     SyncLogRecord logrec, Context ctx)
    {
        super(client, provider, connResult, logrec, ctx, TAG);
    }

    /**
     * Get the account display name
     */
    @Nullable
    public static String getDisplayName(BoxSession client) throws Exception
    {
        BoxApiUser userApi = new BoxApiUser(client);
        BoxUser user = userApi.getCurrentUserInfoRequest().send();
        if (user != null) {
            return user.getName() + " (" + user.getLogin() + ")";
        } else {
            return null;
        }
    }

    /** Get the folder for a file */
    @NonNull
    public static String getFileFolder(@NonNull BoxItem file)
    {
        StringBuilder folderStr = new StringBuilder();
        for (BoxFolder folder: file.getPathCollection()) {
            if (folderStr.length() > 0) {
                folderStr.append("/");
            }
            folderStr.append(folder.getName());
        }
        return folderStr.toString();
    }

    @Override
    protected SyncRemoteFiles getSyncRemoteFiles(@NonNull List<DbFile> dbfiles)
            throws BoxException
    {
        BoxApiFolder folderApi = new BoxApiFolder(itsProviderClient);
        SyncRemoteFiles boxfiles = new SyncRemoteFiles();

        // Get root files
        retrieveBoxFolderFiles(BoxConstants.ROOT_FOLDER_ID, FILE_FIELDS,
                               folderApi, boxfiles);

        for (DbFile dbfile: dbfiles) {
            if (dbfile.itsRemoteId != null) {
                continue;
            }

            for (ProviderRemoteFile remfile: boxfiles.getRemoteFiles()) {
                if (TextUtils.equals(dbfile.itsLocalTitle,
                                     remfile.getTitle())) {
                    boxfiles.addRemoteFileForNew(dbfile.itsId, remfile);
                }
            }
        }

        // Get files in folders matching 'passwdsafe' search
        BoxApiSearch searchApi = new BoxApiSearch(itsProviderClient);
        BoxRequestsSearch.Search searchReq =
                searchApi.getSearchRequest("passwdsafe");

        long offset = 0L;
        boolean hasMoreFiles = true;
        while (hasMoreFiles) {
            searchReq.setOffset(Math.toIntExact(offset));
            BoxIteratorItems items = searchReq.send();
            for (BoxItem item: items) {
                PasswdSafeUtil.dbginfo(TAG, "search item %s",
                                       boxToString(item));
                if (item instanceof BoxFolder) {
                    retrieveBoxFolderFiles(item.getId(), FILE_FIELDS, folderApi,
                                           boxfiles);
                }
            }
            offset += items.limit();
            hasMoreFiles = (offset < items.fullSize()) &&
                           (offset < (long)Integer.MAX_VALUE);
        }

        return boxfiles;
    }

    @Override
    protected AbstractLocalToRemoteSyncOper<BoxSession>
    createLocalToRemoteOper(DbFile dbfile)
    {
        return new BoxLocalToRemoteOper(dbfile);
    }

    @Override
    protected AbstractRemoteToLocalSyncOper<BoxSession>
    createRemoteToLocalOper(DbFile dbfile)
    {
        return new BoxRemoteToLocalOper(dbfile);
    }

    @Override
    protected AbstractRmSyncOper<BoxSession>
    createRmFileOper(DbFile dbfile)
    {
        return new BoxRmFileOper(dbfile);
    }


    /** Update an exception thrown during syncing */
    @Override
    protected Exception updateSyncException(Exception e)
    {
        if (e instanceof BoxException boxExcept) {
            // Massage server exceptions to get the error
            BoxError serverError = boxExcept.getAsBoxError();
            if (serverError != null) {
                String msg = boxExcept.getMessage();
                if (TextUtils.isEmpty(msg)) {
                    msg = "Box server error";
                }
                e = new Exception(msg + ": " + boxToString(serverError), e);
            }
        }
        return e;
    }


    /** Retrieve the files in the given folder */
    @SuppressWarnings("SameParameterValue")
    private void retrieveBoxFolderFiles(
            String folderId,
            String[] fileFields,
            @NonNull BoxApiFolder folderApi,
            SyncRemoteFiles boxfiles)
            throws BoxException
    {
        BoxRequestsFolder.GetFolderItems req =
                folderApi.getItemsRequest(folderId);
        req.setFields(fileFields);

        long offset = 0L;
        boolean hasMoreItems = true;
        while (hasMoreItems) {
            req.setOffset(Math.toIntExact(offset));
            BoxIteratorItems items = req.send();
            for (BoxItem item: items) {
                PasswdSafeUtil.dbginfo(TAG, "item %s", boxToString(item));
                if (item instanceof BoxFile file) {
                    if (file.getName().endsWith(".psafe3")) {
                        boxfiles.addRemoteFile(new BoxProviderFile(file));
                    }
                }
            }
            offset += items.limit();
            hasMoreItems = (offset < items.fullSize()) &&
                           (offset < (long)Integer.MAX_VALUE);
        }
    }


    /** Convert a Box object to a string for debugging */
    private static String boxToString(BoxJsonObject obj)
    {
        return BoxProvider.boxToString(obj);
    }
}
