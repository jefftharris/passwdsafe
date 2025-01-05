/*
 * Copyright (©) 2019-2024 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.onedrive;

import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.exception.MsalException;

/**
 * OneDrive token acquisition callback allowing the user to wait for the result
 * to become available.
 */
public class AcquireTokenCallback implements AuthenticationCallback
{
    private final boolean itsIsReauth;
    private IAuthenticationResult itsAuthResult = null;
    private boolean itsHasResult = false;

    private static final String TAG = "AcquireTokenCallback";

    public AcquireTokenCallback(boolean reauthorization)
    {
        itsIsReauth = reauthorization;
    }

    @Override
    public void onSuccess(IAuthenticationResult authResult)
    {
        PasswdSafeUtil.dbginfo(TAG, "auth ok");
        onAuthDone(authResult);
    }

    @Override
    public void onError(MsalException exception)
    {
        Log.e(TAG, "auth error", exception);
        onAuthDone(null);
    }

    @Override
    public void onCancel()
    {
        Log.e(TAG, "auth cancel");
        onAuthDone(null);
    }

    /**
     * Get whether the acquisition was for a reauthorization
     */
    public boolean isReauth()
    {
        return itsIsReauth;
    }

    /**
     * Get the authentication result
     */
    @WorkerThread
    public synchronized IAuthenticationResult getResult()
            throws InterruptedException
    {
        while (!itsHasResult) {
            wait(15000);
        }
        return itsAuthResult;
    }

    /**
     * Handle when authentication is finished
     */
    @MainThread
    private synchronized void onAuthDone(IAuthenticationResult result)
    {
        itsAuthResult = result;
        itsHasResult = true;
        notifyAll();
    }
}
