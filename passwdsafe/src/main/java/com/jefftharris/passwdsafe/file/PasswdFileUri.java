/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
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
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v4.os.EnvironmentCompat;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.jefftharris.passwdsafe.R;
import com.jefftharris.passwdsafe.lib.ApiCompat;
import com.jefftharris.passwdsafe.lib.DocumentsContractCompat;
import com.jefftharris.passwdsafe.lib.PasswdSafeContract;
import com.jefftharris.passwdsafe.lib.ProviderType;
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
import java.io.IOException;
import java.io.InputStream;

/**
 * The PasswdFileUri class encapsulates a URI to a password file
 */
public class PasswdFileUri implements Parcelable
{
    private static final String TAG = "PasswdFileUri";

    private final Uri itsUri;
    private final Type itsType;
    private final File itsFile;
    private String itsTitle = null;
    private Pair<Boolean, Integer> itsWritableInfo;
    private boolean itsIsDeletable;
    private ProviderType itsSyncType = null;

    /** The type of URI */
    public enum Type
    {
        FILE,
        SYNC_PROVIDER,
        EMAIL,
        GENERIC_PROVIDER
    }


    /** Parcelable CREATOR instance */
    @SuppressWarnings("unused")
    public static final Parcelable.Creator<PasswdFileUri> CREATOR =
            new Parcelable.Creator<PasswdFileUri>()
            {
                /* (non-Javadoc)
                 * @see android.os.Parcelable.Creator#createFromParcel(android.os.Parcel)
                 */
                @Override
                public PasswdFileUri createFromParcel(Parcel source)
                {
                    return new PasswdFileUri(source);
                }

                /* (non-Javadoc)
                 * @see android.os.Parcelable.Creator#newArray(int)
                 */
                @Override
                public PasswdFileUri[] newArray(int size)
                {
                    return new PasswdFileUri[size];
                }
            };

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
            case EMAIL: {
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
            itsFile = new File(uri.getPath());
            resolveFileUri(ctx);
            break;
        }
        case GENERIC_PROVIDER: {
            itsFile = null;
            resolveGenericProviderUri(ctx);
            break;
        }
        case SYNC_PROVIDER: {
            itsFile = null;
            resolveSyncProviderUri(ctx);
            break;
        }
        case EMAIL:
            //noinspection UnnecessaryDefault
        default: {
            itsFile = null;
            itsWritableInfo = new Pair<>(false, null);
            itsIsDeletable = false;
            break;
        }
        }
    }


    /** Constructor from a File */
    private PasswdFileUri(File file, Context ctx)
    {
        itsUri = Uri.fromFile(file);
        itsType = Type.FILE;
        itsFile = file;
        resolveFileUri(ctx);
    }


    /** Constructor from parcelable data */
    private PasswdFileUri(Parcel source)
    {
        String str;
        itsUri = source.readParcelable(getClass().getClassLoader());
        itsType = Type.valueOf(source.readString());
        str = source.readString();
        //noinspection ConstantConditions
        itsFile = (str != null) ? new File(str) : null;
        itsTitle = source.readString();
        str = source.readString();
        //noinspection ConstantConditions
        itsSyncType = (str != null) ? ProviderType.valueOf(str) : null;
    }


    /** Load the password file */
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
        }
        return null;
    }


    /** Create a new file */
    public PwsFile createNew(Owner<PwsPassword>.Param passwd, Context context)
            throws IOException
    {
        switch (itsType) {
        case FILE: {
            PwsFile file = PwsFileFactory.newFile();
            file.setPassphrase(passwd);
            file.setStorage(new PwsFileStorage(itsFile.getAbsolutePath(),
                                               null));
            return file;
        }
        case SYNC_PROVIDER: {
            PwsFile file = PwsFileFactory.newFile();
            file.setPassphrase(passwd);
            String id = getIdentifier(context, false);
            file.setStorage(new PasswdFileSyncStorage(itsUri, id, null));
            return file;
        }
        case GENERIC_PROVIDER: {
            PwsFile file = PwsFileFactory.newFile();
            file.setPassphrase(passwd);
            String id = getIdentifier(context, false);
            file.setStorage(new PasswdFileGenProviderStorage(itsUri, id, null));
            return file;
        }
        case EMAIL: {
            throw new IOException("no file");
        }
        }
        return null;
    }


    /**
     * Validate a new file that is a child of the current URI. Return null if
     * successful; error string otherwise
     */
    public String validateNewChild(String fileName, Context ctx)
    {
        switch (itsType) {
        case FILE: {
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
        case GENERIC_PROVIDER: {
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
        case GENERIC_PROVIDER: {
            break;
        }
        }
        throw new IOException("Can't create child \"" + fileName +
                              "\" for URI " + toString());
    }


    /** Delete a file */
    public void delete(Context context)
            throws IOException
    {
        switch (itsType) {
        case FILE: {
            if (!itsFile.delete()) {
                throw new IOException("Could not delete file: " + toString());
            }
            break;
        }
        case SYNC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            int rc = cr.delete(itsUri, null, null);
            if (rc != 1) {
                throw new IOException("Could not delete file: " + toString());
            }
            break;
        }
        case GENERIC_PROVIDER: {
            ContentResolver cr = context.getContentResolver();
            if (!ApiCompat.documentsContractDeleteDocument(cr, itsUri)) {
                throw new IOException("Could not delete file: " + toString());
            }
            break;
        }
        case EMAIL: {
            throw new IOException("Delete not supported for " + toString());
        }
        }
    }


    /** Does the file exist at the URI */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean exists()
    {
        switch (itsType) {
        case FILE: {
            return (itsFile != null) && itsFile.exists();
        }
        case SYNC_PROVIDER: {
            return (itsSyncType != null);
        }
        case EMAIL:
        case GENERIC_PROVIDER: {
            return true;
        }
        }
        return false;
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

    /**
     * Get the name of the URI's file if known
     */
    public @Nullable String getFileName()
    {
        switch (itsType) {
        case FILE: {
            return itsFile.getName();
        }
        case SYNC_PROVIDER:
        case GENERIC_PROVIDER: {
            return itsTitle;
        }
        case EMAIL: {
            return null;
        }
        }
        return null;
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
        }
        return "";
    }


    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PasswdFileUri)) {
            return false;
        }
        PasswdFileUri uri = (PasswdFileUri)o;
        return itsUri.equals(uri.itsUri);
    }


    /** Convert the URI to a string */
    @Override
    public String toString()
    {
        return itsUri.toString();
    }


    /* (non-Javadoc)
     * @see android.os.Parcelable#describeContents()
     */
    @Override
    public int describeContents()
    {
        return 0;
    }


    /* (non-Javadoc)
     * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeParcelable(itsUri, flags);
        dest.writeString(itsType.name());
        dest.writeString((itsFile != null) ? itsFile.getAbsolutePath() : null);
        dest.writeString(itsTitle);
        dest.writeString((itsSyncType != null) ? itsSyncType.name() : null);
    }


    /** Get the URI type */
    private static Type getUriType(Uri uri)
    {
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            return Type.FILE;
        }
        String auth = uri.getAuthority();
        if (PasswdSafeContract.AUTHORITY.equals(auth)) {
            return Type.SYNC_PROVIDER;
        } else if (auth.contains("mail")) {
            return Type.EMAIL;
        }
        return Type.GENERIC_PROVIDER;
    }


    /** Resolve fields for a file URI */
    private void resolveFileUri(Context ctx)
    {
        boolean writable;
        Integer extraMsgId = null;
        do {
            if (itsFile == null) {
                writable = false;
                break;
            }

            itsTitle = itsFile.getName();
            if (!itsFile.canWrite()) {
                writable = false;

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
                break;
            }

            // Check mount state on kitkat or higher
            if (ApiCompat.SDK_VERSION < ApiCompat.SDK_KITKAT) {
                writable = true;
                break;
            }
            writable = !EnvironmentCompat.getStorageState(itsFile).equals(
                    Environment.MEDIA_MOUNTED_READ_ONLY);
            if (!writable) {
                extraMsgId = R.string.read_only_media;
            }
        } while(false);
        itsWritableInfo = new Pair<>(writable, extraMsgId);
        itsIsDeletable = writable;
    }


    /** Resolve fields for a generic provider URI */
    private void resolveGenericProviderUri(Context context)
    {
        ContentResolver cr = context.getContentResolver();
        itsTitle = "(unknown)";
        boolean writable = false;
        boolean deletable = false;
        Cursor cursor = cr.query(itsUri, null, null, null, null);
        try {
            if ((cursor != null) && cursor.moveToFirst()) {
                int colidx =
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (colidx != -1) {
                    itsTitle = cursor.getString(colidx);
                }

                Pair<Boolean, Boolean> rc =
                        resolveGenericProviderFlags(cursor, context);
                writable = rc.first;
                deletable = rc.second;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        itsWritableInfo = new Pair<>(
                writable, writable ? null : R.string.read_only_provider);
        itsIsDeletable = deletable;
    }


    /**
     * Resolve the writable and deletable flags for a generic provider URI
     */
    private Pair<Boolean, Boolean> resolveGenericProviderFlags(Cursor cursor,
                                                               Context ctx)
    {
        boolean checkFlags = false;
        //noinspection ConstantConditions
        if (DocumentFile.isDocumentUri(ctx, itsUri)) {
            if (ctx.checkCallingOrSelfUriPermission(
                    itsUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION) !=
                PackageManager.PERMISSION_GRANTED) {
                return new Pair<>(false, false);
            }
            checkFlags = true;
        } else if (cursor.getColumnIndex(
                DocumentsContractCompat.COLUMN_DOCUMENT_ID) != -1) {
            checkFlags = true;
        }

        if (checkFlags) {
            int colidx = cursor.getColumnIndex(
                    DocumentsContractCompat.COLUMN_FLAGS);
            if (colidx != -1) {
                int flags = cursor.getInt(colidx);
                return new Pair<>(
                        ((flags & DocumentsContractCompat.FLAG_SUPPORTS_WRITE)
                         != 0),
                        ((flags & DocumentsContractCompat.FLAG_SUPPORTS_DELETE)
                         != 0));
            }
        }

        int colidx = cursor.getColumnIndex("read_only");
        if (colidx != -1) {
            int val = cursor.getInt(colidx);
            boolean writable = (val == 0);
            return new Pair<>(writable, writable);
        }

        return new Pair<>(true, true);
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
            providerId = Long.valueOf(itsUri.getPathSegments().get(1));
            break;
        }
        case PasswdSafeContract.MATCH_PROVIDER_FILE: {
            providerId = Long.valueOf(itsUri.getPathSegments().get(1));
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
                                     ContentResolver cr)
    {
        Uri providerUri = ContentUris.withAppendedId(
                PasswdSafeContract.Providers.CONTENT_URI, providerId);
        Cursor providerCursor = cr.query(
                providerUri,
                PasswdSafeContract.Providers.PROJECTION,
                null, null, null);
        try {
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
        } finally {
            if (providerCursor != null) {
                providerCursor.close();
            }
        }
    }


    /** Resolve sync file information */
    private void resolveSyncFile(ContentResolver cr)
    {
        Cursor fileCursor = cr.query(itsUri,
                                     PasswdSafeContract.Files.PROJECTION,
                                     null, null, null);
        try {
            if ((fileCursor != null) && fileCursor.moveToFirst()) {
                itsTitle = fileCursor.getString(
                        PasswdSafeContract.Files.PROJECTION_IDX_TITLE);
            }
        } finally {
            if (fileCursor != null) {
                fileCursor.close();
            }
        }
    }
}
