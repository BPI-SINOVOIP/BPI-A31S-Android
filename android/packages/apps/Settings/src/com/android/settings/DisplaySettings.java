/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;

import java.util.ArrayList;

import android.os.SystemProperties;
import android.preference.PreferenceCategory;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_SCREEN_SAVER = "screensaver";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;


    private static final String KEY_BRIGHT_SYSTEM = "bright_system";
    private static final String KEY_BRIGHT_SYSTEM_DEMO = "bright_demo_mode";
    private static final String KEY_BRIGHTNESS_LIGHT = "brightness_light";
    private static final String KEY_BRIGHTNESS_LIGHT_DEMO = "backlight_demo_mode";
    private static final String KEY_HDMI_OUTPUT_MODE = "hdmi_output_mode";
    private static final String KEY_HDMI_OUTPUT_MODE_720P = "hdmi_output_mode_720p";
    private static final String KEY_HDMI_OUTPUT_MODE_CATE = "hdmi_output_mode_cate";
    private static final String KEY_HDMI_FULL_SCREEN = "hdmi_full_screen";
    private static final String KEY_ACCELEROMETER_COORDINATE = "accelerometer_coornadite";
    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;
    private CheckBoxPreference mBrightSystem,mBrightSystemDemo;
    private CheckBoxPreference mBrightnessLight,mBrightnessLightDemo;

    private ListPreference mHdmiOutputModePreference;
    private PreferenceCategory mHdmiOutputModeCategory;
    private CheckBoxPreference mHdmiFullScreen;
    private ListPreference mAccelerometerCoordinate;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (!RotationPolicy.isRotationSupported(getActivity())
                || RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings,
            // if the device supports rotation.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);
        mNotificationPulse = (CheckBoxPreference) findPreference(KEY_NOTIFICATION_PULSE);
        if (mNotificationPulse != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationPulse);
        } else {
            try {
                mNotificationPulse.setChecked(Settings.System.getInt(resolver,
                        Settings.System.NOTIFICATION_LIGHT_PULSE) == 1);
                mNotificationPulse.setOnPreferenceChangeListener(this);
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.NOTIFICATION_LIGHT_PULSE + " not found");
            }
        }

        final boolean isShowAccelCoord = (SystemProperties.getInt("ro.sf.showaccelcoord", 0) & 0x01) > 0;
        mAccelerometerCoordinate = (ListPreference) findPreference(KEY_ACCELEROMETER_COORDINATE);
        if(mAccelerometerCoordinate != null && isShowAccelCoord){
            mAccelerometerCoordinate.setOnPreferenceChangeListener(this);
            String value = Settings.System.getString(getContentResolver(),
                    Settings.System.ACCELEROMETER_COORDINATE);
            mAccelerometerCoordinate.setValue(value);
            updateAccelerometerCoordinateSummary(value);
        } else {
            getPreferenceScreen().removePreference(mAccelerometerCoordinate);
        }

        mBrightSystem = (CheckBoxPreference)findPreference(KEY_BRIGHT_SYSTEM);
        mBrightSystemDemo = (CheckBoxPreference)findPreference(KEY_BRIGHT_SYSTEM_DEMO);
        boolean demoEnabled;
        if(mBrightSystem != null) {
            try{
                demoEnabled = (Settings.System.getInt(resolver,
                        Settings.System.BRIGHT_SYSTEM_MODE)&0x01) > 0;
                mBrightSystem.setChecked(demoEnabled);
                mBrightSystem.setOnPreferenceChangeListener(this);
                if (mBrightSystemDemo != null && demoEnabled) {
                    try {
                        mBrightSystemDemo.setChecked((Settings.System.getInt(resolver,
                                Settings.System.BRIGHT_SYSTEM_MODE)&0x02)> 0);
                        mBrightSystemDemo.setOnPreferenceChangeListener(this);
                    } catch (SettingNotFoundException snfe) {
                        Log.e(TAG, Settings.System.BRIGHT_SYSTEM_MODE + " not found");
                    }
                } else if (mBrightSystemDemo == null) {
                    getPreferenceScreen().removePreference(mBrightSystemDemo);
                } else {
                    mBrightSystemDemo.setEnabled(demoEnabled);
                }
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.BRIGHT_SYSTEM_MODE + " not found");
            }
        } else {
            getPreferenceScreen().removePreference(mBrightSystem);
        }

        mBrightnessLight = (CheckBoxPreference)findPreference(KEY_BRIGHTNESS_LIGHT);
        mBrightnessLightDemo = (CheckBoxPreference)findPreference(KEY_BRIGHTNESS_LIGHT_DEMO);
        if(mBrightnessLight != null){
            try{
                demoEnabled = (Settings.System.getInt(resolver,
                        Settings.System.BRIGHTNESS_LIGHT_MODE)&0x01)> 0;
                mBrightnessLight.setChecked(demoEnabled);
                mBrightnessLight.setOnPreferenceChangeListener(this);

                if (mBrightnessLightDemo != null && demoEnabled) {
                    try {
                        mBrightnessLightDemo.setChecked((Settings.System.getInt(resolver,
                                Settings.System.BRIGHTNESS_LIGHT_MODE)&0x02) > 0);
                        mBrightnessLightDemo.setOnPreferenceChangeListener(this);
                    } catch (SettingNotFoundException snfe) {
                        Log.e(TAG, Settings.System.BRIGHTNESS_LIGHT_MODE + " not found");
                    }
                } else if (mBrightnessLightDemo == null) {
                    getPreferenceScreen().removePreference(mBrightnessLightDemo);
                } else {
                    mBrightnessLightDemo.setEnabled(demoEnabled);
                }
            } catch (SettingNotFoundException snfe) {
                Log.e(TAG, Settings.System.BRIGHTNESS_LIGHT_MODE + " not found");
            }
        } else {
            getPreferenceScreen().removePreference(mBrightnessLight);
        }

        final int sethdmimode = SystemProperties.getInt("ro.sf.showhdmisettings", 0);
        final boolean isShowHdmiMode = (sethdmimode & 0x03) > 0;
        final boolean isShow1080p = (sethdmimode & 0x02) > 0;
        final boolean isShowFullScreen = (sethdmimode & 0x04) > 0;
        mHdmiOutputModeCategory = (PreferenceCategory) findPreference(KEY_HDMI_OUTPUT_MODE_CATE);
        mHdmiFullScreen = (CheckBoxPreference)findPreference(KEY_HDMI_FULL_SCREEN);
        if (isShow1080p) {
            mHdmiOutputModePreference = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE_720P);
            mHdmiOutputModeCategory.removePreference(mHdmiOutputModePreference);
            mHdmiOutputModePreference = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE);
        } else {
            mHdmiOutputModePreference = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE);
            mHdmiOutputModeCategory.removePreference(mHdmiOutputModePreference);
            mHdmiOutputModePreference = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE_720P);
        }

        if (sethdmimode != 0) {
            if (isShowHdmiMode) {
                final int currentHdmiMode = Settings.System.getInt(resolver, Settings.System.HDMI_OUTPUT_MODE, 0);
                mHdmiOutputModePreference.setValue(String.valueOf(currentHdmiMode));
                mHdmiOutputModePreference.setOnPreferenceChangeListener(this);
            } else {
                mHdmiOutputModeCategory.removePreference(mHdmiOutputModePreference);
                mHdmiOutputModePreference = null;
            }

            if (isShowFullScreen) {
                final boolean isHdmiFullScreen = Settings.System.getInt(resolver,
                        Settings.System.HDMI_FULL_SCREEN, 0) > 0;
                mHdmiFullScreen.setChecked(isHdmiFullScreen);
                mHdmiFullScreen.setOnPreferenceChangeListener(this);
            } else {
                mHdmiOutputModeCategory.removePreference(mHdmiFullScreen);
                mHdmiFullScreen = null;
            }
        } else {
            getPreferenceScreen().removePreference(mHdmiOutputModeCategory);
            mHdmiOutputModePreference = null;
            mHdmiOutputModeCategory = null;
            mHdmiFullScreen = null;
        }
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                    }
                }
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
            }
        }
        preference.setSummary(summary);
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    @Override
    public void onResume() {
        super.onResume();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
        if (mAccelerometerCoordinate != null) {
            updateAccelerometerCoordinateSummary(mAccelerometerCoordinate.getValue());
        }
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

    private void updateAccelerometerCoordinateSummary(Object value){
        CharSequence[] summaries = getResources().getTextArray(R.array.accelerometer_summaries);
        CharSequence[] values = mAccelerometerCoordinate.getEntryValues();
        for (int i=0; i<values.length; i++) {
            if (values[i].equals(value)) {
                mAccelerometerCoordinate.setSummary(summaries[i]);
                break;
            }
        }
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean value;
        int value2;
        try {
            if (preference == mAccelerometer) {
                RotationPolicy.setRotationLockForAccessibility(
                        getActivity(), !mAccelerometer.isChecked());
            } else if (preference == mNotificationPulse) {
                value = mNotificationPulse.isChecked();
                Settings.System.putInt(getContentResolver(), Settings.System.NOTIFICATION_LIGHT_PULSE,
                        value ? 1 : 0);
                return true;
            } else if (preference == mBrightSystem) {
                value = mBrightSystem.isChecked();
                value2 = Settings.System.getInt(getContentResolver(),
                        Settings.System.BRIGHT_SYSTEM_MODE);
                Settings.System.putInt(getContentResolver(),Settings.System.BRIGHT_SYSTEM_MODE,
                        value ? value2|0x01 : value2&0x02);
                mBrightSystemDemo.setEnabled(value);
            } else if (preference == mBrightSystemDemo) {
                value = mBrightSystemDemo.isChecked();
                value2 = Settings.System.getInt(getContentResolver(),
                        Settings.System.BRIGHT_SYSTEM_MODE);
                Settings.System.putInt(getContentResolver(),Settings.System.BRIGHT_SYSTEM_MODE,
                        value ? value2|0x02 : value2&0x01);
            } else if (preference == mBrightnessLight) {
                value = mBrightnessLight.isChecked();
                value2 = Settings.System.getInt(getContentResolver(),
                        Settings.System.BRIGHTNESS_LIGHT_MODE);
                Settings.System.putInt(getContentResolver(),Settings.System.BRIGHTNESS_LIGHT_MODE,
                        value ? value2|0x01 : value2&0x02);
                mBrightnessLightDemo.setEnabled(value);
            } else if (preference == mBrightnessLightDemo) {
                value = mBrightnessLightDemo.isChecked();
                value2 = Settings.System.getInt(getContentResolver(),
                        Settings.System.BRIGHTNESS_LIGHT_MODE);
                Settings.System.putInt(getContentResolver(),Settings.System.BRIGHTNESS_LIGHT_MODE,
                        value ? value2|0x02 : value2&0x01);
            } else if (preference == mHdmiFullScreen) {
                value = mHdmiFullScreen.isChecked();
                Settings.System.putInt(getContentResolver(),Settings.System.HDMI_FULL_SCREEN,
                        value ? 0x01 : 0);
            }
        } catch (SettingNotFoundException e) {
            Log.e(TAG, Settings.System.BRIGHTNESS_LIGHT_MODE+ " or "+
                    Settings.System.BRIGHT_SYSTEM_MODE + " not found");
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        if (KEY_ACCELEROMETER_COORDINATE.equals(key)) {
            String value = String.valueOf(objValue);
            try {
                Settings.System.putString(getContentResolver(),
                        Settings.System.ACCELEROMETER_COORDINATE, value);
                updateAccelerometerCoordinateSummary(objValue);
            }catch (NumberFormatException e) {
                Log.e(TAG, "could not persist key accelerometer coordinate setting", e);
            }
        }
        if (KEY_HDMI_OUTPUT_MODE.equals(key) || KEY_HDMI_OUTPUT_MODE_720P.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.HDMI_OUTPUT_MODE, value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist hdmi output mode setting", e);
            }
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        }
        return false;
    }
}
