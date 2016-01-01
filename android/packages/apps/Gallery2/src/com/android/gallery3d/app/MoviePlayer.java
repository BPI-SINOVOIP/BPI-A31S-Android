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

package com.android.gallery3d.app;

import java.io.File;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.TimedText;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Virtualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
//import android.widget.VideoView;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
//import android.content.IContentProvider;
import android.content.ContentResolver;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;

import java.lang.String;
import java.util.List;
import java.util.ArrayList;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.BlobCache;
import com.android.gallery3d.util.CacheManager;
import com.android.gallery3d.util.GalleryUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

public class MoviePlayer implements
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnTimedTextListener, ControllerOverlay.Listener {
    @SuppressWarnings("unused")
    private static final String TAG = "MoviePlayer";

    private static final String KEY_VIDEO_POSITION = "video-position";
    private static final String KEY_RESUMEABLE_TIME = "resumeable-timeout";

    // These are constants in KeyEvent, appearing on API level 11.
    private static final int KEYCODE_MEDIA_PLAY = 126;
    private static final int KEYCODE_MEDIA_PAUSE = 127;

    // Copied from MediaPlaybackService in the Music Player app.
    private static final String SERVICECMD = "com.android.music.musicservicecommand";
    private static final String CMDNAME = "command";
    private static final String CMDPAUSE = "pause";

    private static final String VIRTUALIZE_EXTRA = "virtualize";
	
	private static final String[] SUB_EXTS = new String[] {
		".idx",
		".sub",  //.idx
		".srt",
		".smi",
		".rt",
		".txt",
		".ssa",
		".aqt",
		".jss",
		".js",
    	".ass",
    	".vsf",
    	".tts",
    	".stl",
    	".zeg",
    	".ovr",
    	".dks",
    	".lrc",
    	".pan",
    	".sbt",
    	".vkt",
    	".pjs",
    	".mpl",
    	".scr",
    	".psb",
    	".asc",
    	".rtf",
    	".s2k",
    	".sst",
    	".son",
    	".ssts"
	};

	private static final String[] MEDIA_MIMETYPE = new String[] {
		"application/idx-sub",
		"application/sub",  //.idx
		"application/x-subrip",
		"text/smi",
		"text/rt",
		"text/txt",
		"text/ssa",
		"text/aqt",
		"text/jss",
		"text/js",
    	"text/ass",
    	"text/vsf",
    	"text/tts",
    	"text/stl",
    	"text/zeg",
    	"text/ovr",
    	"text/dks",
    	"text/lrc",
    	"text/pan",
    	"text/sbt",
    	"text/vkt",
    	"text/pjs",
    	"text/mpl",
    	"text/scr",
    	"text/psb",
    	"text/asc",
    	"text/rtf",
    	"text/s2k",
    	"text/sst",
    	"text/son",
    	"text/ssts"
	};
    private static final long BLACK_TIMEOUT = 500;

    // If we resume the acitivty with in RESUMEABLE_TIMEOUT, we will keep playing.
    // Otherwise, we pause the player.
    private static final long RESUMEABLE_TIMEOUT = 3 * 60 * 1000; // 3 mins

	private static final int TEXTVIEW_UPDATE = 2;

    private Context mContext;
    private final GalleryVideoView mVideoView;
    private final View mRootView;
    private final Bookmarker mBookmarker;
    private final Uri mUri;
    private final Handler mHandler = new Handler();
    private final AudioBecomingNoisyReceiver mAudioBecomingNoisyReceiver;
    private final MovieControllerOverlay mController;

    private long mResumeableTime = Long.MAX_VALUE;
    private int mVideoPosition = 0;
    private boolean mHasPaused = false;
    private int mLastSystemUiVis = 0;

    // If the time bar is being dragged.
    private boolean mDragging;

    // If the time bar is visible.
    private boolean mShowing;

    private Virtualizer mVirtualizer;
	
	private ArrayList<String> mSrtList = new ArrayList<String>();
	private ArrayList<String> mMediaTypeList = new ArrayList<String>();
/*******************************************************/
//add for handling subtitle
	private ImageView mImageview;
	private Bitmap mBitmap;
	private int mBitmapSubtitleFlag = 0;
	private int mScreenWidth;
	private int mScreenHeight;

	private SubTitleInfoOps subTitleInfoOps = new SubTitleInfoOps();
	private SubTitleInfo mSubTitleInfo;
	private TextViewInfo[] mTextViewInfo = new TextViewInfo[10];
	private final Handler mSubTitleHandler = new MainHandler();   //handle text subtitle
/*******************************************************/

	private static int position = 0;

    private final Runnable mPlayingChecker = new Runnable() {
        @Override
        public void run() {
            if (mVideoView.isPlaying()) {
                mController.showPlaying();
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };

    private final Runnable mProgressChecker = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            mHandler.postDelayed(mProgressChecker, 1000 - (pos % 1000));
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
					for(int i = 0; i < mTextViewInfo.length; i++)
					{
						mTextViewInfo[i].textView.setText(mTextViewInfo[i].text);
					}
                    break;
                }
            }
        }
    }

 private Uri Uri2File2Uri(Uri videoUri) {
		String scheme = videoUri.getScheme();
		String mPathName = null;
		if (scheme == null){
			return videoUri;
		}
		if (scheme.equals("content"))
		{
	    	String path = null;
	    	Cursor c = null;
	        String[] VIDEO_PROJECTION = new String[] { Video.Media.DATA };
			ContentResolver cr = mContext.getContentResolver(); 
			c = cr.query(videoUri,VIDEO_PROJECTION,null,null,null);
			if(c != null)
			{
				c.moveToFirst();
	        	mPathName = c.getString(0);
	        	c.close();
			}
		}
		else if(scheme.equals("file"))
		{
			mPathName = videoUri.getPath();
		}
		
		int idx = mPathName.lastIndexOf(".");   // position of last .
		int idx1 = mPathName.lastIndexOf("/");   // position of last /
		if(idx1 > 0 && idx > 0 && idx > idx1)
		{
			String folder = mPathName.substring(0,idx1);   // storage/emulated/0/DCIM
			String name = mPathName.substring(idx1 + 1,idx);   // a
			File directory = new File(folder);
			if(directory.isDirectory())
			{
				File[] files = directory.listFiles();
				for(File f : files)
				{
					if(f.exists())
					{
						String fileName = f.getName();
						int idx_a = fileName.indexOf(".");
						int idx_b = fileName.lastIndexOf(".");
						int idx_c = fileName.length();
						if(idx_a > 0 && idx_b > 0 && idx_c > 0)
						{
							String name1 = fileName.substring(0, idx_a);
							String name2 = fileName.substring(idx_b, idx_c);
							if(name1.equals(name))
							{
								for(int i = 0; i < SUB_EXTS.length; i ++)
								{
									if(name2.toLowerCase().equals(SUB_EXTS[i]))
									{
										mSrtList.add(folder + "/" + fileName);
										mMediaTypeList.add(MEDIA_MIMETYPE[i]);
									}
								}
							}
						}
					}
				}
			}
		}

        return videoUri;

    }

    public MoviePlayer(View rootView, final MovieActivity movieActivity,
            Uri videoUri, Bundle savedInstance, boolean canReplay, int screenWidth, int screenHeight) {
        mContext = movieActivity.getApplicationContext();
        mRootView = rootView;
        mVideoView = (GalleryVideoView) rootView.findViewById(R.id.surface_view);

		//init subtitle textview
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
		
		mTextViewInfo[0].textView = (TextView) rootView.findViewById(R.id.text_view_0);
		mTextViewInfo[0].used = false;
		mTextViewInfo[1].textView = (TextView) rootView.findViewById(R.id.text_view_1);
		mTextViewInfo[1].used = false;
		mTextViewInfo[2].textView = (TextView) rootView.findViewById(R.id.text_view_2);
		mTextViewInfo[2].used = false;
		mTextViewInfo[3].textView = (TextView) rootView.findViewById(R.id.text_view_3);
		mTextViewInfo[3].used = false;
		mTextViewInfo[4].textView = (TextView) rootView.findViewById(R.id.text_view_4);
		mTextViewInfo[4].used = false;
		mTextViewInfo[5].textView = (TextView) rootView.findViewById(R.id.text_view_5);
		mTextViewInfo[5].used = false;
		mTextViewInfo[6].textView = (TextView) rootView.findViewById(R.id.text_view_6);
		mTextViewInfo[6].used = false;
		mTextViewInfo[7].textView = (TextView) rootView.findViewById(R.id.text_view_7);
		mTextViewInfo[7].used = false;
		mTextViewInfo[8].textView = (TextView) rootView.findViewById(R.id.text_view_8);
		mTextViewInfo[8].used = false;
		mTextViewInfo[9].textView = (TextView) rootView.findViewById(R.id.text_view_9);
		mTextViewInfo[9].used = false;
		
		mImageview = (ImageView) rootView.findViewById(R.id.image_view);
        mBookmarker = new Bookmarker(movieActivity);
		//mUri = videoUri;
        mUri = Uri2File2Uri(videoUri);

        mController = new MovieControllerOverlay(mContext);
        ((ViewGroup)rootView).addView(mController.getView());
        mController.setListener(this);
        mController.setCanReplay(canReplay);

        mVideoView.setOnErrorListener(this);
        mVideoView.setOnCompletionListener(this);
		mVideoView.setOnTimedTextListener(this);
        mVideoView.setVideoURI(mUri);
		
		Log.v("fuqiang","mUri = " + mUri);
		if(mSrtList != null && mSrtList.size() > 0)
		{
			mVideoView.setTimedTextPath(mSrtList, mMediaTypeList);
		}

        Intent ai = movieActivity.getIntent();
        boolean virtualize = ai.getBooleanExtra(VIRTUALIZE_EXTRA, false);
        if (virtualize) {
            int session = mVideoView.getAudioSessionId();
            if (session != 0) {
                mVirtualizer = new Virtualizer(0, session);
                mVirtualizer.setEnabled(true);
            } else {
                Log.w(TAG, "no audio session to virtualize");
            }
        }
        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mController.show();
                return true;
            }
        });
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                if (!mVideoView.canSeekForward() || !mVideoView.canSeekBackward()) {
                    mController.setSeekable(false);
                } else {
                    mController.setSeekable(true);
                }
                setProgress();
            }
        });

        // The SurfaceView is transparent before drawing the first frame.
        // This makes the UI flashing when open a video. (black -> old screen
        // -> video) However, we have no way to know the timing of the first
        // frame. So, we hide the VideoView for a while to make sure the
        // video has been drawn on it.
        mVideoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mVideoView.setVisibility(View.VISIBLE);
            }
        }, BLACK_TIMEOUT);

        setOnSystemUiVisibilityChangeListener();
        // Hide system UI by default
        showSystemUi(false);

        mAudioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver();
        mAudioBecomingNoisyReceiver.register();

        Intent i = new Intent(SERVICECMD);
        i.putExtra(CMDNAME, CMDPAUSE);
        movieActivity.sendBroadcast(i);

        if (savedInstance != null) { // this is a resumed activity
            mVideoPosition = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
            mResumeableTime = savedInstance.getLong(KEY_RESUMEABLE_TIME, Long.MAX_VALUE);
            mVideoView.start();
            mVideoView.suspend();
            mHasPaused = true;
        } else {
            final Integer bookmark = mBookmarker.getBookmark(mUri);
            if (bookmark != null) {
                showResumeDialog(movieActivity, bookmark);
            } else {
                startVideo();
            }
        }
		
		mScreenWidth = screenWidth;
		mScreenHeight = screenHeight;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setOnSystemUiVisibilityChangeListener() {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_HIDE_NAVIGATION) return;

        // When the user touches the screen or uses some hard key, the framework
        // will change system ui visibility from invisible to visible. We show
        // the media control and enable system UI (e.g. ActionBar) to be visible at this point
        mVideoView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                int diff = mLastSystemUiVis ^ visibility;
                mLastSystemUiVis = visibility;
                if ((diff & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0
                        && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                    mController.show();
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showSystemUi(boolean visible) {
        if (!ApiHelper.HAS_VIEW_SYSTEM_UI_FLAG_LAYOUT_STABLE) return;

        int flag = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!visible) {
            // We used the deprecated "STATUS_BAR_HIDDEN" for unbundling
            flag |= View.STATUS_BAR_HIDDEN | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        mVideoView.setSystemUiVisibility(flag);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_VIDEO_POSITION, mVideoPosition);
        outState.putLong(KEY_RESUMEABLE_TIME, mResumeableTime);
    }

    private void showResumeDialog(Context context, final int bookmark) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.resume_playing_title);
        builder.setMessage(String.format(
                context.getString(R.string.resume_playing_message),
                GalleryUtils.formatDuration(context, bookmark / 1000)));
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                onCompletion();
            }
        });
        builder.setPositiveButton(
                R.string.resume_playing_resume, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mVideoView.seekTo(bookmark);
                startVideo();
            }
        });
        builder.setNegativeButton(
                R.string.resume_playing_restart, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startVideo();
            }
        });
        builder.show();
    }

    public void onPause() {
        mHasPaused = true;
        mHandler.removeCallbacksAndMessages(null);
        mVideoPosition = mVideoView.getCurrentPosition();
        mBookmarker.setBookmark(mUri, mVideoPosition, mVideoView.getDuration());
        mVideoView.suspend();
        mResumeableTime = System.currentTimeMillis() + RESUMEABLE_TIMEOUT;
    }

    public void onResume() {
        if (mHasPaused) {
            mVideoView.seekTo(mVideoPosition);
            mVideoView.resume();

            // If we have slept for too long, pause the play
            if (System.currentTimeMillis() > mResumeableTime) {
                pauseVideo();
            }
        }
        mHandler.post(mProgressChecker);
    }

    public void onDestroy() {
        if (mVirtualizer != null) {
            mVirtualizer.release();
            mVirtualizer = null;
        }
        mVideoView.stopPlayback();
        mAudioBecomingNoisyReceiver.unregister();
    }

    // This updates the time bar display (if necessary). It is called every
    // second by mProgressChecker and also from places where the time bar needs
    // to be updated immediately.
    private int setProgress() {
        if (mDragging || !mShowing) {
            return 0;
        }
        int position = mVideoView.getCurrentPosition();
        int duration = mVideoView.getDuration();
        mController.setTimes(position, duration, 0, 0);
        return position;
    }

    private void startVideo() {
        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = mUri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "rtsp".equalsIgnoreCase(scheme)) {
            mController.showLoading();
            mHandler.removeCallbacks(mPlayingChecker);
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mController.showPlaying();
            mController.hide();
        }

        mVideoView.start();
        setProgress();
    }

    private void playVideo() {
        mVideoView.start();
        mController.showPlaying();
        setProgress();
    }

    private void pauseVideo() {
        mVideoView.pause();
        mController.showPaused();
    }

    // Below are notifications from VideoView
    @Override
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mHandler.removeCallbacksAndMessages(null);
        // VideoView will show an error dialog if we return false, so no need
        // to show more message.
        mController.showErrorMessage("");
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mController.showEnded();
        onCompletion();
    }

    public void onCompletion() {
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

		public SubTitleInfoOps()
		{
			mSubTitleInfoList = new ArrayList<SubTitleInfo>();
		}

		public void addSubTitleInfo(SubTitleInfo subTitleInfo)
		{
			mSubTitleInfoList.add(subTitleInfo);
		}

		public void removeSubTitleInfo(SubTitleInfo subTitleInfo)
		{
			for(int i = 0; i < mSubTitleInfoList.size(); i ++)
			{
				if(mSubTitleInfoList.get(i).text.equals(subTitleInfo.text))
				{
					mTextViewInfo[mSubTitleInfoList.get(i).textViewID].used = false;
					mTextViewInfo[mSubTitleInfoList.get(i).textViewID].text = null;
					subTitleNoDraw();
					mSubTitleInfoList.remove(mSubTitleInfoList.get(i));
				}
			}
		}

		public void removeAllSubTitleInfo()
		{
			mSubTitleInfoList.clear();
		}

		public int getNumOfSubTitle()
		{
			return mSubTitleInfoList.size();
		}

		public SubTitleInfo getSubTitleInfo(int index)
		{
			return mSubTitleInfoList.get(index);
		}
	}

	@Override
    public void onTimedText(MediaPlayer mp, TimedText text) {
        if (text != null) {
			mBitmapSubtitleFlag = text.AWExtend_getBitmapSubtitleFlag();
			if(mBitmapSubtitleFlag == 0)
			{
				SubTitleInfo subTitleInfo = new SubTitleInfo();
				subTitleInfo.text = text.getText();
				subTitleInfo.hideSubFlag = text.AWExtend_getHideSubFlag();
				subTitleInfo.subDispPos = text.AWExtend_getSubDispPos();
				subTitleInfo.textScreenBound = text.AWExtend_getTextScreenBounds();
				subTitleInfo.textBound = text.getBounds();
				subTitleInfo.styleList = text.AWExtend_getStyleList();
				
				if(subTitleInfo.text != null)
				{
					if(subTitleInfo.hideSubFlag == 0)
					{
						for(int i = 0; i < mTextViewInfo.length; i++)
						{
							if(mTextViewInfo[i].used == false)
							{
								subTitleInfo.textViewID = i;
								mTextViewInfo[i].used = true;
								mTextViewInfo[i].text = subTitleInfo.text;
								subTitleDraw(i, subTitleInfo.subDispPos, subTitleInfo.textScreenBound, subTitleInfo.textBound, subTitleInfo.styleList);
								break;
							}
						}
						subTitleInfoOps.addSubTitleInfo(subTitleInfo);
					}
					else
					{
						subTitleInfoOps.removeSubTitleInfo(subTitleInfo);
					}
				}
			}
			else if(mBitmapSubtitleFlag == 1)
			{
				mBitmap = text.AWExtend_getBitmap();
				int width = mBitmap.getWidth();
				int height = mBitmap.getHeight();
				if (mBitmap != null) {
					mHandler.removeCallbacks(mUpdateImageView);
					mHandler.post(mUpdateImageView);
	            }
			}
        }
		else
		{
			subTitleInfoOps.removeAllSubTitleInfo();
			for(int i = 0; i < mTextViewInfo.length; i++)
			{
				if(mTextViewInfo[i].used == true)
				{
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

	private void subTitleDraw(int numOfTextViewDrawed, int subDispPos, Rect screenBound, Rect textBound, List<TimedText.Style> styleList)
	{
		/*SUB_DISPPOS_DEFAULT
		    SUB_DISPPOS_BOT_LEFT
		    SUB_DISPPOS_BOT_MID
		    SUB_DISPPOS_BOT_RIGHT
		    SUB_DISPPOS_MID_LEFT
		    SUB_DISPPOS_MID_MID
		    SUB_DISPPOS_MID_RIGHT
		    SUB_DISPPOS_TOP_LEFT
		    SUB_DISPPOS_TOP_MID
		    SUB_DISPPOS_TOP_RIGHT
	    */
	    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
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
		
		if(screenBound != null && textBound != null)
		{
			screenleft = screenBound.left;
			screentop = screenBound.top;
			screenright = screenBound.right;
			screenbottom = screenBound.bottom;

			zoom = (float)mScreenWidth / (float)(screenright - screenleft);
			
			screenleft = (int)(screenleft * zoom);
			screentop = (int)(screentop * zoom);
			screenright = (int)(screenright * zoom);
			screenbottom= (int)(screenbottom* zoom);

			textleft = (int)(textBound.left * zoom);
			texttop = (int)(textBound.top * zoom);
			textright = (int)(textBound.right * zoom);
			textbottom = (int)(textBound.bottom * zoom);
		}

		if(subDispPos == TimedText.SUB_DISPPOS_DEFAULT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
			params.setMargins(0,0,0,140);
			
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_BOT_LEFT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE); 
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
			params.setMargins(textleft,0,0,screenbottom - textbottom);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_BOT_MID)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
			params.setMargins(0,0,0,screenbottom - textbottom);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_BOT_RIGHT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
			params.setMargins(0,0,screenright - textright,screenbottom - textbottom);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_MID_LEFT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(textleft,0,0,0);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_MID_MID)
		{
			params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(0,0,0,0);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_MID_RIGHT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
			params.setMargins(0,0,screenright - textright,0);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_TOP_LEFT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE); 
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.setMargins(textleft,texttop,0,0);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_TOP_MID)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
			params.setMargins(0,texttop,0,0);
		}
		else if(subDispPos == TimedText.SUB_DISPPOS_TOP_RIGHT)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE); 
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.setMargins(0,texttop,screenright - textright,0);
		}
		mTextViewInfo[numOfTextViewDrawed].textView.setLayoutParams(params);
		
		if(styleList != null)
		{
			TimedText.Style style = styleList.get(0);
			/*
			 isBold
		        isItalic
		        isUnderlined
		        fontSize
		        colorRGBA
			*/
			isBold = style.isBold;
			isItalic = style.isItalic;
			isUnderlined = style.isUnderlined;
			fontSize = style.fontSize;
			colorRGBA = style.colorRGBA;
		}
		mTextViewInfo[numOfTextViewDrawed].textView.setTextColor(colorRGBA);
		mTextViewInfo[numOfTextViewDrawed].textView.setTextSize((float)((float)fontSize * zoom / 1.5));
		
		if(isBold == true && isItalic == false)
		{
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
		}
		else if(isItalic == true && isBold == false)
		{
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
		}
		else if(isItalic == true && isBold == true)
		{
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
		}
		else
		{
			mTextViewInfo[numOfTextViewDrawed].textView.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
		}

		mSubTitleHandler.sendEmptyMessage(TEXTVIEW_UPDATE);
	}

	private void subTitleNoDraw()
	{
		mSubTitleHandler.sendEmptyMessage(TEXTVIEW_UPDATE);
	}

    // Below are notifications from ControllerOverlay
    @Override
    public void onPlayPause() {
        if (mVideoView.isPlaying()) {
            pauseVideo();
        } else {
            playVideo();
        }
    }

    @Override
    public void onSeekStart() {
        mDragging = true;
    }

    @Override
    public void onSeekMove(int time) {
        mVideoView.seekTo(time);
    }

    @Override
    public void onSeekEnd(int time, int start, int end) {
        mDragging = false;
        mVideoView.seekTo(time);
        setProgress();
    }

    @Override
    public void onShown() {
        mShowing = true;
        setProgress();
        showSystemUi(true);
    }

    @Override
    public void onHidden() {
        mShowing = false;
        showSystemUi(false);
    }

    @Override
    public void onReplay() {
        startVideo();
    }

    // Below are key events passed from MovieActivity.
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        // Some headsets will fire off 7-10 events on a single click
        if (event.getRepeatCount() > 0) {
            return isMediaKey(keyCode);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mVideoView.isPlaying()) {
                    pauseVideo();
                } else {
                    playVideo();
                }
                return true;
            case KEYCODE_MEDIA_PAUSE:
                if (mVideoView.isPlaying()) {
                    pauseVideo();
                }
                return true;
            case KEYCODE_MEDIA_PLAY:
                if (!mVideoView.isPlaying()) {
                    playVideo();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                // TODO: Handle next / previous accordingly, for now we're
                // just consuming the events.
                return true;
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return isMediaKey(keyCode);
    }

    private static boolean isMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE;
    }

    // We want to pause when the headset is unplugged.
    private class AudioBecomingNoisyReceiver extends BroadcastReceiver {

        public void register() {
            mContext.registerReceiver(this,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mVideoView.isPlaying()) pauseVideo();
        }
    }
}

