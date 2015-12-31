
package com.android.settings;

/*
 ************************************************************************************
 *                                    Android Settings

 *                       (c) Copyright 2006-2010, huanglong Allwinner 
 *                                 All Rights Reserved
 *
 * File       : SaturationPreference.java
 * By         : huanglong
 * Version    : v1.0
 * Date       : 2011-9-5 16:20:00
 * Description: Add the Saturation settings to Display.
 * Update     : date                author      version     notes
 *           
 ************************************************************************************
 */

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.preference.SeekBarDialogPreference;
import android.preference.SeekBarPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import java.lang.Integer;
import android.os.SystemProperties;
import com.softwinner.SecureFile;
import java.lang.Integer;
import java.lang.String;
import java.lang.Exception;

public class DisplayPercentPreference extends SeekBarDialogPreference implements
        SeekBar.OnSeekBarChangeListener {

    private SeekBar mSeekBar;

    private int OldValue;

    private int MAXIMUM_VALUE = 100;
    private int MINIMUM_VALUE = 90;
    private String DISPLAY_AREA_RADIO = "display.area_radio";
    private String TAG = "DisplayPercentPreference";
    private DisplayManager mDisplayManager;

    public DisplayPercentPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDisplayManager = (DisplayManager)
                context.getSystemService(Context.DISPLAY_SERVICE);
        setDialogLayoutResource(R.layout.preference_dialog_saturation);
        setDialogIcon(R.drawable.ic_settings_saturation);
    }

    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSeekBar = getSeekBar(view);
        mSeekBar.setMax(MAXIMUM_VALUE - MINIMUM_VALUE);
        try{
            OldValue = getSysInt();
        }catch(SettingNotFoundException snfe){
            OldValue = MAXIMUM_VALUE;
        }
        mSeekBar.setProgress(OldValue - MINIMUM_VALUE);
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromTouch) {
        setDisplayPercent(progress + MINIMUM_VALUE);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            putSysInt(mSeekBar.getProgress() + MINIMUM_VALUE);
        } else {
            setDisplayPercent(OldValue);
        }
        super.onDialogClosed(positiveResult);
    }

    private int getSysInt() throws SettingNotFoundException{
        return Settings.System.getInt(getContext().getContentResolver(),
                   Settings.System.DISPLAY_AREA_RATIO, MINIMUM_VALUE);
    }

    private boolean putSysInt(int value) {
        return Settings.System.putInt(getContext().getContentResolver(),
                Settings.System.DISPLAY_AREA_RATIO, value);
    }

    private void setDisplayPercent(int value) {
        mDisplayManager.setDisplayPercent(android.view.Display.TYPE_BUILT_IN, value);
    }

    /* implements method in SeekBar.OnSeekBarChangeListener */
    @Override
    public void onStartTrackingTouch(SeekBar arg0) {
        // NA

    }

    /* implements method in SeekBar.OnSeekBarChangeListener */
    @Override
    public void onStopTrackingTouch(SeekBar arg0) {
        // NA

    }

}
