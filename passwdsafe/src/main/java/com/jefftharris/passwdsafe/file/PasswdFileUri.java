/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.file;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.EnvironmentCompat;
import androidx.documentfile.provider.DocumentFile;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.db.BackupFile;
import com.jefftharris.passwdsafe.db.BackupFilesDao;
import com.jefftharris.passwdsafe.db.PasswdSafeDb;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.ProviderType;
import com.jefftharris.passwdsafe.lib.Utils;
import com.jefftharris.passwdsafe.util.Pair;

import org.pwsafe.lib.exception.EndOfFileException;
import org.pwsafe.lib.exception.InvalidPassphraseException;
import org.pwsafe.lib.exception.UnsupportedFileVersionException;
import org.pwsafe.lib.file.Owner;
import org.pwsafe.lib.file.PwsFile;
import org.pwsafe.lib.file.PwsFileFactory;
import org.pwsafe.lib.file.PwsFileStorage;
import org.pwsafe.lib.file.PwsPassword;
import org.pwsafe.lib.file.PwsStorage;
import org.pwsafe.lib.file.PwsStreamStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The PasswdFileUri class encapsulates a URI to a password file
 */
public class PasswdFileUri
{
    private static final String TAG = "PasswdFileUri";

    private final Uri itsUri;
    private final Type itsType;
    private final File itsFile;
    private final BackupFile itsBackupFile;
    private String itsTitle = null;
    private Pair<Boolean, Integer> itsWritableInfo;
    private boolean itsIsDeletable;
    private ProviderType itsSyncType = null;

    /// Regex for a valid new file name, without extension
    private static final Pattern FILENAME_REGEX = Pattern.compile(
            "^[\\p{Alnum}_-]+$");

    /** The type of URI */
    public enum Type
    {
        FILE,
        SYNC_PROVIDER,
        EMAIL,
        GENERIC_PROVIDER,
        BACKUP
    }

    /**
     * Creator for a PasswdFileUri that can work with an AsyncTask
     */
    public static class Creator
    {
        private final Uri itsFileUri;
        private final Context itsContext;
        private PasswdFileUri itsResolvedUri;
        private Throwable itsResolveEx;

        /**
         * Constructor
         */
        public Creator(Uri fileUri, Context ctx)
        {
            itsFileUri = fileUri;
            itsContext = ctx;
        }

        /**
         * Handle a pre-execute call in the main thread
         */
        public void onPreExecute()
        {
            switch (PasswdFileUri.getUriType(itsFileUri)) {
            case GENERIC_PROVIDER: {
                create();
                break;
            }
            case FILE:
            case SYNC_PROVIDER:
            case EMAIL:
            case BACKUP: {
                break;
            }
            }
        }

        /**
         * Finish creating the PasswdFileUri, typically in a background thread
         */
        public PasswdFileUri finishCreate() throws Throwable
        {
            if ((itsResolvedUri == null) && (itsResolveEx == null)) {
                create();
            }
            if (itsResolveEx != null) {
                throw itsResolveEx;
            }
            return itsResolvedUri;
        }

        /**
         * Get an exception that occurred during the creation
         */
        public Throwable getResolveEx()
        {
            return itsResolveEx;
        }

        /**
         * Create the PasswdFileUri
         */
        private void create()
        {
            try {
                itsResolvedUri = new PasswdFileUri(itsFileUri, itsContext);
            } catch (Throwable e) {
                itsResolveEx = e;
            }
        }
    }

    /** Constructor */
    private PasswdFileUri(Uri uri, Context ctx)
    {
        itsUri = uri;
        itsType = getUriType(uri);
        switch (itsType) {
        case FILE: {
            itsFile = new File(Objects.requireNonNull(uri.getPath()));
            itsBackupFile = null;
            resolveFileUri(ctx);
            return;
        }
        case GENERIC_PROVIDER: {
            itsFile = null;
            itsBackupFile = null;
            resolveGenericProviderUri(ctx);
            return;
        }
        case SYNC_PROVIDER: {
            itsFile = null;
            itsBackupFile = null;
            resolveSyncProviderUri(ctx);
            return;
        }
        case BACKUP: {
            itsFile = null;
            itsBackupFile = resolveBackupUri(ctx);
            return;
        }
        case EMAIL: {
            break;
        }
        }
        itsFile = null;
        itsBackupFile = null;
        itsWritableInfo = new Pair<>(false, null);
        itsIsDeletable = false;
    }


