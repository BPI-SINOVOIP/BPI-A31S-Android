package com.softwinner.dragonbox.engine.testcase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.softwinner.dragonbox.R;
import com.softwinner.dragonbox.engine.BaseCase;
import com.softwinner.dragonbox.xml.Node;

/**
 * spdif测试
 * 
 * @author AW_maizirong
 * 
 */
public class CaseSpdif extends BaseCase {
        
        private static final String TAG = "CaseSpdif";
	private Button mRedo;

	private Button mButtonLeft;
	private Button mButtonRight;

	private CheckBox checkBoxTrueLeft;
	private CheckBox checkBoxFalseLeft;

	private CheckBox checkBoxTrueRight;
	private CheckBox checkBoxFalseRight;

	private boolean soundStateLeft = false;
	private boolean soundStateRight = false;

	private AudioManager mAM;

	private TimerTask mTask;

	private SoundPool soundPool;
	private int soundId;
	private boolean flag = false;

	private int exitNum = 0;
	String fileName;
	File file;

	@Override
	protected void finalize() throws Throwable {

		super.finalize();
	}

	private void playLeft() {
		// TODO Auto-generated method stub
		if (flag) {
			soundPool.play(soundId, 1.0f, 0.0f, 1, 2, 1.0f);
		} else {
			Toast.makeText(mContext, R.string.case_spdif_loading,
					Toast.LENGTH_SHORT).show();
		}
	}

