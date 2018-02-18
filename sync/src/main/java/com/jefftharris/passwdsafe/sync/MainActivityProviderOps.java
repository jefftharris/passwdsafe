/*
 * Copyright (©) 2018 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.net.Uri;

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
     * Handle a request to show a settings dialog for a provider, if supported
     */
    void handleProviderEditDialog(ProviderType type,
                                  Uri providerUri,
                                  ProviderSyncFreqPref freq);

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
    SyncResults getProviderSyncResults(ProviderType type);

    /**
     * Get a warning message to show for the provider
     */
    CharSequence getProviderWarning(ProviderType type);

    /**
     * Is the activity running
     */
    boolean isActivityRunning();
}
