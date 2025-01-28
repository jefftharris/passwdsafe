/*
 * Copyright (©) 2016-2025 Jeff Harris <jefftharris@gmail.com>
 * All rights reserved. Use of the code is allowed under the
 * Artistic License 2.0 terms, as specified in the LICENSE file
 * distributed with this code, or available from
 * http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.jefftharris.passwdsafe.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.jefftharris.passwdsafe.sync.lib.Preferences;

import java.util.Objects;

/**
 * Preferences activity
 */
public class PreferencesActivity extends AppCompatActivity
{
    /**
     * Fragment for the preferences
     */
    @SuppressWarnings({"WeakerAccess", "RedundantSuppression"})
    public static final class PreferencesFragment
            extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        private EditTextPreference itsDebugTagsPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState,
                                        String rootKey)
        {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
            itsDebugTagsPref = requirePreference(Preferences.PREF_DEBUG_TAGS);
            itsDebugTagsPref.setDialogMessage(R.string.logging_tags_desc);
            itsDebugTagsPref.setDefaultValue(Preferences.PREF_DEBUG_TAGS_DEF);
            onSharedPreferenceChanged(prefs, Preferences.PREF_DEBUG_TAGS);
       }

        @Override
        public void onResume()
        {
            super.onResume();
            SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause()
        {
            super.onPause();
            SharedPreferences prefs = Preferences.getSharedPrefs(getContext());
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              @Nullable String key)
        {
            boolean updateDebugTags = false;
            if (key == null) {
                updateDebugTags = true;
            } else {
                switch (key) {
                case Preferences.PREF_DEBUG_TAGS: {
                    updateDebugTags = true;
                    break;
                }
                }
            }
            if (updateDebugTags) {
                String val = Preferences.getDebugTagsPref(prefs);
                itsDebugTagsPref.setSummary(val);
            }
        }

        /**
         * Find a non-null preference
         * @noinspection SameParameterValue
         */
        @NonNull
        private <T extends Preference> T requirePreference(String key)
        {
            return Objects.requireNonNull(findPreference(key));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        if (savedInstanceState == null) {
            PreferencesFragment frag = new PreferencesFragment();
            frag.setArguments(getIntent().getExtras());
            FragmentManager fragMgr = getSupportFragmentManager();
            fragMgr.beginTransaction().replace(R.id.contents, frag).commit();
        }
    }
}
