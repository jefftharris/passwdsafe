/*
 * Copyright (©) 2016-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.fragment.app.FragmentActivity;

import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.view.ProgressFragment;

/**
 * Async task to update an account
 */
public abstract class AccountUpdateTask extends AsyncTask<Void, Void, Void>
{
    public interface Listener
    {
        /** Notification the task is starting */
        void notifyUpdateStarted(AccountUpdateTask task);

        /** Notification the task is finished */
        void notifyUpdateFinished(AccountUpdateTask task);
    }

    private static final String TAG = "AccountUpdateTask";

    protected final Uri itsAccountUri;

    private final String itsProgressMsg;
    private ManagedRef<FragmentActivity> itsActivity;
    private Listener itsListener;
    private ContentResolver itsContentResolver;
    private ProgressFragment itsProgressFrag;

    /** Start the update task */
    public void startTask(FragmentActivity activity, Listener listener)
    {
        itsActivity = new ManagedRef<>(activity);
        itsListener = listener;
        itsContentResolver = activity.getContentResolver();
        execute();
    }

    /**
     * Cancel the task
     */
    public void cancelTask()
    {
        cancel(true);
        onPostExecute(null);
    }

    /** Constructor */
    protected AccountUpdateTask(Uri accountUri, String progressMsg)
    {
        itsAccountUri = accountUri;
        itsProgressMsg = progressMsg;
    }

    @Override
    protected void onPreExecute()
    {
        super.onPreExecute();
        itsListener.notifyUpdateStarted(this);
        FragmentActivity frag = itsActivity.get();
        if (frag != null) {
            itsProgressFrag = ProgressFragment.newInstance(itsProgressMsg);
            itsProgressFrag.show(frag.getSupportFragmentManager(), null);
        }
    }

    @Override
    protected final Void doInBackground(Void... params)
    {
        try {
            doAccountUpdate(itsContentResolver);
        } catch (Exception e) {
            Log.e(TAG, "Account update error", e);
        }

        return null;
    }

    /** Do the account update in the background */
    protected abstract void doAccountUpdate(ContentResolver cr);

    @Override
    protected void onPostExecute(Void arg)
    {
        if (itsProgressFrag != null) {
            itsProgressFrag.dismissAllowingStateLoss();
        }
        itsListener.notifyUpdateFinished(this);
    }

}