	private void playRight() {
		// TODO Auto-generated method stub
		if (flag) {
			soundPool.play(soundId, 0.0f, 1.0f, 1, 2, 1.0f);
		} else {
			Toast.makeText(mContext, R.string.case_spdif_loading,
					Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected void onInitialize(Node attr) {
		fileName = "/mnt/private/factory_pass";
		file = new File(fileName);
		setView(R.layout.case_speaker);

		mAM = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

		soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
		soundId = soundPool.load(mContext, R.raw.beatplucker, 1);
		soundPool
				.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
					@Override
					public void onLoadComplete(SoundPool soundPool,
							int sampleId, int status) {
						flag = true;
					}
				});
		mButtonLeft = (Button) getView().findViewById(R.id.button_left);
		mButtonLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Toast.makeText(mContext, R.string.case_spdif_left,
						Toast.LENGTH_SHORT).show();
				checkBoxTrueLeft.setVisibility(0);
				checkBoxFalseLeft.setVisibility(0);

				checkBoxTrueLeft.setChecked(false);
				checkBoxFalseLeft.setChecked(false);

				checkBoxTrueLeft.setClickable(true);
				checkBoxFalseLeft.setClickable(true);

				playLeft();
			}

		});

		mButtonRight = (Button) getView().findViewById(R.id.button_right);
		mButtonRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Toast.makeText(mContext, R.string.case_spdif_right,
						Toast.LENGTH_SHORT).show();
				checkBoxTrueRight.setVisibility(0);
				checkBoxFalseRight.setVisibility(0);

				checkBoxTrueRight.setChecked(false);
				checkBoxFalseRight.setChecked(false);

				checkBoxTrueRight.setClickable(true);
				checkBoxFalseRight.setClickable(true);

				playRight();
			}
		});
		checkBoxTrueLeft = (CheckBox) getView().findViewById(
				R.id.checkBoxTrue_left);

		checkBoxTrueLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				soundStateLeft = true;
				checkBoxFalseLeft.setChecked(false);
				checkBoxTrueLeft.setClickable(false);
				checkBoxFalseLeft.setClickable(false);
				checkBoxFalseLeft.setVisibility(4);

			}
		});

		checkBoxTrueRight = (CheckBox) getView().findViewById(
				R.id.checkBoxTrue_right);
		checkBoxTrueRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				soundStateRight = true;
				checkBoxFalseRight.setChecked(false);
				checkBoxTrueRight.setClickable(false);
				checkBoxFalseRight.setClickable(false);
				checkBoxFalseRight.setVisibility(4);

			}
		});

		checkBoxFalseLeft = (CheckBox) getView().findViewById(
				R.id.checkBoxFalse_left);
		checkBoxFalseLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				soundStateLeft = false;
				checkBoxTrueLeft.setChecked(false);
				checkBoxFalseLeft.setClickable(false);
				checkBoxTrueLeft.setClickable(false);
				checkBoxTrueLeft.setVisibility(4);
			}
		});

		checkBoxFalseRight = (CheckBox) getView().findViewById(
				R.id.checkBoxFalse_right);
		checkBoxFalseRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				soundStateRight = false;
				checkBoxTrueRight.setChecked(false);
				checkBoxFalseRight.setClickable(false);
				checkBoxTrueRight.setClickable(false);
				checkBoxTrueRight.setVisibility(4);
			}
		});

		mRedo = (Button) getView().findViewById(R.id.redo);
		mRedo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (file.exists()) {
					exitNum++;
					Log.e(TAG, "======mRedo===exitNum========"
							+ exitNum);
				}

				soundStateLeft = false;
				soundStateRight = false;

				checkBoxTrueLeft.setChecked(false);
				checkBoxFalseLeft.setChecked(false);

				checkBoxTrueLeft.setClickable(true);
				checkBoxFalseLeft.setClickable(true);

				checkBoxTrueLeft.setVisibility(4);
				checkBoxFalseLeft.setVisibility(4);

				checkBoxTrueRight.setChecked(false);
				checkBoxFalseRight.setChecked(false);

				checkBoxTrueRight.setClickable(true);
				checkBoxFalseRight.setClickable(true);

				checkBoxTrueRight.setVisibility(4);
				checkBoxFalseRight.setVisibility(4);

			}
		});
		// 保持在同一线程
		mEngine.getWorkerHandler().post(new Runnable() {
			@Override
			public void run() {

			}
		});

		setName(R.string.case_speaker_name);

		checkBoxTrueLeft.setVisibility(4);
		checkBoxFalseLeft.setVisibility(4);
		checkBoxTrueRight.setVisibility(4);
		checkBoxFalseRight.setVisibility(4);
	}

	@Override
	protected boolean onCaseStarted() {
		// ArrayList<String> list =
		// mAM.getAudioDevices(AudioManager.AUDIO_OUTPUT_TYPE);

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

		// 保持在同一线程
		mEngine.getWorkerHandler().post(new Runnable() {
			@Override
			public void run() {

			}
		});

		mTask = new TimerTask() {
			@Override
			public void run() {
				((Activity) mContext).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (exitNum > 15) {
							Log.e(TAG, "=======exitNum > 15======");
							// System.exit(0);

							if (file.exists()) {
								Log.e(TAG, "=========exit========");
								throw new RuntimeException(
										"Artificial Crash By User.");
							}
						}

						if (soundStateLeft && soundStateRight) {
							setPassable(true);
						} else {
							setPassable(false);
						}
					}
				});
			}
		};
		Timer timer = new Timer();
		timer.schedule(mTask, 100, 500);

		return false;
	}

	@Override
	protected void onCaseFinished() {

		// 保持在同一线程
		mEngine.getWorkerHandler().post(new Runnable() {
			@Override
			public void run() {
				if (soundPool != null) {
					soundPool.release();
					soundPool = null;
				}
			}
		});
		// mMediaPlayer.setRawDataMode(MediaPlayer.AUDIO_DATA_MODE_HDMI_RAW);
	}

	@Override
	protected void onRelease() {
		// 保持在同一线程
		mEngine.getWorkerHandler().post(new Runnable() {
			@Override
			public void run() {

			}
		});
	}

	@Override
	protected void onPassableChange() {
		super.onPassableChange();
		if (getPassable()) {
			getView().findViewById(R.id.spdif_name).setBackgroundColor(
					Color.GREEN);
		} else {
			getView().findViewById(R.id.spdif_name).setBackgroundColor(
					Color.RED);
		}
	}

}
