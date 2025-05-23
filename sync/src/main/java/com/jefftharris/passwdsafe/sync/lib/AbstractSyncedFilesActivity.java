/*
 * Copyright (©) 2017-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync.lib;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.jefftharris.passwdsafe.lib.ActContext;
import com.jefftharris.passwdsafe.lib.GenericProviderNaming;
import com.jefftharris.passwdsafe.lib.ManagedRef;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.view.PasswdCursorLoader;
import com.jefftharris.passwdsafe.sync.ProviderFactory;
import com.jefftharris.passwdsafe.sync.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *  Activity for managing files synced from providers
 */
public abstract class AbstractSyncedFilesActivity extends AppCompatActivity
        implements SyncedFilesFragment.Listener
{
    public static final String INTENT_PROVIDER_URI = "provider_uri";

    private static final int LOADER_TITLE = 0;
    private static final int LOADER_FILES = 1;

    private static final String TAG = "AbstractSyncedFilesAct";

    private Uri itsProviderUri;
    private Uri itsFilesUri;
    private final String itsRootId;
    private final ProviderType itsProviderType;
    private final HashMap<String, Long> itsSyncedFiles = new HashMap<>();
    private LoaderCallbacks<Cursor> itsProviderLoaderCb;
    private LoaderCallbacks<Cursor> itsFilesLoaderCb;
    private final List<AbstractListFilesTask> itsListTasks = new ArrayList<>();
    private final List<FileSyncedUpdateTask> itsUpdateTasks = new ArrayList<>();


    /** Constructor */
    protected AbstractSyncedFilesActivity(ProviderType providerType,
                                          String rootId)
    {
        itsProviderType = providerType;
        itsRootId = rootId;
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle args)
    {
        EdgeToEdge.enable(this);
        super.onCreate(args);
        setContentView(R.layout.activity_synced_files);

        GenericProviderNaming.setSyncedFilesActivityTitle(this,
                                                          itsProviderType);

        itsProviderUri = getIntent().getParcelableExtra(INTENT_PROVIDER_URI);
        if (itsProviderUri == null) {
            PasswdSafeUtil.showFatalMsg("Required args missing", this);
            return;
        }

        itsFilesUri = itsProviderUri.buildUpon().appendPath(
                PasswdSafeContract.RemoteFiles.TABLE).build();

        if (args == null) {
            changeDir(ProviderRemoteFile.PATH_SEPARATOR, itsRootId);
        }

        itsProviderLoaderCb = new ProviderLoaderCb();
        itsFilesLoaderCb = new FilesLoaderCb();
        LoaderManager lm = LoaderManager.getInstance(this);
        lm.initLoader(LOADER_TITLE, null, itsProviderLoaderCb);
        lm.initLoader(LOADER_FILES, null, itsFilesLoaderCb);
    }


    /* (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onDestroy()
     */
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        for (AbstractListFilesTask task: itsListTasks) {
            task.cancel(true);
        }
        for (FileSyncedUpdateTask task: itsUpdateTasks) {
            task.cancel(true);
        }
    }


    /* (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.activity_synced_files, menu);
        return super.onCreateOptionsMenu(menu);
    }


    /* (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int menuId = item.getItemId();
        if (menuId == R.id.menu_reload) {
            reloadFiles();
            LoaderManager lm = LoaderManager.getInstance(this);
            lm.restartLoader(LOADER_TITLE, null, itsProviderLoaderCb);
            lm.restartLoader(LOADER_FILES, null, itsFilesLoaderCb);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.SyncedFilesFragment.Listener#listFiles(java.lang.String, com.jefftharris.passwdsafe.sync.lib.SyncedFilesFragment.Listener.ListFilesCb)
     */
    @Override
    public void listFiles(String path, final ListFilesCb cb)
    {
        PasswdSafeUtil.dbginfo(TAG, "listFiles client path: %s", path);
        for (AbstractListFilesTask task: itsListTasks) {
            task.cancel(true);
        }
        itsListTasks.clear();

        AbstractListFilesTask task = createListFilesTask(
                this, (files, cbtask) -> {
                    itsListTasks.remove(cbtask);
                    cb.handleFiles(files);
                });
        itsListTasks.add(task);
        task.execute(path);
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.SyncedFilesFragment.Listener#changeDir(java.lang.String)
     */
    @Override
    public void changeDir(String pathDisplay, String pathId)
    {
        PasswdSafeUtil.dbginfo(TAG, "changeDir: %s", pathDisplay);
        Fragment files = SyncedFilesFragment.newInstance(pathDisplay, pathId);
        FragmentManager fragmgr = getSupportFragmentManager();
        FragmentTransaction txn = fragmgr.beginTransaction();
        txn.replace(R.id.content, files);
        if (!TextUtils.equals(pathId, itsRootId)) {
            txn.addToBackStack(null);
        }
        txn.commit();
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.SyncedFilesFragment.Listener#changeParentDir()
     */
    public void changeParentDir()
    {
        PasswdSafeUtil.dbginfo(TAG, "changeParentDir");
        FragmentManager fragmgr = getSupportFragmentManager();
        fragmgr.popBackStack();
    }


    /* (non-Javadoc)
     * @see com.jefftharris.passwdsafe.sync.lib.SyncedFilesFragment.Listener#isSelected(java.lang.String)
     */
    @Override
    public boolean isSelected(String filePath)
    {
        return itsSyncedFiles.containsKey(filePath);
    }


    @Override
    public void updateFileSynced(final ProviderRemoteFile file,
                                 final boolean synced)
    {
        PasswdSafeUtil.dbginfo(TAG, "updateFileSynced sync %b, file: %s",
                               synced, file.toDebugString());

        FileSyncedUpdateTask task = new FileSyncedUpdateTask(
                itsProviderUri, file, synced,
                (error, remFileId, cbtask) -> {
                    itsUpdateTasks.remove(cbtask);
                    if (error == null) {
                        if (synced) {
                            itsSyncedFiles.put(file.getRemoteId(), remFileId);
                        } else {
                            itsSyncedFiles.remove(file.getRemoteId());
                        }
                    } else {
                        String msg = "Error updating sync for " +
                                     file.getRemoteId();
                        PasswdSafeUtil.showError(msg, TAG, error,
                                                 new ActContext(this));
                    }
                    getContentResolver().notifyChange(itsFilesUri, null);
                    Provider provider = ProviderFactory.getProvider(
                            itsProviderType,
                            AbstractSyncedFilesActivity.this);
                    provider.requestSync(false);
                });
        itsUpdateTasks.add(task);
        task.execute();
    }


    /** Create a list files task */
    protected abstract AbstractListFilesTask createListFilesTask(
            Context ctx,
            AbstractListFilesTask.Callback cb);


    /** Reload the files shown by the activity */
    private void reloadFiles()
    {
        FragmentManager fragmgr = getSupportFragmentManager();
        Fragment filesfrag = fragmgr.findFragmentById(R.id.content);
        if (filesfrag instanceof SyncedFilesFragment) {
            ((SyncedFilesFragment)filesfrag).reload();
        }
    }


    /** Update the state of the synced files shown by the activity */
    private void updateSyncedFiles()
    {
        FragmentManager fragmgr = getSupportFragmentManager();
        Fragment filesfrag = fragmgr.findFragmentById(R.id.content);
        if (filesfrag instanceof SyncedFilesFragment) {
            ((SyncedFilesFragment)filesfrag).updateSyncedFiles();
        }
    }


   /** Loader callbacks for the provider */
    private class ProviderLoaderCb implements LoaderCallbacks<Cursor>
    {
        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args)
        {
            return new PasswdCursorLoader(
                    AbstractSyncedFilesActivity.this, itsProviderUri,
                    PasswdSafeContract.Providers.PROJECTION,
                    null, null, null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                   Cursor cursor)
        {
            if (!PasswdCursorLoader.checkResult(
                    loader, AbstractSyncedFilesActivity.this)) {
                return;
            }
            String name;
            if ((cursor != null) && cursor.moveToFirst()) {
                name = PasswdSafeContract.Providers.getDisplayName(cursor);

                String typeStr = cursor.getString(
                        PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
                ProviderType type = ProviderType.valueOf(typeStr);
                type.setIcon((ImageView)findViewById(R.id.icon));
            } else {
                name = getString(R.string.no_account);
            }
            PasswdSafeUtil.dbginfo(TAG, "provider: %s", name);
            TextView title = findViewById(R.id.title);
            assert title != null;
            title.setText(name);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader)
        {
            onLoadFinished(loader, null);
        }
    }


    /** Loader callbacks for the synced remote files for a provider */
    private class FilesLoaderCb implements LoaderCallbacks<Cursor>
    {
        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args)
        {
            return new PasswdCursorLoader(
                    AbstractSyncedFilesActivity.this, itsFilesUri,
                    PasswdSafeContract.RemoteFiles.PROJECTION,
                    PasswdSafeContract.RemoteFiles.NOT_DELETED_SELECTION,
                    null, null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                   Cursor cursor)
        {
            if (!PasswdCursorLoader.checkResult(
                    loader, AbstractSyncedFilesActivity.this)) {
                return;
            }
            itsSyncedFiles.clear();
            if (cursor != null) {
                for (boolean more = cursor.moveToFirst(); more;
                     more = cursor.moveToNext()) {
                    long id = cursor.getLong(
                            PasswdSafeContract.RemoteFiles.PROJECTION_IDX_ID);
                    String remoteId = cursor.getString(
                            PasswdSafeContract.RemoteFiles.PROJECTION_IDX_REMOTE_ID);

                    PasswdSafeUtil.dbginfo(TAG, "sync file: %s", remoteId);
                    itsSyncedFiles.put(remoteId, id);
                }
            }
            updateSyncedFiles();
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader)
        {
            onLoadFinished(loader, null);
        }
    }


    /** Background task for listing files from a provider */
    public static abstract class AbstractListFilesTask
            extends AsyncTask<String, Void,
                              Pair<List<ProviderRemoteFile>, Exception>>
    {
        /** Callback for when the task is finished; null files if cancelled
         *  or an error occurred.
         */
        public interface Callback
        {
            void handleFiles(List<ProviderRemoteFile> files,
                             AbstractListFilesTask task);
        }

        protected final ManagedRef<Context> itsContext;
        private final Callback itsCb;


        /** Constructor */
        public AbstractListFilesTask(Context ctx, Callback cb)
        {
            itsCb = cb;
            itsContext = new ManagedRef<>(ctx);
        }


        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(
                Pair<List<ProviderRemoteFile>, Exception> result)
        {
            Context ctx = itsContext.get();
            if ((result.second != null) && (ctx != null)) {
                Log.e(TAG, "Error listing files", result.second);
                Toast.makeText(
                        ctx,
                        "Error listing files: " + result.second.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            itsCb.handleFiles(result.first, this);
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onCancelled(java.lang.Object)
         */
        @Override
        protected void onCancelled(
                Pair<List<ProviderRemoteFile>, Exception> result)
        {
            itsCb.handleFiles(null, this);
        }
    }


    /** Background task for updating the synced state of a file from a
     * provider */
    private static class FileSyncedUpdateTask
            extends AsyncTask<Void, Void, Pair<Exception, Long>>
            implements SyncDb.DbUser<Long>
    {
        /** Callback for when the update is complete */
        protected interface Callback
        {
            void updateComplete(Exception error,
                                long remFileId,
                                FileSyncedUpdateTask task);
        }


        private final Uri itsProviderUri;
        private final ProviderRemoteFile itsFile;
        private final boolean itsIsSynced;
        private final Callback itsCb;


        /** Constructor */
        protected FileSyncedUpdateTask(Uri providerUri, ProviderRemoteFile file,
                                       boolean synced, Callback cb)
        {
            itsProviderUri = providerUri;
            itsFile = file;
            itsIsSynced = synced;
            itsCb = cb;
        }

        @Override
        protected Pair<Exception, Long> doInBackground(Void... params)
        {
            try {
                return Pair.create(null, SyncDb.useDb(this));
            } catch (Exception e) {
                return Pair.create(e, -1L);
            }
        }

        @Override
        public Long useDb(SQLiteDatabase db)
        {
            long providerId =
                    PasswdSafeContract.Providers.getId(itsProviderUri);

            DbFile remfile = SyncDb.getFileByRemoteId(
                    providerId, itsFile.getRemoteId(), db);
            if (itsIsSynced) {
                if (remfile != null) {
                    SyncDb.updateRemoteFileChange(
                            remfile.itsId, DbFile.FileChange.ADDED, db);
                    return remfile.itsId;
                } else {
                    return SyncDb.addRemoteFile(
                            providerId, itsFile.getRemoteId(),
                            itsFile.getTitle(), itsFile.getFolder(),
                            itsFile.getModTime(), itsFile.getHash(), db);
                }
            } else {
                if (remfile != null) {
                    SyncDb.updateRemoteFileDeleted(remfile.itsId, db);
                }
                return -1L;
            }
        }

        @Override
        protected void onPostExecute(Pair<Exception, Long> result)
        {
            itsCb.updateComplete(result.first, result.second, this);
        }
    }
}