class Bookmarker {
    private static final String TAG = "Bookmarker";

    private static final String BOOKMARK_CACHE_FILE = "bookmark";
    private static final int BOOKMARK_CACHE_MAX_ENTRIES = 100;
    private static final int BOOKMARK_CACHE_MAX_BYTES = 10 * 1024;
    private static final int BOOKMARK_CACHE_VERSION = 1;

    private static final int HALF_MINUTE = 30 * 1000;
    private static final int TWO_MINUTES = 4 * HALF_MINUTE;

    private final Context mContext;

    public Bookmarker(Context context) {
        mContext = context;
    }

    public void setBookmark(Uri uri, int bookmark, int duration) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeUTF(uri.toString());
            dos.writeInt(bookmark);
            dos.writeInt(duration);
            dos.flush();
            cache.insert(uri.hashCode(), bos.toByteArray());
        } catch (Throwable t) {
            Log.w(TAG, "setBookmark failed", t);
        }
    }

    public Integer getBookmark(Uri uri) {
        try {
            BlobCache cache = CacheManager.getCache(mContext,
                    BOOKMARK_CACHE_FILE, BOOKMARK_CACHE_MAX_ENTRIES,
                    BOOKMARK_CACHE_MAX_BYTES, BOOKMARK_CACHE_VERSION);

            byte[] data = cache.lookup(uri.hashCode());
            if (data == null) return null;

            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(data));

            String uriString = DataInputStream.readUTF(dis);
            int bookmark = dis.readInt();
            int duration = dis.readInt();

            if (!uriString.equals(uri.toString())) {
                return null;
            }

            if ((bookmark < HALF_MINUTE) || (duration < TWO_MINUTES)
                    || (bookmark > (duration - HALF_MINUTE))) {
                return null;
            }
            return Integer.valueOf(bookmark);
        } catch (Throwable t) {
            Log.w(TAG, "getBookmark failed", t);
        }
        return null;
    }
}
