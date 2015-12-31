/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.softwinner.TvdVideo;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.SubInfo;
import android.media.MediaPlayer.TrackInfo;
import android.media.MediaPlayer.TrackInfoVendor;
import android.media.TimedText;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.hardware.display.DisplayManager;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Display;

import com.softwinner.TvdVideo.JumpView.OnTimeConfirmListener;

public class MovieViewControl implements MediaPlayer.OnErrorListener,
		MediaPlayer.OnCompletionListener, MediaController.OnListDataChanged,
		MediaPlayer.OnTimedTextListener, VideoView.OnSubFocusItems,
		MediaPlayer.OnInfoListener {

	@SuppressWarnings("unused")
	private static final String TAG = "MovieViewControl";

	private static final String STORE_NAME = "VideoSetting";
	private static final String EDITOR_SUBGATE = "MovieViewControl.SUBGATE";
	private static final String EDITOR_SUBSELECT = "MovieViewControl.SUBSELECT";
	private static final String EDITOR_SUBCHARSET = "MovieViewControl.SUBCHARSET";
	private static final String EDITOR_SUBCOLOR = "MovieViewControl.SUBCOLOR";
	private static final String EDITOR_SUBCHARSIZE = "MovieViewControl.SUBCHARSIZE";
	private static final String EDITOR_SUBOFFSET = "MovieViewControl.SUBOFFSET";
	private static final String EDITOR_SUBDELAY = "MovieViewControl.SUBDELAY";
	private static final String EDITOR_ZOOM = "MovieViewControl.MODEZOOM";
	private static final String EDITOR_MODE3D = "MovieViewControl.MODE3D";
	private static final String EDITOR_MODEREPEAT = "MovieViewControl.MODEREPEAT";
	private static final String EDITOR_TRACK = "MovieViewControl.TRACK";

	private static final int TEXTVIEW_UPDATE = 2;

	private static final String[] SUB_EXTS = new String[] {
			".idx",
			".sub", // .idx
			".srt", ".smi", ".rt", ".txt", ".ssa", ".aqt", ".jss", ".js",
			".ass", ".vsf", ".tts", ".stl", ".zeg", ".ovr", ".dks", ".lrc",
			".pan", ".sbt", ".vkt", ".pjs", ".mpl", ".scr", ".psb", ".asc",
			".rtf", ".s2k", ".sst", ".son", ".ssts" };

	private static final String[] MEDIA_MIMETYPE = new String[] {
			"application/idx-sub",
			"application/sub", // .idx
			"application/x-subrip", "text/smi", "text/rt", "text/txt",
			"text/ssa", "text/aqt", "text/jss", "text/js", "text/ass",
			"text/vsf", "text/tts", "text/stl", "text/zeg", "text/ovr",
			"text/dks", "text/lrc", "text/pan", "text/sbt", "text/vkt",
			"text/pjs", "text/mpl", "text/scr", "text/psb", "text/asc",
			"text/rtf", "text/s2k", "text/sst", "text/son", "text/ssts" };
	private static final int DISMISS_DIALOG = 0x10;
	private static final int DISMISS_DELAY = 5000; // 5 s

	private BookmarkService mBookmarkService;
	private JumpView mJumpView;
	private final VideoView mVideoView;
	private final View mProgressView;
	private Dialog mReplayDialog;
	private Message mDismissMsg;
	private Uri mUri;
	private Context mContext;
	DisplayManager displayManager;
	// add by aw:lisidong
	private boolean mIsDoubleStream = false;
	private boolean mToQuit = false;
	private boolean mOnPause, mFinishOnCompletion;
	private String mPlayListType;
	private final SharedPreferences sp;
	private final SharedPreferences.Editor editor;
	private Resources mRes;
	private int mCurrentIndex = 0, mRepeatMode = 0;
	private int mVideoPosition = 0;
	private ArrayList<String> mPlayList;
	private MediaController mMediaController;
	private static MovieViewControl mInstance = null;
	private Toast mJumpToast;
	private MyToast mSubtitleToast, mAudioToast, mPrevToast, mNextToast,
			mStopToast, mRepeatToast;
	private String[] mTransformSubtitle, mTransformTrack;

	private BroadcastReceiver mBroadcastReceiver;

	private ArrayList<String> mSrtList = new ArrayList<String>();
	private ArrayList<String> mMediaTypeList = new ArrayList<String>();

	// add for handling subtitle
	private ImageView mImageview;
	private Bitmap mBitmap;
	private int mBitmapSubtitleFlag = 0;
	private int mScreenWidth;
	private int mScreenHeight;

	private SubTitleInfoOps subTitleInfoOps = new SubTitleInfoOps();
	private SubTitleInfo mSubTitleInfo;
	private TextViewInfo[] mTextViewInfo = new TextViewInfo[10];
	private final Handler mSubTitleHandler = new MainHandler(); // handle text
																// subtitle

	// SAVE sub and track, add by maizirong
	private int mCurrentTrackSave = -1;
	private int mCurrentSubSave = -1;
	// SAVE subcolor and subfontsize, add by maizirong
	private int mCurrentSubColorSave = -1;
	private int mCurrentSubSizeSave = 32;
	private boolean switchSubOn = true;

	private String realPath;

	Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			if (msg.what == DISMISS_DIALOG && mReplayDialog.isShowing()) {
				mReplayDialog.dismiss();
			}
		}

	};

	Runnable mMode3DRunnable = null;
	Runnable mRepeatRunnable = null;
	Runnable mTrackRunnable = null;
	Runnable mZoomRunnable = null;
	Runnable mSubsetRunnable = null;
	Runnable mPlayingChecker = new Runnable() {
		public void run() {
			if (mVideoView.isPlaying()) {
				mProgressView.setVisibility(View.GONE);
			} else {
				mHandler.postDelayed(mPlayingChecker, 250);
			}
		}
	};

	Runnable mPlayRunnable = new Runnable() {
		@Override
		public void run() {
			setImageButtonListener();
			mVideoView.setVideoURI(mUri);
			Log.v(TAG,
					"-------mVideoView.setVideoURI(mUri)-----" + mUri.getPath());
			mMediaController.setFilePathTextView(mUri.getPath());
			mVideoView.requestFocus();
			mVideoView.start();

			mHandler.removeCallbacks(mPlayRunnable);
		}
	};

	private final Runnable mUpdateImageView = new Runnable() {
		@Override
		public void run() {
			mImageview.setImageBitmap(mBitmap);
		}
	};

	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case TEXTVIEW_UPDATE: {
				for (int i = 0; i < mTextViewInfo.length; i++) {
					mTextViewInfo[i].textView.setText(mTextViewInfo[i].text);
				}
				break;
			}
			}
		}
	}

	class MyToast extends Toast {
		View mNextView;
		int mDuration;

		public MyToast(Context context) {
			super(context);
			// TODO Auto-generated constructor stub
			LayoutInflater inflate = (LayoutInflater) mContext
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mNextView = inflate.inflate(R.layout.transient_notification, null);
			mDuration = MyToast.LENGTH_SHORT;
			super.setView(mNextView);
			super.setDuration(mDuration);
			super.setGravity(Gravity.LEFT | Gravity.TOP, 100, 0);
		}

		@Override
		public void onHide() {
			// TODO Auto-generated method stub
			super.onHide();

			if (mSubtitleToast.isShowing() && mTransformSubtitle != null) {
				String newSub = (String) ((TextView) mSubtitleToast.getView()
						.findViewById(R.id.message)).getText();
				for (int i = 0; i < mTransformSubtitle.length; i++) {
					if (newSub.equals(mTransformSubtitle[i])) {
						mVideoView.switchSub(i);
						break;
					}
				}
			} else if (mAudioToast.isShowing() && mTransformTrack != null) {
				String newTrack = (String) ((TextView) mAudioToast.getView()
						.findViewById(R.id.message)).getText();
				for (int i = 0; i < mTransformTrack.length; i++) {
					if (newTrack.equals(mTransformTrack[i])) {
						mVideoView.switchTrack(i);
						break;
					}
				}
			}
		}
	}

	public static String formatDuration(final Context context, int durationMs) {
		int duration = durationMs / 1000;
		int h = duration / 3600;
		int m = (duration - h * 3600) / 60;
		int s = duration - (h * 3600 + m * 60);
		String durationValue;
		if (h == 0) {
			durationValue = String.format("%02d:%02d", m, s);
		} else {
			durationValue = String.format("%d:%02d:%02d", h, m, s);
		}
		return durationValue;
	}

	public static MovieViewControl getInstance() {
		return mInstance;
	}

	public void resetAllTextView() {
		Log.v(TAG, "00-----resetAllTextView-----resetAllTextView-----");
		int i = 0;
		for (i = 0; i < 10; i++) {
			mTextViewInfo[i].text = "";
			mTextViewInfo[i].textView.setText(mTextViewInfo[i].text);
			mTextViewInfo[i].used = false;
		}
		mImageview.setImageBitmap(null);
		Log.v(TAG, "11-----resetAllTextView-----resetAllTextView-----");
	}

	public MovieViewControl(View rootView, Context context, Intent intent,
			String path000, int width, int height) {
		realPath = path000;
		mInstance = this;
		mVideoView = (VideoView) rootView.findViewById(R.id.surface_view);

		// init subtitle textview
		mTextViewInfo[0] = new TextViewInfo();
		mTextViewInfo[1] = new TextViewInfo();
		mTextViewInfo[2] = new TextViewInfo();
		mTextViewInfo[3] = new TextViewInfo();
		mTextViewInfo[4] = new TextViewInfo();
		mTextViewInfo[5] = new TextViewInfo();
		mTextViewInfo[6] = new TextViewInfo();
		mTextViewInfo[7] = new TextViewInfo();
		mTextViewInfo[8] = new TextViewInfo();
		mTextViewInfo[9] = new TextViewInfo();

		mTextViewInfo[0].textView = (TextView) rootView
				.findViewById(R.id.text_view_0);
		mTextViewInfo[0].used = false;
		mTextViewInfo[1].textView = (TextView) rootView
				.findViewById(R.id.text_view_1);
		mTextViewInfo[1].used = false;
		mTextViewInfo[2].textView = (TextView) rootView
				.findViewById(R.id.text_view_2);
		mTextViewInfo[2].used = false;
		mTextViewInfo[3].textView = (TextView) rootView
				.findViewById(R.id.text_view_3);
		mTextViewInfo[3].used = false;
		mTextViewInfo[4].textView = (TextView) rootView
				.findViewById(R.id.text_view_4);
		mTextViewInfo[4].used = false;
		mTextViewInfo[5].textView = (TextView) rootView
				.findViewById(R.id.text_view_5);
		mTextViewInfo[5].used = false;
		mTextViewInfo[6].textView = (TextView) rootView
				.findViewById(R.id.text_view_6);
		mTextViewInfo[6].used = false;
		mTextViewInfo[7].textView = (TextView) rootView
				.findViewById(R.id.text_view_7);
		mTextViewInfo[7].used = false;
		mTextViewInfo[8].textView = (TextView) rootView
				.findViewById(R.id.text_view_8);
		mTextViewInfo[8].used = false;
		mTextViewInfo[9].textView = (TextView) rootView
				.findViewById(R.id.text_view_9);
		mTextViewInfo[9].used = false;

		mImageview = (ImageView) rootView.findViewById(R.id.image_view);

		mProgressView = rootView.findViewById(R.id.progress_indicator);

		mContext = context;
		mUri = Uri2File2Uri(intent.getData());
		mRes = mContext.getResources();
		sp = mContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
		editor = sp.edit();
		initToast();

		// For streams that we expect to be slow to start up, show a
		// progress spinner until playback starts.
		String scheme = mUri.getScheme();
		if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
			mHandler.postDelayed(mPlayingChecker, 250);
		} else {
			mProgressView.setVisibility(View.GONE);
		}

		/* create playlist */
		mFinishOnCompletion = intent.getBooleanExtra(
				MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
		mPlayListType = intent.getStringExtra(MediaStore.PLAYLIST_TYPE);
		mPlayList = new ArrayList<String>();
		if (mPlayListType != null) {
			if (mPlayListType
					.equalsIgnoreCase(MediaStore.PLAYLIST_TYPE_CUR_FOLDER)) {
				/* create playlist from current folder */
				createFolderDispList();
			} else if (mPlayListType
					.equalsIgnoreCase(MediaStore.PLAYLIST_TYPE_MEDIA_PROVIDER)) {
				/* create playlist from mediaprovider */
				createMediaProviderDispList(mUri, mContext);
			}
		} else {
			Log.w(TAG,
					"****** scheme is null or scheme != file, create playlist failed ******");
		}
		mMediaController = new MediaController(context);
		mVideoView.setBDFolderPlayMode(intent.getBooleanExtra(
				MediaStore.EXTRA_BD_FOLDER_PLAY_MODE, false));
		mVideoView.setOnSubFocusItems(this);
		mVideoView.setOnErrorListener(this);
		mVideoView.setOnInfoListener(this);
		mVideoView.setOnCompletionListener(this);
		mVideoView.setOnTimedTextListener(this);
		mVideoView.setVideoURI(mUri);

		Log.v(TAG, "mUri = " + mUri);

		if (mSrtList != null && mSrtList.size() > 0) {
			Log.v("mVideoView.setTimedTextPath", "mVideoView.setTimedTextPath");
			mVideoView.setTimedTextPath(mSrtList, mMediaTypeList);
		}
		setImageButtonListener();
		mVideoView.setMediaController(mMediaController);
		mMediaController.setFilePathTextView(mUri.getPath());
		// make the video view handle keys for seeking and pausing
		mVideoView.requestFocus();
		mMediaController.setOnListDataChanged(this);

		mBookmarkService = new BookmarkService(mContext);
		final int bookmark = getBookmark();
		// SAVE sub and track, add by maizirong
		mCurrentSubSave = getSubSave();
		mCurrentTrackSave = getTrackSave();
		mCurrentSubColorSave = getSubColorSave();
		mCurrentSubSizeSave = getSubSizeSave();
		Log.v(TAG, "_subBookMark______" + Integer.toString(mCurrentSubSave));
		if (bookmark > 0) {
			replayVideoDialog();
			mVideoView.seekTo(bookmark);
			// deleteBookmark();
		}

		mVideoView.start();

		mScreenWidth = width;
		mScreenHeight = height;

		Log.v(TAG, "======mScreenWidth======" + mScreenWidth);
		Log.v(TAG, "======mScreenHeight======" + mScreenHeight);
	}

	private Uri Uri2File2Uri(Uri videoUri) {
		String scheme = videoUri.getScheme();
		String mPathName = null;
		if (scheme == null) {
			return videoUri;

		}
		if (scheme.equals("content")) {
			String path = null;
			Cursor c = null;
			IContentProvider mMediaProvider = mContext.getContentResolver()
					.acquireProvider("media");
			String[] VIDEO_PROJECTION = new String[] { Video.Media.DATA };
			/* get video file */
			try {
				c = mMediaProvider.query(null, videoUri, VIDEO_PROJECTION,
						null, null, null, null);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (c != null) {
				try {
					while (c.moveToNext()) {
						path = c.getString(0);
					}
				} finally {
					c.close();
					c = null;
				}
			}
			/*
			 * if (path != null) { return Uri.fromFile(new File(path)); } else {
			 * Log.w(TAG, "************ Uri2File2Uri failed ***************");
			 * return videoUri; }
			 */
		} else if (scheme.equals("file")) {
			if (realPath == null || realPath.trim().length() == 0) {
				mPathName = videoUri.getPath();
			} else {
				mPathName = realPath;
			}
			Log.v(TAG, "_2Uri___mPathName___" + mPathName);
		}
		Log.v(TAG, "00-----Uri2File2Uri-----Uri2File2Uri-----");
		searchSubTitle(mPathName);
		return videoUri;

	}

	private void searchSubTitle(String mPathName) {
		Log.v(TAG, "00-----searchSubTitle-----searchSubTitle-----");
		int idx = mPathName.lastIndexOf("."); // position of last .
		int idx1 = mPathName.lastIndexOf("/"); // position of last /
		Log.v(TAG, "__2Uri___idx___" + idx + "___idx1___" + idx1);
		if (idx1 > 0 && idx > 0 && idx > idx1) {
			String folder = mPathName.substring(0, idx1); // storage/emulated/0/DCIM
			Log.v(TAG, "__2Uri___folder___" + folder);
			String name = mPathName.substring(idx1 + 1, idx); // a
			Log.v(TAG, "__2Uri___name___" + name);
			File directory = new File(folder);
			if (directory.isDirectory()) {
				File[] files = directory.listFiles();
				for (File f : files) {
					if (f.exists()) {
						String fileName = f.getName();
						Log.v(TAG, "__2Uri___fileName___" + fileName);
						int idx_a = fileName.indexOf(".");
						int idx_b = fileName.lastIndexOf(".");
						int idx_c = fileName.length();
						Log.v(TAG, "__2Uri___idx_a___" + idx_a + "___idx_b___"
								+ idx_b + "___idx_c___" + idx_c);
						if (idx_a > 0 && idx_b > 0 && idx_c > 0) {
							for (; idx_a > 0;) {
								String name1 = fileName.substring(0, idx_a);
								Log.v(TAG, "__2Uri___name1___" + name1);
								String name2 = fileName.substring(idx_b, idx_c);
								Log.v(TAG, "__2Uri___name2___" + name2);
								Log.v(TAG, "__2Uri___SUB_EXTS.length___"
										+ SUB_EXTS.length);
								if (name1.equals(name)) {
									for (int i = 0; i < SUB_EXTS.length; i++) {
										if (name2.toLowerCase().equals(
												SUB_EXTS[i])) {
											mSrtList.add(folder + "/"
													+ fileName);
											mMediaTypeList
													.add(MEDIA_MIMETYPE[i]);
										}
									}
								}
								idx_a = fileName.indexOf(".", idx_a + 1);
								Log.v(TAG, "__2Uri___idx_a___" + idx_a);
							}
						}
					}
				}
			}
		}
	}

	private void replayVideoDialog() {
		LayoutInflater inflate = (LayoutInflater) mContext
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View replayView = inflate.inflate(R.layout.dialog_replay, null);
		((Button) replayView.findViewById(R.id.replay_confirm))
				.setOnClickListener(new View.OnClickListener() {
					public void onClick(View arg0) {
						resetAllTextView();
						mHandler.removeMessages(DISMISS_DIALOG);
						mReplayDialog.dismiss();
						Boolean i = deleteBookmark();
						switchSubOn = true;
						mCurrentSubSave = 0;
						mCurrentTrackSave = 0;
						mCurrentSubColorSave = -1;
						mCurrentSubSizeSave = 32;
						Log.v(TAG,
								"___deleteBookmark()_____"
										+ Boolean.toString(i));
						mVideoView.setVideoURI(mUri);
						mVideoView.start();
						setSaves();
					}
				});
		((Button) replayView.findViewById(R.id.replay_cancel))
				.setOnClickListener(new View.OnClickListener() {
					public void onClick(View arg0) {
						mHandler.removeMessages(DISMISS_DIALOG);
						mReplayDialog.dismiss();
					}
				});
		mReplayDialog = new Dialog(mContext, R.style.dialog);
		mReplayDialog.getWindow().getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide
																			// nav
																			// bar
						| View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
				);
		mReplayDialog.setContentView(replayView);
		mReplayDialog.show();

		mDismissMsg = mHandler.obtainMessage(DISMISS_DIALOG);
		mHandler.sendMessageDelayed(mDismissMsg, DISMISS_DELAY);
	}

	private void initToast() {
		mJumpToast = Toast.makeText(mContext, "", Toast.LENGTH_SHORT);
		mJumpToast.setGravity(Gravity.CENTER, 0, 0);
		mSubtitleToast = new MyToast(mContext);
		mAudioToast = new MyToast(mContext);
		mPrevToast = new MyToast(mContext);
		mNextToast = new MyToast(mContext);
		mStopToast = new MyToast(mContext);
		mRepeatToast = new MyToast(mContext);

		ImageView imageView;
		imageView = (ImageView) mSubtitleToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_subtitle);
		imageView = (ImageView) mAudioToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_track);
		imageView = (ImageView) mPrevToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_prev);
		imageView = (ImageView) mNextToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_next);
		imageView = (ImageView) mStopToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_stop);
		imageView = (ImageView) mRepeatToast.getView().findViewById(
				R.id.message_image);
		imageView.setImageResource(R.drawable.button_repeat);
		TextView textView;
		textView = (TextView) mPrevToast.getView().findViewById(R.id.message);
		textView.setVisibility(View.GONE);
		textView = (TextView) mPrevToast.getView().findViewById(R.id.message);
		textView.setVisibility(View.GONE);
	}

	public void subFocusItems() {
		/* repeat mode */
		mRepeatMode = sp.getInt(EDITOR_MODEREPEAT, 0);

		/* sub gate */
		boolean gate = sp.getBoolean(EDITOR_SUBGATE, true);
		// mVideoView.setSubGate(gate);
		switchSubOn = gate;
		/* sub color */
		int[] listColor = mRes.getIntArray(R.array.screen_color_values);
		int clorIndex = sp.getInt(EDITOR_SUBCOLOR, 0);
		// mVideoView.setSubColor(listColor[clorIndex]);

		/* sub char size */
		int[] listCharsize = mRes.getIntArray(R.array.screen_charsize_values);
		int charsizeIndex = sp.getInt(EDITOR_SUBCHARSIZE, 2);
		// mVideoView.setSubFontSize(listCharsize[charsizeIndex]);

		/* zoom mode */
		int zoom = sp.getInt(EDITOR_ZOOM, 0);
		mVideoView.setZoomMode(zoom);
		Log.v(TAG, "========subFocusItems()=========");
	}

	private static boolean uriSupportsBookmarks(Uri uri) {
		if (uri.getScheme() == null) {
			return false;
		}
		return ("file".equalsIgnoreCase(uri.getScheme()));
	}

	private int getBookmark() {
		return mBookmarkService.findByPathReturnSeek(mUri.getPath());
	}

	// SAVE sub and track, add by maizirong
	private int getSubSave() {
		return mBookmarkService.findByPathReturnSubSave(mUri.getPath());
	}

	private int getTrackSave() {
		return mBookmarkService.findByPathReturnTrackSave(mUri.getPath());
	}

	// SAVE subColor and subFontSize, add by maizirong
	private int getSubColorSave() {
		return mBookmarkService.findByPathReturnSubColorSave(mUri.getPath());
	}

	private int getSubSizeSave() {
		return mBookmarkService.findByPathReturnSubSizeSave(mUri.getPath());
	}

	private boolean deleteBookmark() {
		return mBookmarkService.delete(mUri.getPath());
	}

	private void setBookmark(int bookmark) {
		if (!uriSupportsBookmarks(mUri)) {
			return;
		}

		String path = mUri.getPath();
		if (mBookmarkService.findByPathReturnSeek(path) != 0) {
			mBookmarkService.update(path, bookmark, mCurrentSubSave,
					mCurrentTrackSave, mCurrentSubColorSave,
					mCurrentSubSizeSave);
		} else {
			mBookmarkService.save(path, bookmark, mCurrentSubSave,
					mCurrentTrackSave, mCurrentSubColorSave,
					mCurrentSubSizeSave);
		}

	}

	@Override
	public void initSubAndTrackInfo() {
		// TODO Auto-generated method stub
		resetAllTextView();
		Log.v(TAG, "initSubAndTrackInfo-----resetAllTextView-----");

		/* get track info */
		mTransformTrack = null;
		TrackInfo[] trackList = mVideoView.getTrackList();
		if (trackList != null) {
			int trackCount = trackList.length;
			mTransformTrack = new String[trackList.length];
			for (int i = 0; i < trackCount; i++) {
				Log.v(TAG, "____Track___" + trackList[i].getLanguage());
				mTransformTrack[i] = new String(trackList[i].getLanguage());
				/*
				 * try { if
				 * (trackList[i].charset.equals(MediaPlayer.CHARSET_UNKNOWN)) {
				 * mTransformTrack[i] = new String(trackList[i].name,"UTF-8"); }
				 * else { mTransformTrack[i] = new
				 * String(trackList[i].name,trackList[i].charset); } } catch
				 * (UnsupportedEncodingException e) { // TODO Auto-generated
				 * catch block Log.w(TAG, "*********** unsupported encoding: "+
				 * trackList[i].charset); e.printStackTrace(); }
				 */
			}
		}

		/* get sub info */
		mTransformSubtitle = null;
		TrackInfo[] subList = mVideoView.getSubList();

		if (subList != null) {
			int subCount = subList.length;
			mTransformSubtitle = new String[subList.length];
			for (int i = 0; i < subCount; i++) {
				Log.v(TAG, "___Sub___" + subList[i].getLanguage());
				mTransformSubtitle[i] = new String(subList[i].getLanguage());
				/*
				 * try { if
				 * (subList[i].charset.equals(MediaPlayer.CHARSET_UNKNOWN)) {
				 * mTransformSubtitle[i] = new String(subList[i].name,"UTF-8");
				 * } else { mTransformSubtitle[i] = new
				 * String(subList[i].name,subList[i].charset); } } catch
				 * (UnsupportedEncodingException e) { // TODO Auto-generated
				 * catch block Log.w(TAG, "*********** unsupported encoding: "+
				 * subList[i].charset); e.printStackTrace(); }
				 */
			}
		}
	}

	private void sendKeyIntent(int keycode) {
		final int keyCode = keycode;
		// to avoid deadlock, start a thread to perform operations
		Thread sendKeyDelay = new Thread() {
			public void run() {
				try {
					int count = 1;

					IWindowManager wm = IWindowManager.Stub
							.asInterface(ServiceManager.getService("window"));
					for (int i = 0; i < count; i++) {
						Thread.sleep(100);
						long now = SystemClock.uptimeMillis();
						if (!mOnPause) {
							KeyEvent keyDown = new KeyEvent(now, now,
									KeyEvent.ACTION_DOWN, keyCode, 0);
							InputManager.getInstance().injectInputEvent(
									keyDown,
									InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
							// wm.injectKeyEvent(keyDown, false);
							KeyEvent keyUp = new KeyEvent(now, now,
									KeyEvent.ACTION_UP, keyCode, 0);
							InputManager.getInstance().injectInputEvent(keyUp,
									InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
							// wm.injectKeyEvent(keyUp, false);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}/*
				 * catch (RemoteException e) { e.printStackTrace(); }
				 */
			}
		};
		sendKeyDelay.start();
	}

	private void createFolderDispList() {
		String fileNameText, filePathText;
		File filePath;

		String[] fileEndingVideo = mRes.getStringArray(R.array.fileEndingVideo);
		fileNameText = mUri.getPath();
		int index = fileNameText.lastIndexOf('/');
		if (index >= 0) {
			filePathText = fileNameText.substring(0, index);
			filePath = new File(filePathText);
			File[] fileList = filePath.listFiles();
			if (fileList != null && filePath.isDirectory()) {
				for (File currenFile : fileList) {
					String fileName = currenFile.getName();
					int indexPoint = fileName.lastIndexOf('.');
					if (indexPoint > 0 && currenFile.isFile()) {
						String fileEnd = fileName.substring(indexPoint + 1);
						for (int i = 0; i < fileEndingVideo.length; i++) {
							if (fileEnd.equalsIgnoreCase(fileEndingVideo[i])) {
								mPlayList.add(currenFile.getPath());
								break;
							}
						}
					}
				}
			}
		}
		Collections.sort(mPlayList);

		/* get current index */
		mCurrentIndex = 0;
		String mCurrentPath = mUri.getPath();
		for (int i = 0; i < mPlayList.size(); i++) {
			if (mCurrentPath.equalsIgnoreCase(mPlayList.get(i))) {
				mCurrentIndex = i;
				break;
			}
		}
	}

	private void createMediaProviderDispList(Uri uri, Context mContext) {
		Cursor c = null;
		IContentProvider mMediaProvider = mContext.getContentResolver()
				.acquireProvider("media");
		Uri mVideoUri = Video.Media.getContentUri("external");
		String[] VIDEO_PROJECTION = new String[] { Video.Media.DATA };

		/* get playlist */
		try {
			c = mMediaProvider.query(null, mVideoUri, VIDEO_PROJECTION, null,
					null, null, null);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (c != null) {
			try {
				while (c.moveToNext()) {
					String path = c.getString(0);
					if (new File(path).exists()) {
						mPlayList.add(path);
					}
				}
			} finally {
				c.close();
				c = null;
			}

			/* get current index */
			mCurrentIndex = 0;
			String mCurrentPath = mUri.getPath();
			for (int i = 0; i < mPlayList.size(); i++) {
				if (mCurrentPath.equalsIgnoreCase(mPlayList.get(i))) {
					mCurrentIndex = i;
					break;
				}
			}
		}
	}

	private void updateDispList(String mediaPath) {
		int size = mPlayList.size();
		String path;
		if (size > 0) {
			for (int i = size - 1; i >= 0; i--) {
				path = mPlayList.get(i);
				if (path.startsWith(mediaPath)) {
					mPlayList.remove(i);
				}
			}
		}
	}

	private void setImageButtonListener() {
		String scheme = mUri.getScheme();
		if (scheme != null && scheme.equalsIgnoreCase("file")) {
			mMediaController.setPrevNextListeners(mNextListener, mPrevListener);
		} else {
			mMediaController.setPrevNextListeners(null, null);
		}
		if (mPlayList.size() > 0) {
			mMediaController.setRepeatListener(mRepeatModeListener);
		}
		mMediaController.set3DListener(mMode3DListener);
		mMediaController.setTrackListener(mTrackListener);
		mMediaController.setZoomListener(mZoomListener);
		mMediaController.setSubsetListener(mSubsetListener);
		mMediaController.setJumpListener(mJumpClickListener);
	}

	View.OnClickListener mNextListener = new View.OnClickListener() {
		public void onClick(View v) {
			PlayNext();
		}
	};
	View.OnClickListener mPrevListener = new View.OnClickListener() {
		public void onClick(View v) {
			PlayPrev();
		}
	};

	@Override
	public void OnMediaPrevKeyClickListener() {
		// TODO Auto-generated method stub
		mPrevToast.show();
		PlayPrev();
	}

	@Override
	public void OnMediaNextKeyClickListener() {
		// TODO Auto-generated method stub
		mNextToast.show();
		PlayNext();
	}

	@Override
	public void OnMediaStopKeyClickListener() {
		// TODO Auto-generated method stub
		mStopToast.show();
		sendKeyIntent(KeyEvent.KEYCODE_BACK);
	}

	@Override
	public void OnMediaRepeatKeyClickListener() {
		// TODO Auto-generated method stub
		if (mPlayList.size() > 0) {
			String[] listModeRepeat = mRes
					.getStringArray(R.array.screen_repeat_entries);
			mRepeatMode = (mRepeatMode + 1) % listModeRepeat.length;
			editor.putInt(EDITOR_MODEREPEAT, mRepeatMode);
			editor.commit();

			TextView tv = (TextView) mRepeatToast.getView().findViewById(
					R.id.message);
			tv.setText(listModeRepeat[mRepeatMode]);
			mRepeatToast.show();
		} else {
			TextView tv = (TextView) mRepeatToast.getView().findViewById(
					R.id.message);
			tv.setText(R.string.playlist_miss);
			mRepeatToast.show();
		}
	}

	@Override
	public void OnMediaSubtitleKeyClickListener() {
		// TODO Auto-generated method stub

		TextView tv = (TextView) mSubtitleToast.getView().findViewById(
				R.id.message);

		if (mTransformSubtitle != null) {
			int focus;
			int subCount = mTransformSubtitle.length;
			focus = mVideoView.getCurSub();
			/* if(mSubtitleToast.isShowing()) */{
				String currentSub = tv.getText().toString();
				for (int i = 0; i < mTransformSubtitle.length; i++) {
					if (currentSub.equals(mTransformSubtitle[i])) {
						focus = i;
						break;
					}
				}
				focus = (focus + 1) % subCount;
			}
			if (focus < subCount) {
				tv.setText(mTransformSubtitle[focus]);
			}
		} else {
			String[] SubList = mRes
					.getStringArray(R.array.screen_select_entries);
			tv.setText(SubList[0]);
		}
		mSubtitleToast.show();
	}

	@Override
	public void OnMediaAudioKeyClickListener() {
		// TODO Auto-generated method stub
		TextView tv = (TextView) mAudioToast.getView().findViewById(
				R.id.message);

		if (mTransformTrack != null) {
			int trackCount = mTransformTrack.length;
			int focus = mVideoView.getCurTrack();

			/* if(mAudioToast.isShowing()) */{
				String currentTrack = tv.getText().toString();
				for (int i = 0; i < mTransformTrack.length; i++) {
					if (currentTrack.equals(mTransformTrack[i])) {
						focus = i;
						break;
					}
				}
				focus = (focus + 1) % trackCount;
			}
			if (focus < trackCount) {
				tv.setText(mTransformTrack[focus]);
			}
		} else {
			String[] TrackList = mRes
					.getStringArray(R.array.screen_track_entries);
			tv.setText(TrackList[0]);
		}
		mAudioToast.show();
	}

	private void PlayPrev() {
		int size = mPlayList.size();
		if (mCurrentIndex >= 0 && size > 0) {
			mVideoView.clearCurrentStat();
			Log.v(TAG, "-----PlayPrev-----mVideoView.clearCurrentStat()");
			if (size == 1) {
				mCurrentIndex = 0;
			} else if (mCurrentIndex == 0) {
				mCurrentIndex = size - 1;
			} else {
				mCurrentIndex = (mCurrentIndex - 1) % size;
			}
			mUri = Uri.fromFile(new File(mPlayList.get(mCurrentIndex)));
			searchSubTitle(mUri.getPath());
			if (mSrtList != null && mSrtList.size() > 0) {
				mVideoView.setTimedTextPath(mSrtList, mMediaTypeList);
			}
			playFile();
		}
	}

	private void PlayNext() {
		int size = mPlayList.size();
		if (mCurrentIndex >= 0 && size > 0) {
			mVideoView.clearCurrentStat();
			Log.v(TAG, "-----PlayNext-----mVideoView.clearCurrentStat()");
			mCurrentIndex = (mCurrentIndex + 1) % size;
			mUri = Uri.fromFile(new File(mPlayList.get(mCurrentIndex)));
			searchSubTitle(mUri.getPath());
			Log.v(TAG, "--------PlayNext-------mUri-" + mUri.getPath());
			if (mSrtList != null && mSrtList.size() > 0) {
				mVideoView.setTimedTextPath(mSrtList, mMediaTypeList);
			}
			playFile();
		}
	}

	private void playFile() {
		// mWakeLock.acquire();
		resetAllTextView();
		Log.v(TAG, "playFile-----resetAllTextView-----");
		mHandler.postDelayed(mPlayRunnable, 500);

		// mWakeLock.release();
	}

	View.OnClickListener mJumpClickListener = new View.OnClickListener() {

		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			mMediaController.setHolding(true);
			OnTimeConfirmListener confirmListener = new OnTimeConfirmListener() {

				@Override
				public void onTimeConfirm(int time) {
					// TODO Auto-generated method stub
					Log.i("jump", "*************time: " + time);
					int duration = mVideoView.getDuration() / 1000;
					if (time <= duration) {
						mVideoView.seekTo(time * 1000);
					} else {
						String string = mContext.getResources().getString(
								R.string.overflow_toast);
						string += String.format("%02d:%02d:%02d",
								duration / 3600, duration / 60 % 60,
								duration % 60);
						mJumpToast.setText(string);
						mJumpToast.show();
					}
					mJumpView.jumpViewDismiss();
					mMediaController.setHolding(false);
				}

			};
			OnCancelListener cancelListener = new OnCancelListener() {

				@Override
				public void onCancel(DialogInterface dialog) {
					// TODO Auto-generated method stub
					mJumpView.jumpViewDismiss();
					mMediaController.setHolding(false);
				}
			};
			int duration = mVideoView.getDuration() / 1000;
			mJumpView = new JumpView(mContext, duration, confirmListener);
			mJumpView.setOnCancelListener(cancelListener);
		}
	};

	View.OnFocusChangeListener mMode3DListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			// TODO Auto-generated method stub

			if (mMediaController.getMediaControlFocusId() == R.id.mode3D) {
				return;
			}

			if (hasFocus) {
				mMode3DRunnable = new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						Log.w(TAG, "Mode3D has focused");
						// int currentMode =
						// mVideoView.getOutputDimensionType();
						String[] listMode3D = mRes
								.getStringArray(R.array.screen_3d_entries);
						mMediaController.setListViewData(R.id.mode3D, 0,
								listMode3D);
					}

				};
				mHandler.postDelayed(mMode3DRunnable, 200);
			} else {
				if (mMode3DRunnable != null) {
					mHandler.removeCallbacks(mMode3DRunnable);
					mMode3DRunnable = null;
				}
			}
		}
	};

	View.OnFocusChangeListener mRepeatModeListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			// TODO Auto-generated method stub

			if (mMediaController.getMediaControlFocusId() == R.id.repeat) {
				return;
			}

			if (hasFocus) {
				mRepeatRunnable = new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						Log.w(TAG, "Mode repeat has focused");
						int currentMode = sp.getInt(EDITOR_MODEREPEAT, 0);
						String[] listModeRepeat = mRes
								.getStringArray(R.array.screen_repeat_entries);
						mMediaController.setListViewData(R.id.repeat,
								currentMode, listModeRepeat);
					}
				};
				mHandler.postDelayed(mRepeatRunnable, 200);
			} else {
				if (mRepeatRunnable != null) {
					mHandler.removeCallbacks(mRepeatRunnable);
					mRepeatRunnable = null;
				}
			}
		}
	};

	View.OnFocusChangeListener mTrackListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			// TODO Auto-generated method stub

			if (mMediaController.getMediaControlFocusId() == R.id.track) {
				return;
			}

			if (hasFocus) {
				mTrackRunnable = new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						if (mTransformTrack != null) {
							int currentTrack = mVideoView.getCurTrack();
							mMediaController.setListViewData(R.id.track,
									currentTrack, mTransformTrack);
						} else {
							mMediaController
									.setListViewData(
											R.id.track,
											0,
											mRes.getStringArray(R.array.screen_track_entries));
						}
					}
				};
				mHandler.postDelayed(mTrackRunnable, 200);
			} else {
				if (mTrackRunnable != null) {
					mHandler.removeCallbacks(mTrackRunnable);
					mTrackRunnable = null;
				}
			}
		}
	};

	View.OnFocusChangeListener mZoomListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			// TODO Auto-generated method stub

			if (mMediaController.getMediaControlFocusId() == R.id.zoom) {
				return;
			}

			if (hasFocus) {
				mZoomRunnable = new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						int currentMode = sp.getInt(EDITOR_ZOOM, 0);
						String[] listModeZoom = mRes
								.getStringArray(R.array.screen_zoom_entries);
						mMediaController.setListViewData(R.id.zoom,
								currentMode, listModeZoom);
					}
				};
				mHandler.postDelayed(mZoomRunnable, 200);
			} else {
				if (mZoomRunnable != null) {
					mHandler.removeCallbacks(mZoomRunnable);
					mZoomRunnable = null;
				}
			}
		}
	};

	View.OnFocusChangeListener mSubsetListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			// TODO Auto-generated method stub

			if (mMediaController.getMediaControlFocusId() == R.id.subset) {
				return;
			}

			if (hasFocus) {
				mSubsetRunnable = new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						String[] listSubset = mRes
								.getStringArray(R.array.screen_subset_entries);
						mMediaController.setListViewData(R.id.subset, 0,
								listSubset);
					}
				};
				mHandler.postDelayed(mSubsetRunnable, 200);
			} else {
				if (mSubsetRunnable != null) {
					mHandler.removeCallbacks(mSubsetRunnable);
					mSubsetRunnable = null;
				}
			}
		}
	};

	public void onCompletion(MediaPlayer arg0) {
		if (mJumpView != null) {
			mJumpView.jumpViewDismiss();
		}
		mIsDoubleStream = false;
		onCompletion();
	}

	public void onCompletion() {
		// TODO Auto-generated method stub

		mVideoView.setOnErrorListener(this);
		int size = mPlayList.size();
		Log.i(TAG, "************************ in onCompletion, mToQuit: "
				+ mToQuit + ", mPlayList size: " + size + ", mRepeatMode"
				+ mRepeatMode);
		if (mCurrentIndex >= 0 && size > 0) {
			switch (mRepeatMode) {
			case 0: // repeat all
			{
				mCurrentIndex = (mCurrentIndex + 1) % size;
				break;
			}
			case 1: // sequence play
			{
				if (mCurrentIndex + 1 < size) {
					mCurrentIndex++;
				} else {
					mToQuit = true;
					return;
				}
				break;
			}
			case 2: // repeat one
			{
				break;
			}
			case 3: // random play
			{
				mCurrentIndex = (int) (Math.random() * size);
				break;
			}
			default:
				break;
			}

			mVideoView.clearCurrentStat();
			Log.v(TAG, "-----onCompletion-----mVideoView.clearCurrentStat()");
			File nextFile = new File(mPlayList.get(mCurrentIndex));
			if (!nextFile.exists()) {
				mToQuit = true;
			} else {
				mUri = Uri.fromFile(nextFile);
				searchSubTitle(mUri.getPath());
				if (mSrtList != null && mSrtList.size() > 0) {
					mVideoView.setTimedTextPath(mSrtList, mMediaTypeList);
				}
				playFile();
			}
		}

		if (size == 0 && mFinishOnCompletion) {
			mToQuit = true;
		}
	}

	public void onPause() {
		mOnPause = true;

		mContext.unregisterReceiver(mBroadcastReceiver);

		mVideoPosition = mVideoView.getCurrentPosition();
		int duration = mVideoView.getDuration();

		// current time > 10s and save current position
		if (mVideoPosition > 10 * 1000 && duration - mVideoPosition > 10 * 1000) {
			setBookmark(mVideoPosition - 3 * 1000);
		} else {
			deleteBookmark();
		}

		if (mJumpView != null) {
			mJumpView.jumpViewDismiss();
		}
		mHandler.removeCallbacksAndMessages(null);
		mVideoView.suspend();

		// when pause, reset to normal 2d
		DisplayManager displayManager = (DisplayManager) mContext
				.getSystemService(Context.DISPLAY_SERVICE);
		displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
				DisplayManager.DISPLAY_2D_ORIGINAL);
		mIsDoubleStream = false;
	}

	public void onResume() {
		resetAllTextView();
		if (mOnPause) {
			mVideoView.seekTo(mVideoPosition);
			mVideoView.resume();
			mOnPause = false;
		}

		/* receive the device eject message */
		mBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				// TODO Auto-generated method stub
				final String action = intent.getAction();
				final String mediaPath = intent.getData().getPath();

				if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
					String path = mUri.getPath();
					Log.i(TAG, "*********** media eject **********");
					/* the current video's media was eject */
					if (path.startsWith(mediaPath)) {
						Toast toast = Toast.makeText(mContext,
								R.string.play_finish, Toast.LENGTH_SHORT);
						toast.setGravity(Gravity.CENTER, 0, 0);
						toast.show();
						onCompletion();
					} else if (mPlayList.size() > 0) {
						updateDispList(mediaPath);
					}
				}
			}
		};

		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		iFilter.addDataScheme("file");
		mContext.registerReceiver(mBroadcastReceiver, iFilter);
		setSaves();
	}

	public void setSaves() {
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				Log.v(TAG,
						"___run__" + Boolean.toString(mVideoView.isPlaying()));

				mVideoView.switchSub(mCurrentSubSave);
				mVideoView.switchTrack(mCurrentTrackSave);
				// mVideoView.setSubColor(mCurrentSubColorSave);
				// mVideoView.setSubFontSize(mCurrentSubSizeSave);

				Log.v(TAG, "___setSaves___mCurrentSubSave:" + mCurrentSubSave
						+ "___mCurrentTrackSave" + mCurrentTrackSave
						+ "___mCurrentSubColorSave" + mCurrentSubColorSave
						+ "___mCurrentSubSizeSave" + mCurrentSubSizeSave);
			}
		}, 1000);
	}

	public void onStop() {

	}

	public void onDestroy() {
		mVideoView.stopPlayback();
		mBookmarkService.close();
		mVideoView.setZoomMode(0);
		editor.putInt(EDITOR_ZOOM, 0);
		editor.commit();
		displayManager.setDisplay3DLayerOffset(Display.TYPE_BUILT_IN, 0);
	}

	@Override
	public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		mHandler.removeCallbacksAndMessages(null);
		mProgressView.setVisibility(View.GONE);
		mToQuit = true;

		return false;
	}

	public boolean toQuit() {
		return mToQuit;
	}

	@Override
	public void OnListDataChangedListener(int mediaControlFocusId,
			int selectedIterm) {
		// TODO Auto-generated method stub
		switch (mediaControlFocusId) {
		case R.id.mode3D: {
			if (selectedIterm > 1) {
				setSublistData(selectedIterm);
			} else {
				mMediaController.setGridViewData(selectedIterm, 0);
			}
			/*
			 * int focusItem = selectedIterm; int[] list =
			 * mRes.getIntArray(R.array.screen_3d_values); int currentMode =
			 * mVideoView.getOutputDimensionType(); if(currentMode ==
			 * list[focusItem]) { break; } focusItem = focusItem%list.length;
			 * if( mVideoView.setOutputDimensionType(focusItem) != 0 ) {
			 * Log.w(TAG, "*********** change the 3D mode failed !"); }
			 */

			break;
		}
		case R.id.repeat: {
			mRepeatMode = selectedIterm;
			editor.putInt(EDITOR_MODEREPEAT, selectedIterm);
			editor.commit();

			break;
		}
		case R.id.track: {

			if (selectedIterm == mVideoView.getCurTrack()) {
				break;
			}
			if (mVideoView.switchTrack(selectedIterm) != 0) {
				Log.w(TAG, "*********** change the sub track failed !");
			} else {
				mCurrentTrackSave = selectedIterm;
			}

			break;
		}
		case R.id.zoom: {
			if (selectedIterm == mVideoView.getZoomMode()) {
				break;
			}
			mVideoView.setZoomMode(selectedIterm);
			editor.putInt(EDITOR_ZOOM, selectedIterm);
			editor.commit();

			break;
		}
		case R.id.subset: {
			setSublistData(selectedIterm);

			break;
		}
		default:
			break;
		}
	}

	public class SubTitleInfo {
		String text;
		int hideSubFlag;
		int subDispPos;
		Rect textScreenBound;
		Rect textBound;
		List<TimedText.Style> styleList;
		int textViewID;
	}

	public class TextViewInfo {
		TextView textView;
		boolean used;
		String text;
	}

	public class SubTitleInfoOps {
		private List<SubTitleInfo> mSubTitleInfoList = null;

		public SubTitleInfoOps() {
			mSubTitleInfoList = new ArrayList<SubTitleInfo>();
		}

		public void addSubTitleInfo(SubTitleInfo subTitleInfo) {
			mSubTitleInfoList.add(subTitleInfo);
		}

		public void removeSubTitleInfo(SubTitleInfo subTitleInfo) {
			for (int i = 0; i < mSubTitleInfoList.size(); i++) {
				if (mSubTitleInfoList.get(i).text.equals(subTitleInfo.text)) {
					mTextViewInfo[mSubTitleInfoList.get(i).textViewID].used = false;
					mTextViewInfo[mSubTitleInfoList.get(i).textViewID].text = null;
					subTitleNoDraw();
					mSubTitleInfoList.remove(mSubTitleInfoList.get(i));
				}
			}
		}

		public void removeAllSubTitleInfo() {
			mSubTitleInfoList.clear();
		}

		public int getNumOfSubTitle() {
			return mSubTitleInfoList.size();
		}

		public SubTitleInfo getSubTitleInfo(int index) {
			return mSubTitleInfoList.get(index);
		}
	}

	@Override
	public void onTimedText(MediaPlayer mp, TimedText text) {
		if (text != null) {
			mBitmapSubtitleFlag = text.AWExtend_getBitmapSubtitleFlag();
			if (mBitmapSubtitleFlag == 0) {
				SubTitleInfo subTitleInfo = new SubTitleInfo();
				subTitleInfo.text = text.getText();
				subTitleInfo.hideSubFlag = text.AWExtend_getHideSubFlag();
				subTitleInfo.subDispPos = text.AWExtend_getSubDispPos();
				subTitleInfo.textScreenBound = text
						.AWExtend_getTextScreenBounds();
				subTitleInfo.textBound = text.getBounds();
				subTitleInfo.styleList = text.AWExtend_getStyleList();

				if (subTitleInfo.text != null) {
					if (subTitleInfo.hideSubFlag == 0) {
						for (int i = 0; i < mTextViewInfo.length; i++) {
							if (mTextViewInfo[i].used == false) {
								subTitleInfo.textViewID = i;
								mTextViewInfo[i].used = true;
								if (switchSubOn) {
									mTextViewInfo[i].text = subTitleInfo.text;
								}

								subTitleDraw(i, subTitleInfo.subDispPos,
										subTitleInfo.textScreenBound,
										subTitleInfo.textBound,
										subTitleInfo.styleList);
								break;
							}
						}
						subTitleInfoOps.addSubTitleInfo(subTitleInfo);
					} else {
						subTitleInfoOps.removeSubTitleInfo(subTitleInfo);
					}
				}
			} else if (mBitmapSubtitleFlag == 1) {
				mBitmap = text.AWExtend_getBitmap();
				int width = mBitmap.getWidth();
				int height = mBitmap.getHeight();
				if (mBitmap != null) {
					mHandler.removeCallbacks(mUpdateImageView);
					mHandler.post(mUpdateImageView);
				}
			}
		} else {
			subTitleInfoOps.removeAllSubTitleInfo();
			for (int i = 0; i < mTextViewInfo.length; i++) {
				if (mTextViewInfo[i].used == true) {
					mTextViewInfo[i].used = false;
					mTextViewInfo[i].text = null;
					subTitleNoDraw();
				}
			}

			mBitmap = null;
			mHandler.removeCallbacks(mUpdateImageView);
			mHandler.post(mUpdateImageView);
		}
	}

	private void subTitleDraw(int numOfTextViewDrawed, int subDispPos,
			Rect screenBound, Rect textBound, List<TimedText.Style> styleList) {
		/*
		 * SUB_DISPPOS_DEFAULT SUB_DISPPOS_BOT_LEFT SUB_DISPPOS_BOT_MID
		 * SUB_DISPPOS_BOT_RIGHT SUB_DISPPOS_MID_LEFT SUB_DISPPOS_MID_MID
		 * SUB_DISPPOS_MID_RIGHT SUB_DISPPOS_TOP_LEFT SUB_DISPPOS_TOP_MID
		 * SUB_DISPPOS_TOP_RIGHT
		 */
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		int screenleft = 0;
		int screentop = 0;
		int screenright = 0;
		int screenbottom = 0;
		int textleft = 0;
		int texttop = 0;
		int textright = 0;
		int textbottom = 0;
		boolean isBold = false;
		boolean isItalic = false;
		boolean isUnderlined = false;
		int fontSize = 40;
		int colorRGBA = -1;
		float zoom = 1;

		if (screenBound != null && textBound != null) {
			screenleft = screenBound.left;
			screentop = screenBound.top;
			screenright = screenBound.right;
			screenbottom = screenBound.bottom;

			Log.v(TAG, "0...screenleft:" + screenleft + " screentop:"
					+ screentop + " screenright:" + screenright
					+ " screenbottom:" + screenbottom);
			zoom = (float) mScreenWidth / (float) (screenright - screenleft);
			// Log.v(TAG, "==0==zoom=" + zoom);

			if (zoom == 0) {
				zoom = (float) 1.0;
				// Log.v(TAG, "==00==zoom=" + zoom);
			}
			// Log.v(TAG, "==1==zoom=" + zoom);
			screenleft = (int) (screenleft * zoom);
			screentop = (int) (screentop * zoom);
			screenright = (int) (screenright * zoom);
			screenbottom = (int) (screenbottom * zoom);

			Log.v(TAG, "1...screenleft:" + screenleft + " screentop:"
					+ screentop + " screenright:" + screenright
					+ " screenbottom:" + screenbottom);

			textleft = (int) (textBound.left * zoom);
			texttop = (int) (textBound.top * zoom);
			textright = (int) (textBound.right * zoom);
			textbottom = (int) (textBound.bottom * zoom);

			Log.v(TAG, "2...textleft:" + textleft + " texttop:" + texttop
					+ " textright:" + textright + " textbottom:" + textbottom);
		}

		if (subDispPos == TimedText.SUB_DISPPOS_DEFAULT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL,
					RelativeLayout.TRUE);
			params.setMargins(0, 0, 0, 120);

		} else if (subDispPos == TimedText.SUB_DISPPOS_BOT_LEFT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			params.setMargins(textleft, 0, 0, screenbottom - textbottom);
		} else if (subDispPos == TimedText.SUB_DISPPOS_BOT_MID) {
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL,
					RelativeLayout.TRUE);
			params.setMargins(0, 0, 0, screenbottom - textbottom);
		} else if (subDispPos == TimedText.SUB_DISPPOS_BOT_RIGHT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,
					RelativeLayout.TRUE);
			params.setMargins(0, 0, screenright - textright, screenbottom
					- textbottom);
		} else if (subDispPos == TimedText.SUB_DISPPOS_MID_LEFT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(textleft, 0, 0, 0);
		} else if (subDispPos == TimedText.SUB_DISPPOS_MID_MID) {
			params.addRule(RelativeLayout.CENTER_HORIZONTAL,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(0, 0, 0, 0);
		} else if (subDispPos == TimedText.SUB_DISPPOS_MID_RIGHT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(0, 0, screenright - textright, 0);
		} else if (subDispPos == TimedText.SUB_DISPPOS_TOP_LEFT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.setMargins(textleft, texttop, 0, 0);
		} else if (subDispPos == TimedText.SUB_DISPPOS_TOP_MID) {
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL,
					RelativeLayout.TRUE);
			params.setMargins(0, texttop, 0, 0);
		} else if (subDispPos == TimedText.SUB_DISPPOS_TOP_RIGHT) {
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,
					RelativeLayout.TRUE);
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.setMargins(0, texttop, screenright - textright, 0);
		}
		mTextViewInfo[numOfTextViewDrawed].textView.setLayoutParams(params);

		if (styleList != null) {
			TimedText.Style style = styleList.get(0);
			/*
			 * isBold isItalic isUnderlined fontSize colorRGBA
			 */

			// colorRGBA = style.colorRGBA;
			// Log.v(TAG, "-----style.colorRGBA------" +
			// style.colorRGBA);
		}

		colorRGBA = mCurrentSubColorSave;
		fontSize = mCurrentSubSizeSave;

		mTextViewInfo[numOfTextViewDrawed].textView.setTextColor(colorRGBA);
		// mTextViewInfo[numOfTextViewDrawed].textView.setTextSize((float)
		// ((float) fontSize * zoom / 1.5));
		mTextViewInfo[numOfTextViewDrawed].textView.setTextSize(fontSize);
		Log.v(TAG, "-------------zoom-------------" + zoom);
		Log.v(TAG, "-------------getTextSize-------------"
				+ mTextViewInfo[numOfTextViewDrawed].textView.getTextSize());
		if (isBold == true && isItalic == false) {
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface
					.create(Typeface.SERIF, Typeface.BOLD));
		} else if (isItalic == true && isBold == false) {
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface
					.create(Typeface.SERIF, Typeface.ITALIC));
		} else if (isItalic == true && isBold == true) {
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface
					.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
		} else {
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface
					.create(Typeface.SERIF, Typeface.NORMAL));
		}
		mSubTitleHandler.sendEmptyMessage(TEXTVIEW_UPDATE);
	}

	private void subTitleNoDraw() {
		mSubTitleHandler.sendEmptyMessage(TEXTVIEW_UPDATE);
	}

	private void setSublistData(int selectedIterm) {
		switch (selectedIterm) {
		case 0: {
			// set sub gate
			if (mMediaController.getMediaControlFocusId() == R.id.subset) {
				boolean gate = sp.getBoolean(EDITOR_SUBGATE, true);
				Log.v(TAG, "__gate__" + gate);
				int focus = (gate == true) ? 0 : 1;
				Log.v(TAG, "__focus__" + focus);
				String[] listGate = mRes
						.getStringArray(R.array.screen_gate_entries);
				mMediaController.setSublistViewData(0, focus, listGate);
			}
			break;
		}
		case 1: {
			// set sub select
			if (mMediaController.getMediaControlFocusId() == R.id.subset) {
				int focus = 0;
				if (mTransformSubtitle != null) {
					int subCount = mTransformSubtitle.length;
					focus = mVideoView.getCurSub();
					mMediaController.setSublistViewData(1, focus,
							mTransformSubtitle);
				} else {
					String[] transformSub = mRes
							.getStringArray(R.array.screen_select_entries);
					mMediaController.setSublistViewData(1, focus, transformSub);
				}
			}
			break;
		}
		case 2: {
			// set sub color
			if (mMediaController.getMediaControlFocusId() == R.id.subset) {
				int focus = sp.getInt(EDITOR_SUBCOLOR, 0);
				String[] listColor = mRes
						.getStringArray(R.array.screen_color_entries);
				mMediaController.setSublistViewData(2, focus, listColor);
			} else if (mMediaController.getMediaControlFocusId() == R.id.mode3D) {
				/* set the 3D anaglagh */
				int[] listAnaglaghValue = mRes
						.getIntArray(R.array.screen_3d_anaglagh_values);
				int currentAnaglagh = 0;// mVideoView.getAnaglaghType();
				int focus = 0;
				for (int i = 0; i < listAnaglaghValue.length; i++) {
					if (currentAnaglagh == listAnaglaghValue[i]) {
						focus = i;
						break;
					}
				}
				String[] listAnaglaghEntry = mRes
						.getStringArray(R.array.screen_3d_anaglagh_entries);
				mMediaController
						.setSublistViewData(2, focus, listAnaglaghEntry);
			}

			break;
		}
		case 3: {
			// set sub charsize
			if (mMediaController.getMediaControlFocusId() == R.id.subset) {
				int focus = sp.getInt(EDITOR_SUBCHARSIZE, 2);
				String[] listcharsize = mRes
						.getStringArray(R.array.screen_charsize_entries);
				mMediaController.setSublistViewData(3, focus, listcharsize);
			} else if (mMediaController.getMediaControlFocusId() == R.id.mode3D) {
				/* set the 3D anaglagh */
				int[] listAnaglaghValue = mRes
						.getIntArray(R.array.screen_3d_anaglagh_values);
				int currentAnaglagh = 0;// mVideoView.getAnaglaghType();
				int focus = 0;
				for (int i = 0; i < listAnaglaghValue.length; i++) {
					if (currentAnaglagh == listAnaglaghValue[i]) {
						focus = i;
						break;
					}
				}
				String[] listAnaglaghEntry = mRes
						.getStringArray(R.array.screen_3d_anaglagh_entries);
				mMediaController
						.setSublistViewData(3, focus, listAnaglaghEntry);
			}

			break;
		}
		case 4: // set sub charset
		{
			int focus = 0;
			String currentCharset = mVideoView.getSubCharset();
			String[] charsetValue = mRes
					.getStringArray(R.array.screen_charset_values);
			String[] listCharset = mRes
					.getStringArray(R.array.screen_charset_entries);
			for (int i = 0; i < charsetValue.length; i++) {
				if (currentCharset.equalsIgnoreCase(charsetValue[i])) {
					focus = i;
					break;
				}
			}
			mMediaController.setSublistViewData(4, focus, listCharset);

			break;
		}
		case 5: // set sub delay
		{
			int focus = 3;
			int subDelay = mVideoView.getSubDelay();
			int[] listValue = mRes.getIntArray(R.array.screen_delay_values);
			for (int i = 0; i < listValue.length; i++) {
				if (subDelay == listValue[i]) {
					focus = i;
					break;
				}
			}
			String[] listDelay = mRes
					.getStringArray(R.array.screen_delay_entries);
			mMediaController.setSublistViewData(5, focus, listDelay);

			break;
		}
		case 6: {
			String[] listOffset = mRes
					.getStringArray(R.array.screen_offset_entries);
			mMediaController.setSublistViewData(6, 0, listOffset);

			break;
		}
		}
	}

	@Override
	public void OnSublistDataChangedListener(int listFoucsIndex,
			int selectedIterm) {
		// TODO Auto-generated method stub
		if (mMediaController.getMediaControlFocusId() == R.id.subset) {
			setSubsetListData(listFoucsIndex, selectedIterm);
		} else if (mMediaController.getMediaControlFocusId() == R.id.mode3D) {
			setMode3DListData(listFoucsIndex, selectedIterm);
		}
	}

	private void setSubsetListData(int listFoucsIndex, int selectedIterm) {
		switch (listFoucsIndex) {
		case 0: // set subgate
		{

			if (selectedIterm == 0) {
				switchSubOn = true;
			} else {
				switchSubOn = false;
			}
			// mVideoView.setSubGate(switchOn);

			editor.putBoolean(EDITOR_SUBGATE, switchSubOn);
			editor.commit();

			break;
		}
		case 1: // set sub select
		{

			if (mVideoView.switchSub(selectedIterm) != 0) {
				Log.w(TAG, "*********** change the sub select failed !");
			} else {
				mCurrentSubSave = selectedIterm;
				Log.v(TAG, "______setSubsetListData_mCurrentSubSave:______"
						+ Integer.toString(selectedIterm));
			}
			editor.putInt(EDITOR_SUBSELECT, selectedIterm);
			editor.commit();

			break;
		}
		case 2: // set sub color
		{

			Log.v(TAG,
					"set_sub_color________"
							+ Boolean.toString(mVideoView.isPlaying()));
			int[] listColor = mRes.getIntArray(R.array.screen_color_values);

			mCurrentSubColorSave = listColor[selectedIterm];
			Log.v(TAG, "______setSubsetListData_mCurrentSubColorSave:______"
					+ Integer.toString(listColor[selectedIterm]));
			editor.putInt(EDITOR_SUBCOLOR, selectedIterm);
			editor.commit();

			break;
		}
		case 3: // set sub charsize
		{

			int[] listCharsize = mRes
					.getIntArray(R.array.screen_charsize_values);

			mCurrentSubSizeSave = listCharsize[selectedIterm];
			Log.v(TAG, "______setSubsetListData_mCurrentSubSizeSave:______"
					+ Integer.toString(listCharsize[selectedIterm]));
			editor.putInt(EDITOR_SUBCHARSIZE, selectedIterm);
			editor.commit();

			break;
		}
		case 4: // set sub charset
		{
			String[] listCharSet = mRes
					.getStringArray(R.array.screen_charset_values);
			if (mVideoView.setSubCharset(listCharSet[selectedIterm]) != 0) {
				Log.w(TAG, "*********** change the sub charset failed !");
			} else {
				editor.putInt(EDITOR_SUBCHARSET, selectedIterm);
				editor.commit();
			}

			break;
		}
		case 5: // set sub delay
		{
			int[] listDelay = mRes.getIntArray(R.array.screen_delay_values);
			if (mVideoView.setSubDelay(listDelay[selectedIterm]) != 0) {
				Log.w(TAG, "*********** change the sub delay failed !");
			}

			break;
		}
		case 6: // set sub offset
		{
			/*
			 * int[] listOffset =
			 * mRes.getIntArray(R.array.screen_offset_values); if
			 * (mVideoView.setSubPosition(listOffset[selectedIterm]) != 0) {
			 * Log.w(TAG, "*********** change the sub offset failed !"); }
			 */

			break;
		}
		}
	}

	private void setMode3DListData(int listFoucsIndex, int selectedIterm) {
		mVideoView.setZoomMode(1);
		editor.putInt(EDITOR_ZOOM, 1);
		editor.commit();
		// anaglagh mode
		if (listFoucsIndex >= 2) {
			if (listFoucsIndex == 2) {
				// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_SIDE_BY_SIDE);
			} else if (listFoucsIndex == 3) {
				// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_TOP_TO_BOTTOM);
			}
			switch (selectedIterm) {
			case 0: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_RED_BLUE);

				break;
			}
			case 1: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_RED_GREEN);

				break;
			}
			case 2: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_RED_CYAN);

				break;
			}
			case 3: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_COLOR);

				break;
			}
			case 4: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_HALF_COLOR);

				break;
			}
			case 5: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_OPTIMIZED);

				break;
			}
			case 6: {
				// mVideoView.setAnaglaghType(MediaPlayer.ANAGLAGH_YELLOW_BLUE);

				break;
			}
			}
			// mVideoView.setOutputDimensionType(MediaPlayer.DISPLAY_3D_MODE_ANAGLAGH);
		} else { // 2D | 3D
			displayManager = (DisplayManager) mContext
					.getSystemService(Context.DISPLAY_SERVICE);

			if (listFoucsIndex == 0) { // 2D
				displayManager
						.setDisplay3DLayerOffset(Display.TYPE_BUILT_IN, 0);
				switch (selectedIterm) {
				case 0: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_SIDE_BY_SIDE);
					// mVideoView.setOutputDimensionType(MediaPlayer.DISPLAY_3D_MODE_HALF_PICTURE);
					displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
							DisplayManager.DISPLAY_2D_LEFT);
					break;
				}
				case 1: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_TOP_TO_BOTTOM);
					// mVideoView.setOutputDimensionType(MediaPlayer.DISPLAY_3D_MODE_HALF_PICTURE);
					displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
							DisplayManager.DISPLAY_2D_TOP);
					break;
				}
				case 2: {
					// mVideoView.setOutputDimensionType(MediaPlayer.DISPLAY_3D_MODE_2D);
					mVideoView.setZoomMode(0);
					editor.putInt(EDITOR_ZOOM, 0);
					editor.commit();
					if (mIsDoubleStream) {
						displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
								DisplayManager.DISPLAY_2D_DUAL_STREAM);
					} else {
						displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
								DisplayManager.DISPLAY_2D_ORIGINAL);
					}
					break;
				}
				}
			} else if (listFoucsIndex == 1) { // 3D
				// check Mode 3D enable
				int displayType = displayManager
						.getDisplayOutputType(Display.TYPE_BUILT_IN);
				if (displayType != DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI
						|| displayType == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI
						&& displayManager
								.getDisplaySupport3DMode(Display.TYPE_BUILT_IN) <= 0) {
					int id = R.string.not_hdmi;
					if (displayType == DisplayManager.DISPLAY_OUTPUT_TYPE_HDMI) {
						id = R.string.not_support;
					}
					Toast toast = Toast.makeText(mContext, id,
							Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.CENTER, 0, 0);
					toast.show();
					return;
				}
				displayManager.setDisplay3DLayerOffset(Display.TYPE_BUILT_IN,
						31);// 3D ui offset.
				switch (selectedIterm) {
				case 0: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_SIDE_BY_SIDE);
					displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
							DisplayManager.DISPLAY_3D_LEFT_RIGHT_HDMI);
					break;
				}
				case 1: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_TOP_TO_BOTTOM);
					displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
							DisplayManager.DISPLAY_3D_TOP_BOTTOM_HDMI);
					break;
				}
				case 2: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_DOUBLE_STREAM);
					displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
							DisplayManager.DISPLAY_3D_DUAL_STREAM);
					// TODO
					break;
				}
				case 3: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_LINE_INTERLEAVE);
					// TODO
					break;
				}
				case 4: {
					// mVideoView.setInputDimensionType(MediaPlayer.PICTURE_3D_MODE_COLUME_INTERLEAVE);
					// TODO
					break;
				}
				}
				// mVideoView.setOutputDimensionType(MediaPlayer.DISPLAY_3D_MODE_3D);
			}
		}
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		// TODO Auto-generated method stub

		Log.v(TAG, "=====onInfo=====what=" + what + "==extra=" + extra);
		switch (what) {
		case MediaPlayer.MEDIA_INFO_AWEXTEND_INDICATE_3D_DOUBLE_STREAM:
			mIsDoubleStream = true;
			DisplayManager displayManager = (DisplayManager) mContext
					.getSystemService(Context.DISPLAY_SERVICE);
			displayManager.setDisplay3DMode(Display.TYPE_BUILT_IN,
					DisplayManager.DISPLAY_2D_DUAL_STREAM);
			break;
		}
		return true;
	}
}
