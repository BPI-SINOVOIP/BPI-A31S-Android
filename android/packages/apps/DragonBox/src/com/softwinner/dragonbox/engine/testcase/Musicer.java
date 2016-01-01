package com.softwinner.dragonbox.engine.testcase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.logging.Logger;

import com.softwinner.dragonbox.Main;
import com.softwinner.dragonbox.R;
import android.os.Build;
import android.os.SystemClock;
import android.R.integer;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;
import android.widget.Toast;

public class Musicer {
        
    private final static String TAG = "Musicer";	
	private static Musicer instance;
	
	AudioManager mAM;
	TimerTask mTask;
	SoundPool mSoundPool;
	MediaPlayer mMediaPlayer;
	int soundIdLeft;
	int soundIdRight;
	int streamIdLeft;
	int streamIdRight;
	int sdk_int = Build.VERSION.SDK_INT;
	boolean flagLeft = false;
	boolean flagRight = false;
	boolean mSoundLeftPlayed = false;
	boolean mSoundRightPlayed = false;
	boolean mStop = false;
	boolean mPause = false;
	Context mContext;

	public static Musicer getMusicerInstance(Context context){
		if (instance == null){
			instance = new Musicer(context);
		}
		return instance;
	}
	
	private Musicer(Context context) {
		mContext = context;
		mAM = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		ArrayList<String> audioOutputChannels = mAM
				.getActiveAudioDevices(AudioManager.AUDIO_OUTPUT_ACTIVE);

		for (String stc : audioOutputChannels) {
			Log.e(TAG, "=audioOutputChannels=" + "." + stc + ".");
		}
		audioOutputChannels.clear();
		audioOutputChannels.add(AudioManager.AUDIO_NAME_CODEC);
		audioOutputChannels.add(AudioManager.AUDIO_NAME_HDMI);
		audioOutputChannels.add(AudioManager.AUDIO_NAME_SPDIF);

		mAM.setAudioDeviceActive(audioOutputChannels,
				AudioManager.AUDIO_OUTPUT_ACTIVE);
		int maxVolume = mAM.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		mAM.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
		prepare();
		
	}

	private void prepare(){
		if(sdk_int<=17){
			mSoundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
			mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
				@Override
				public void onLoadComplete(SoundPool soundPool,
					int sampleId, int status) {
					Log.v("soundpool", "load  sample-" + sampleId + " complete!");
					if (sampleId == soundIdLeft){
						flagLeft = true;
					}else if(sampleId == soundIdRight){
						flagRight = true;
					}
				}
			});
			soundIdLeft = mSoundPool.load(mContext, R.raw.beatplucker, 1);
			soundIdRight = mSoundPool.load(mContext, R.raw.loveflute, 1);
		}else{
			mMediaPlayer = new MediaPlayer();
		}
	}
	
//	void prepareLeft() {
//
//		soundPoolLeft = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
//		soundIdLeft = soundPoolLeft.load(mContext, R.raw.beatplucker, 1);
//		//FIXME 
////		soundPoolLeft = Main.SOUNDPOOL_LEFT;
////		soundIdLeft = Main.SOUNDPOOL_LEFT_ID;
//		soundPoolLeft
//				.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
//					@Override
//					public void onLoadComplete(SoundPool soundPool,
//							int sampleId, int status) {
//						Log.v("soundpool", "load  left complete!");
//						flagLeft = true;
//					}
//				});
//	}
//
//	void prepareRight() {
//
//		soundPoolRight = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
//		soundIdRight = soundPoolRight.load(mContext, R.raw.loveflute, 1);
////		//FIXME
////		soundPoolRight = Main.SOUNDPOOL_RIGHT;
////		soundIdRight = Main.SOUNDPOOL_RIGHT_ID;
//		soundPoolRight
//				.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
//					@Override
//					public void onLoadComplete(SoundPool soundPool,
//							int sampleId, int status) {
//						Log.v("soundpool", "load  right complete!");
//						flagRight = true;
//					}
//				});
//	}

	private void playSample(int resid,float left,float right){
		
		AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resid);
		try{  
			Log.d(TAG,"playSample " + afd.getFileDescriptor());
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
			mMediaPlayer.setVolume(left, right);
			mMediaPlayer.setLooping(true);
			mMediaPlayer.prepare();
			mMediaPlayer.start();
			afd.close();
		}catch (IllegalArgumentException e){
			Log.e(TAG, "Unable to play audio queue do to exception: " + e.getMessage(), e);
		}catch (IllegalStateException e){
			Log.e(TAG, "Unable to play audio queue do to exception: " + e.getMessage(), e);
		}catch (IOException e){
			Log.e(TAG, "Unable to play audio queue do to exception: " + e.getMessage(), e);
		}
	}
	
	void playLeft() {
		if(sdk_int<=17){
			if (mSoundLeftPlayed){
				mSoundPool.resume(streamIdLeft);
				return;
			}
			Log.v("soundpool open", "soundPoolLeft:" + flagLeft);
			if (flagLeft) {
				streamIdLeft = mSoundPool.play(soundIdLeft, 1.0f, 0.0f, 1, -1, 1.0f);
				mSoundLeftPlayed = true;
			} else {
				Toast.makeText(mContext, R.string.case_spdif_loading,
					Toast.LENGTH_SHORT).show();
			}
		}else{
			Log.d(TAG,"playLeft playSample");
			playSample(R.raw.beatplucker,1.0f,0.0f);
		}
	}

	void playRight() {
		if(sdk_int<=17){
			if (mSoundRightPlayed){
				mSoundPool.resume(streamIdRight);
				return;
			}
			// TODO Auto-generated method stub
			Log.v("soundpool open", "soundPoolRight:" + flagRight);
			if (flagRight) {
				streamIdRight = mSoundPool.play(soundIdRight, 0.0f, 1.0f, 1,-1, 1.0f);
				mSoundRightPlayed = true;
			}else {
				Toast.makeText(mContext, R.string.case_spdif_loading,
					Toast.LENGTH_SHORT).show();
			}
		}else{
				Log.d(TAG,"playRight playSample");
				playSample(R.raw.loveflute,0.0f,1.0f);
		}
	}


	void pause() {
		if(sdk_int<=17){
			mSoundPool.autoPause();
		}else{
			if (mMediaPlayer.isPlaying()) {  
				mMediaPlayer.pause();
			}	
		}
	}

	public void waitUtilReady() {
		if(sdk_int<=17){
			while(flagLeft == false || flagRight == false){
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.v(TAG, "waitting for res! flagLeft = " + flagLeft + "and flagRight" + flagRight);
			}
		}
	}


	public void release(){
		if(sdk_int<=17){
			mSoundPool.release();
		}else{
			if (mMediaPlayer.isPlaying()) {  
				mMediaPlayer.stop();
			}  
		}
		instance = null;
	}
}
