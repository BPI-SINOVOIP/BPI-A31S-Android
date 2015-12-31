package com.android.server;

import android.content.Context;
import java.lang.Exception;
import java.lang.Thread;
import android.util.Log;
import java.util.ArrayList;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.net.Uri;
import android.os.Bundle;
import android.hardware.display.DisplayManager;
import android.widget.Toast;
import com.android.internal.R;
import android.app.NotificationManager;
import android.app.Notification;
import android.os.Handler;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.provider.Settings;
import android.os.Message;
import android.media.AudioManager;
import android.media.AudioSystem;
import java.io.FileReader;
import java.io.IOException;

import android.os.SystemProperties;
//import com.android.server.AudioDeviceManagerObserver;
/**
*@hide
*/
public class AudioManagerPolicy extends BroadcastReceiver {

    private Context mCtx;
    private static final String TAG = "AudioManager";
    private AudioManager mAudioManager = null;
    private DisplayManager mDisplayManager = null;
    private static final int AUTOMATIC = 0x00;
    private static final int MANUAL = 0x01;

    public AudioManagerPolicy(Context context) {
        mCtx = context;
        mAudioManager = (AudioManager)mCtx.getSystemService(Context.AUDIO_SERVICE);
        mDisplayManager = (DisplayManager)mCtx.getSystemService(Context.DISPLAY_SERVICE);
        initReceriver();
		initAudioOut();
        initAudioIn();

        mCtx.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ENABLE_PASS_THROUGH),
                true, mContentObserver);
    }

    private void initAudioOut(){
        ArrayList<String> actived = new ArrayList<String>();
        ArrayList<String> mAudioOutputChannels = mAudioManager.getAudioDevices(AudioManager.AUDIO_OUTPUT_TYPE);
        int i = 0;
    	//if the headphone is available,use it first
    	if(AudioDeviceManagerObserver.getInstance(mCtx).getHeadphoneAvailableState() == AudioSystem.DEVICE_STATE_AVAILABLE){
            Log.d(TAG, "headphone is available");
            actived.add(AudioManager.AUDIO_NAME_CODEC);
            mAudioManager.setAudioDeviceActive(actived,AudioManager.AUDIO_OUTPUT_ACTIVE);
        }

        //otherwise check whether the usb audio output device is available?
        else{

            for(i = 0; i < mAudioOutputChannels.size(); i++){
                String device = mAudioOutputChannels.get(i);
                if(device.contains("USB")){
                    actived.add(device);
                    mAudioManager.setAudioDeviceActive(actived, AudioManager.AUDIO_OUTPUT_ACTIVE);
                    break;
                }
            }
        }
        Log.d(TAG, "no usb audio i=" + i + " size=" + mAudioOutputChannels.size());
        //check whether the hdmi is available
        int curType = mDisplayManager.getDisplayOutputType(0);
        if(curType == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI 
        	&& isHdmiPlugin()){
        	Log.d(TAG, "hdmi is available on init.");
        	notifyHdmiAvailable();
        }

    	//otherwise, switch the audio device based on current disp mode and the value save in database.
    	int format = mDisplayManager.getDisplayOutputType(0);
    	if(i == mAudioOutputChannels.size()){
            switchAudioDevice(format);
        }

    	//start observe display mode change
    	observeDispChange(format);
    }

    private void initAudioIn(){
        //if the usb audio input device is avaiable,switch to it
        ArrayList<String> actived = new ArrayList<String>();
        ArrayList<String> mAudioInputChannels = mAudioManager.getAudioDevices(AudioManager.AUDIO_INPUT_TYPE);
        int i = 0;
        for(i = 0; i < mAudioInputChannels.size(); i++){
            String device = mAudioInputChannels.get(i);
            if(device.contains("USB")){
                actived.add(device);
                break;
            }
        }
        //otherwise use codec by default
        if(i == mAudioInputChannels.size()){
            actived.add(AudioManager.AUDIO_NAME_CODEC);
        }
        mAudioManager.setAudioDeviceActive(actived, AudioManager.AUDIO_INPUT_ACTIVE);
    }

    private void initReceriver(){
        //register receiver for audio device plug event
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AUDIO_PLUG_IN_OUT);
        mCtx.registerReceiver(this, filter);
   	}

    private void switchAudioDevice(int dispFormat){
        String name = AudioManager.AUDIO_NAME_CODEC;
        if(dispFormat == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI){
            name = AudioManager.AUDIO_NAME_HDMI;
        }

        AudioManager mAudioManager =
            (AudioManager)mCtx.getSystemService(Context.AUDIO_SERVICE);
        ArrayList<String> actived = new ArrayList<String>();
        String audioOutputChannelName = Settings.System.getString(mCtx.getContentResolver(),
                Settings.System.AUDIO_OUTPUT_CHANNEL);
        Log.d(TAG, "save audio is " + audioOutputChannelName);
        ArrayList<String> audioOutputChannels = new ArrayList<String>();
        if (audioOutputChannelName != null) {
            String[] audioList = audioOutputChannelName.split(",");
            for (String audio : audioList) {
                if (!"".equals(audio)) {
                    audioOutputChannels.add(audio);
                }
            }
        }
        switch(getPolicy()){
        case AUTOMATIC:
        	if (audioOutputChannels.contains(AudioManager.AUDIO_NAME_SPDIF) && !audioOutputChannels.contains(name)) {
                mAudioManager.setAudioDeviceActive(audioOutputChannels, AudioManager.AUDIO_OUTPUT_ACTIVE);
            } else {
                actived.add(name);
                mAudioManager.setAudioDeviceActive(actived, AudioManager.AUDIO_OUTPUT_ACTIVE);
            }
        	break;
        case MANUAL:
            if (!audioOutputChannels.contains(name))
                audioOutputChannels.add(name);
            Log.d(TAG, "audio manual is " + audioOutputChannels);
            mAudioManager.setAudioDeviceActive(audioOutputChannels, AudioManager.AUDIO_OUTPUT_ACTIVE);
            break;
        }
    }

    private void observeDispChange(final int defType){
        Thread thread = new Thread(new Runnable(){
            private int curType = defType;
            private int preType = defType;

            @Override
            public void run(){
                while(true){
                    DisplayManager dmg = (DisplayManager)mCtx.getSystemService(Context.DISPLAY_SERVICE);
                    try{
                        curType = dmg.getDisplayOutputType(0);
                    }catch(Exception e){
                        curType = DisplayManager.DISPLAY_OUTPUT_TYPE_NONE;
                    }
                    if(preType != curType){
                    	if(curType == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI && 
                    		isHdmiPlugin()){
                    		notifyHdmiAvailable();	
                    	}
                        notifyDispChange(curType);
                        preType = curType;
                    }
                    try
					{
						Thread.currentThread().sleep(200);
					}
					catch(Exception e) {};
                }
            }
        });
        thread.start();
    }

    private void notifyDispChange(int curFormat){
        switchAudioDevice(curFormat);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if(bundle == null){
            Log.d(TAG, "bundle is null");
            return;
        }
        final int state = bundle.getInt(AudioDeviceManagerObserver.AUDIO_STATE);
        final String name = bundle.getString(AudioDeviceManagerObserver.AUDIO_NAME);
        final int type = bundle.getInt(AudioDeviceManagerObserver.AUDIO_TYPE);
        final String extra = bundle.getString(AudioDeviceManagerObserver.EXTRA_MNG);
        Log.d(TAG, "On Audio device plug in/out receive,name=" + name + " type=" + type + " state=" + state + " extra=" + extra);

        if(name == null){
            Log.d(TAG, "audio name is null");
            return;
        }
		if(name.equals(AudioManager.AUDIO_NAME_HDMI)){
			if(state == AudioDeviceManagerObserver.PLUG_IN &&
				mDisplayManager.getDisplayOutputType(0) == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI){
				notifyHdmiAvailable();
			}
		}
        if(name.equals(AudioManager.AUDIO_NAME_HDMI)
            || (name.equals(AudioManager.AUDIO_NAME_CODEC) && ((extra == null) || (extra != null) && !extra.equals(AudioDeviceManagerObserver.H2W_DEV)))
            || (name.equals(AudioManager.AUDIO_NAME_SPDIF))){

        }else{
            handleExternalDevice(name, type, state, extra);
        }
    }

    private static final int AUDIO_OUT_NOTIFY = 20130815;
    private static final int AUDIO_IN_NOTIFY = 20130816;
    private boolean headPhoneConnected = false;
    private void toastMessage(String message){
        Toast.makeText(mCtx, message, Toast.LENGTH_SHORT).show();
    }
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            String message = (String)msg.obj;
            toastMessage(message);
        }
    };

    /* 监听pass through的设置变化 */
    private ContentObserver mContentObserver = new ContentObserver(mHandler){

        @Override
            public void onChange(boolean selfChange, Uri uri) {
                ArrayList<String> list = mAudioManager
                    .getActiveAudioDevices(AudioManager.AUDIO_OUTPUT_ACTIVE);
                mAudioManager.setAudioDeviceActive(list, AudioManager.AUDIO_OUTPUT_ACTIVE);
            }

    };

    private void handleToastMessage(String message){
        if(mHandler == null) return;
        Message mng = mHandler.obtainMessage();
        mng.obj = message;
        mHandler.sendMessage(mng);
    }
    private void toastPlugOutNotification(String title,String mng, int id){

        NotificationManager notificationManager = (NotificationManager) mCtx
            .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(id);
        handleToastMessage(mng);
    }

    private void toastPlugInNotification(String title, int id){
        Notification notification = new Notification(com.android.internal.R.drawable.stat_sys_data_usb,
                title,System.currentTimeMillis());
        String contentTitle = title;
        String contextText = title;
        notification.setLatestEventInfo(mCtx, contentTitle, contextText, null);

        notification.defaults &= ~Notification.DEFAULT_SOUND;
        notification.flags = Notification.FLAG_AUTO_CANCEL;

        NotificationManager notificationManager = (NotificationManager) mCtx
            .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, notification);

        handleToastMessage(title);
    }
    private void handleExternalDevice(final String name, final int type, final int state, final String extra){
        //handle device:headphone(if has), or usb, they have higner privileges then internal device(hdmi,codec,spdif)

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                ArrayList<String> mAudioOutputChannels = mAudioManager.getAudioDevices(AudioManager.AUDIO_OUTPUT_TYPE);
                ArrayList<String> mAudioInputChannels = mAudioManager.getAudioDevices(AudioManager.AUDIO_INPUT_TYPE);

                String title = null;
                String message = null;
                switch(state){
                case AudioDeviceManagerObserver.PLUG_IN:
                    switch(type){
                    case AudioDeviceManagerObserver.AUDIO_INPUT_TYPE:
                        //auto change to this audio-in channel
                        Log.d(TAG, "audio input plug in");
                        ArrayList<String> audio_in = new ArrayList<String>();
                        audio_in.add(name);
                        mAudioManager.setAudioDeviceActive(audio_in, AudioManager.AUDIO_INPUT_ACTIVE);

                        title = mCtx.getResources().getString(R.string.usb_audio_in_plug_in_title);
                        message = mCtx.getResources().getString(R.string.usb_audio_plug_in_message);
                        toastPlugInNotification(title, AUDIO_IN_NOTIFY);
                        break;
                    case AudioDeviceManagerObserver.AUDIO_OUTPUT_TYPE:
                        Log.d(TAG, "audio output plug in");
                        //update devices state
                        if(extra != null && extra.equals(AudioDeviceManagerObserver.H2W_DEV)){
                            headPhoneConnected = true;
                        }

                        //switch audio output
                        ArrayList<String> audio_out = new ArrayList<String>();
                        if(extra != null && extra.equals(AudioDeviceManagerObserver.H2W_DEV)){
                            audio_out.add(name);
                            mAudioManager.setAudioDeviceActive(audio_out, AudioManager.AUDIO_OUTPUT_ACTIVE);
                            title = mCtx.getResources().getString(R.string.headphone_plug_in_title);
                            message = mCtx.getResources().getString(R.string.headphone_plug_in_message);
                        }else if(name.contains("USB")){
                            audio_out.add(name);
                            mAudioManager.setAudioDeviceActive(audio_out, AudioManager.AUDIO_OUTPUT_ACTIVE);
                            title = mCtx.getResources().getString(R.string.usb_audio_out_plug_in_title);
                            message = mCtx.getResources().getString(R.string.usb_audio_plug_in_message);
                        }
                        toastPlugInNotification(title, AUDIO_OUT_NOTIFY);
                        break;
                    }
                    break;
                case AudioDeviceManagerObserver.PLUG_OUT:
                    switch(type){
                    case AudioDeviceManagerObserver.AUDIO_INPUT_TYPE:
                        Log.d(TAG, "audio input plug out");
                        title = mCtx.getResources().getString(R.string.usb_audio_in_plug_out_title);
                        message = mCtx.getResources().getString(R.string.usb_audio_plug_out_message);
                        ArrayList<String> actived = mAudioManager.getActiveAudioDevices(AudioManager.AUDIO_INPUT_ACTIVE);
                        if(actived == null || actived.size() == 0 || actived.contains(name)){
                            ArrayList<String> ilist = new ArrayList<String>();
                            for(String dev:mAudioInputChannels){
                                if(dev.contains("USB")){
                                    ilist.add(dev);
                                    break;
                                }
                            }
                            if(ilist.size() == 0){
                                ilist.add(AudioManager.AUDIO_NAME_CODEC);
                            }
                            mAudioManager.setAudioDeviceActive(ilist, AudioManager.AUDIO_INPUT_ACTIVE);
                            toastPlugOutNotification(title, message, AUDIO_IN_NOTIFY);
                        }
                        else if(!actived.contains("USB")){
                            ArrayList<String> ilist = new ArrayList<String>();
                            ilist.add(AudioManager.AUDIO_NAME_CODEC);
                            mAudioManager.setAudioDeviceActive(ilist, AudioManager.AUDIO_INPUT_ACTIVE);
                        }
                        break;
                    case AudioDeviceManagerObserver.AUDIO_OUTPUT_TYPE:
                        ArrayList<String> olist = new ArrayList<String>();
                        Log.d(TAG, "audio output plug out");
                        if(extra != null && extra.equals(AudioDeviceManagerObserver.H2W_DEV)){
                            headPhoneConnected = false;
                            for(String dev:mAudioOutputChannels){
                                if(dev.contains("USB")){
                                    olist.add(dev);
                                    mAudioManager.setAudioDeviceActive(olist,AudioManager.AUDIO_OUTPUT_ACTIVE);
                                    break;
                                }
                            }
                            if(olist.size() == 0){
                                switchAudioDevice(mDisplayManager.getDisplayOutputType(0));
                            }
                            
                            title = mCtx.getResources().getString(R.string.headphone_plug_out_title);
                            message = mCtx.getResources().getString(R.string.headphone_plug_out_message);
                        }else{
                            if(headPhoneConnected){
                                olist.add(AudioManager.AUDIO_NAME_CODEC);
                            }else{
                                for(String dev:mAudioOutputChannels){
                                    if(dev.contains("USB")){
                                        olist.add(dev);
                                        mAudioManager.setAudioDeviceActive(olist,AudioManager.AUDIO_OUTPUT_ACTIVE);
                                        break;
                                    }
                                }
                                if(olist.size() == 0){
                                    switchAudioDevice(mDisplayManager.getDisplayOutputType(0));
                                }
                            }
                            
                            title = mCtx.getResources().getString(R.string.usb_audio_out_plug_out_title);
                            message = mCtx.getResources().getString(R.string.usb_audio_plug_out_message);
                        }
                        toastPlugOutNotification(title, message, AUDIO_OUT_NOTIFY);
                        break;
                    }
                    break;
                }
            }
        });
        thread.start();

    }
    
    private void notifyHdmiAvailable(){
    	Log.d(TAG, "hdmi is available");
    	AudioManager.setHdmiAvailable(true);
    	try{
    		Thread.currentThread().sleep(100);	
    	}catch(Exception e){};
    	
    	ArrayList<String> list = mAudioManager.getActiveAudioDevices(AudioManager.AUDIO_OUTPUT_ACTIVE);
    	if(AudioManager.getHdmiExpected()){
    		mAudioManager.setAudioDeviceActive(list, AudioManager.AUDIO_OUTPUT_ACTIVE);
    	}
	}
	
	private boolean isHdmiPlugin() {
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
            Log.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
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
        return plugged;
    }
	
	private int getPolicy(){
		int ret = Settings.System.getInt(mCtx.getContentResolver(), Settings.System.AUDIO_MANAGE_POLICY, AUTOMATIC);
		Log.d(TAG, "get AUDIO_MANAGE_POLICY is " + ret);
		return ret;
	}
}
