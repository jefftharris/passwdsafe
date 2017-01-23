/*
 * Copyright (©) 2016 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.jefftharris.passwdsafe.lib.PasswdSafeUtil;
import com.jefftharris.passwdsafe.util.Pair;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Collections;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

/**
 * The SavedPasswordsMgr class encapsulates functionality for saving
 * passwords to files
 */
public final class SavedPasswordsMgr
{
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TAG = "SavedPasswordsMgr";

    private final FingerprintManagerCompat itsFingerprintMgr;
    private final SavedPasswordsDb itsDb;
    private final Context itsContext;

    /**
     * User of the saved password manager
     */
    public static abstract class User
            extends FingerprintManagerCompat.AuthenticationCallback
            implements CancellationSignal.OnCancelListener
    {
        private final CancellationSignal itsCancelSignal;

        /**
         * Constructor
         */
        public User()
        {
            itsCancelSignal = new CancellationSignal();
            itsCancelSignal.setOnCancelListener(this);
        }

        /**
         * Cancel use of the manager
         */
        public void cancel()
        {
            itsCancelSignal.cancel();
        }

        /**
         * Is the user for encryption or decryption
         */
        protected abstract boolean isEncrypt();

        /**
         * Callback when the user has started
         */
        protected abstract void onStart();

        /**
         * Get the cancellation signaler
         */
        private CancellationSignal getCancelSignal()
        {
            return itsCancelSignal;
        }
    }

    /**
     * Constructor
     */
    public SavedPasswordsMgr(Context ctx)
    {
        itsFingerprintMgr = FingerprintManagerCompat.from(ctx);
        itsDb = new SavedPasswordsDb(ctx);
        itsContext = ctx;
    }

    /**
     * Are saved passwords available
     */
    public boolean isAvailable()
    {
        return itsFingerprintMgr.isHardwareDetected();
    }

    /**
     * Is there a saved password for a file
     */
    public synchronized boolean isSaved(Uri uri)
    {
        try {
            return itsDb.isSaved(uri);
        } catch (Exception e) {
            Log.e(TAG, "Error checking saved for " + uri, e);
            return false;
        }
    }

    /**
     * Generate a saved password key for a file
     */
    @TargetApi(Build.VERSION_CODES.M)
    public synchronized void generateKey(Uri fileUri)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
            NoSuchProviderException, IOException
    {
        PasswdSafeUtil.dbginfo(TAG, "generateKey: %s", fileUri);

        if (!itsFingerprintMgr.hasEnrolledFingerprints()) {
            throw new IOException(
                    itsContext.getString(R.string.no_fingerprints_registered));
        }

        String keyName = getUriAlias(fileUri);
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            keyGen.init(
                    new KeyGenParameterSpec.Builder(
                            keyName,
                            KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(
                                    KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(true)
                            .build());
            keyGen.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException |
                InvalidAlgorithmParameterException e) {
            Log.e(TAG, "generateKey failure", e);
            removeSavedPassword(fileUri);
            throw e;
        }
    }

    /**
     * Start access to the key protecting the saved password for a file
     */
    public void startPasswordAccess(Uri fileUri, User user)
    {
        try {
            Cipher cipher = getKeyCipher(fileUri, user.isEncrypt());
            FingerprintManagerCompat.CryptoObject cryptoObj =
                    new FingerprintManagerCompat.CryptoObject(cipher);
            itsFingerprintMgr.authenticate(cryptoObj, 0, user.getCancelSignal(),
                                           user, null);
            user.onStart();
        } catch (CertificateException | NoSuchAlgorithmException |
                KeyStoreException | UnrecoverableKeyException |
                NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException | IOException e) {
            String msg = itsContext.getString(R.string.key_error, fileUri,
                                              e.getLocalizedMessage());
            Log.e(TAG, msg, e);
            user.onAuthenticationError(0, msg);
        }
    }

    /**
     * Load a saved password for a file
     */
    public String loadSavedPassword(Uri fileUri, Cipher cipher)
            throws IOException, BadPaddingException, IllegalBlockSizeException
    {
        Pair<String, String> saved = null;
        Exception exc = null;
        try {
            saved = itsDb.getSavedPassword(fileUri);
        } catch (Exception e) {
            exc = e;
        }

        if ((saved == null) || TextUtils.isEmpty(saved.second)) {
            throw new IOException(
                    itsContext.getString(R.string.password_not_found, fileUri),
                    exc);
        }

        byte[] enc = Base64.decode(saved.second, Base64.NO_WRAP);
        byte[] decPassword = cipher.doFinal(enc);
        return new String(decPassword, "UTF-8");
    }

    /**
     * Add a saved password for a file
     */
    public void addSavedPassword(Uri fileUri, String password, Cipher cipher)
            throws Exception
    {
        byte[] enc = cipher.doFinal(password.getBytes("UTF-8"));
        String encStr = Base64.encodeToString(enc, Base64.NO_WRAP);
        String ivStr = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);

        itsDb.addSavedPassword(fileUri.toString(), ivStr, encStr);
    }