    /** Constructor from a File */
    private PasswdFileUri(File file, Context ctx)
    {
        itsUri = Uri.fromFile(file);
        itsType = Type.FILE;
        itsFile = file;
        itsBackupFile = null;
        resolveFileUri(ctx);
    }


    /** Load the password file */
    @Nullable
    public PwsFile load(Owner<PwsPassword>.Param passwd, Context context)
            throws EndOfFileException, InvalidPassphraseException, IOException,
                   UnsupportedFileVersionException
    {
        switch (itsType) {
        case FILE: {
            return PwsFileFactory.loadFile(itsFile.getAbsolutePath(), passwd);
        }
        case SYNC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            InputStream is = cr.openInputStream(itsUri);
            String id = getIdentifier(context, false);
            PwsStorage storage = new PasswdFileSyncStorage(itsUri, id, is);
            return PwsFileFactory.loadFromStorage(storage, passwd);
        }
        case EMAIL:
        case GENERIC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            InputStream is = cr.openInputStream(itsUri);
            String id = getIdentifier(context, false);
            PwsStorage storage;
            if (itsWritableInfo.first) {
                storage = new PasswdFileGenProviderStorage(itsUri, id, is);
            } else {
                storage = new PwsStreamStorage(id, is);
            }
            return PwsFileFactory.loadFromStorage(storage, passwd);
        }
        case BACKUP: {
            if (itsBackupFile == null) {
                throw new FileNotFoundException(itsUri.toString());
            }
            InputStream is =
                    BackupFilesDao.openBackupFile(itsBackupFile, context);
            return PwsFileFactory.loadFromStorage(
                    new PwsStreamStorage(getIdentifier(context, false), is),
                    passwd);
        }
        }
        return null;
    }


    /** Create a new file */
    public PwsFile createNew(Owner<PwsPassword>.Param passwd, Context context)
            throws IOException
    {
        switch (itsType) {
        case FILE:
        case SYNC_PROVIDER:
        case GENERIC_PROVIDER: {
            PwsFile file = PwsFileFactory.newFile();
            file.setPassphrase(passwd);
            file.setStorage(createStorageForSave(context));
            return file;
        }
        case EMAIL:
        case BACKUP: {
            break;
        }
        }
        throw new IOException("no file");
    }


    /**
     * Create file storage to save to this URI
     */
    public @NonNull PwsStorage createStorageForSave(Context context)
            throws IOException
    {
        switch (itsType) {
        case FILE: {
            return new PwsFileStorage(itsFile.getAbsolutePath(), null);
        }
        case SYNC_PROVIDER: {
            return new PasswdFileSyncStorage(itsUri,
                                             getIdentifier(context, false),
                                             null);
        }
        case EMAIL:
        case GENERIC_PROVIDER: {
            String id = getIdentifier(context, false);
            if (itsWritableInfo.first) {
                return new PasswdFileGenProviderStorage(itsUri, id, null);
            } else {
                return new PwsStreamStorage(id, null);
            }
        }
        case BACKUP: {
            return new PwsStreamStorage(getIdentifier(context, false), null);
        }
        }

        throw new IOException("Unknown URI type");
    }


    /**
     * Validate the file name base (no extension)
     * @return error message if invalid; null if valid
     */
    @Nullable
    public static String validateFileNameBase(@NonNull String fileNameBase,
                                              @NonNull Context ctx)
    {
        if (fileNameBase.isEmpty()) {
            return ctx.getString(R.string.empty_file_name);
        }
        if (!FILENAME_REGEX.matcher(fileNameBase).matches()) {
            return ctx.getString(R.string.invalid_file_name);
        }

        return null;
    }


    /**
     * Validate a new file that is a child of the current URI. Return null if
     * successful; error string otherwise
     */
    @Nullable
    public String validateNewChild(String fileName, Context ctx)
    {
        switch (itsType) {
        case FILE: {
            if (fileName.contains("..") || fileName.contains("/") ||
                fileName.contains("\\")) {
                return ctx.getString(R.string.invalid_file_name);
            }
            var error = validateFileNameBase(fileName, ctx);
            if (error != null) {
                return error;
            }

            File f = new File(itsFile, fileName + ".psafe3");
            if (f.exists()) {
                return ctx.getString(R.string.file_exists);
            }
            return null;
        }
        case SYNC_PROVIDER: {
            return null;
        }
        case EMAIL:
        case GENERIC_PROVIDER:
        case BACKUP: {
            break;
        }
        }
        return ctx.getString(R.string.new_file_not_supp_uri, toString());
    }


    /** Create a new children file URI */
    public PasswdFileUri createNewChild(String fileName, Context ctx)
            throws IOException
    {
        switch (itsType) {
        case FILE: {
            File file = new File(itsFile, fileName);
            return new PasswdFileUri(file, ctx);
        }
        case SYNC_PROVIDER: {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(PasswdSafeContract.Files.COL_TITLE, fileName);
            Uri childUri = cr.insert(itsUri, values);
            return new PasswdFileUri(childUri, ctx);
        }
        case EMAIL:
        case GENERIC_PROVIDER:
        case BACKUP: {
            break;
        }
        }
        throw new IOException("Can't create child \"" + fileName +
                              "\" for URI " + this);
    }


    /** Delete a file */
    public void delete(Context context)
            throws IOException
    {
        switch (itsType) {
        case FILE: {
            if (!itsFile.delete()) {
                throw new IOException("Could not delete file: " + this);
            }
            break;
        }
        case SYNC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            int rc = cr.delete(itsUri, null, null);
            if (rc != 1) {
                throw new IOException("Could not delete file: " + this);
            }
            break;
        }
        case GENERIC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            if (!ApiCompat.documentsContractDeleteDocument(cr, itsUri)) {
                throw new IOException("Could not delete file: " + this);
            }
            break;
        }
        case EMAIL:
        case BACKUP: {
            throw new IOException("Delete not supported for " + this);
        }
        }
    }


    /** Does the file exist at the URI */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean exists()
    {
        return switch (itsType) {
            case FILE -> (itsFile != null) && itsFile.exists();
            case SYNC_PROVIDER -> (itsSyncType != null);
            case EMAIL, GENERIC_PROVIDER -> true;
            case BACKUP -> (itsBackupFile != null);
        };
    }


    /** Is the file writable */
    public Pair<Boolean, Integer> isWritable()
    {
        return itsWritableInfo;
    }

    /** Is the file deletable */
    public boolean isDeletable()
    {
        return itsIsDeletable;
    }


    /** Get the URI of the file */
    public Uri getUri()
    {
        return itsUri;
    }


    /** Get the type of the URI */
    public Type getType()
    {
        return itsType;
    }


    /** Get the sync type of the URI */
    public ProviderType getSyncType()
    {
        return itsSyncType;
    }

    /** Get the backup file */
    public BackupFile getBackupFile()
    {
        return itsBackupFile;
    }

    /**
     * Get the name of the URI's file if known
     */
    public @Nullable String getFileName()
    {
        return switch (itsType) {
            case FILE -> itsFile.getName();
            case SYNC_PROVIDER,
                 GENERIC_PROVIDER -> itsTitle;
            case BACKUP -> {
                if (itsBackupFile != null) {
                    yield "backup - " + itsBackupFile.title;
                }
                yield "backup.psafe3";
            }
            case EMAIL -> null;
        };
    }

    /** Get an identifier for the URI */
    public String getIdentifier(Context context, boolean shortId)
    {
        switch (itsType) {
        case FILE: {
            if (shortId) {
                return itsUri.getLastPathSegment();
            } else {
                return itsUri.getPath();
            }
        }
        case SYNC_PROVIDER: {
            if (itsSyncType != null) {
                return String.format("%s - %s",
                                     itsSyncType.getName(context), itsTitle);
            }
            return context.getString(R.string.unknown_sync_file);
        }
        case EMAIL: {
            return context.getString(R.string.email_attachment);
        }
        case GENERIC_PROVIDER: {
            if (itsTitle != null) {
                return itsTitle;
            }
            return context.getString(R.string.content_file);
        }
        case BACKUP: {
            if (itsTitle != null) {
                return itsTitle;
            }
            return context.getString(R.string.backup_file);
        }
        }
        return "";
    }


    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PasswdFileUri uri)) {
            return false;
        }
        return itsUri.equals(uri.itsUri);
    }


    /** Convert the URI to a string */
    @Override
    @NonNull
    public String toString()
    {
        return itsUri.toString();
    }


    /** Get the URI type */
    public static Type getUriType(@NonNull Uri uri)
    {
        String scheme = uri.getScheme();
        if (scheme != null) {
            if (scheme.equals(ContentResolver.SCHEME_FILE)) {
                return Type.FILE;
            } else if (scheme.equals(BackupFile.URL_SCHEME)) {
                return Type.BACKUP;
            }
        }
        String auth = uri.getAuthority();
        if (PasswdSafeContract.AUTHORITY.equals(auth)) {
            return Type.SYNC_PROVIDER;
        } else if ((auth != null) && auth.contains("mail")) {
            return Type.EMAIL;
        }
        return Type.GENERIC_PROVIDER;
    }


    /** Resolve fields for a file URI */
    private void resolveFileUri(Context ctx)
    {
        itsWritableInfo = doResolveFileUri(ctx);
        itsIsDeletable = itsWritableInfo.first;
    }


    /**
     * Implementation of resolving fields for a file URI
     */
    @NonNull
    private Pair<Boolean, Integer> doResolveFileUri(Context ctx)
    {
        if (itsFile == null) {
            return new Pair<>(false, null);
        }

        itsTitle = itsFile.getName();
        if (!itsFile.canWrite()) {
            Integer extraMsgId = null;

            // Check for SD card location
            File[] extdirs = ApiCompat.getExternalFilesDirs(ctx, null);
            if ((extdirs != null) && (extdirs.length > 1)) {
                for (int i = 1; i < extdirs.length; ++i) {
                    if (extdirs[i] == null) {
                        continue;
                    }
                    String path = extdirs[i].getAbsolutePath();
                    int pos = path.indexOf("/Android/");
                    if (pos == -1) {
                        continue;
                    }

                    String basepath = path.substring(0, pos + 1);
                    if (itsFile.getAbsolutePath().startsWith(basepath)) {
                        extraMsgId = R.string.read_only_sdcard;
                        break;
                    }
                }
            }
            return new Pair<>(false, extraMsgId);
        }

        // Check mount state on kitkat or higher
        if (ApiCompat.SDK_VERSION < ApiCompat.SDK_KITKAT) {
            return new Pair<>(true, null);
        }

        boolean writable = !EnvironmentCompat.getStorageState(itsFile).equals(
                Environment.MEDIA_MOUNTED_READ_ONLY);
        return new Pair<>(writable,
                          writable ? null : R.string.read_only_media);
    }


    /** Resolve fields for a generic provider URI */
    private void resolveGenericProviderUri(@NonNull Context context)
    {
        ContentResolver cr = context.getContentResolver();
        itsTitle = "(unknown)";
        var perms = new FilePerms(false, false);
        try (Cursor cursor = cr.query(itsUri, null, null, null, null)) {
            if ((cursor != null) && cursor.moveToFirst()) {
                int colidx =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (colidx != -1) {
                    itsTitle = cursor.getString(colidx);
                }

                perms = resolveGenericProviderFlags(cursor, context);
            }
        }
        itsWritableInfo = new Pair<>(perms.isWritable,
                                     perms.isWritable ? null :
                                     R.string.read_only_provider);
        itsIsDeletable = perms.isDeletable;
    }


    /**
     * Resolve the writable and deletable flags for a generic provider URI
     */
    @NonNull
    private FilePerms resolveGenericProviderFlags(@NonNull Cursor cursor,
                                                  @NonNull Context ctx)
    {
        if (ApiCompat.isChromeOS(ctx)) {
            /*
            NOTES:
            - Local files: can write, not doc uri, write perms, flags are 0
            - GDrive files: can't write, not doc uri, write perms, flags are 0
            - OneDrive files: can write, is doc uri, write perms, flags are 1
            - GDrive app: can write, not doc uri, doesn't grant write
            permissions but flags imply writes
            - OneDrive app: can't write, doesn't grant write perms, flags N/A

            So:
            - If flags imply writes -> writeable and/or deletable
            - If write permission -> writable / not deletable (not supported)
            - not write / not delete
             */
            var perms = getDocumentUriFilePerms(cursor);
            if ((perms != null) && perms.isWritable) {
                return perms;
            }

            if (isUriGrantedWritePermission(ctx)) {
                // ChromeOS doesn't seem to support deletes on its basic
                // volume file provider
                return new FilePerms(true, false);
            }

            return new FilePerms(false, false);
        }

        boolean checkFlags = false;
        if (DocumentFile.isDocumentUri(ctx, itsUri)) {
            if (!isUriGrantedWritePermission(ctx)) {
                return new FilePerms(false, false);
            }
            checkFlags = true;
        } else if (cursor.getColumnIndex(
                DocumentsContractCompat.COLUMN_DOCUMENT_ID) != -1) {
            checkFlags = true;
        }

        if (checkFlags) {
            var perms = getDocumentUriFilePerms(cursor);
            if (perms != null) {
                return perms;
            }
        }

        int colidx = cursor.getColumnIndex("read_only");
        if (colidx != -1) {
            int val = cursor.getInt(colidx);
            boolean writable = (val == 0);
            return new FilePerms(writable, writable);
        }

        return new FilePerms(true, true);
    }


    /**
     * Get the file permissions from the resolved cursor for a document contract
     * URI
     */
    @Nullable
    private FilePerms getDocumentUriFilePerms(@NonNull Cursor cursor)
    {
        int colidx =
                cursor.getColumnIndex(DocumentsContractCompat.COLUMN_FLAGS);
        if (colidx != -1) {
            int flags = cursor.getInt(colidx);
            return new FilePerms(
                    (flags & DocumentsContractCompat.FLAG_SUPPORTS_WRITE) != 0,
                    (flags & DocumentsContractCompat.FLAG_SUPPORTS_DELETE) !=
                    0);
        } else {
            return null;
        }
    }


    /**
     * Has the URI been granted write permission
     */
    private boolean isUriGrantedWritePermission(@NonNull Context ctx)
    {
        var rc = ctx.checkCallingOrSelfUriPermission(
                itsUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return rc == PackageManager.PERMISSION_GRANTED;
    }


    /** Resolve fields for a sync provider URI */
    private void resolveSyncProviderUri(Context context)
    {
        itsWritableInfo = new Pair<>(true, null);
        itsIsDeletable = true;
        if (itsSyncType != null) {
            return;
        }

        long providerId = -1;
        boolean isFile = false;
        switch (PasswdSafeContract.MATCHER.match(itsUri)) {
        case PasswdSafeContract.MATCH_PROVIDER:
        case PasswdSafeContract.MATCH_PROVIDER_FILES: {
            providerId = Long.parseLong(itsUri.getPathSegments().get(1));
            break;
        }
        case PasswdSafeContract.MATCH_PROVIDER_FILE: {
            providerId = Long.parseLong(itsUri.getPathSegments().get(1));
            isFile = true;
            break;
        }
        }

        if (providerId != -1) {
            ContentResolver cr = context.getContentResolver();
            resolveSyncProvider(providerId, cr);
            if (isFile) {
                resolveSyncFile(cr);
            }
        }
    }


    /** Resolve sync provider information */
    private void resolveSyncProvider(long providerId,
                                     @NonNull ContentResolver cr)
    {
        Uri providerUri = ContentUris.withAppendedId(
                PasswdSafeContract.Providers.CONTENT_URI, providerId);
        try (Cursor providerCursor = cr.query(
                providerUri,
                PasswdSafeContract.Providers.PROJECTION,
                null, null, null)) {
            if ((providerCursor != null) && providerCursor.moveToFirst()) {
                String typeStr = providerCursor.getString(
                        PasswdSafeContract.Providers.PROJECTION_IDX_TYPE);
                try {
                    itsSyncType = ProviderType.valueOf(typeStr);
                    itsTitle = providerCursor.getString(
                            PasswdSafeContract.Providers.PROJECTION_IDX_ACCT);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Unknown provider type: " + typeStr);
                }
            }
        }
    }


    /** Resolve sync file information */
    private void resolveSyncFile(@NonNull ContentResolver cr)
    {
        try (Cursor fileCursor = cr.query(itsUri,
                                          PasswdSafeContract.Files.PROJECTION,
                                          null, null, null)) {
            if ((fileCursor != null) && fileCursor.moveToFirst()) {
                itsTitle = fileCursor.getString(
                        PasswdSafeContract.Files.PROJECTION_IDX_TITLE);
            }
        }
    }

    /**
     * Resolve fields for a backup file URI
     */
    private BackupFile resolveBackupUri(Context context)
    {
        itsWritableInfo = new Pair<>(false, null);
        itsIsDeletable = false;

        long backupFileId = Long.parseLong(itsUri.getSchemeSpecificPart());
        BackupFilesDao backupFiles =
                PasswdSafeDb.get(context).accessBackupFiles();
        BackupFile backup = backupFiles.getBackupFile(backupFileId);
        if (backup != null) {
            itsTitle =
                    context.getString(R.string.backup_for_file_on, backup.title,
                                      Utils.formatDate(backup.date, context));
        } else {
            itsTitle = null;
        }
        return backup;
    }

    /**
     * Resolved file permissions
     */
    private static class FilePerms
    {
        protected final boolean isWritable;
        protected final boolean isDeletable;

        protected FilePerms(boolean writable, boolean deletable)
        {
            isWritable = writable;
            isDeletable = deletable;
        }
    }
}
