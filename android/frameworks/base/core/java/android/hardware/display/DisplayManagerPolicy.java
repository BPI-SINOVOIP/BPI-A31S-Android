
package android.hardware.display;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

/** @hide */
public class DisplayManagerPolicy {

    private static final String TAG = "DisplayManagerPolicy";

    private Context mContext;
    private DisplayManager mDm;

    /* 当前插入的设备 */
    private ArrayList<Integer> mDisplayDevices = new ArrayList<Integer>();
    /* 每一种设备所使用的默认格式 */
    private final int[] mDispDefFormat;
    /* 每一种设备所使用的默认格式的名称 */
    private final String[] mDispDefNames;
    /* 当前输出的显示格式 */
    private int mCurrentFormat;
    /* 不支持热插拔的设备 */
    private final Integer mUnsupportDectectedDevice;

    /* 在开机结束之后，再去切换模式，保证画面转化的流畅 */
    private boolean mBootCompleted = false;
    private static final String PROPERTIES_DISPLAY_TYPE = "dev.disp_type";
    private static final String PROPERTIES_DISPLAY_MODE = "dev.disp_mode";

    private Handler mH = new Handler(Looper.getMainLooper());

    private BroadcastReceiver mBootCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int percent = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.DISPLAY_AREA_RATIO, 100);
            mDm.setDisplayPercent(Display.TYPE_BUILT_IN, percent);
            int brightness = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.COLOR_BRIGHTNESS, 100);
            mDm.setDisplayBright(Display.TYPE_BUILT_IN, brightness);
            int contrast = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.COLOR_CONTRAST, 100);
            mDm.setDisplayContrast(Display.TYPE_BUILT_IN, contrast);
            int saturation = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.COLOR_SATURATION, 100);
            mDm.setDisplaySaturation(Display.TYPE_BUILT_IN, saturation);
            mBootCompleted = true;
        }
    };

    public DisplayManagerPolicy(Context context) {
        mContext = context;
        mDm = new DisplayManager(context);

        // 注册BOOT completed广播
        IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(mBootCompletedReceiver, filter);

        // 获得刚启动时的显示输出模式
        mCurrentFormat = mDm.getDisplayOutput(Display.TYPE_BUILT_IN);
        Log.v(TAG, "Current format is " + Integer.toHexString(mCurrentFormat));

        // 获得系统配置的显示支持信息
        String defFormats[] = mContext.getResources().getStringArray(
                com.android.internal.R.array.default_display_format);
        mDispDefFormat = new int[defFormats.length];
        for (int i = 0; i < defFormats.length; i++) {
            Log.d(TAG, "Built in display default format " + defFormats[i]);
            mDispDefFormat[i] = Integer.valueOf(defFormats[i], 16);
        }
        mDispDefNames = mContext.getResources().getStringArray(
                com.android.internal.R.array.default_display_format_names);
        // 获得不支持的检测的显示设备列表，一般情况下，当所有可检测设备都被拔出的时候，会切到该设备之上
        mUnsupportDectectedDevice = mContext.getResources().getInteger(
                com.android.internal.R.integer.unsupport_detect_device_type);
    }

    /**
     * @hide 通知设备插拔发生改变
     * @param displaytype 发生改变的设备
     * @param plugedIn true为插入, false为拔出
     */
    public synchronized void notifyDisplayDevicePlugedChanged(int displaytype, boolean pluggedIn) {
        Integer type = new Integer(displaytype);
        if (pluggedIn) {
            mDisplayDevices.remove(mUnsupportDectectedDevice);
            mDisplayDevices.add(type);
        } else {
            mDisplayDevices.remove(type);
            if (mDisplayDevices.size() == 0) {
                Log.v(TAG, "The devices that support detected are plugged out, switch to the unsupport detected device "
                                + mUnsupportDectectedDevice);
                mDisplayDevices.add(mUnsupportDectectedDevice);
            }
        }
        onDisplayTypeChanged(displaytype, pluggedIn);

        makeDisplaySwitch();

    }

    private void onDisplayTypeChanged(int displaytype, boolean pluggedIn) {
        if (displaytype == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI) {
            if (mBootCompleted) {
                int resId = pluggedIn ? com.android.internal.R.string.display_device_plugged_in
                        : com.android.internal.R.string.display_device_plugged_out;
                final String msg = String.format(mContext.getString(resId), "HDMI");
                mH.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }

        }

        if (displaytype == DisplayManager.DISPLAY_OUTPUT_TYPE_TV) {
            // TODO
        }

        if (displaytype == DisplayManager.DISPLAY_OUTPUT_TYPE_VGA) {
            // TODO
        }

        if (displaytype == DisplayManager.DISPLAY_OUTPUT_TYPE_LCD) {
            // TODO
        }

    }

    /** @hide */
    public void showNextDevice() {
        if (mDispDefFormat.length == 0)
            return;

        int curType = mDm.getDisplayOutputType(Display.TYPE_BUILT_IN);
        int i = 0;
        int len = mDispDefFormat.length;
        while (i < len) {
            try {
                int defFormat = mDispDefFormat[i++];
                if (DisplayManager.getDisplayTypeFromFormat(defFormat) == curType)
                    break;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid default display output format ");
            }
        }
        final int j = i % len;
        int format = mDispDefFormat[j];
        mDm.setDisplayOutput(Display.TYPE_BUILT_IN, format);
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.DISPLAY_OUTPUT_FORMAT, Integer.toHexString(format));

        mH.postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, mDispDefNames[j], Toast.LENGTH_LONG).show();
            }
        }, 1000);
    }

    private synchronized void makeDisplaySwitch() {

        // 优先切到数据库中保存的输出模式
        int databaseValue = 0;
        mCurrentFormat = mDm.getDisplayOutput(Display.TYPE_BUILT_IN);
        try {
            databaseValue = Integer.valueOf(Settings.System.getString(
                    mContext.getContentResolver(), Settings.System.DISPLAY_OUTPUT_FORMAT), 16);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid display output format that save in database!");
            databaseValue = mCurrentFormat;
        }
        int databaseType = DisplayManager.getDisplayTypeFromFormat(databaseValue);
        int databaseMode = DisplayManager.getDisplayModeFromFormat(databaseValue);
        if (mDisplayDevices.contains(databaseType) && isFormatSupport(databaseValue)) {
            Log.v(TAG, "Use the user format to display, " + Integer.toHexString(databaseValue));
            SystemProperties.set(PROPERTIES_DISPLAY_TYPE, String.valueOf(databaseType));
            SystemProperties.set(PROPERTIES_DISPLAY_MODE, String.valueOf(databaseMode));
            // Boot completed之前不要切换显示输出模式, SurfaceFlinger会去切模式
            if(mBootCompleted){
                mDm.setDisplayOutput(Display.TYPE_BUILT_IN, databaseValue);
            }
            return;
        }

        // 如果切到数据库中保存的输出模式失败，则使用配置设置的优先级进行输出
        for (int i = 0; i < mDispDefFormat.length; i++) {
            int defaultFormat = 0;
            try {
                defaultFormat = mDispDefFormat[i];
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid default display output format ");
                defaultFormat = mCurrentFormat;
            }
            int supportType = DisplayManager.getDisplayTypeFromFormat(defaultFormat);
            int supportMode = DisplayManager.getDisplayModeFromFormat(defaultFormat);
            if (mDisplayDevices.contains(supportType)) {
                Log.v(TAG, "Use the default format to display, " + Integer.toHexString(defaultFormat));
                SystemProperties.set(PROPERTIES_DISPLAY_TYPE, String.valueOf(supportType));
                SystemProperties.set(PROPERTIES_DISPLAY_MODE, String.valueOf(supportMode));
                // Boot completed之前不要切换显示输出模式, SurfaceFlinger会去切模式
                if(mBootCompleted){
                    mDm.setDisplayOutput(Display.TYPE_BUILT_IN, defaultFormat);
                }
                return;
            }
        }
    }

    private boolean isFormatSupport(int format) {
        if (DisplayManager.getDisplayTypeFromFormat(format) == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI) {
            Log.v(TAG, "check Hdmi Mode");
            return mDm.isSupportHdmiMode(Display.TYPE_BUILT_IN,
                    DisplayManager.getDisplayModeFromFormat(format));
        }else if (DisplayManager.getDisplayTypeFromFormat(format) == DisplayManager.DISPLAY_OUTPUT_TYPE_TV) {
            Log.v(TAG, "check cvbs Mode");
            switch (DisplayManager.getDisplayModeFromFormat(format))
            {
                case DisplayManager.DISPLAY_TVFORMAT_PAL:
                case DisplayManager.DISPLAY_TVFORMAT_NTSC:
                     return true;
                default:
                     return false;
            }
        }
        return false;
    }
}