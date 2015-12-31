package com.softwinner.TvdVideo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.util.Log;

public class TvdVideoActivity extends Activity {
	private static final String TAG = "TvdVideoActivity";

	private MovieViewControl mControl;
	private boolean mBDFolderPlayMode = false;
	private int mScreenWidth;
	private int mScreenHeight;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		String path000 = getIntent().getStringExtra("VideoPath000");
		Log.v(TAG , "___run video___ path000= " + path000);
		setContentView(R.layout.movie_view);
		View rootView = findViewById(R.id.root);
		getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		mBDFolderPlayMode = getIntent().getBooleanExtra(
				MediaStore.EXTRA_BD_FOLDER_PLAY_MODE, false);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		mScreenWidth = dm.widthPixels;
		mScreenHeight = dm.heightPixels;
		mControl = new MovieViewControl(rootView, this, getIntent(),path000,
				mScreenWidth, mScreenHeight) {
			@Override
			public void onCompletion() {
				if (mBDFolderPlayMode) {
					finish();
				} else {
					super.onCompletion();
					if (super.toQuit()) {
						finish();
					}
				}
			}
		};
	}

	@Override
	public void onPause() {
		mControl.onPause();
		super.onPause();
	}

	@Override
	public void onResume() {
		mControl.onResume();
		super.onResume();
	}

	public void onStop() {
		mControl.onStop();
		super.onStop();
	}

	@Override
	public void onDestroy() {
		mControl.onDestroy();
		super.onDestroy();
	}
}
