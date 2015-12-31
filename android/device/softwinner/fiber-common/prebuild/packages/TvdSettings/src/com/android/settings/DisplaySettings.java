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
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

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
    private static final int FALLBACK_DISPLAY_MODE_TIMEOUT = 10;
    private static final String DISPLAY_MODE_AUTO_KEY = "display_mode_auto";

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_PULSE = "notification_pulse";
    private static final String KEY_SCREEN_SAVER = "screensaver";
	private static final String KEY_SMART_BRIGHTNESS = "smart_brightness";
	private static final String KEY_SMART_BRIGHTNESS_PREVIEW = "key_smart_brightness_preview";
    private static final String KEY_WIFI_DISPLAY = "wifi_display";
    private static final String KEY_TV_OUTPUT_MODE = "display_output_mode";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;

    private DisplayManager mDisplayManager;

    private static final String KEY_BRIGHT_SYSTEM_CTG = "bright_system_ctg";
    private static final String KEY_BRIGHT_SYSTEM = "bright_system";
    private static final String KEY_BRIGHT_SYSTEM_DEMO = "bright_demo_mode";
    private static final String KEY_BRIGHTNESS_LIGHT_CTG = "brightness_light_ctg";
    private static final String KEY_BRIGHTNESS_LIGHT = "brightness_light";
    private static final String KEY_BRIGHTNESS_LIGHT_DEMO = "backlight_demo_mode";
    private static final String KEY_HDMI_OUTPUT_MODE = "hdmi_output_mode";
    private static final String KEY_HDMI_OUTPUT_MODE_720P = "hdmi_output_mode_720p";
    private static final String KEY_COLOR_SETTING_CATE = "display_color_setting_cate";
    private static final String KEY_DISPLAY_OUTPUT_MODE_CATE = "display_output_mode_cate";
    private static final String KEY_HDMI_FULL_SCREEN = "hdmi_full_screen";
    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private CheckBoxPreference mNotificationPulse;
    private CheckBoxPreference mBrightSystem,mBrightSystemDemo;
    private CheckBoxPreference mBrightnessLight,mBrightnessLightDemo;
	private ListPreference mOutputMode;
    private ListPreference mHdmiOutputModePreference;
    private PreferenceCategory mDisplayOutputModeCategory;
    private PreferenceCategory mDisplayColorSettingCategory;
    private PreferenceCategory mBrightSystemCategory;
    private PreferenceCategory mBrightnessLightModeCategory;
    private CheckBoxPreference mHdmiFullScreen;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private WifiDisplayStatus mWifiDisplayStatus;
    private Preference mWifiDisplayPreference;
    private boolean isSupport = false;
    private boolean isSameMode = false;
    private BroadcastReceiver mDisplayPluggedListener = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
            int value = mDisplayManager.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
            mOutputMode.setValue(Integer.toHexString(value));
        }
    };
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
        mDisplayColorSettingCategory = (PreferenceCategory) findPreference(KEY_COLOR_SETTING_CATE);

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

        mDisplayManager = (DisplayManager)getActivity().getSystemService(
                Context.DISPLAY_SERVICE);
        mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        mWifiDisplayPreference = (Preference)findPreference(KEY_WIFI_DISPLAY);
        if (mWifiDisplayStatus.getFeatureState()
                == WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE) {
            getPreferenceScreen().removePreference(mWifiDisplayPreference);
            mWifiDisplayPreference = null;
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
        final boolean isShowDisplayMode = (sethdmimode & 0x03) > 0;
        final boolean isShowTvMode = (sethdmimode & 0x02) > 0;
        final boolean isShowFullScreen = (sethdmimode & 0x04) > 0;
        mDisplayOutputModeCategory = (PreferenceCategory) findPreference(KEY_DISPLAY_OUTPUT_MODE_CATE);
        mHdmiFullScreen = (CheckBoxPreference)findPreference(KEY_HDMI_FULL_SCREEN);
        if (isShowTvMode) {
            mOutputMode = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE);
            mDisplayOutputModeCategory.removePreference(mOutputMode);
            mOutputMode = (ListPreference) findPreference(KEY_TV_OUTPUT_MODE);
        } else {
            mOutputMode = (ListPreference) findPreference(KEY_TV_OUTPUT_MODE);
            mDisplayOutputModeCategory.removePreference(mOutputMode);
            mOutputMode = (ListPreference) findPreference(KEY_HDMI_OUTPUT_MODE);
        }

        if (sethdmimode != 0) {
            if (isShowDisplayMode) {
                int format = mDisplayManager.getDisplayOutput(android.view.Display.TYPE_BUILT_IN);
		        mOutputMode.setValue(Integer.toHexString(format));
                mOutputMode.setOnPreferenceChangeListener(this);
            } else {
                mDisplayOutputModeCategory.removePreference(mOutputMode);
                mOutputMode = null;
            }

            if (isShowFullScreen) {
                final boolean isHdmiFullScreen = Settings.System.getInt(resolver,
                        Settings.System.HDMI_FULL_SCREEN, 0) > 0;
                mHdmiFullScreen.setChecked(isHdmiFullScreen);
                mHdmiFullScreen.setOnPreferenceChangeListener(this);
            } else {
                mDisplayOutputModeCategory.removePreference(mHdmiFullScreen);
                mHdmiFullScreen = null;
            }
        } else {
            getPreferenceScreen().removePreference(mDisplayOutputModeCategory);
            mOutputMode = null;
            mDisplayOutputModeCategory = null;
            mHdmiFullScreen = null;
        }
        mBrightSystemCategory = (PreferenceCategory) findPreference(KEY_BRIGHT_SYSTEM_CTG);
        mBrightnessLightModeCategory = (PreferenceCategory) findPreference(KEY_BRIGHTNESS_LIGHT_CTG);
        // remove mBrightSystemCategory and mBrightnessLightModeCategory for box
		if (mBrightSystemCategory != null)
            getPreferenceScreen().removePreference(mBrightSystemCategory);
        if (mBrightnessLightModeCategory != null)
            getPreferenceScreen().removePreference(mBrightnessLightModeCategory);

		getPreferenceScreen().removePreference(mScreenTimeoutPreference);
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

        if (mWifiDisplayPreference != null) {
            getActivity().registerReceiver(mReceiver, new IntentFilter(
                    DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED));
            mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();
        }
        if (mOutputMode != null) {
            getActivity().registerReceiver(mDisplayPluggedListener, new IntentFilter(
                         Intent.ACTION_HDMISTATUS_CHANGED));
        }
        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        if (mWifiDisplayPreference != null) {
            getActivity().unregisterReceiver(mReceiver);
        }
        if (mOutputMode != null) {
            getActivity().unregisterReceiver(mDisplayPluggedListener);
        }
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
        updateWifiDisplaySummary();
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateWifiDisplaySummary() {
        if (mWifiDisplayPreference != null) {
            switch (mWifiDisplayStatus.getFeatureState()) {
                case WifiDisplayStatus.FEATURE_STATE_OFF:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_off);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_ON:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_on);
                    break;
                case WifiDisplayStatus.FEATURE_STATE_DISABLED:
                default:
                    mWifiDisplayPreference.setSummary(R.string.wifi_display_summary_disabled);
                    break;
            }
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        //mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
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
            /*if (preference == mAccelerometer) {
                RotationPolicy.setRotationLockForAccessibility(
                        getActivity(), !mAccelerometer.isChecked());
            } else */if (preference == mNotificationPulse) {
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
        if (KEY_TV_OUTPUT_MODE.equals(key) || KEY_HDMI_OUTPUT_MODE.equals(key)) {
            final String oldFormat = mOutputMode.getValue();
            final String newValue = (String)objValue;
            isSameMode = oldFormat.equals(newValue);
            if(isSameMode)
               return true;
            switchDispFormat(newValue, false);

            OnClickListener listener = new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int btn) {
                if (btn == AlertDialog.BUTTON_POSITIVE) {
                    switchDispFormat(newValue, true);
                    dialog.dismiss();
                } else if (btn == AlertDialog.BUTTON_NEGATIVE) {
					switchDispFormat(oldFormat, false);
                }
            }
			};
            if(isSupport){
                String str = getString(com.android.settings.R.string.display_mode_time_out_desc);
                final AlertDialog dialog = new AlertDialog.Builder(this.getActivity())
								.setTitle(com.android.settings.R.string.display_mode_time_out_title)
								.setMessage(String.format(str, Integer.toString(FALLBACK_DISPLAY_MODE_TIMEOUT)))
								.setPositiveButton(com.android.internal.R.string.ok, listener)
								.setNegativeButton(com.android.internal.R.string.cancel, listener)
								.create();
                dialog.show();

                new AsyncTask(){
                @Override
                protected Object doInBackground(Object... arg0) {
                int time = FALLBACK_DISPLAY_MODE_TIMEOUT;
                while(time >= 0 && dialog.isShowing()){
                    publishProgress(time);
                    try{
                        Thread.sleep(1000);
                    }catch(Exception e){}
                        time--;
                    }
                    return null;
                 }
                 @Override
                 protected void onPostExecute(Object result) {
                 super.onPostExecute(result);
                 if (dialog.isShowing()) {
                     switchDispFormat(oldFormat, false);
                     dialog.dismiss();
                  }
                }
                @Override
                protected void onProgressUpdate(Object... values) {
                    super.onProgressUpdate(values);
                    int time = (Integer)values[0];
                    String str = getString(com.android.settings.R.string.display_mode_time_out_desc);
                    dialog.setMessage(String.format(str, Integer.toString(time)));
                }
             }.execute();
           }
        }
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
                mWifiDisplayStatus = (WifiDisplayStatus)intent.getParcelableExtra(
                        DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
                updateWifiDisplaySummary();
            }
        }
    };
   
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

	private void switchDispFormat(String value, boolean save) {
	    try {
	        int format = Integer.parseInt(value, 16);
	        final DisplayManager dm = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
            int dispformat = dm.getDisplayModeFromFormat(format);
            int mCurType = dm.getDisplayOutputType(android.view.Display.TYPE_BUILT_IN);
            if(mCurType == dm.DISPLAY_OUTPUT_TYPE_HDMI)
                isSupport = dm.isSupportHdmiMode(android.view.Display.TYPE_BUILT_IN,dispformat);
            else isSupport = true;
            if(isSupport){
                dm.setDisplayOutput(android.view.Display.TYPE_BUILT_IN, format);
                if(save){
                    Settings.System.putString(getContentResolver(), Settings.System.DISPLAY_OUTPUT_FORMAT, value);
                }
                mOutputMode.setValue(value);
            }else {
                Toast.makeText(getActivity(), com.android.settings.R.string.display_mode_unsupport,Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid display output format!");
        }
	}
}
