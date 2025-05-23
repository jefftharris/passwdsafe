/*
 * Copyright (©) 2018-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.lib.SyncResults;

/**
 * Provider operations for the MainActivity
 */
interface MainActivityProviderOps
{
    /**
     * Handle a request to choose the files for a provider, if supported
     */
    void handleProviderChooseFiles(ProviderType type, Uri providerUri);

    /**
     * Handle a request to delete a provider
     */
    void handleProviderDelete(Uri providerUri);

    /**
     * Handle a request to sync a provider
     */
    void handleProviderSync(ProviderType type, Uri providerUri);

    /**
     * Handle a request to set a provider's the sync frequency
     */
    void updateProviderSyncFreq(Uri providerUri,
                                ProviderSyncFreqPref freq);

    /**
     * Get the results of the syncs for a provider
     */
    @Nullable
    SyncResults getProviderSyncResults(ProviderType type);

    /**
     * Get a warning message to show for the provider
     */
    @Nullable
    CharSequence getProviderWarning(ProviderType type);

    /**
     * Is the activity running
     */
    boolean isActivityRunning();

    /**
     * Open a URL
     */
    void openUrl(Uri url, @StringRes int titleId);
}
