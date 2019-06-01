/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.core.content.ContentResolverCompat;
import androidx.core.os.CancellationSignal;

import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;

/**
 * Async task to sync a provider URI
 */
public class ProviderSyncTask
{
    private AsyncSyncTask itsSyncTask;

    private static final String TAG = "ProviderSyncTask";

    /**
     * Constructor
     */
    public ProviderSyncTask()
    {
    }

    /**
     * Start the task
     */
    public void start(Uri provider, Context ctx)
    {
        cancel();
        itsSyncTask = new AsyncSyncTask(provider, ctx.getContentResolver(),
                                        this);
        itsSyncTask.execute();
    }

    /**
     * Cancel the task
     */
    public void cancel()
    {
        if (itsSyncTask != null) {
            itsSyncTask.cancelTask();
            itsSyncTask = null;
        }
    }

    /**
     * Background task
     */
    private static class AsyncSyncTask extends AsyncTask<Void, Void, Void>
    {
        private final String[] itsProviderArgs;
        private final ContentResolver itsContentResolver;
        private final CancellationSignal itsCancelSignal;
        private final ManagedRef<ProviderSyncTask> itsSyncTask;

        /**
         * Constructor
         */
        public AsyncSyncTask(Uri provider,
                             ContentResolver resolver,
                             ProviderSyncTask task)
        {
            if (provider != null) {
                itsProviderArgs = new String[] {
                        PasswdSafeContract.Methods.METHOD_SYNC,
                        provider.toString() };
            } else {
                itsProviderArgs = new String[] {
                        PasswdSafeContract.Methods.METHOD_SYNC };
            }
            itsContentResolver = resolver;
            itsCancelSignal = new CancellationSignal();
            itsSyncTask = new ManagedRef<>(task);
        }

        /**
         * Cancel the task
         */
        public void cancelTask()
        {
            itsCancelSignal.cancel();
            cancel(true);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            PasswdSafeUtil.dbginfo(TAG, "doInBackground");
            try {
                ContentResolverCompat.query(
                        itsContentResolver,
                        PasswdSafeContract.Methods.CONTENT_URI,
                        null, null, itsProviderArgs, null, itsCancelSignal);
            } catch (Exception e) {
                Log.e(TAG, "Error syncing", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            ProviderSyncTask task = itsSyncTask.get();
            if (task != null) {
                task.itsSyncTask = null;
            }
        }

        @Override
        protected void onCancelled(Void result)
        {
            PasswdSafeUtil.dbginfo(TAG, "onCancelled");
            ProviderSyncTask task = itsSyncTask.get();
            if (task != null) {
                task.itsSyncTask = null;
            }
        }
    }
}
