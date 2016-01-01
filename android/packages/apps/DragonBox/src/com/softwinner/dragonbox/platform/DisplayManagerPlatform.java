package com.softwinner.dragonbox.platform;

import java.lang.reflect.InvocationTargetException;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.media.AudioManager;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.IOException;
/**
 * android.view.DisplayManager在版本4.2中改名为android.view.DisplayManagerAw
 * @author Zengsc
 * 
 */
public class DisplayManagerPlatform {
	private final static String TAG = "DisplayManagerPlatform";
	private Class<?> mClass;
	private Context mContext;
	private Object mManager;
	private String mHardware; 

	public DisplayManagerPlatform(Context context) {
		mContext = context;
		int sdk_int = Build.VERSION.SDK_INT;
		mHardware = Build.HARDWARE;
		Log.d(TAG,"sdk_int = " + sdk_int);
		Log.d(TAG,"DisplayManagerPlatform hardware = " + mHardware);
		if(mHardware.equals("sun4i")){
				mManager = mContext.getSystemService(Context.DISPLAY_SERVICE);
				try {
					mClass = Class.forName("android.view.DisplayManager");
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}		
		}else if(mHardware.equals("sun6i")){
				mManager = mContext.getSystemService(Context.DISPLAY_SERVICE);
				try {
					mClass = Class.forName("android.hardware.display.DisplayManager");
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}		
		}else if(mHardware.equals("sun7i")){
			if (sdk_int <= 16) {
				mManager = mContext.getSystemService("display_aw");
				try {
					mClass = Class.forName("android.view.DisplayManager");
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			} else if (sdk_int >= 17) {
				mManager = mContext.getSystemService("display_aw");
				try {
					mClass = Class.forName("android.view.DisplayManagerAw");
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}			
		}else{
			Log.e(TAG,"platform not support!");
			return;
		}
		if (mClass ==null)
			return;
		try {
			EXTRA_HDMISTATUS = mClass.getField("EXTRA_HDMISTATUS").get(mManager).toString();
			DISPLAY_OUTPUT_TYPE_VGA = mClass.getField("DISPLAY_OUTPUT_TYPE_VGA").getInt(mManager);
			DISPLAY_OUTPUT_TYPE_TV = mClass.getField("DISPLAY_OUTPUT_TYPE_TV").getInt(mManager);
			DISPLAY_OUTPUT_TYPE_HDMI = mClass.getField("DISPLAY_OUTPUT_TYPE_HDMI").getInt(mManager);
			if(mHardware.equals("sun7i")){
				DISPLAY_MODE_SINGLE_FB_GPU = mClass.getField("DISPLAY_MODE_SINGLE_FB_GPU").getInt(mManager);
			}
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	private boolean sun6iGetHdmiHotPlugStatus(){
		boolean plugged = false;
		final String filename = "/sys/class/switch/hdmi/state";
        FileReader reader = null;
        try {
            reader = new FileReader(filename);
            char[] buf = new char[15];
            int n = reader.read(buf);
            if (n > 1) {
               plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
            }
        } catch (IOException ex) {
                Log.e(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
        } catch (NumberFormatException ex) {
                Log.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                }
            }
        }
		Log.d(TAG,"sun6iGetHdmiHotPlugStatus = " + plugged);
		return plugged;
	}
	public int getHdmiHotPlugStatus() {
		int status = 0;
		if(mHardware.equals("sun7i")){
			try {
				status = (Integer) mClass.getDeclaredMethod("getHdmiHotPlugStatus").invoke(mManager);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
			return status;
		}else if(mHardware.equals("sun6i")){
			status = sun6iGetHdmiHotPlugStatus()==true?1:0;
		}else if(mHardware.equals("sun4i")){
			Log.e(TAG,"platform is sun4i!");
		}else{
			Log.e(TAG,"platform not support!");
		}
		return status;
	}
	
	public int getTvHotPlugStatus() {
		int status = 0;
		if(mHardware.equals("sun7i")){
			try {
				status = (Integer) mClass.getDeclaredMethod("getTvHotPlugStatus").invoke(mManager);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}else if(mHardware.equals("sun6i")){
			Log.d(TAG,"getTvHotPlugStatus return 2");
			status = 2; // sun6i not support cvbs test
		}else if(mHardware.equals("sun4i")){
			Log.e(TAG,"platform is sun4i!");
		}else{
			Log.e(TAG,"platform not support!");
		}
		return status;
	}
	
	public int getDisplayOutputType(int mDisplay) {
		int result = 0;
		if(mHardware.equals("sun7i")){
			try {
				result = (Integer) mClass.getDeclaredMethod("getDisplayOutputType", Integer.class).invoke(mManager,
					mDisplay);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}else if(mHardware.equals("sun6i")){
			Log.d(TAG,"getDisplayOutputType");
			try {
				result = (Integer) mClass.getDeclaredMethod("getDisplayOutput", Integer.class).invoke(mManager,
					mDisplay);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	public void setAudioOutputHdmi(){
		AudioManager audioManager =  (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
		if (audioManager == null) {
			Log.e(TAG, "audioManager is null");
			return;
		}
		ArrayList<String> audioOutputChannels = audioManager
				.getActiveAudioDevices(AudioManager.AUDIO_OUTPUT_ACTIVE);
		audioOutputChannels.clear();
		audioOutputChannels.add(AudioManager.AUDIO_NAME_HDMI);
		audioManager.setAudioDeviceActive(audioOutputChannels,
				AudioManager.AUDIO_OUTPUT_ACTIVE);		
	}
	
	public void setAudioOuputCvbs(){
		AudioManager audioManager =  (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
		if (audioManager == null) {
			Log.e(TAG, "audioManager is null");
			return;
		}
		ArrayList<String> audioOutputChannels = audioManager
				.getActiveAudioDevices(AudioManager.AUDIO_OUTPUT_ACTIVE);
		audioOutputChannels.clear();
		audioOutputChannels.add(AudioManager.AUDIO_NAME_CODEC);
		audioManager.setAudioDeviceActive(audioOutputChannels,
				AudioManager.AUDIO_OUTPUT_ACTIVE);			
	}
	
	private void switchDispFormat(String value, boolean save) {
		boolean isSupport = true;
	    try {
	        int format = Integer.parseInt(value, 16);
			Log.d(TAG,"switchDispFormat value = " + value + " save = " + save);
	        final DisplayManager dm = (DisplayManager)mContext.getSystemService(Context.DISPLAY_SERVICE);
            int dispformat = dm.getDisplayModeFromFormat(format);
			Log.d(TAG,"dispformat = " + dispformat + "format = " + format);
            int mCurType = dm.getDisplayOutputType(android.view.Display.TYPE_BUILT_IN);
            dm.setDisplayOutput(android.view.Display.TYPE_BUILT_IN, format);
            if(save){
                    Settings.System.putString(mContext.getContentResolver(), Settings.System.DISPLAY_OUTPUT_FORMAT, value);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid display output format!");
        }
	}
	
	public void sun6iHdmiChangeToCvbs(){
		Log.d(TAG,"sun6iHdmiChangeToCvbs");
		/*
		if (getDisplayOutputType(0) != DISPLAY_OUTPUT_TYPE_TV) {
			try {
				result = (Integer) mClass.getDeclaredMethod("setDisplayOutput",  new  Class[]{ Integer.class ,Integer.class}).invoke(mManager,
					Display.TYPE_BUILT_IN,523);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}*/
		switchDispFormat("20b",false);
		return ;
	}
	
	public void sun6iCvbsChangeToHdmi(){
		Log.d(TAG,"sun6iCvbsChangeToHdmi");
				Log.d(TAG,"sun6iHdmiChangeToCvbs");
		/*
		if(getDisplayOutputType(0) != DISPLAY_OUTPUT_TYPE_HDMI) {
			try {
				result = (Integer) mClass.getDeclaredMethod("setDisplayOutput",  new  Class[]{ Integer.class ,Integer.class})
					.invoke(mManager,Display.TYPE_BUILT_IN,523);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}
		}*/
		switchDispFormat("1028",false);
		return ;
	}
	
	public void sun7iHdmiChangeToCvbs(){
		Log.d(TAG,"sun7iHdmiChangeToCvbs");
		int result;
		if (getDisplayOutputType(0) != DISPLAY_OUTPUT_TYPE_TV) {
			try {
				result = (Integer) mClass.getDeclaredMethod("setDisplayOutputType",  new  Class[]{ Integer.class ,Integer.class,Integer.class})
					.invoke(mManager,0,DISPLAY_OUTPUT_TYPE_HDMI,DISPLAY_TVFORMAT_720P_50HZ);
				result = (Integer) mClass.getDeclaredMethod("setDisplayParameter",  new  Class[]{ Integer.class ,Integer.class,Integer.class})
					.invoke(mManager,0,DISPLAY_OUTPUT_TYPE_HDMI,DISPLAY_TVFORMAT_720P_50HZ);
				result = (Integer) mClass.getDeclaredMethod("setDisplayMode",  Integer.class)
					.invoke(mManager,DISPLAY_MODE_SINGLE_FB_GPU);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}		
		}
	}
	
	public void sun7iCvbsChangeToHdmi(){
		Log.d(TAG,"sun7iCvbsChangeToHdmi");
		int result;
		if (getDisplayOutputType(0) != DISPLAY_OUTPUT_TYPE_HDMI) {
			try {
				result = (Integer) mClass.getDeclaredMethod("setDisplayOutputType",  new  Class[]{ Integer.class ,Integer.class,Integer.class})
					.invoke(mManager,0,DISPLAY_OUTPUT_TYPE_TV,DISPLAY_TVFORMAT_PAL);
				result = (Integer) mClass.getDeclaredMethod("setDisplayParameter",  new  Class[]{ Integer.class ,Integer.class,Integer.class})
					.invoke(mManager,0,DISPLAY_OUTPUT_TYPE_TV,DISPLAY_TVFORMAT_PAL);
				result = (Integer) mClass.getDeclaredMethod("setDisplayMode",  Integer.class)
					.invoke(mManager,DISPLAY_MODE_SINGLE_FB_GPU);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			}		
		}	
	}
	
	public void sun4iHdmiChangeToCvbs(){
		Log.d(TAG,"sun4iHdmiChangeToCvbs");
	}
	
	public void sun4iCvbsChangeToHdmi(){
		Log.d(TAG,"sun4iCvbsChangeToHdmi");
	}
	
	public void hdmiChangeToCvbs(){
		Log.d(TAG,"hdmiChangeToCvbs");
		if(mHardware.equals("sun4i")){
			sun4iHdmiChangeToCvbs();
		}else if(mHardware.equals("sun6i")){
			sun6iHdmiChangeToCvbs();
		}else if(mHardware.equals("sun7i")){
			sun7iHdmiChangeToCvbs();
		}else{
			Log.e(TAG,"platform not support!");
		}
		setAudioOuputCvbs();
	}
	
	public void cvbsChangeToHdmi(){
		Log.d(TAG,"cvbsChangeToHdmi");
		if(mHardware.equals("sun4i")){
			sun4iCvbsChangeToHdmi();
		}else if(mHardware.equals("sun6i")){
			sun6iCvbsChangeToHdmi();
		}else if(mHardware.equals("sun7i")){
			sun7iCvbsChangeToHdmi();
		}else{
			Log.e(TAG,"platform not support!");
		}
		setAudioOutputHdmi();
	}
	/**
	 * Use this method to get the default Display object.
	 * 
	 * @return default Display object
	 */
	public String EXTRA_HDMISTATUS;
	public int DISPLAY_OUTPUT_TYPE_VGA;
	public int DISPLAY_OUTPUT_TYPE_TV;
	public int DISPLAY_OUTPUT_TYPE_HDMI;
	public int DISPLAY_TVFORMAT_720P_50HZ;
	public int DISPLAY_TVFORMAT_PAL;
	public int DISPLAY_MODE_SINGLE_FB_GPU;
}
