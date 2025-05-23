/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.box;

import android.accounts.Account;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.box.androidsdk.content.BoxConfig;
import com.box.androidsdk.content.BoxException;
import com.box.androidsdk.content.auth.BoxAuthentication;
import com.box.androidsdk.content.models.BoxJsonObject;
import com.box.androidsdk.content.models.BoxSession;
import com.jefftharris.passwdsafe.lib.ObjectHolder;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.sync.BuildConfig;
import com.jefftharris.passwdsafe.sync.SyncApp;
import com.jefftharris.passwdsafe.sync.lib.AbstractSyncTimerProvider;
import com.jefftharris.passwdsafe.sync.lib.DbProvider;
import com.jefftharris.passwdsafe.sync.lib.NewAccountTask;
import com.jefftharris.passwdsafe.sync.lib.NotifUtils;
import com.jefftharris.passwdsafe.sync.lib.SyncConnectivityResult;
import com.jefftharris.passwdsafe.sync.lib.SyncDb;
import com.jefftharris.passwdsafe.sync.lib.SyncLogRecord;

/**
 * Implements a provider for the Box.com service
 */
public class BoxProvider extends AbstractSyncTimerProvider
        implements BoxAuthentication.AuthListener
{
    private static final String BOX_CLIENT_ID = BuildConfig.BOX_CLIENT_ID;
    private static final String BOX_CLIENT_SECRET =
            BuildConfig.BOX_CLIENT_SECRET;

    private static final String PREF_AUTH_ACCESS_TOKEN = "boxAccessToken";
    private static final String PREF_AUTH_EXPIRES_IN = "boxExpiresIn";
    private static final String PREF_AUTH_REFRESH_TOKEN = "boxRefreshToken";
    private static final String PREF_AUTH_TOKEN_TYPE = "boxTokenType";
    private static final String PREF_AUTH_USER_ID = "boxUserId";

    private static final String TAG = "BoxProvider";

    private BoxSession itsClient;
    private PendingIntent itsAcctLinkIntent = null;

    /** Constructor */
    public BoxProvider(Context ctx)
    {
        super(ProviderType.BOX, ctx, TAG);
    }

    @Override
    public void init(@Nullable DbProvider dbProvider)
    {
        super.init(dbProvider);
        BoxConfig.CLIENT_ID = BOX_CLIENT_ID;
        BoxConfig.CLIENT_SECRET = BOX_CLIENT_SECRET;
        BoxConfig.IS_LOG_ENABLED = false;
        itsClient = new BoxSession(getContext());
        itsClient.setSessionAuthListener(this);
        updateBoxAcct();

        // Check for migration
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        if (prefs.contains(PREF_AUTH_REFRESH_TOKEN)) {
            NotifUtils.showNotif(NotifUtils.Type.BOX_MIGRATGED, getContext());
        }
        if (prefs.contains(PREF_AUTH_USER_ID)) {
            prefs.edit().remove(PREF_AUTH_USER_ID).apply();
        }
    }

    @Override
    public void startAccountLink(FragmentActivity activity, int requestCode)
    {
        SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        if (prefs.contains(PREF_AUTH_USER_ID) ||
            prefs.contains(PREF_AUTH_REFRESH_TOKEN)) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.remove(PREF_AUTH_USER_ID);
            edit.remove(PREF_AUTH_ACCESS_TOKEN);
            edit.remove(PREF_AUTH_EXPIRES_IN);
            edit.remove(PREF_AUTH_REFRESH_TOKEN);
            edit.remove(PREF_AUTH_TOKEN_TYPE);
            edit.apply();
        }

        if (isAccountAuthorized()) {
            unlinkAccount();
        }

        Intent intent = new Intent();
        itsAcctLinkIntent = activity.createPendingResult(
                requestCode, intent, PendingIntent.FLAG_ONE_SHOT);
        itsClient.authenticate(getContext());
    }

    @Nullable
    @Override
    public NewAccountTask<? extends AbstractSyncTimerProvider>
    finishAccountLink(FragmentActivity activity,
                      int activityRequestCode,
                      int activityResult,
                      Intent activityData,
                      Uri acctProviderUri)
    {
        if (!isAccountAuthorized()) {
            Log.e(TAG, "finishAccountLink auth failed");
            return null;
        }
        updateBoxAcct();
        return new NewBoxTask(acctProviderUri, this);
    }

    @Override
    public void unlinkAccount()
    {
        if (itsAcctLinkIntent != null) {
            itsAcctLinkIntent.cancel();
            itsAcctLinkIntent = null;
        }
        itsClient.logout();
        updateBoxAcct();
    }

    @Override
    public synchronized boolean isAccountAuthorized()
    {
        BoxAuthentication.BoxAuthenticationInfo authInfo =
                itsClient.getAuthInfo();
        return ((authInfo != null) &&
                !TextUtils.isEmpty(itsClient.getUserId()) &&
                !TextUtils.isEmpty(authInfo.refreshToken()));
    }

    @Override
    public Account getAccount(String acctName)
    {
        return new Account(acctName, SyncDb.BOX_ACCOUNT_TYPE);
    }

    @Override
    public void cleanupOnDelete()
    {
        if (!isPendingAdd()) {
            unlinkAccount();
        }
    }

    @Override
    protected String getAccountUserId()
    {
        return itsClient.getUserId();
    }

    @Override
    public void requestSync(boolean manual)
    {
        boolean authorized = isAccountAuthorized();
        PasswdSafeUtil.dbginfo(TAG, "requestSync authorized: %b", authorized);
        if (authorized) {
            doRequestSync(manual);
        }
    }

    @Override
    public SyncConnectivityResult checkSyncConnectivity(Account acct)
            throws Exception
    {
        final ObjectHolder<SyncConnectivityResult> connResult =
                new ObjectHolder<>();
        useBoxService(() -> {
            String displayName = BoxSyncer.getDisplayName(itsClient);
            connResult.set(new SyncConnectivityResult(displayName));
        });
        return connResult.get();
    }

    @Override
    public void sync(Account acct,
                     final DbProvider provider,
                     final SyncConnectivityResult connResult,
                     final SyncLogRecord logrec) throws Exception
    {
        useBoxService(() -> new BoxSyncer(itsClient, provider, connResult,
                                          logrec, getContext()).sync());
    }

    @Override
    public void onAuthCreated(BoxAuthentication.BoxAuthenticationInfo info)
    {
        PasswdSafeUtil.dbginfo(TAG, "onAuthCreated: %s", boxToString(info));
        if (itsAcctLinkIntent != null) {
            try {
                itsAcctLinkIntent.send(Activity.RESULT_OK);
                itsAcctLinkIntent = null;
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "login intent send failed", e);
            }
        }
    }

    @Override
    public void onRefreshed(BoxAuthentication.BoxAuthenticationInfo info)
    {
        PasswdSafeUtil.dbginfo(TAG, "onRefreshed: %s", boxToString(info));
    }

    @Override
    public void onAuthFailure(BoxAuthentication.BoxAuthenticationInfo info,
                              Exception ex)
    {
        PasswdSafeUtil.dbginfo(TAG, "onAuthFailure: %s: %s",
                               boxToString(info), ex);
        if (itsAcctLinkIntent != null) {
            itsAcctLinkIntent.cancel();
            itsAcctLinkIntent = null;
        }
    }

    @Override
    public void onLoggedOut(BoxAuthentication.BoxAuthenticationInfo info,
                            Exception ex)
    {
        PasswdSafeUtil.dbginfo(TAG, "onLoggedOut: %s: %s",
                               boxToString(info), ex);
    }

    /**
     * Interface for users of the Box service
     */
    private interface BoxUser
    {
        void useBox() throws Exception;
    }

    /**
     * Use the Box service
     */
    private void useBoxService(BoxUser user) throws Exception
    {
        boolean authorized = false;
        try {
            authorized = isAccountAuthorized();
            PasswdSafeUtil.dbginfo(TAG, "account authorized: %b", authorized);
            if (authorized) {
                user.useBox();
            }
        } catch (Exception e) {
            Throwable t = e.getCause();
            if (t instanceof BoxException.RefreshFailure) {
                if (((BoxException.RefreshFailure)t).isErrorFatal()) {
                    Log.e(TAG, "sync: fatal refresh", t);
                    unlinkAccount();
                }
            }
            throw e;
        } finally {
            if (authorized && !isAccountAuthorized()) {
                SyncApp.get(getContext()).updateProviderState();
            }
        }
    }

    /** Update the Box account client based on availability of authentication
     *  information. */
    private synchronized void updateBoxAcct()
    {
        boolean isAuthorized = isAccountAuthorized();
        PasswdSafeUtil.dbginfo(TAG, "updateBoxAcct isAuth %b", isAuthorized);
        if (isAuthorized) {
            String userId = itsClient.getUserId();
            if (userId != null) {
                try {
                    updateProviderSyncFreq(userId);
                } catch (Exception e) {
                    Log.e(TAG, "updateBoxAcct failure", e);
                }
            } else {
                requestSync(false);
            }
        } else {
            updateSyncFreq(null, 0);
        }
    }

    /** Convert a Box object to a string for debugging */
    @Nullable
    public static String boxToString(BoxJsonObject obj)
    {
        return (obj != null) ? obj.toJson() : null;
    }

    /**
     * New Box account task
     */
    private static class NewBoxTask extends NewAccountTask<BoxProvider>
    {
        /**
         * Constructor
         */
        protected NewBoxTask(Uri currAcctUri, BoxProvider provider)
        {
            super(currAcctUri, null, provider, false, provider.getContext(),
                  TAG);
        }

        @Override
        protected boolean doProviderUpdate(@NonNull BoxProvider provider)
        {
            itsNewAcct = provider.itsClient.getUserId();
            return true;
        }
    }
}