    /**
     * Removed the saved password and key for a file
     */
    public synchronized void removeSavedPassword(Uri fileUri)
    {
        try {
            itsDb.removeSavedPassword(fileUri);
        } catch (Exception e) {
            Log.e(TAG, "Error removing " + fileUri, e);
        }
        if (isAvailable()) {
            PasswdSafeUtil.dbginfo(TAG, "removeSavedPassword: %s", fileUri);
            try {
                KeyStore keyStore = getKeystore();
                String keyName = getUriAlias(fileUri);
                keyStore.deleteEntry(keyName);
            } catch (KeyStoreException | CertificateException |
                    IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Remove all saved passwords and keys
     */
    public synchronized void removeAllSavedPasswords()
    {
        try {
            itsDb.removeAllSavedPasswords();
        } catch (Exception e) {
            Log.e(TAG, "Error removing passwords", e);
        }
        if (isAvailable()) {
            try {
                KeyStore keyStore = getKeystore();
                for (String key: Collections.list(keyStore.aliases())) {
                    PasswdSafeUtil.dbginfo(
                            TAG, "removeAllSavedPasswords key: %s", key);
                    keyStore.deleteEntry(key);
                }
            } catch (CertificateException | NoSuchAlgorithmException |
                    IOException | KeyStoreException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the cipher for the key protecting the saved password for a file
     */
    @TargetApi(Build.VERSION_CODES.M)
    private Cipher getKeyCipher(Uri fileUri, boolean encrypt)
            throws CertificateException, NoSuchAlgorithmException,
                   KeyStoreException, IOException, UnrecoverableKeyException,
                   NoSuchPaddingException, InvalidKeyException,
                   InvalidAlgorithmParameterException
    {
        String keyName = getUriAlias(fileUri);
        KeyStore keystore = getKeystore();
        Key key = keystore.getKey(keyName, null);
        if (key == null) {
            throw new IOException(itsContext.getString(R.string.key_not_found,
                                                       fileUri));
        }

        Cipher ciph = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_CBC + "/" +
                KeyProperties.ENCRYPTION_PADDING_PKCS7);
        if (encrypt) {
            ciph.init(Cipher.ENCRYPT_MODE, key);
        } else {
            Pair<String, String> saved = null;
            Exception exc = null;
            try {
                saved = itsDb.getSavedPassword(fileUri);
            } catch (Exception e) {
                exc = e;
            }

            if ((saved == null) || TextUtils.isEmpty(saved.first)) {
                throw new IOException("Key IV not found for " + fileUri, exc);
            }
            byte[] iv = Base64.decode(saved.first, Base64.NO_WRAP);
            ciph.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        }
        return ciph;
    }

    /**
     * Get the Android keystore containing the keys protecting saved passwords
     */
    private KeyStore getKeystore()
            throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException
    {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        return store;
    }

    /**
     * Get the keystore alias for a URI
     */
    private String getUriAlias(Uri uri)
    {
        return "key_" + uri.toString();
    }

    /**
     * Saved passwords database
     */
    private static class SavedPasswordsDb
    {
        private static final String[] QUERY_COLUMNS = new String[] {
                PasswdSafeDb.DB_COL_SAVED_PASSWORDS_URI,
                PasswdSafeDb.DB_COL_SAVED_PASSWORDS_IV,
                PasswdSafeDb.DB_COL_SAVED_PASSWORDS_ENC_PASSWD};

        private static final int QUERY_COL_IV = 1;
        private static final int QUERY_COL_ENC_PASSWD = 2;

        private static final String WHERE_BY_URI =
                PasswdSafeDb.DB_COL_SAVED_PASSWORDS_URI + " = ?";

        private final PasswdSafeDb itsDb;

        /**
         * Constructor
         */
        public SavedPasswordsDb(Context ctx)
        {
            PasswdSafeApp app = (PasswdSafeApp)ctx.getApplicationContext();
            itsDb = app.getPasswdSafeDb();
            processDbUpgrade(ctx);
        }

        /**
         * Is there a saved password for a URI
         */
        public boolean isSaved(final Uri uri) throws Exception
        {
            return itsDb.useDb(new PasswdSafeDb.DbUser<Boolean>()
            {
                @Override
                public Boolean useDb(SQLiteDatabase db) throws Exception
                {
                    Cursor c = db.query(PasswdSafeDb.DB_TABLE_SAVED_PASSWORDS,
                                        QUERY_COLUMNS,
                                        WHERE_BY_URI,
                                        new String[] {uri.toString()},
                                        null, null, null);
                    try {
                        return c.moveToFirst();
                    } finally {
                        c.close();
                    }
                }
            });
        }

        /**
         * Get the IV and encrypted saved password for a URI
         */
        public Pair<String, String> getSavedPassword(final Uri uri)
                throws Exception
        {
            return itsDb.useDb(new PasswdSafeDb.DbUser<Pair<String, String>>()
            {
                @Override
                public Pair<String, String> useDb(SQLiteDatabase db)
                        throws Exception
                {
                    Cursor c = db.query(PasswdSafeDb.DB_TABLE_SAVED_PASSWORDS,
                                        QUERY_COLUMNS,
                                        WHERE_BY_URI,
                                        new String[] {uri.toString()},
                                        null, null, null);
                    try {
                        if (c.moveToFirst()) {
                            return new Pair<>(
                                    c.getString(QUERY_COL_IV),
                                    c.getString(QUERY_COL_ENC_PASSWD));
                        }
                    } finally {
                        c.close();
                    }
                    return null;
                }
            });
        }

        /**
         * Add the saved password to the database
         */
        public void addSavedPassword(String uri, String iv, String encPasswd)
                throws Exception
        {
            final ContentValues values = new ContentValues();
            values.put(PasswdSafeDb.DB_COL_SAVED_PASSWORDS_URI, uri);
            values.put(PasswdSafeDb.DB_COL_SAVED_PASSWORDS_IV, iv);
            values.put(PasswdSafeDb.DB_COL_SAVED_PASSWORDS_ENC_PASSWD,
                       encPasswd);
            itsDb.useDb(new PasswdSafeDb.DbUser<Void>()
            {
                @Override
                public Void useDb(SQLiteDatabase db) throws Exception
                {
                    db.replaceOrThrow(PasswdSafeDb.DB_TABLE_SAVED_PASSWORDS,
                                      null, values);
                    return null;
                }
            });
        }

        /**
         * Remove the saved password
         */
        public void removeSavedPassword(final Uri uri) throws Exception
        {
            itsDb.useDb(new PasswdSafeDb.DbUser<Void>()
            {
                @Override
                public Void useDb(SQLiteDatabase db) throws Exception
                {
                    db.delete(PasswdSafeDb.DB_TABLE_SAVED_PASSWORDS,
                              WHERE_BY_URI, new String[] {uri.toString()});
                    return null;
                }
            });
        }

        /**
         * Remove all saved passwords
         */
        public void removeAllSavedPasswords() throws Exception
        {
            itsDb.useDb(new PasswdSafeDb.DbUser<Void>()
            {
                @Override
                public Void useDb(SQLiteDatabase db) throws Exception
                {
                    db.delete(PasswdSafeDb.DB_TABLE_SAVED_PASSWORDS,
                              null, null);
                    return null;
                }
            });
        }

        /**
         * Upgrade the database storage
         */
        private void processDbUpgrade(Context ctx)
        {
            // Upgrade from preferences storage
            SharedPreferences prefs =
                    ctx.getSharedPreferences("saved", Context.MODE_PRIVATE);
            for (String pref : prefs.getAll().keySet()) {
                if (!pref.startsWith("key_")) {
                    continue;
                }
                String uri = pref.substring("key_".length());
                String encPasswd = prefs.getString(pref, null);
                String iv = prefs.getString("iv_" + pref, null);
                if ((encPasswd == null) || (iv == null)) {
                    continue;
                }
                try {
                    addSavedPassword(uri, iv, encPasswd);
                } catch (Exception e) {
                    Log.e(TAG, "Error upgrading keys", e);
                }
            }
            prefs.edit().clear().apply();
        }
    }
}
