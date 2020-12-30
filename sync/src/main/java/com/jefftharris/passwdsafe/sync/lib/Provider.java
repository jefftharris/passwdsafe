/*
 * Copyright (©) 2017 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.accounts.Account;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import java.io.File;

/**
 * The Provider interface encapsulates a service that provides files which are
 * synchronized
 */
public interface Provider
{
    /** Initialize the provider */
    void init(@Nullable DbProvider dbProvider);

    /** Finalize the provider */
    void fini();

    /** Start the process of linking to an account */
    void startAccountLink(FragmentActivity activity, int requestCode);

    /** Finish the process of linking to an account */
    NewAccountTask<? extends AbstractSyncTimerProvider>
    finishAccountLink(FragmentActivity activity,
                      int activityRequestCode,
                      int activityResult,
                      Intent activityData,
                      Uri providerAcctUri);

    /** Unlink an account */
    void unlinkAccount();

    /** Is the account fully authorized */
    boolean isAccountAuthorized();

    /** Get the account for the named provider */
    Account getAccount(String acctName);

    /** Check whether a provider can be added */
    void checkProviderAdd(SQLiteDatabase db)
            throws Exception;

    /** Cleanup a provider when deleted */
    @SuppressWarnings("RedundantThrows")
    void cleanupOnDelete()
            throws Exception;

    /** Update a provider's sync frequency */
    void updateSyncFreq(Account acct, int freq);

    /** Request a sync */
    void requestSync(boolean manual);

    /** Create a background sync task */
    ProviderSync createBackgroundSync(boolean manual);

    /** Check connectivity for a sync */
    SyncConnectivityResult checkSyncConnectivity(Account acct) throws Exception;

    /** Sync a provider */
    void sync(Account acct,
              DbProvider provider,
              SyncConnectivityResult connResult,
              SyncLogRecord logrec)
            throws Exception;

    /**
     * Set the result of the last sync operation
     */
    void setLastSyncResult(boolean success, long syncEndTime);

    /**
     * Get the results of the syncs for the provider
     */
    @NonNull SyncResults getSyncResults();

    /** Insert a local file */
    long insertLocalFile(long providerId, String title, SQLiteDatabase db)
            throws SQLException;

    /** Update a local file */
    void updateLocalFile(DbFile file,
                         String localFileName,
                         File localFile,
                         SQLiteDatabase db);

    /** Delete a local file */
    void deleteLocalFile(DbFile file, SQLiteDatabase db);
}
