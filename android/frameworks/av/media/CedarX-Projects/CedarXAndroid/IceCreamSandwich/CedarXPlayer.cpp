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
//#define LOG_NDEBUG 0
#include <CDX_LogNDebug.h>
#define LOG_TAG "CedarXPlayer"
#include <CDX_Debug.h>

#include "CDX_Version.h"

#include <dlfcn.h>
#if (CEDARX_ANDROID_VERSION >= 7)
#include <utils/Trace.h>
#endif 
#include "CedarXPlayer.h"
#include "FileOperateCB.h"
#include "CedarXSoftwareRenderer.h"

#include <CDX_ErrorType.h>
#include <CDX_Config.h>
#include <libcedarv.h>
#include <CDX_Fileformat.h>

#include <binder/IPCThreadState.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/IAudioFlinger.h>

#if (CEDARX_ANDROID_VERSION < 7)
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/foundation/ADebug.h>
#else
#include <media/stagefright/foundation/ADebug.h>
#endif
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/foundation/ALooper.h>

#include <AwesomePlayer.h>

#if (CEDARX_ANDROID_VERSION < 7)
#include <surfaceflinger/Surface.h>
#include <surfaceflinger/ISurfaceComposer.h>
#endif

//#include <gui/ISurfaceTexture.h>
//#include <gui/SurfaceTextureClient.h>
#include <gui/IGraphicBufferProducer.h>
#include <gui/Surface.h>

#include <include_sft/StreamingSource.h>
#include <cutils/properties.h>

#include <CDX_Subtitle.h>
#include <include_render/SubtitleRender.h>
#include <include_render/video_render.h>

#include "unicode/ucnv.h"
#include "unicode/ustring.h"


#define PROP_CHIP_VERSION_KEY  "media.cedarx.chipver"
#define PROP_CONSTRAINT_RES_KEY  "media.cedarx.cons_res"

extern "C" {
extern uint32_t cedarv_address_phy2vir(void *addr);
}

namespace android {


#define DYNAMIC_ROTATION_ENABLE 1

#define SUPPORT_3D_DOUBLE_STREAM (1)
//static int32_t getmRotation = 0;
//static int32_t dy_rotate_flag=0;

extern "C" int32_t CedarXPlayerCallbackWrapper(void *cookie, int32_t event, void *info);

struct CedarXLocalRenderer : public CedarXRenderer {
    CedarXLocalRenderer(
            const sp<ANativeWindow> &nativeWindow, const sp<MetaData> &meta)
        : mTarget(new CedarXSoftwareRenderer(nativeWindow, meta)) {
    }

    int32_t perform(int32_t cmd, int32_t para) {
        return mTarget->perform(cmd, para);
    }

    void render(const void *data, size_t size) {

    }
    int32_t dequeueFrame(ANativeWindowBufferCedarXWrapper *pObject){
        return mTarget->dequeueFrame(pObject);
    }
    int32_t enqueueFrame(ANativeWindowBufferCedarXWrapper *pObject)
    {
        return mTarget->enqueueFrame(pObject);
    }
protected:
    virtual ~CedarXLocalRenderer() {
        delete mTarget;
        mTarget = NULL;
    }

private:
    CedarXSoftwareRenderer *mTarget;

    CedarXLocalRenderer(const CedarXLocalRenderer &);
    CedarXLocalRenderer &operator=(const CedarXLocalRenderer &);;
};

#if 0
#define GET_CALLING_PID	(IPCThreadState::self()->getCallingPid())
static int32_t getCallingProcessName(char *name)
{
	char proc_node[128];

	if (name == 0)
	{
		LOGE("error in params");
		return -1;
	}
	
	memset(proc_node, 0, sizeof(proc_node));
	sprintf(proc_node, "/proc/%d/cmdline", GET_CALLING_PID);
	int32_t fp = ::open(proc_node, O_RDONLY);
	if (fp > 0) 
	{
		memset(name, 0, 128);
		::read(fp, name, 128);
		::close(fp);
		fp = 0;
		LOGV("Calling process is: %s", name);
        return OK;
	}
	else 
	{
		LOGE("Obtain calling process failed");
        return -1;
    }
}

int32_t IfContextNeedGPURender()
{
    int32_t GPURenderFlag = 0;
    int32_t ret;
    char mCallingProcessName[128];    
    memset(mCallingProcessName, 0, sizeof(mCallingProcessName));	
    ret = getCallingProcessName(mCallingProcessName);	
    if(ret != OK)
    {
        return 0;
    }
    //LOGD("~~~~~ mCallingProcessName %s", mCallingProcessName);

    if (strcmp(mCallingProcessName, "com.google.android.youtube") == 0)
    {           
        LOGV("context is youtube");
        GPURenderFlag = 1;
    }
    else
    {
        GPURenderFlag = 0;
    }
    return GPURenderFlag;
}
#endif
CedarXPlayer::CedarXPlayer() :
	mSftSource(NULL),
	mAudioPlayer(NULL),
	mFlags(0),
	mCanSeek(0) {
	LOGV("Construction, this %p", this);
	mAudioSink = NULL;
	mAwesomePlayer = NULL;

	mExtendMember = (CedarXPlayerExtendMember *)malloc(sizeof(CedarXPlayerExtendMember));
	memset(mExtendMember, 0, sizeof(CedarXPlayerExtendMember));

	reset_l();

	LOGD("=============================================\n");
	LOGD("CDX_PLATFORM: %s\n", CDX_PLATFORM);
	LOGD("CDX_SVN_REPOSITORY: %s\n", CDX_SVN_REPOSITORY);
	LOGD("CDX_SVN_VERSION: %s\n", CDX_SVN_VERSION);
	LOGD("CDX_SVN_COMMITTER: %s\n", CDX_SVN_COMMITTER);
	LOGD("CDX_SVN_DATE: %s\n", CDX_SVN_DATE);
	LOGD("CDX_RELEASE_AUTHOR: %s\n", CDX_RELEASE_AUTHOR);
	LOGD("=============================================\n");
	
	CDXPlayer_Create((void**)&mPlayer);
    
	mPlayer->control(mPlayer, CDX_CMD_REGISTER_CALLBACK, (unsigned int32_t)&CedarXPlayerCallbackWrapper, (unsigned int32_t)this);
	mIsCedarXInitialized = true;
	mMaxOutputWidth 	= 0;
	mMaxOutputHeight 	= 0;
	mVideoRenderer 		= NULL;
    mVpsspeed           = 0;

	mVideoScalingMode = NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW;

    mDynamicRotation                    = 0;
    mInitRotation                     	= 0;
    m3dModeDoubleStreamFlag             = 0;
    mAudioTrackIndex 	= 0;
    mSubTrackIndex 		= 0;

    mIsDRMMedia		= false;
    mHDMIPlugged  	= false;
    mHDMIListener 	= new HDMIListerner();

	char value[PROPERTY_VALUE_MAX];
	if (property_get("ro.sys.mutedrm", value, NULL)
	    && (!strncasecmp(value, "true", 4))) {
		mMuteDRMWhenHDMI = true;
	} else {
		mMuteDRMWhenHDMI = false;
	}
}

CedarXPlayer::~CedarXPlayer() {
	LOGD("~CedarXPlayer()");
	if(mAwesomePlayer) {
		delete mAwesomePlayer;
		mAwesomePlayer = NULL;
	}

	if (mSftSource != NULL) {
		mSftSource.clear();
		mSftSource = NULL;
	}

	if(mIsCedarXInitialized){
		mPlayer->control(mPlayer, CDX_CMD_STOP_ASYNC, 0, 0);
		CDXPlayer_Destroy(mPlayer);
		mPlayer = NULL;
		mIsCedarXInitialized = false;
	}
//    if(pCedarXPlayerAdapter)
//    {
//        delete pCedarXPlayerAdapter;
//        pCedarXPlayerAdapter = NULL;
//    }

	if (mAudioPlayer) {
		LOGV("delete mAudioPlayer");
		delete mAudioPlayer;
		mAudioPlayer = NULL;
	}

	if(mExtendMember != NULL){
		free(mExtendMember);
		mExtendMember = NULL;
	}
	if(mHDMIListener) {
		mHDMIListener->setNotifyCallback(0, 0);
		mHDMIListener->stop();
		delete mHDMIListener;
		mHDMIListener = NULL;
	}
	//LOGV("Deconstruction %x",mFlags);
}

void CedarXPlayer::setUID(uid_t uid) {
    LOGV("CedarXPlayer running on behalf of uid %d", uid);

    mUID = uid;
    mUIDValid = true;
}

void CedarXPlayer::setListener(const wp<MediaPlayerBase> &listener) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	Mutex::Autolock autoLock(mLock);
	mListener = listener;
}

status_t CedarXPlayer::setDataSource(const char *uri, const KeyedVector<
		String8, String8> *headers) {
	//Mutex::Autolock autoLock(mLock);
	LOGV("CedarXPlayer::setDataSource (%s)", uri);
	memset(&mMediaInfo, 0, sizeof(CedarXMediaInformations));
	mUri = uri;

	if(!strncasecmp("file:///", uri, 8)) {
		mUri = &uri[8];
	}
	mSourceType = SOURCETYPE_URL;
	if (headers) {
	    mUriHeaders = *headers;
	    mPlayer->control(mPlayer, CDX_CMD_SET_URL_HEADERS, (unsigned int32_t)&mUriHeaders, 0);
	} else {
		mPlayer->control(mPlayer, CDX_CMD_SET_URL_HEADERS, (unsigned int32_t)0, 0);
	}
//	mUriHeaders.add(String8("SeekToPos"),String8("0"));
	ssize_t index = mUriHeaders.indexOfKey(String8("SeekToPos"));
	if(index >= 0 && !strncasecmp(mUriHeaders.valueAt(index), "0", 1)) {
		//DLNA do not support seek
		mPlayer->control(mPlayer, CDX_CMD_SET_DISABLE_SEEK, 1, 0);
	}
    status_t ret = mPlayer->control(mPlayer, CDX_SET_DATASOURCE_URL, (unsigned int32_t)mUri.string(), 0);
    if(ret == OK)
    {
        setDataSource_post();
    	return OK;
    }
    else
    {
        return UNKNOWN_ERROR;
    }
}

status_t CedarXPlayer::setDataSource(int32_t fd, int64_t offset, int64_t length) {
	//Mutex::Autolock autoLock(mLock);
	LOGV("CedarXPlayer::setDataSource fd");
	CedarXExternFdDesc ext_fd_desc;
	memset(&mMediaInfo, 0, sizeof(CedarXMediaInformations));
	ext_fd_desc.fd = fd;
	ext_fd_desc.offset = offset;
	ext_fd_desc.length = length;
	mSourceType = SOURCETYPE_FD;

	setDataSource_pre();
	status_t ret = OK;
	if((ret = mPlayer->control(mPlayer, CDX_SET_DATASOURCE_FD, (unsigned int32_t)&ext_fd_desc, 0))) {
		if (ret == CDX_ERROR_UNSUPPORT_RAWMUSIC) {
			//fd music  
			mAwesomePlayer = new AwesomePlayer;
			mAwesomePlayer->setListener(mListener);
			if(mAudioSink != NULL) {
				mAwesomePlayer->setAudioSink(mAudioSink);
			}

			if(mFlags & LOOPING) {
				mAwesomePlayer->setLooping(!!(mFlags & LOOPING));
			}
			mAwesomePlayer->setDataSource(dup(fd), offset, length);
			mAwesomePlayer->prepareAsync();
			return OK;			
		}
		else {
			return UNKNOWN_ERROR;
		}
	}
	setDataSource_post();
	return OK;
}

status_t CedarXPlayer::setDataSource(const sp<IStreamSource> &source) {
	RefBase *refValue;
	//Mutex::Autolock autoLock(mLock);
	LOGV("CedarXPlayer::setDataSource stream");
	memset(&mMediaInfo, 0, sizeof(CedarXMediaInformations));

	mSftSource = new StreamingSource(source);
	refValue = mSftSource.get();

	mSourceType = SOURCETYPE_SFT_STREAM;
	mPlayer->control(mPlayer, CDX_SET_DATASOURCE_SFT_STREAM, (unsigned int32_t)refValue, 0);
	return OK;
}


status_t CedarXPlayer::setDataSource_pre()
{
	//do common init here.
	return OK;
}
status_t CedarXPlayer::setDataSource_post()
{
	mPlayer->control(mPlayer, CDX_CMD_GET_MEDIAINFO, (unsigned int32_t)&mMediaInfo, 0);
	return OK;
}

status_t CedarXPlayer::setParameter(int32_t key, const Parcel &request)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    status_t ret = OK;
    switch(key)
    {
        case PARAM_KEY_ENCRYPT_FILE_TYPE_DISABLE:
        {
            mExtendMember->encrypt_type = PARAM_KEY_ENCRYPT_FILE_TYPE_DISABLE;
            LOGV("setParameter: encrypt_type = [%d]",mExtendMember->encrypt_type);
            ret = OK;
            break;
        }
        case PARAM_KEY_ENCRYPT_ENTIRE_FILE_TYPE:
        {
            mExtendMember->encrypt_type = PARAM_KEY_ENCRYPT_ENTIRE_FILE_TYPE;
            mExtendMember->encrypt_file_format = request.readInt32();
            LOGV("setParameter: encrypt_type = [%d]",mExtendMember->encrypt_type);
            LOGV("setParameter: encrypt_file_format = [%d]",mExtendMember->encrypt_file_format);
            ret = OK;
            break;
        }
        case PARAM_KEY_ENCRYPT_PART_FILE_TYPE:
        {
            mExtendMember->encrypt_type = PARAM_KEY_ENCRYPT_PART_FILE_TYPE;
            mExtendMember->encrypt_file_format = request.readInt32();
            LOGV("setParameter: encrypt_type = [%d]",mExtendMember->encrypt_type);
            LOGV("setParameter: encrypt_file_format = [%d]",mExtendMember->encrypt_file_format);
            ret = OK;
            break;
        }
        case PARAM_KEY_SET_AV_SYNC:
        {
        	if(mPlayer != NULL) {
        		int32_t av_sync = request.readInt32();
        		mPlayer->control(mPlayer, CDX_CMD_SET_AV_SYNC, av_sync, 0);
        	}
        	break;
        }
        case PARAM_KEY_ENABLE_KEEP_FRAME:
        {
        	break;
        }
        case PARAM_KEY_ENABLE_BOOTANIMATION:
        {
            mExtendMember->bootanimation_enable = 1;
            LOGV("setParameter: bootanimation_enable = [%d]",mExtendMember->bootanimation_enable);
            ret = OK;
            break;
        }

        case PARAM_KEY_CLEAR_BUFFER:
        	if(mPlayer != NULL) {
        		int32_t clear_buffer = request.readInt32();
        		mPlayer->control(mPlayer, CDX_CMD_CLEAR_BUFFER_ASYNC, clear_buffer, 0);
        	}
        	break;

        case PARAM_KEY_SWITCH_CHANNEL:
        	if(mPlayer != NULL) {
        		int32_t channel = request.readInt32();
        		LOGV("PARAM_KEY_SWITCH_CHANNEL, %d", channel);
        		mPlayer->control(mPlayer, CDX_CMD_SWITCH_AUDIO_CHANNEL, channel, 0);
        	}
        	break;
        default:
        {
            LOGW("unknown Key[0x%x] of setParameter", key);
            ret = ERROR_UNSUPPORTED;
            break;
        }
    }
	return ret;
}

status_t CedarXPlayer::getParameter(int32_t key, Parcel *reply) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	return OK;
}

void CedarXPlayer::reset() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	//Mutex::Autolock autoLock(mLock);
	LOGV("RESET????, context: %p",this);
	
	mPlayerState = PLAYER_STATE_UNKOWN;

	if(mAwesomePlayer) {
		mAwesomePlayer->reset();
	}

	if (mPlayer != NULL) {
		mPlayer->control(mPlayer, CDX_CMD_RESET, 0, 0);

		if (mIsCedarXInitialized) {
			mPlayer->control(mPlayer, CDX_CMD_STOP_ASYNC, 0, 0);
			CDXPlayer_Destroy(mPlayer);
			mPlayer = NULL;
			mIsCedarXInitialized = false;
		}
	}

	reset_l();
}

void CedarXPlayer::reset_l() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);

	notifyListener_l(MEDIA_STOPPED);

	mPlayerState = PLAYER_STATE_UNKOWN;
	pause_l(true);

	{
		Mutex::Autolock autoLock(mLockNativeWindow);
		if (mVideoRenderer != NULL) {
			mVideoRenderer.clear();
			mVideoRenderer = NULL;
		}
	}

	if(mAudioPlayer){
		delete mAudioPlayer;
		mAudioPlayer = NULL;
	}
	LOGV("RESET End");

	mDurationUs = 0;
	mFlags = 0;
	mFlags |= RESTORE_CONTROL_PARA;
	mVideoWidth = mVideoHeight = -1;

	mTagPlay = 1;
	mSeeking = false;
	mSeekNotificationSent = false;
	mSeekTimeUs = 0;

	mAudioTrackIndex = 0;
	mBitrate = -1;
	mUri.setTo("");
	mUriHeaders.clear();

	if (mSftSource != NULL) {
		mSftSource.clear();
		mSftSource = NULL;
	}

	memset(&mSubtitleParameter, 0, sizeof(struct SubtitleParameter));
}

void CedarXPlayer::notifyListener_l(int32_t msg, int32_t ext1, int32_t ext2) {
	if (mListener != NULL) {
		sp<MediaPlayerBase> listener = mListener.promote();

		if (listener != NULL) {
			if(mSourceType == SOURCETYPE_SFT_STREAM
				&& msg== MEDIA_INFO && (ext1 == MEDIA_INFO_BUFFERING_START
						|| ext1 == MEDIA_INFO_BUFFERING_END
						|| ext1 == MEDIA_BUFFERING_UPDATE)) {
				LOGV("skip notifyListerner");
				return;
			}

			//if(msg != MEDIA_BUFFERING_UPDATE)
			listener->sendEvent(msg, ext1, ext2);
		}
	}
}

status_t CedarXPlayer::play() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    //LOGD("play()!");
	SuspensionState *state = &mSuspensionState;

	if(mAwesomePlayer) {
		return mAwesomePlayer->play();
	}

	if(mFlags & NATIVE_SUSPENDING) {
		LOGW("you has been suspend by other's");
		mFlags &= ~NATIVE_SUSPENDING;
		state->mFlags |= PLAYING;
		return resume();
	}

	Mutex::Autolock autoLock(mLock);

	mFlags &= ~CACHE_UNDERRUN;

	status_t ret = play_l(CDX_CMD_START_ASYNC);

	LOGV("CedarXPlayer::play() end");
	return ret;
}

status_t CedarXPlayer::play_l(int32_t command)
{
	LOGV("CedarXPlayer::play_l()");
    
	if (mFlags & PLAYING)
	{
		return OK;
	}

	int32_t outputSetting = 0;

	if (mSourceType == SOURCETYPE_SFT_STREAM)
	{
		if (!(mFlags & (PREPARED | PREPARING)))
		{
			mFlags |= PREPARING;
			mIsAsyncPrepare = true;

			//mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_OUTPUT_SETTING, CEDARX_OUTPUT_SETTING_MODE_PLANNER, 0);
			if(mNativeWindow != NULL)
			{
				Mutex::Autolock autoLock(mLockNativeWindow);
				outputSetting |= CEDARX_OUTPUT_SETTING_MODE_PLANNER;

			}

			mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_OUTPUT_SETTING, outputSetting, 0);
			mPlayer->control(mPlayer, CDX_CMD_PREPARE_ASYNC, 0, 0);
		}
	}
	else if (!(mFlags & PREPARED))
	{
        status_t err = prepare_l();

        if (err != OK)
            return err;
    }

	mFlags |= PLAYING;

	if(mAudioPlayer)
	{
		mAudioPlayer->resume();
		mAudioPlayer->setPlaybackEos(false);
	}

	if(mFlags & RESTORE_CONTROL_PARA){
		if(mMediaInfo.mSubtitleStreamCount > 0) {
			LOGV("Restore control parameter!");
			if(mSubtitleParameter.mSubtitleDelay != 0){
                LOGV("(f:%s, l:%d), set subDelay[%d]", __FUNCTION__, __LINE__, mSubtitleParameter.mSubtitleDelay);
				setSubDelay(mSubtitleParameter.mSubtitleDelay);
			}
		}

		mFlags &= ~RESTORE_CONTROL_PARA;
	}

	if(mSeeking && mTagPlay && mSeekTimeUs > 0){
        if(!(mFlags & PAUSING))
        {
    		mPlayer->control(mPlayer, CDX_CMD_TAG_START_ASYNC, (unsigned int32_t)&mSeekTimeUs, 0);
    		LOGD("--tag play %lldus, mSeeking=%d, mTagPlay=%d",mSeekTimeUs, mSeeking, mTagPlay);
        }
        else
        {
            int64_t nMediaDuration = mMediaInfo.mDuration;
            if(mSeekTimeUs >= nMediaDuration*1000)
            {
                LOGD("mSeekTimeUs[%lld] >= mDuration[%lld]*1000", mSeekTimeUs, nMediaDuration);
                if(nMediaDuration*1000 > 500*1000)
                {
                    mSeekTimeUs = nMediaDuration*1000 - 500*1000;
                }
            }
            mPlayer->control(mPlayer, CDX_CMD_PAUSE_TAG_START_ASYNC, (unsigned int32_t)&mSeekTimeUs, 0);
    		LOGD("--pause_tag_play %lldus, mSeeking=%d, mTagPlay=%d",mSeekTimeUs, mSeeking, mTagPlay);
        }
	}
	else if(mPlayerState == PLAYER_STATE_SUSPEND || mPlayerState == PLAYER_STATE_RESUME){
		mPlayer->control(mPlayer, CDX_CMD_TAG_START_ASYNC, (unsigned int32_t)&mSuspensionState.mPositionUs, 0);
		LOGD("--tag play %lldus",mSuspensionState.mPositionUs);
	}
	else {
		mPlayer->control(mPlayer, command, (unsigned int32_t)&mSuspensionState.mPositionUs, 0);
	}

    //make sure play state is in!
    int32_t wait_time = 0;
    int32_t cdx_state;
    while(1)
    {
        cdx_state = mPlayer->control(mPlayer, CDX_CMD_GETSTATE, 0, 0);
        if(cdx_state == CDX_STATE_EXCUTING)
        {
            //LOGV("(f:%s, l:%d) cdx play alread!", __FUNCTION__, __LINE__);
            break;
        }
        else
        {
            //LOGD("(f:%s, l:%d) cdx_state=0x%x, sleep 10ms to wait cdx to play, wait_time=%d!", __FUNCTION__, __LINE__, cdx_state, wait_time);
            usleep(10*1000);
            wait_time++;
        }
        if(wait_time > 30)  //wait 300ms.
        {
            //LOGD("(f:%s, l:%d) wait cdx to pause fail, break!", __FUNCTION__, __LINE__);
            break;
        }
    }

    mPlayer->control(mPlayer, CDX_CMD_SWITCHSUB, mSubTrackIndex, 0);
    mPlayer->control(mPlayer, CDX_CMD_SWITCHTRACK, mAudioTrackIndex, 0);
	mSeeking = false;
	mTagPlay = 0;
	mPlayerState = PLAYER_STATE_PLAYING;
	mFlags &= ~PAUSING;

	return OK;
}

status_t CedarXPlayer::stop() {
	LOGV("CedarXPlayer::stop");

	if(mAwesomePlayer) {
		return mAwesomePlayer->pause();
	}

	if(mPlayer != NULL){
		mPlayer->control(mPlayer, CDX_CMD_STOP_ASYNC, 0, 0);
	}
	stop_l();

	return OK;
}

status_t CedarXPlayer::stop_l() {
	LOGV("stop() status:%x", mFlags & PLAYING);

	if(!mExtendMember->mPlaybackNotifySend) {
		notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
		LOGD("MEDIA_PLAYBACK_COMPLETE");
		notifyListener_l(MEDIA_PLAYBACK_COMPLETE);
		mExtendMember->mPlaybackNotifySend = 1;
	}

	pause_l(true);

	{
		Mutex::Autolock autoLock(mLockNativeWindow);
		if(mVideoRenderer != NULL)
		{
	        if(1 == mExtendMember->bootanimation_enable)
	        {
	            LOGI("SET LAYER BOTTOM");
	            mExtendMember->bootanimation_enable = 0;
	        }
			mVideoRenderer.clear();
			mVideoRenderer = NULL;
		}
	}

	mFlags &= ~SUSPENDING;
	LOGV("stop finish 1...");

	return OK;
}

status_t CedarXPlayer::pause() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	//Mutex::Autolock autoLock(mLock);
	//LOGD("pause()");

	if(mAwesomePlayer) {
		return mAwesomePlayer->pause();
	}

#if 0
	mFlags &= ~CACHE_UNDERRUN;
	mPlayer->control(mPlayer, CDX_CMD_PAUSE, 0, 0);

	return pause_l(false);
#else
	if (!(mFlags & PLAYING)) {
		return OK;
	}

	pause_l(false);
	mPlayer->control(mPlayer, CDX_CMD_PAUSE_ASYNC, 0, 0);

	return OK;
#endif
}

status_t CedarXPlayer::pause_l(bool at_eos) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);

	mPlayerState = PLAYER_STATE_PAUSE;

	if (!(mFlags & PLAYING)) {
		return OK;
	}

	if (mAudioPlayer != NULL) {
		if (at_eos) {
			// If we played the audio stream to completion we
			// want to make sure that all samples remaining in the audio
			// track's queue are played out.
			mAudioPlayer->pause(true /* playPendingSamples */);
		} else {
			mAudioPlayer->pause();    //AudioRender component will pause too!
		}
	}

	mFlags &= ~PLAYING;
	mFlags |= PAUSING;

	return OK;
}

bool CedarXPlayer::isPlaying() const {
    //LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mAwesomePlayer) {
		return mAwesomePlayer->isPlaying();
	}
	//LOGV("isPlaying cmd mFlags=0x%x",mFlags);
	return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
}

status_t CedarXPlayer::setNativeWindow_l(const sp<ANativeWindow> &native) {
    LOGD("(f:%s, l:%d), native_p[%p]", __FUNCTION__, __LINE__, native.get());
	int32_t i = 0;
	int32_t  outputSetting = 0;

	if(mNativeWindow == NULL && (mFlags&PREPARED))	//* setSurface() is called after prepareAsync(), play not start yet.
	{
		Mutex::Autolock autoLock(mLockNativeWindow);

		LOGI("(f:%s, l:%d) use render GPU 0", __FUNCTION__, __LINE__);
		outputSetting |= CEDARX_OUTPUT_SETTING_MODE_PLANNER;

		mExtendMember->mOutputSetting = outputSetting;
		mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_OUTPUT_SETTING, outputSetting, 0);

	    //0: no rotate, 1: 90 degree (clock wise), 2: 180, 3: 270, 4: horizon flip, 5: vertical flig;
		if(mInitRotation)
		{
			LOGD("GUI_mode_for_android42:init rotate[%d] in setNativeWindow_l()!", mInitRotation);
			mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_ROTATION, mInitRotation, 0);

		}
	}

    mNativeWindow = native;

    if (mVideoWidth <= 0) {
        return OK;
    }

    LOGV("attempting to reconfigure to use new surface");

    bool wasPlaying = (mFlags & PLAYING) != 0;
    pause();
    int32_t cdx_state;
    int32_t wait_time = 0;
    while(1)
    {
        cdx_state = mPlayer->control(mPlayer, CDX_CMD_GETSTATE, 0, 0);
        if(cdx_state == CDX_STATE_PAUSE)
        {
            //LOGD("(f:%s, l:%d) pause success!", __FUNCTION__, __LINE__);
            break;
        }
        else
        {
            //LOGD("(f:%s, l:%d) cdx_state=0x%x, sleep 10ms to wait cdx to pause!", __FUNCTION__, __LINE__, cdx_state);
            usleep(10*1000);
            wait_time++;
        }
        if(wait_time > 30)  //wait 300ms.
        {
            //LOGD("(f:%s, l:%d) wait cdx to pause fail, break!", __FUNCTION__, __LINE__);
            break;
        }
    }
    
    {
    	Mutex::Autolock autoLock(mLockNativeWindow);
        if(mVideoRenderer != NULL)
        {
        	mVideoRenderer.clear();
        	mVideoRenderer = NULL;
        }
    }

    if (wasPlaying) {
        play();
    }

    return OK;
}

status_t CedarXPlayer::setSurface(const sp<Surface> &surface) {
    Mutex::Autolock autoLock(mLock);
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    LOGV("setSurface");
    return setNativeWindow_l(surface);
}

status_t CedarXPlayer::setSurfaceTexture(const sp<IGraphicBufferProducer> &bufferProducer) {
    //Mutex::Autolock autoLock(mLock);

    LOGV("(f:%s, l:%d) surfaceTexture_p=%p", __FUNCTION__, __LINE__, bufferProducer.get());

    status_t err;
    if (bufferProducer != NULL) {
        LOGV("(f:%s, l:%d) surfaceTexture!=NULL", __FUNCTION__, __LINE__);
        err = setNativeWindow_l(new Surface(bufferProducer));
    } else {
        LOGV("(f:%s, l:%d) surfaceTexture==NULL", __FUNCTION__, __LINE__);
        err = setNativeWindow_l(NULL);
    }

    return err;
}

void CedarXPlayer::setAudioSink(const sp<MediaPlayerBase::AudioSink> &audioSink) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	//Mutex::Autolock autoLock(mLock);
	mAudioSink = audioSink;
}

status_t CedarXPlayer::setLooping(bool shouldLoop) {
    LOGV("(f:%s, l:%d), shouldLoop=%d", __FUNCTION__, __LINE__, shouldLoop);
	//Mutex::Autolock autoLock(mLock);
	if (mAwesomePlayer) {
		mAwesomePlayer->setLooping(shouldLoop);
	}

	mFlags = mFlags & ~LOOPING;

	if (shouldLoop) {
		mFlags |= LOOPING;
	}

	return OK;
}

status_t CedarXPlayer::getDuration(int64_t *durationUs) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if (mAwesomePlayer) {
	    int64_t tmp;
	    status_t err = mAwesomePlayer->getDuration(&tmp);

	    if (err != OK) {
	        *durationUs = 0;
	        return OK;
	    }

	    *durationUs = tmp;

	    return OK;
	}

	mPlayer->control(mPlayer, CDX_CMD_GET_DURATION, (unsigned int32_t)durationUs, 0);
	*durationUs *= 1000;
	mDurationUs = *durationUs;

	LOGV("mDurationUs %lld", mDurationUs);

	return OK;
}

status_t CedarXPlayer::getPosition(int64_t *positionUs) {
	LOGV("getPosition");

	if (mAwesomePlayer) {
		int64_t tmp;
		status_t err = mAwesomePlayer->getPosition(&tmp);

		if (err != OK) {
			return err;
		}
        *positionUs = tmp;  //use microsecond! don't convert to millisecond
		LOGV("getPosition:%lld",*positionUs);
		return OK;
	}

	if ((mFlags & AT_EOS) && (mFlags & PLAYING)) {
		*positionUs = mDurationUs;
		return OK;
	}

	{
		//Mutex::Autolock autoLock(mLock);
		if(mPlayer != NULL){
			mPlayer->control(mPlayer, CDX_CMD_GET_POSITION, (unsigned int32_t)positionUs, 0);
		}
	}

	if(mSeeking == true) {
		*positionUs = mSeekTimeUs;
	}

	int64_t nowUs = ALooper::GetNowUs();
//	LOGV("nowUs %lld, mLastGetPositionTimeUs %lld", nowUs, mExtendMember->mLastGetPositionTimeUs);
	//Theoretically, below conndition is satisfied.
	CHECK_GT(nowUs, mExtendMember->mLastGetPositionTimeUs);

	if((nowUs - mExtendMember->mLastGetPositionTimeUs) < 40000ll) {
		*positionUs = mExtendMember->mLastPositionUs;
		return OK;
	}
	mExtendMember->mLastGetPositionTimeUs = nowUs;

	*positionUs = (*positionUs / 1000) * 1000; //to fix android 4.0 cts bug
	mExtendMember->mLastPositionUs = *positionUs;
//	LOGV("getPosition: %lld mSeekTimeUs:%lld, nowUs %lld",
//			*positionUs / 1000, mSeekTimeUs, nowUs);

	return OK;
}

status_t CedarXPlayer::seekTo(int64_t timeMs) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);

	LOGV("seek to time %lld, mSeeking %d", timeMs, mSeeking);
	if (mAwesomePlayer)
	{
		int32_t ret;
		ret = mAwesomePlayer->seekTo(timeMs * 1000);
		return ret;
	}
	int64_t currPositionUs;
    if(mPlayer->control(mPlayer, CDX_CMD_SUPPORT_SEEK, 0, 0) == 0)
    {
    	notifyListener_l(MEDIA_SEEK_COMPLETE);
        return OK;
    }

    if(mSeeking && !(mFlags & PAUSING))
    {
    	notifyListener_l(MEDIA_SEEK_COMPLETE);
    	mSeekNotificationSent = true;
    	return OK;
    }

	getPosition(&currPositionUs);

	{
		Mutex::Autolock autoLock(mLock);
		mSeekNotificationSent = false;
		LOGV("seek cmd to %lld s start", timeMs/1000);

		if (mFlags & CACHE_UNDERRUN) {
			mFlags &= ~CACHE_UNDERRUN;
			play_l(CDX_CMD_START_ASYNC);
		}

		mSeekTimeUs = timeMs * 1000;
		mSeeking = true;
		//This is necessary, if apps want position after seek operation
		//immediately, then the diff between current time and
		// mExtendMember->mLastPositionUs may be smaller than 40ms.
		//In this case, the position we returned is same as that before seek.
		//We can fix this by setting mExtendMember->mLastGetPositionTimeUs to 0
		//or setting mExtendMember->mLastPositionUs to mSeekTimeUs;

//		mExtendMember->mLastGetPositionTimeUs = 0;
		mExtendMember->mLastPositionUs        = mSeekTimeUs;

		mFlags &= ~(AT_EOS | AUDIO_AT_EOS | VIDEO_AT_EOS);

		if (!(mFlags & PLAYING)) {
			LOGV( "seeking while paused, mFlags=0x%x,sending SEEK_COMPLETE notification"
						" immediately.", mFlags);
			mTagPlay = 1;
			notifyListener_l(MEDIA_SEEK_COMPLETE);
			mSeekNotificationSent = true;
			return OK;
		}
	}

	//mPlayer->control(mPlayer, CDX_CMD_SET_AUDIOCHANNEL_MUTE, 3, 0);
	mPlayer->control(mPlayer, CDX_CMD_SEEK_ASYNC, (int32_t)timeMs, (int32_t)(currPositionUs/1000));

	LOGV("seek cmd to %lld s end", timeMs/1000);

	return OK;
}

void CedarXPlayer::finishAsyncPrepare_l(int32_t err){
    LOGV("(f:%s, l:%d) err[%d]", __FUNCTION__, __LINE__, err);
	if (mAwesomePlayer) {
		//fd music
		return;
	}
	if(err == CDX_ERROR_UNSUPPORT_USESFT) {
		//current http+mp3 etc goes here
		mAwesomePlayer = new AwesomePlayer;
		mAwesomePlayer->setListener(mListener);
		if(mAudioSink != NULL) {
			mAwesomePlayer->setAudioSink(mAudioSink);
		}

		if(mFlags & LOOPING) {
			mAwesomePlayer->setLooping(!!(mFlags & LOOPING));
		}
		mAwesomePlayer->setDataSource(mUri.string(), &mUriHeaders);
		mAwesomePlayer->prepareAsync();
		return;
	}

	if(err < 0){
		LOGE("CedarXPlayer:prepare error! %d", err);
		abortPrepare(UNKNOWN_ERROR);
		return;
	}

	mPlayer->control(mPlayer, CDX_CMD_GET_STREAM_TYPE, (unsigned int32_t)&mStreamType, 0);
	if(mSourceType != SOURCETYPE_FD && mSourceType != SOURCETYPE_URL) {
		mPlayer->control(mPlayer, CDX_CMD_GET_MEDIAINFO, (unsigned int32_t)&mMediaInfo, 0);
	}
	//if(mStreamType != CEDARX_STREAM_LOCALFILE) {
	mFlags &= ~CACHE_UNDERRUN;
	//}
	//must be same with the value set to native_window_set_buffers_geometry()!
	mVideoWidth  = mMediaInfo.mVideoInfo[0].mFrameWidth; 
	mVideoHeight = mMediaInfo.mVideoInfo[0].mFrameHeight;
	mCanSeek = mMediaInfo.mFlags & 1;
	if (mVideoWidth && mVideoHeight) {
        LOGD("(f:%s, l:%d) mVideoWidth[%d], mVideoHeight[%d], notifyListener_l(MEDIA_SET_VIDEO_SIZE_)", __FUNCTION__, __LINE__, mVideoWidth, mVideoHeight);
		notifyListener_l(MEDIA_SET_VIDEO_SIZE, mVideoWidth, mVideoHeight);
	}
	else {
		LOGW("unkown video size after prepared");
		//notifyListener_l(MEDIA_SET_VIDEO_SIZE, 640, 480);
	}
	mFlags &= ~(PREPARING|PREPARE_CANCELLED);
	mFlags |= PREPARED;

	//mPlayer->control(mPlayer, CDX_CMD_SET_AUDIOCHANNEL_MUTE, 1, 0);

	if(mIsAsyncPrepare && mSourceType != SOURCETYPE_SFT_STREAM){
		notifyListener_l(MEDIA_PREPARED);
	}

	if(mHDMIListener && mIsDRMMedia) {
		/*only  start thread if media is encrypted*/
		mHDMIListener->setNotifyCallback(this, HDMINotify);
		mHDMIListener->start();
	}

	return;
}

void CedarXPlayer::finishSeek_l(int32_t err){
	Mutex::Autolock autoLock(mLock);
	LOGV("finishSeek_l");

	if(mAudioPlayer){
		mAudioPlayer->seekTo(0);
	}
	mSeeking = false;
	if (!mSeekNotificationSent) {
		LOGV("MEDIA_SEEK_COMPLETE return");
		notifyListener_l(MEDIA_SEEK_COMPLETE);
		mSeekNotificationSent = true;
	}
	//mPlayer->control(mPlayer, CDX_CMD_SET_AUDIOCHANNEL_MUTE, 0, 0);

	return;
}

status_t CedarXPlayer::prepareAsync() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	Mutex::Autolock autoLock(mLock);
	int32_t  outputSetting = 0;
	int32_t  disable_media_type = 0;
	char prop_value[128];

	if ((mFlags & PREPARING) || (mPlayer == NULL)) {
		return UNKNOWN_ERROR; // async prepare already pending
	}

	property_get(PROP_CHIP_VERSION_KEY, prop_value, "3");
	mPlayer->control(mPlayer, CDX_CMD_SET_SOFT_CHIP_VERSION, atoi(prop_value), 0);

#if 1
	if (atoi(prop_value) == 5) {
		property_get(PROP_CONSTRAINT_RES_KEY, prop_value, "1");
		if (atoi(prop_value) == 1) {
			mPlayer->control(mPlayer, CDX_CMD_SET_MAX_RESOLUTION, 1288<<16 | 1288, 0);
		}
	}
#endif

	if (mSourceType == SOURCETYPE_SFT_STREAM) {
		notifyListener_l(MEDIA_PREPARED);
		return OK;
	}

	mFlags |= PREPARING;
	mIsAsyncPrepare = true;


	if(mSourceType == SOURCETYPE_SFT_STREAM) {
		outputSetting |= CEDARX_OUTPUT_SETTING_MODE_PLANNER;
	}

	if(mNativeWindow != NULL) {
		Mutex::Autolock autoLock(mLockNativeWindow);

		LOGI("use render GPU");
		outputSetting |= CEDARX_OUTPUT_SETTING_MODE_PLANNER;

	}

	//outputSetting |= CEDARX_OUTPUT_SETTING_MODE_PLANNER;
	mExtendMember->mPlaybackNotifySend = 0;
	mExtendMember->mOutputSetting = outputSetting;
	mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_OUTPUT_SETTING, outputSetting, 0);

    
    //0: no rotate, 1: 90 degree (clock wise), 2: 180, 3: 270, 4: horizon flip, 5: vertical flig;
	if(mInitRotation)
	{
		LOGD("GUI_mode_for_android42:init rotate[%d] in prepareAsync()!", mInitRotation);
		mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_ROTATION, mInitRotation, 0);
	}

//	mMaxOutputWidth = 720;
//	mMaxOutputHeight = 576;

	//scale down in decoder when using GPU
#if 0 //* chenxiaochuan. We need not to limite the picture size because pixel format is transformed by decoder.
	if(outputSetting & CEDARX_OUTPUT_SETTING_MODE_PLANNER) {
		mMaxOutputWidth  = (mMaxOutputWidth > 1280 || mMaxOutputWidth <= 0) ? 1280 : mMaxOutputWidth;
		mMaxOutputHeight = (mMaxOutputHeight > 720 || mMaxOutputHeight <= 0) ? 720 : mMaxOutputHeight;
	}
#endif

	if(mMaxOutputWidth && mMaxOutputHeight) {
		LOGV("Max ouput size %dX%d", mMaxOutputWidth, mMaxOutputHeight);
		mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_MAXWIDTH, mMaxOutputWidth, 0);
		mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_MAXHEIGHT, mMaxOutputHeight, 0);
	}

#if 0
	disable_media_type |= CDX_MEDIA_TYPE_DISABLE_MPG | CDX_MEDIA_TYPE_DISABLE_TS | CDX_MEDIA_TYPE_DISABLE_ASF;
	disable_media_type |= CDX_CODEC_TYPE_DISABLE_MPEG2 | CDX_CODEC_TYPE_DISABLE_VC1;
	disable_media_type |= CDX_CODEC_TYPE_DISABLE_WMA;
	mPlayer->control(mPlayer, CDX_CMD_DISABLE_MEDIA_TYPE, disable_media_type, 0);
#endif

	//mPlayer->control(mPlayer, CDX_SET_THIRDPART_STREAM, CEDARX_THIRDPART_STREAM_USER0, 0);
	//mPlayer->control(mPlayer, CDX_SET_THIRDPART_STREAM, CEDARX_THIRDPART_STREAM_USER0, CDX_MEDIA_FILE_FMT_AVI);
	//int32_t     encrypt_enable;
    //CDX_MEDIA_FILE_FORMAT  encrypt_file_format;
	if (PARAM_KEY_ENCRYPT_ENTIRE_FILE_TYPE == mExtendMember->encrypt_type)
	{
        mPlayer->control(mPlayer, CDX_SET_THIRDPART_STREAM, CEDARX_THIRDPART_STREAM_USER0, mExtendMember->encrypt_file_format);
    }
    else if (PARAM_KEY_ENCRYPT_PART_FILE_TYPE == mExtendMember->encrypt_type)
    {
        mPlayer->control(mPlayer, CDX_SET_THIRDPART_STREAM, CEDARX_THIRDPART_STREAM_USER1, mExtendMember->encrypt_file_format);
    }
    else 
    {
        mExtendMember->encrypt_type = PARAM_KEY_ENCRYPT_FILE_TYPE_DISABLE;
        mExtendMember->encrypt_file_format = CDX_MEDIA_FILE_FMT_UNKOWN;
        mPlayer->control(mPlayer, CDX_SET_THIRDPART_STREAM, CEDARX_THIRDPART_STREAM_NONE, mExtendMember->encrypt_file_format);
    }

    LOGD("play vps[%d] before CDX CMD_PREPARE_ASYNC!", mVpsspeed);
    mPlayer->control(mPlayer, CDX_CMD_SET_VPS, mVpsspeed, 0);
	return (mPlayer->control(mPlayer, CDX_CMD_PREPARE_ASYNC, 0, 0) == 0 ? OK : UNKNOWN_ERROR);
}

status_t CedarXPlayer::prepare() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	status_t ret;

	Mutex::Autolock autoLock(mLock);
	LOGV("prepare");

	ret = prepare_l();
	//getInputDimensionType();

	return ret;
}

status_t CedarXPlayer::prepare_l() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	int32_t ret;
	if (mFlags & PREPARED) {
	    return OK;
	}

	mIsAsyncPrepare = false;
	ret = mPlayer->control(mPlayer, CDX_CMD_PREPARE, 0, 0);
	if(ret != 0){
		return UNKNOWN_ERROR;
	}

	finishAsyncPrepare_l(0);

	return OK;
}

void CedarXPlayer::abortPrepare(status_t err) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	CHECK(err != OK);

	if (mIsAsyncPrepare) {
		notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
	}

	mFlags &= ~(PREPARING | PREPARE_CANCELLED);
}

status_t CedarXPlayer::suspend() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	LOGD("suspend start");

	if (mFlags & SUSPENDING)
		return OK;

	SuspensionState *state = &mSuspensionState;
	getPosition(&state->mPositionUs);

	//Mutex::Autolock autoLock(mLock);

	state->mFlags = mFlags & (PLAYING | AUTO_LOOPING | LOOPING | AT_EOS);
    state->mUri = mUri;
    state->mUriHeaders = mUriHeaders;
	mFlags |= SUSPENDING;

	if(mIsCedarXInitialized){
		mPlayer->control(mPlayer, CDX_CMD_STOP_ASYNC, 0, 0);
		CDXPlayer_Destroy(mPlayer);
		mPlayer = NULL;
		mIsCedarXInitialized = false;
	}

	pause_l(true);

	{
		Mutex::Autolock autoLock(mLockNativeWindow);
		mVideoRenderer.clear();
		mVideoRenderer = NULL;
	}

	if(mAudioPlayer){
		delete mAudioPlayer;
		mAudioPlayer = NULL;
	}
	mPlayerState = PLAYER_STATE_SUSPEND;
	LOGD("suspend end");

	return OK;
}

status_t CedarXPlayer::resume() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	LOGD("resume start");
    //Mutex::Autolock autoLock(mLock);
    SuspensionState *state = &mSuspensionState;
    status_t err;
    if (mSourceType != SOURCETYPE_URL){
        LOGW("NOT support setdatasouce non-uri currently");
        return UNKNOWN_ERROR;
    }

    if(mPlayer == NULL){
    	CDXPlayer_Create((void**)&mPlayer);
    	mPlayer->control(mPlayer, CDX_CMD_REGISTER_CALLBACK, (unsigned int32_t)&CedarXPlayerCallbackWrapper, (unsigned int32_t)this);
    	mIsCedarXInitialized = true;
    }

    //mPlayer->control(mPlayer, CDX_CMD_SET_STATE, CDX_STATE_UNKOWN, 0);

    err = setDataSource(state->mUri, &state->mUriHeaders);
	mPlayer->control(mPlayer, CDX_CMD_SET_VIDEO_OUTPUT_SETTING, mExtendMember->mOutputSetting, 0);

    mFlags = state->mFlags & (AUTO_LOOPING | LOOPING | AT_EOS);

    mFlags |= RESTORE_CONTROL_PARA;

    if (state->mFlags & PLAYING) {
        play_l(CDX_CMD_TAG_START_ASYNC);
    }
    mFlags &= ~SUSPENDING;
    //state->mPositionUs = 0;
    mPlayerState = PLAYER_STATE_RESUME;

    LOGD("resume end");

	return OK;
}
/*******************************************************************************
Function name: android.CedarXPlayer.setVps
Description: 
    1.set variable play speed.
Parameters: 
    
Return: 
    
Time: 2013/1/12
*******************************************************************************/
int32_t CedarXPlayer::setVps(int32_t vpsspeed)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    mVpsspeed = vpsspeed;
    if(isPlaying())
    {
        LOGD("state is playing, change vps to[%d]", mVpsspeed);
        mPlayer->control(mPlayer, CDX_CMD_SET_VPS, mVpsspeed, 0);
    }
    return OK;
}

int32_t CedarXPlayer::getMeidaPlayerState() {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    return mPlayerState;
}

status_t CedarXPlayer::setSubCharset(const char *charset)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mPlayer == NULL){
		return -1;
	}

	//mSubtitleParameter.mSubtitleCharset = percent;
    return mPlayer->control(mPlayer, CDX_CMD_SETSUBCHARSET, (unsigned int32_t)charset, 0);
}

status_t CedarXPlayer::getSubCharset(char *charset)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mPlayer == NULL){
		return -1;
	}

    return mPlayer->control(mPlayer, CDX_CMD_GETSUBCHARSET, (unsigned int32_t)charset, 0);
}

status_t CedarXPlayer::setSubDelay(int32_t time)
{
    LOGV("(f:%s, l:%d), time[%d]", __FUNCTION__, __LINE__, time);
	if(mPlayer == NULL){
		return -1;
	}

	mSubtitleParameter.mSubtitleDelay = time;
	return mPlayer->control(mPlayer, CDX_CMD_SETSUBDELAY, time, 0);
}

int32_t CedarXPlayer::getSubDelay()
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	int32_t tmp;

	if(mPlayer == NULL){
		return -1;
	}

	mPlayer->control(mPlayer, CDX_CMD_GETSUBDELAY, (unsigned int32_t)&tmp, 0);
	return tmp;
}

status_t CedarXPlayer::switchSub_l(int32_t index)
{
    LOGV("(f:%s, l:%d) index=%d", __FUNCTION__, __LINE__, index);
	if(mPlayer == NULL){
		return -1;
	} else if(mSubTrackIndex == index) {
		return 0;
	}

	mSubTrackIndex = index;
	return mPlayer->control(mPlayer, CDX_CMD_SWITCHSUB, index, 0);
}

status_t CedarXPlayer::setSubGate_l(bool showSub)
{
	if(mPlayer == NULL){
		return -1;
	}
	return mPlayer->control(mPlayer, CDX_CMD_SETSUBGATE, showSub, 0);
}

bool CedarXPlayer::getSubGate_l()
{
	int32_t tmp = 0;

	if(mPlayer != NULL){
		mPlayer->control(mPlayer, CDX_CMD_GETSUBGATE, (unsigned int32_t)&tmp, 0);
	}
	return !!tmp;
}

status_t CedarXPlayer::switchTrack_l(int32_t index)
{
    LOGV("(f:%s, l:%d) index=0x%x", __FUNCTION__, __LINE__, index);
	if(mPlayer == NULL){
		return -1;
	} else if(mAudioTrackIndex == index) {
		return 0;
	}

	mAudioTrackIndex = index;
	return mPlayer->control(mPlayer, CDX_CMD_SWITCHTRACK, index, 0);
}

status_t CedarXPlayer::enableScaleMode(bool enable, int32_t width, int32_t height)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mPlayer == NULL){
		return -1;
	}

	if(enable) {
		mMaxOutputWidth = width;
		mMaxOutputHeight = height;
	}

	return 0;
}

status_t CedarXPlayer::setChannelMuteMode(int32_t muteMode)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mPlayer == NULL){
		return -1;
	}

	mPlayer->control(mPlayer, CDX_CMD_SET_AUDIOCHANNEL_MUTE, muteMode, 0);
	return OK;
}

int32_t CedarXPlayer::getChannelMuteMode()
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mPlayer == NULL){
		return -1;
	}

	int32_t mute;
	mPlayer->control(mPlayer, CDX_CMD_GET_AUDIOCHANNEL_MUTE, (unsigned int32_t)&mute, 0);
	return mute;
}

status_t CedarXPlayer::extensionControl(int32_t command, int32_t para0, int32_t para1)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	return OK;
}

status_t CedarXPlayer::generalInterface(int32_t cmd, int32_t int1, int32_t int2, int32_t int3, void *p)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if (mPlayer == NULL) {
		return -1;
	}

	switch (cmd) {

	case MEDIAPLAYER_CMD_SET_BD_FOLDER_PLAY_MODE:
		mPlayer->control(mPlayer, CDX_CMD_PLAY_BD_FILE, int1, 0);
		break;

	case MEDIAPLAYER_CMD_GET_BD_FOLDER_PLAY_MODE:
		mPlayer->control(mPlayer, CDX_CMD_IS_PLAY_BD_FILE, (unsigned int32_t)p, 0);
		break;

//    case MEDIAPLAYER_CMD_SET_PRESENTANTION_SCREEN:
//        mPlayer->control(mPlayer, CDX_CMD_SET_PRESENT_SCREEN, int1, 0);
//        break;
//
//    case MEDIAPLAYER_CMD_GET_PRESENTANTION_SCREEN:
//        mPlayer->control(mPlayer, CDX_CMD_GET_PRESENT_SCREEN, (unsigned int32_t)p, 0);
//        break;

	case MEDIAPLAYER_CMD_SET_STREAMING_TYPE:
		mPlayer->control(mPlayer, CDX_CMD_SET_STREAMING_TYPE, int1, 0);
		break;

//#ifdef MEDIAPLAYER_CMD_SET_ROTATION
//	case MEDIAPLAYER_CMD_SET_ROTATION:
//	{
//        LOGD("call CedarXPlayer dynamic MEDIAPLAYER_CMD_SET_ROTATION! anti-clock rotate=%d", int1);
//		if(!DYNAMIC_ROTATION_ENABLE)
//		{
//			return OK;
//		}
//        int32_t nCurDynamicRotation;
//        nCurDynamicRotation = (int1 < 0 || int1 > 3) ? mDynamicRotation : int1;
//        return OK;
//	}
//#endif
#ifdef MEDIAPLAYER_CMD_SET_INIT_ROTATION
    case MEDIAPLAYER_CMD_SET_INIT_ROTATION:
	{
        LOGD("call CedarXPlayer init MEDIAPLAYER_CMD_SET_INIT_ROTATION! anti-clock rotate=%d", int1);
        int32_t nCurInitRotation = (int1 < 0 || int1 > 3) ? mInitRotation:int1;

        if(nCurInitRotation != mInitRotation)
        {
            mDynamicRotation = mInitRotation = nCurInitRotation;
            LOGD("init rotate=%d, cedarx will decide whether rotate in prepareAsync()!", mInitRotation);
        }
		break;
	}
#endif

	default:
		break;
	}

	return OK;
}

uint32_t CedarXPlayer::flags() const {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if(mCanSeek) {
		return MediaExtractor::CAN_PAUSE | MediaExtractor::CAN_SEEK  | MediaExtractor::CAN_SEEK_FORWARD  | MediaExtractor::CAN_SEEK_BACKWARD;
	}
	else {
		return MediaExtractor::CAN_PAUSE;
	}
}

int32_t CedarXPlayer::nativeSuspend()
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	if (mFlags & PLAYING) {
		LOGV("nativeSuspend may fail, I'am still playing");
		return -1;
	}
	suspend();

	mFlags |= NATIVE_SUSPENDING;

	return 0;
}

status_t CedarXPlayer::setVideoScalingMode(int32_t mode) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
    Mutex::Autolock lock(mLock);
    return setVideoScalingMode_l(mode);
}

status_t CedarXPlayer::setVideoScalingMode_l(int32_t mode) {
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	LOGV("mVideoScalingMode %d, mode %d", mVideoScalingMode, mode);
	if(mVideoScalingMode == mode) {
		return OK;
	}

    mVideoScalingMode = mode;
    if (mNativeWindow != NULL) {
        status_t err = native_window_set_scaling_mode(
                mNativeWindow.get(), mVideoScalingMode);
        if (err != OK) {
            LOGW("Failed to set scaling mode: %d", err);
        }
    }
    return OK;
}
#if (CEDARX_ANDROID_VERSION >= 7)
/*
 * add by weihongqiang
 */
enum {
    // These keys must be in sync with the keys in TimedText.java
    KEY_DISPLAY_FLAGS                 = 1, // int32_t
    KEY_STYLE_FLAGS                   = 2, // int32_t
    KEY_BACKGROUND_COLOR_RGBA         = 3, // int32_t
    KEY_HIGHLIGHT_COLOR_RGBA          = 4, // int32_t
    KEY_SCROLL_DELAY                  = 5, // int32_t
    KEY_WRAP_TEXT                     = 6, // int32_t
    KEY_START_TIME                    = 7, // int32_t
    KEY_STRUCT_BLINKING_TEXT_LIST     = 8, // List<CharPos>
    KEY_STRUCT_FONT_LIST              = 9, // List<Font>
    KEY_STRUCT_HIGHLIGHT_LIST         = 10, // List<CharPos>
    KEY_STRUCT_HYPER_TEXT_LIST        = 11, // List<HyperText>
    KEY_STRUCT_KARAOKE_LIST           = 12, // List<Karaoke>
    KEY_STRUCT_STYLE_LIST             = 13, // List<Style>
    KEY_STRUCT_TEXT_POS               = 14, // TextPos
    KEY_STRUCT_JUSTIFICATION          = 15, // Justification
    KEY_STRUCT_TEXT                   = 16, // Text

    KEY_STRUCT_AWEXTEND_BMP           = 50, // bmp subtitle such as idxsub and pgs.
    KEY_STRUCT_AWEXTEND_PIXEL_FORMAT  = 51,  //PIXEL_FORMAT_RGBA_8888
    KEY_STRUCT_AWEXTEND_PICWIDTH      = 52, // bmp subtitle item's width
    KEY_STRUCT_AWEXTEND_PICHEIGHT     = 53, // bmp subtitle item's height
    KEY_STRUCT_AWEXTEND_SUBDISPPOS    = 54, // text subtitle's position, SUB_DISPPOS_BOT_LEFT
    KEY_STRUCT_AWEXTEND_SCREENRECT    = 55, // text subtitle's position need a whole area as a ref.
    KEY_STRUCT_AWEXTEND_HIDESUB       = 56, // when multi subtitle show the same time, such as ssa, we need to tell app which subtitle need to hide.

    KEY_GLOBAL_SETTING                = 101,
    KEY_LOCAL_SETTING                 = 102,
    KEY_START_CHAR                    = 103,
    KEY_END_CHAR                      = 104,
    KEY_FONT_ID                       = 105,
    KEY_FONT_SIZE                     = 106,
    KEY_TEXT_COLOR_RGBA               = 107,
};

size_t CedarXPlayer::countTracks() const {
	//Do not change the order of track.
    return mMediaInfo.mVideoStreamCount
    		+ mMediaInfo.mAudioStreamCount + mMediaInfo.mSubtitleStreamCount;
}

int32_t convertToUTF16LE(char *pDesString, int32_t nDesBufSize, const char *pSrcString)
{
    const char* enc = NULL;
    const char* desCharset = NULL;
    int32_t nDesTextLen;
    enc = "UTF-8";
    desCharset = "UTF-16LE";
    if (enc) 
    {
        UErrorCode status = U_ZERO_ERROR;

        UConverter *conv = ucnv_open(enc, &status);
        if (U_FAILURE(status)) 
        {
            LOGW("could not create UConverter for %s\n", enc);
            
            return -1;
        }
        
        UConverter *desConv = ucnv_open(desCharset, &status);
        if (U_FAILURE(status)) 
        {
            LOGW("could not create UConverter for [%s]\n", desCharset);
            
            ucnv_close(conv);
            return -1;
        }

        // first we need to untangle the utf8 and convert it back to the original bytes
        // since we are reducing the length of the string, we can do this in place
        const char* src = (const char*)pSrcString;
        //LOGV("CedarXTimedText::convertUniCode src = %s\n",src);
        int32_t len = strlen((char *)src);
		//LOGV("CedarXTimedText::convertUniCode len = %d\n",len);
        // now convert from native encoding to UTF-8
        int32_t targetLength = len * 3 + 1;
        if(targetLength > nDesBufSize)
        {
            LOGW("(f:%s, l:%d) fatal error! targetLength[%d]>nDesBufSize[%d], need cut!", __FUNCTION__, __LINE__, targetLength, nDesBufSize);
            targetLength = nDesBufSize;
        }
        memset(pDesString,0,nDesBufSize);
        char* target = (char*)&pDesString[0];
        
		//LOGV("(f:%s, l:%d), target[%p], targetLength[%d], src[%p], len[%d]", __FUNCTION__, __LINE__, target, targetLength, src, len);
        ucnv_convertEx(desConv, conv, &target, (const char*)target + targetLength,&src, (const char*)src + len, NULL, NULL, NULL, NULL, true, true, &status);
        if (U_FAILURE(status)) 
        {
            LOGW("ucnv_convertEx failed: %d\n", status);
            
            memset(pDesString,0,nDesBufSize);
        } 
        nDesTextLen = target - (char*)pDesString;
        //LOGV("(f:%s, l:%d)_2, target[%p], targetLength[%d], src[%p]", __FUNCTION__, __LINE__, target, (int32_t)(target - (char*)subTextBuf), src, (int32_t)(src - (char*)sub_info->subTextBuf));
        //LOGD("CedarXTimedText::convertUniCode src = %s,target = %s\n", pSrcString, pDesString);
//        for(int32_t i=0; i<strlen(pSrcString);i++ )
//        {
//            LOGD("src[0x%x]", pSrcString[i]);
//        }
//        for(int32_t i=0; i<nDesTextLen;i++ )
//        {
//            LOGD("[0x%x]", pDesString[i]);
//        }
//	        int32_t targetlen2 = strlen((char *)subTextBuf);
//          LOGV("(f:%s, l:%d), targetLength2[%d], nDesTextLen[%d]", __FUNCTION__, __LINE__, targetlen2, nDesTextLen);
        
        ucnv_close(conv);
        ucnv_close(desConv);
    }
    return 0;
}

status_t CedarXPlayer::getTrackInfo(Parcel *reply) const {
    Mutex::Autolock autoLock(mLock);
    size_t trackCount = countTracks();
    LOGV("tack count %u, %d:%d:%d", trackCount, mMediaInfo.mVideoStreamCount,
    		mMediaInfo.mAudioStreamCount, mMediaInfo.mSubtitleStreamCount);
    reply->writeInt32(trackCount);
    const char *lang;
    for(size_t i = 0; i < mMediaInfo.mVideoStreamCount; ++i) {
    	reply->writeInt32(2);
    	reply->writeInt32(MEDIA_TRACK_TYPE_VIDEO);
    	lang = "und";
    	reply->writeString16(String16(lang));
    }

    for(size_t i = 0; i < mMediaInfo.mAudioStreamCount; ++i) {
    	reply->writeInt32(2);
    	reply->writeInt32(MEDIA_TRACK_TYPE_AUDIO);
    	lang = mMediaInfo.mAudioInfo[i].mLang;
    	if(!strlen(lang)) {
    		lang = "und";
    	}
    	reply->writeString16(String16(lang));
    }

    for(size_t i = 0; i < mMediaInfo.mSubtitleStreamCount; ++i) {
    	reply->writeInt32(2);
    	reply->writeInt32(MEDIA_TRACK_TYPE_TIMEDTEXT);
    	lang = mMediaInfo.mSubtitleInfo[i].mLang;
        //LOGD("(f:%s, l:%d) i=%d, lang_len=%d, lang[%s]", __FUNCTION__, __LINE__, i, strlen(lang), lang);
    	if(!strlen(lang)) {
    		lang = "und";
    	}
        char string16Array[1024];
        const char16_t* pDesString16 = (const char16_t*)string16Array;
        convertToUTF16LE(string16Array, 1024, lang);
    	//reply->writeString16(String16(lang));
    	reply->writeString16(String16(pDesString16));
    }

    return OK;
}

status_t CedarXPlayer::selectTrack(size_t trackIndex, bool select) {
    ATRACE_CALL();
    ALOGV("selectTrack: trackIndex = %d and select=%d", trackIndex, select);
    Mutex::Autolock autoLock(mLock);
    size_t trackCount = countTracks();

    if (trackIndex >= trackCount) {
        ALOGE("Track index (%d) is out of range [0, %d)", trackIndex, trackCount);
        return ERROR_OUT_OF_RANGE;
    }
    ssize_t videoTrackIndex = (ssize_t)trackIndex;
    ssize_t audioTrackIndex = (ssize_t)(videoTrackIndex - mMediaInfo.mVideoStreamCount);
    ssize_t subTrackIndex   = (ssize_t)(audioTrackIndex - mMediaInfo.mAudioStreamCount);

    status_t err = OK;
    if(videoTrackIndex < mMediaInfo.mVideoStreamCount) {
    	//Video track. ignore.
    } else if(audioTrackIndex < mMediaInfo.mAudioStreamCount) {
    	if(!select) {
    		err =  ERROR_UNSUPPORTED;
    	} else {
    		err = switchTrack_l(audioTrackIndex);
    	}
    } else if(subTrackIndex < mMediaInfo.mSubtitleStreamCount) {
    	if(!select) {
    		if(getSubGate_l()) {
    			err = setSubGate_l(select);
    		}
    	} else {
    		if(!getSubGate_l()) {
    			setSubGate_l(true);
    		}
    		switchSub_l(subTrackIndex);
    	}
    } else {
    	err =  ERROR_UNSUPPORTED;
    }

    return err;
}


status_t CedarXPlayer::invoke(const Parcel &request, Parcel *reply) {
	ALOGV("invoke");
    ATRACE_CALL();
    if (NULL == reply) {
        return android::BAD_VALUE;
    }
    int32_t methodId;
    status_t ret = request.readInt32(&methodId);
    if (ret != android::OK) {
        return ret;
    }
    switch(methodId) {
        case INVOKE_ID_SET_VIDEO_SCALING_MODE:
        {
            int32_t mode = request.readInt32();
            return setVideoScalingMode(mode);
        }

        case INVOKE_ID_GET_TRACK_INFO:
        {
            return getTrackInfo(reply);
        }
        case INVOKE_ID_ADD_EXTERNAL_SOURCE:
        {
        	//update track info.
        	mPlayer->control(mPlayer, CDX_CMD_GET_MEDIAINFO, (unsigned int32_t)&mMediaInfo, 0);
            return OK;
        }
        case INVOKE_ID_ADD_EXTERNAL_SOURCE_FD:
        {
        	ALOGV("add external source fd");
        	Mutex::Autolock autoLock(mLock);

        	CedarXExternFdDesc extFd;
        	//Must dup a new.
        	extFd.fd 		= dup(request.readFileDescriptor());
            extFd.offset 	= request.readInt64();
            extFd.length  	= request.readInt64();
            extFd.cur_offset = extFd.offset ;
            String8 mimeType(request.readString16());
            mPlayer->control(mPlayer, CDX_CMD_ADD_EXTERNAL_SOURCE_FD,
            		(unsigned int32_t)&extFd, (unsigned int32_t)mimeType.string());
        	//update track info after new external source added.
        	mPlayer->control(mPlayer, CDX_CMD_GET_MEDIAINFO, (unsigned int32_t)&mMediaInfo, 0);
            return OK;
        }
        case INVOKE_ID_SELECT_TRACK:
        {
            int32_t trackIndex = request.readInt32();
            return selectTrack(trackIndex, true /* select */);
        }
        case INVOKE_ID_UNSELECT_TRACK:
        {
            int32_t trackIndex = request.readInt32();
            return selectTrack(trackIndex, false /* select */);
        }

        default:
        {
            return ERROR_UNSUPPORTED;
        }
    }
    // It will not reach here.
    return OK;
}
#endif

#if 0

static int32_t g_dummy_render_frame_id_last = -1;
static int32_t g_dummy_render_frame_id_curr = -1;

int32_t CedarXPlayer::StagefrightVideoRenderInit(int32_t width, int32_t height, int32_t format, void *frame_info)
{
	g_dummy_render_frame_id_last = -1;
	g_dummy_render_frame_id_curr = -1;
	return 0;
}

void CedarXPlayer::StagefrightVideoRenderData(void *frame_info, int32_t frame_id)
{
	g_dummy_render_frame_id_last = g_dummy_render_frame_id_curr;
	g_dummy_render_frame_id_curr = frame_id;
	return ;
}

int32_t CedarXPlayer::StagefrightVideoRenderGetFrameID()
{
	return g_dummy_render_frame_id_last;
}

void CedarXPlayer::StagefrightVideoRenderExit()
{
}

#else
static int32_t cedarvPixel2CedarXPixel(int32_t cedarv_pixel)
{
    //TODO:add other format.
    if(cedarv_pixel == CEDARV_PIXEL_FORMAT_PLANNER_NV21)
        return CDX_PIXEL_FORMAT_YCrCb_420_SP;
    else if(cedarv_pixel == CEDARV_PIXEL_FORMAT_PLANNER_YVU420)
        return CDX_PIXEL_FORMAT_YV12;
    else if(cedarv_pixel == CEDARV_PIXEL_FORMAT_MB_UV_COMBINE_YUV420)
        return CDX_PIXEL_FORMAT_AW_MB420;
    else if(cedarv_pixel == CEDARV_PIXEL_FORMAT_MB_UV_COMBINE_YUV422)
        return CDX_PIXEL_FORMAT_AW_MB422;

    //Should not be here.
    LOGE("why cedarv_pixel[0x%x]", cedarv_pixel);
    TRESPASS()
    return 0;
}

static void frameInfo2RenderInfo(void *frame_info, void *render_info)
{
	cedarv_picture_t *frm_inf = (cedarv_picture_t *) frame_info;
	CedarXRenderInfo *info  = (CedarXRenderInfo *)render_info;
	info->addr0[0] = frm_inf->y;
	info->addr0[1] = frm_inf->u;
	info->addr0[2] = frm_inf->v;
	info->addr1[0] = frm_inf->y2;
	info->addr1[1] = frm_inf->u2;
	info->addr1[2] = frm_inf->v2;
	info->format = cedarvPixel2CedarXPixel(frm_inf->pixel_format);

    info->is_progressive    = frm_inf->is_progressive;
    info->top_field_first   = frm_inf->top_field_first;
}

void CedarXPlayer::initRenderer_l(bool all)
{
	LOGV("VideoRenderInit_l");
    int32_t nGUIInitRotation;
    nGUIInitRotation  = (mInitRotation + mMediaInfo.mVideoInfo[0].mFileSelfRotation)%4;
    if(all) {
    	//init when start to play, notify video size
        int32_t nAppVideoWidth  = mDisplayWidth;
    	int32_t nAppVideoHeight = mDisplayHeight;
    	if(1 == nGUIInitRotation || 3 == nGUIInitRotation) {
    		nAppVideoWidth = mDisplayHeight;
    		nAppVideoHeight = mDisplayWidth;
    	}
    	if(mVideoWidth != nAppVideoWidth ||  mVideoHeight != nAppVideoHeight) {
            mVideoWidth = nAppVideoWidth;
    		mVideoHeight = nAppVideoHeight;
    		LOGI("notify video size %d X %d", nAppVideoWidth, nAppVideoHeight);
    		notifyListener_l(MEDIA_SET_VIDEO_SIZE, nAppVideoWidth, nAppVideoHeight);
    	}
    }

	sp<MetaData> meta = new MetaData;
    meta->setInt32(kKeyColorFormat, mDisplayFormat);
    meta->setInt32(kKeyWidth, mDisplayWidth);
    meta->setInt32(kKeyHeight, mDisplayHeight);
    meta->setInt32(kKeyRotation, nGUIInitRotation);
    meta->setInt32(kKey3dDoubleStream, m3dModeDoubleStreamFlag);
    meta->setInt32(kKeyIsDRM, mIsDRMMedia);

    //Must hold mLockNativeWindow outside.
    mVideoRenderer.clear();

    // Must ensure that mVideoRenderer's destructor is actually executed
    // before creating a new one.
    IPCThreadState::self()->flushCommands();
    mVideoRenderer = new CedarXLocalRenderer(mNativeWindow, meta);
    setVideoScalingMode_l(mVideoScalingMode);
}

int32_t CedarXPlayer::StagefrightVideoRenderInit(int32_t width, int32_t height, int32_t format, void *frame_info)
{
    LOGV("(f:%s, l:%d)", __FUNCTION__, __LINE__);
	mDisplayWidth 	= width;
	mDisplayHeight 	= height;
	mFirstFrame 	= 1;
    mDisplayFormat 	= cedarvPixel2CedarXPixel(format);

	LOGI("video render size:%dx%d", width, height);
	if (mNativeWindow == NULL)
	    return -1;

#if (defined(__CHIP_VERSION_F23) || (defined(__CHIP_VERSION_F51)))
#if (1 == ADAPT_A10_GPU_RENDER)
	int32_t nGpuBufWidth, nGpuBufHeight;
	nGpuBufWidth = (nAppVideoWidth + 15) & ~15;
	nGpuBufHeight = nAppVideoHeight;
	//A10's GPU has a bug, we can avoid it
	if((nGpuBufHeight%8 != 0) && ((nGpuBufWidth*nGpuBufHeight)%256 != 0))
	{
		nGpuBufHeight = (nGpuBufHeight+7)&~7;
		LOGW("(f:%s, l:%d) the video height to tell app, change from [%d] to [%d]", __FUNCTION__, __LINE__, nAppVideoHeight, nGpuBufHeight);
	}
	nAppVideoHeight = nGpuBufHeight;
	//nAppVideoWidth = nGpuBufWidth;
#endif
#endif

	Mutex::Autolock autoLock(mLockNativeWindow);
	initRenderer_l(true);

    return 0;
}

void CedarXPlayer::StagefrightVideoRenderExit()
{
	Mutex::Autolock autoLock(mLockNativeWindow);
	mVideoRenderer.clear();
	mVideoRenderer = NULL;
}

void CedarXPlayer::StagefrightVideoRenderData(void *frame_info, int32_t frame_id)
{
	//useless.
	LOGW("deprecated function, shouldn't be here");
}

int32_t CedarXPlayer::StagefrightVideoRenderPerform(int32_t param_key, void *data)
{
	int32_t ret = -1;
	switch(param_key) {
	case VIDEO_RENDER_GET_FRAME_ID:
		//useless.
		break;

	case VIDEO_RENDER_GET_OUTPUT_TYPE:
	{
	    Mutex::Autolock autoLock(mLockNativeWindow);
	    if (mVideoRenderer != NULL) {
	    	ret = mVideoRenderer->perform(CDX_RENDER_GET_OUTPUT_TYPE, 0);
	    	//LOGV("output type %d", ret);
	    }
		break;
	}

	case VIDEO_RENDER_SET_VIDEO_BUFFERS_INFO:
	{
	    Mutex::Autolock autoLock(mLockNativeWindow);
	    if (mVideoRenderer != NULL) {
	    	CedarXRenderInfo info;
	    	frameInfo2RenderInfo(data, &info);
	    	ret = mVideoRenderer->perform(CDX_RENDER_SET_VIDEO_BUFFERS_INFO, (int32_t)&info);
	    }
		break;
	}

	case VIDEO_RENDER_SET_VIDEO_BUFFERS_DIMENSIONS:
	{	    
		Mutex::Autolock autoLock(mLockNativeWindow);
	    if (mVideoRenderer != NULL) {
	    	ret = mVideoRenderer->perform(CDX_RENDER_SET_BUFFERS_DIMENSIONS, (int32_t)data);
	    }
		break;
	}
	default:
		break;
	}
	return ret;
}

/*******************************************************************************
Function name: android.CedarXPlayer.StagefrightVideoRenderDequeueFrame
Description: 
    
Parameters: 
    
Return: 
    ret = 0: ok
    ret = -1: error
    ret = 10: mNativeWindow = NULL;
Time: 2013/5/31
*******************************************************************************/
int32_t CedarXPlayer::StagefrightVideoRenderDequeueFrame(void *frame_info, ANativeWindowBufferCedarXWrapper *pANativeWindowBuffer)
{
    int32_t ret = -1;

    Mutex::Autolock autoLock(mLockNativeWindow);
    if(mVideoRenderer == NULL) {
		if (mNativeWindow != NULL) {
			initRenderer_l(false);
		} else {
			LOGD("native window is NULL");
			return 10;
        }
    }

	ret = mVideoRenderer->dequeueFrame(pANativeWindowBuffer);
	if(mFirstFrame) {
		//add by weihongqiang.
		notifyListener_l(MEDIA_INFO, MEDIA_INFO_RENDERING_START);
		mFirstFrame = 0;
	}

    return ret;
}

int32_t CedarXPlayer::StagefrightVideoRenderEnqueueFrame(ANativeWindowBufferCedarXWrapper *pANativeWindowBuffer)
{
    int32_t ret;
    Mutex::Autolock autoLock(mLockNativeWindow);
    if(mVideoRenderer == NULL) {
        LOGW("(f:%s, l:%d) Be careful! mVideoRenderer==NULL", __FUNCTION__, __LINE__);
        return 10;  //10 indicate NativeWindow is NULL, consider enqueue success.
    }
    ret = mVideoRenderer->enqueueFrame(pANativeWindowBuffer);
    return ret;
}

#endif


// Parse the SRT text sample, and store the timing and text sample in a Parcel.
// The Parcel will be sent to MediaPlayer.java through event, and will be
// parsed in TimedText.java.
static int32_t extractTextLocalDescriptions(CedarXTimedText *pCedarXTimedText, Parcel *parcel)
{
    // In the absence of any bits set in flags, the text
    // is plain. Otherwise, 1: bold, 2: italic, 4: underline
    int32_t nStyleFlags = 0;
    parcel->writeInt32(KEY_LOCAL_SETTING);
    parcel->writeInt32(KEY_START_TIME);
    parcel->writeInt32(pCedarXTimedText->startTime);

    parcel->writeInt32(KEY_STRUCT_TEXT);
    // write the size of the text sample
    parcel->writeInt32(pCedarXTimedText->subTextLen);
    // write the text sample as a byte array
    parcel->writeInt32(pCedarXTimedText->subTextLen);
    parcel->write(pCedarXTimedText->subTextBuf, pCedarXTimedText->subTextLen);


    if(pCedarXTimedText->subDispPos != SUB_DISPPOS_DEFAULT)
    {
        parcel->writeInt32(KEY_STRUCT_AWEXTEND_SUBDISPPOS);
        //write text subtitle's position area.
        parcel->writeInt32(pCedarXTimedText->subDispPos);
        
        parcel->writeInt32(KEY_STRUCT_AWEXTEND_SCREENRECT);
        //write text subtitle's whole screen rect.
        parcel->writeInt32(0);
        parcel->writeInt32(0);
        parcel->writeInt32(pCedarXTimedText->nScreenHeight);
        parcel->writeInt32(pCedarXTimedText->nScreenWidth);

        parcel->writeInt32(KEY_STRUCT_TEXT_POS);
        //write text subtitle's position Rect.
        parcel->writeInt32(pCedarXTimedText->starty);
        parcel->writeInt32(pCedarXTimedText->startx);
        parcel->writeInt32(pCedarXTimedText->endy);
        parcel->writeInt32(pCedarXTimedText->endx);
    }
    
    if(pCedarXTimedText->subHasFontInfFlag)
    {
        parcel->writeInt32(KEY_STRUCT_STYLE_LIST);
        parcel->writeInt32(KEY_FONT_ID);
        parcel->writeInt32((int32_t)pCedarXTimedText->fontStyle);
        parcel->writeInt32(KEY_FONT_SIZE);
        parcel->writeInt32((int32_t)pCedarXTimedText->fontSize);
        parcel->writeInt32(KEY_TEXT_COLOR_RGBA);
        parcel->writeInt32((int32_t)pCedarXTimedText->primaryColor);
        parcel->writeInt32(KEY_STYLE_FLAGS);
        nStyleFlags = (pCedarXTimedText->subStyle&0x7);
        parcel->writeInt32(nStyleFlags);
    }

    if(pCedarXTimedText->getSubShowFlag() == 0) //SUB_SHOW_INVALID
    {
        parcel->writeInt32(KEY_STRUCT_AWEXTEND_HIDESUB);
        //nofity app to hide this subtitle
        parcel->writeInt32(1);
    }
    return OK;
}

/*******************************************************************************
Function name: android.extractBmpLocalDescriptions
Description: 
    we extend android subtitle interface for bmp subtitle such as idxsub and pgs.
Parameters: 
    
Return: 
    
Time: 2013/6/18
*******************************************************************************/
static int32_t extractBmpLocalDescriptions(CedarXTimedText *pCedarXTimedText, Parcel *parcel)
{
    int32_t bufsize;
    parcel->writeInt32(KEY_LOCAL_SETTING);
    parcel->writeInt32(KEY_START_TIME);
    parcel->writeInt32(pCedarXTimedText->startTime);

    parcel->writeInt32(KEY_STRUCT_AWEXTEND_BMP);
    // write the pixel format of bmp subtitle
    parcel->writeInt32(KEY_STRUCT_AWEXTEND_PIXEL_FORMAT);
    parcel->writeInt32(pCedarXTimedText->mSubPicPixelFormat);
    // write the width of bmp subtitle
    parcel->writeInt32(KEY_STRUCT_AWEXTEND_PICWIDTH);
    parcel->writeInt32(pCedarXTimedText->subPicWidth);
    // write the height of bmp subtitle
    parcel->writeInt32(KEY_STRUCT_AWEXTEND_PICHEIGHT);
    parcel->writeInt32(pCedarXTimedText->subPicHeight);
    // write the size of the text sample
    bufsize = pCedarXTimedText->subPicWidth*pCedarXTimedText->subPicHeight*4;   //we know format is PIXEL_FORMAT_RGBA_8888, so 4 byte every pixel.
    if(bufsize > SUB_FRAME_BUFSIZE)
    {
        LOGW("(f:%s, l:%d) fatal error! bufsize[%d], SUB_FRAME_BUFSIZE[%d]", __FUNCTION__, __LINE__, bufsize, SUB_FRAME_BUFSIZE);
        bufsize = SUB_FRAME_BUFSIZE;
    }
    parcel->writeInt32(bufsize);
    // write the argb buffer as an int32_t array
    parcel->writeInt32(bufsize/4);
    //LOGD("(f:%s, l:%d) bmp_length_int=%d", __FUNCTION__, __LINE__, bufsize/4);
    parcel->write(pCedarXTimedText->subBitmapBuf, bufsize);

    return OK;
}

int32_t CedarXPlayer::StagefrightAudioRenderInit(int32_t samplerate, int32_t channels, int32_t format)
{
	//initial audio playback
	if (mAudioPlayer == NULL) {
		if (mAudioSink != NULL) {
			mAudioPlayer = new CedarXAudioPlayer(mAudioSink, this);
			//mAudioPlayer->setSource(mAudioSource);
			LOGV("set audio format: (%d : %d)", samplerate, channels);
			mAudioPlayer->setFormat(samplerate, channels);

			status_t err = mAudioPlayer->start(true /* sourceAlreadyStarted */);

			if (err != OK) {
				delete mAudioPlayer;
				mAudioPlayer = NULL;

				//mFlags &= ~(PLAYING);

				return err;
			}
			//Set whether suppress audio data, since audioPlayer
			//may have not initialized when calling setHDMIState at start.
			mAudioPlayer->setSuppressData(mHDMIPlugged && mIsDRMMedia);
		}
	} else {
		mAudioPlayer->resume();
	}

	return 0;
}

void CedarXPlayer::StagefrightAudioRenderExit(int32_t immed)
{
	if(mAudioPlayer){
		delete mAudioPlayer;
		mAudioPlayer = NULL;
	}
}

int32_t CedarXPlayer::StagefrightAudioRenderData(void* data, int32_t len)
{
	if(mAudioPlayer == NULL)
			return 0;
	return mAudioPlayer->render(data,len);
}

int32_t CedarXPlayer::StagefrightAudioRenderGetSpace(void)
{
	if(mAudioPlayer == NULL)
		return 0;
	return mAudioPlayer->getSpace();
}

int32_t CedarXPlayer::StagefrightAudioRenderGetDelay(void)
{
	if(mAudioPlayer == NULL)
		return 0;
	return mAudioPlayer->getLatency();
}

int32_t CedarXPlayer::StagefrightAudioRenderFlushCache(void)
{
	if(mAudioPlayer == NULL)
		return 0;

	return mAudioPlayer->seekTo(0);
}

int32_t CedarXPlayer::StagefrightAudioRenderPause(void)
{
	if(mAudioPlayer == NULL)
		return 0;

	mAudioPlayer->pause();
	return 0;
}


int32_t CedarXPlayer::getMaxCacheSize()
{
	int32_t arg[5];
	mPlayer->control(mPlayer, CDX_CMD_GET_CACHE_PARAMS, (unsigned int32_t)arg, 0);
	return arg[0];
}

int32_t CedarXPlayer::getMinCacheSize()
{
	int32_t arg[5];
	mPlayer->control(mPlayer, CDX_CMD_GET_CACHE_PARAMS, (unsigned int32_t)arg, 0);
	return arg[2];
}

int32_t CedarXPlayer::getStartPlayCacheSize()
{
	int32_t arg[5];
	mPlayer->control(mPlayer, CDX_CMD_GET_CACHE_PARAMS, (unsigned int32_t)arg, 0);
	return arg[1];
}


int32_t CedarXPlayer::getCachedDataSize()
{
	int32_t cachedDataSize;
	mPlayer->control(mPlayer, CDX_CMD_GET_CURRETN_CACHE_SIZE, (unsigned int32_t)&cachedDataSize, 0);
	return cachedDataSize;
}


int32_t CedarXPlayer::getCachedDataDuration()
{
	int32_t bitrate;
	int32_t cachedDataSize;
	float tmp;

	mPlayer->control(mPlayer, CDX_CMD_GET_CURRENT_BITRATE, (unsigned int32_t)&bitrate, 0);
	mPlayer->control(mPlayer, CDX_CMD_GET_CURRETN_CACHE_SIZE, (unsigned int32_t)&cachedDataSize, 0);

	if(bitrate <= 0)
		return 0;
	else
	{
		tmp = (float)cachedDataSize*8.0*1000.0;
		tmp /= (float)bitrate;
		return (int32_t)tmp;
	}
}


int32_t CedarXPlayer::getStreamBitrate()
{
	int32_t bitrate;
	mPlayer->control(mPlayer, CDX_CMD_GET_CURRENT_BITRATE, (unsigned int32_t)&bitrate, 0);
	return bitrate;
}


int32_t CedarXPlayer::getCacheSize(int32_t *nCacheSize)
{
	*nCacheSize = getCachedDataSize();
	if(*nCacheSize > 0)
	{
		return *nCacheSize;
	}
	return 0;
}

int32_t CedarXPlayer::getCacheDuration(int32_t *nCacheDuration)
{
	*nCacheDuration = getCachedDataDuration();
	if(*nCacheDuration > 0)
	{
		return *nCacheDuration;
	}
	return 0;
}


bool CedarXPlayer::setCacheSize(int32_t nMinCacheSize,int32_t nStartCacheSize,int32_t nMaxCacheSize)
{
	return setCacheParams(nMaxCacheSize, nStartCacheSize, nMinCacheSize, 0, 0);
}

bool CedarXPlayer::setCacheParams(int32_t nMaxCacheSize, int32_t nStartPlaySize, int32_t nMinCacheSize, int32_t nCacheTime, bool bUseDefaultPolicy)
{
	int32_t arg[5];
	arg[0] = nMaxCacheSize;
	arg[1] = nStartPlaySize;
	arg[2] = nMinCacheSize;
	arg[3] = nCacheTime;
	arg[4] = bUseDefaultPolicy;

	if(mPlayer->control(mPlayer, CDX_CMD_SET_CACHE_PARAMS, (unsigned int32_t)arg, 0) == 0)
		return true;
	else
		return false;
}


void CedarXPlayer::getCacheParams(int32_t* pMaxCacheSize, int32_t* pStartPlaySize, int32_t* pMinCacheSize, int32_t* pCacheTime, bool* pUseDefaultPolicy)
{
	int32_t arg[5];
	mPlayer->control(mPlayer, CDX_CMD_GET_CACHE_PARAMS, (unsigned int32_t)arg, 0);
	*pMaxCacheSize     = arg[0];
	*pStartPlaySize    = arg[1];
	*pMinCacheSize     = arg[2];
	*pCacheTime        = arg[3];
	*pUseDefaultPolicy = arg[4];
	return;
}

void CedarXPlayer::setHDMIState(bool state) {
	LOGV("setHDMIState");
	mHDMIPlugged = state;
	if(mAudioPlayer) {
		mAudioPlayer->setSuppressData(mHDMIPlugged && mIsDRMMedia);
	}
}

//static
void CedarXPlayer::HDMINotify(void* cookie, bool state)
{
	LOGV("HDMINotify");
	CedarXPlayer * player = static_cast<CedarXPlayer *>(cookie);
	if(player) {
		player->setHDMIState(state);
	}
}

int32_t CedarXPlayer::CedarXPlayerCallback(int32_t event, void *info)
{
	int32_t ret = 0;
	int32_t *para = (int32_t*)info;

	//LOGV("----------CedarXPlayerCallback event:%d info:%p\n", event, info);

	switch (event) {
	case CDX_EVENT_PLAYBACK_COMPLETE:
		mFlags &= ~PLAYING;
		mFlags |= AT_EOS;
		if(mAudioPlayer) {
			//CedarX framework based on notification.
			//It's necessary to nofity AudioPlayer
			//that a playback is end.
			mAudioPlayer->setPlaybackEos(true);
		}
		stop_l(); //for gallery
		break;

    case CDX_EVENT_VDEC_OUT_OF_MEMORY:
        LOGD("vdec_out_of_memory, cedarxPlayer know it and notify to upper!");
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_OUT_OF_MEMORY);
        //notifyListener_l(MEDIA_ERROR, 900);
        mFlags &= ~PLAYING;
		mFlags |= AT_EOS;
		stop_l(); //for gallery
		break;

	case CDX_EVENT_VIDEORENDERINIT:
	    StagefrightVideoRenderInit(para[0], para[1], para[2], (void *)para[3]);
		break;

	case CDX_EVENT_VIDEORENDERDATA:
		StagefrightVideoRenderData((void*)para[0],para[1]);
		break;

	case CDX_EVENT_VIDEORENDEREXIT:
		StagefrightVideoRenderExit();
		break;

	case CDX_EVENT_VIDEORENDERGETPERFORM:
		ret = StagefrightVideoRenderPerform((int32_t)para[0], (void *)para[1]);
		break;
    case CDX_EVENT_VIDEORENDER_DEQUEUEFRAME:
        ret = StagefrightVideoRenderDequeueFrame((void*)para[0], (ANativeWindowBufferCedarXWrapper*)para[1]);
        break;
    case CDX_EVENT_VIDEORENDER_ENQUEUEFRAME:
        ret = StagefrightVideoRenderEnqueueFrame((ANativeWindowBufferCedarXWrapper*)para);
        break;
	case CDX_EVENT_AUDIORENDERINIT:
		StagefrightAudioRenderInit(para[0], para[1], para[2]);
		break;

	case CDX_EVENT_AUDIORENDEREXIT:
		StagefrightAudioRenderExit(0);
		break;

	case CDX_EVENT_AUDIORENDERDATA:
		ret = StagefrightAudioRenderData((void*)para[0],para[1]);
		break;

	case CDX_EVENT_AUDIORENDERPAUSE:
		ret = StagefrightAudioRenderPause();
		break;

	case CDX_EVENT_AUDIORENDERGETSPACE:
		ret = StagefrightAudioRenderGetSpace();
		break;

	case CDX_EVENT_AUDIORENDERGETDELAY:
		ret = StagefrightAudioRenderGetDelay();
		break;

	case CDX_EVENT_AUDIORENDERFLUSHCACHE:
		ret = StagefrightAudioRenderFlushCache();
		break;

	case CDX_MEDIA_INFO_BUFFERING_START:
		LOGV("MEDIA_INFO_BUFFERING_START");
		notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
		break;

	case CDX_MEDIA_INFO_BUFFERING_END:
		LOGV("MEDIA_INFO_BUFFERING_END ...");
		notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
		break;

	case CDX_MEDIA_BUFFERING_UPDATE:
	{
		LOGV("buffering percentage %d", (int32_t)para);
		notifyListener_l(MEDIA_BUFFERING_UPDATE, (int32_t)para);
		break;
	}

	case CDX_MEDIA_WHOLE_BUFFERING_UPDATE:
		notifyListener_l(MEDIA_BUFFERING_UPDATE, (int32_t)para);
		break;

	case CDX_EVENT_FATAL_ERROR:
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, (int32_t)para);
		break;

	case CDX_EVENT_PREPARED:
    {
        CDXPrepareDoneInfo *pCDXPrepareDoneInfo = (CDXPrepareDoneInfo*)para;
        m3dModeDoubleStreamFlag = pCDXPrepareDoneInfo->_3d_mode_double_stream_flag;
    #if (0 == SUPPORT_3D_DOUBLE_STREAM)
        if(m3dModeDoubleStreamFlag)
        {
            LOGD("SDK don't support 3D double stream!");
            m3dModeDoubleStreamFlag = 0;
        }
    #endif
        if(m3dModeDoubleStreamFlag)
        {
            LOGD("3D double stream, notify app!");
            notifyListener_l(MEDIA_INFO, MEDIA_INFO_AWEXTEND_INDICATE_3D_DOUBLE_STREAM);
        }
		finishAsyncPrepare_l(pCDXPrepareDoneInfo->ret);
		break;
	}

	case CDX_EVENT_SEEK_COMPLETE:
		finishSeek_l(0);
		break;

	case CDX_EVENT_NATIVE_SUSPEND:
		LOGV("receive CDX_EVENT_NATIVE_SUSPEND");
		ret = nativeSuspend();
		break;
		
    case CDX_EVENT_NETWORK_STREAM_INFO:
        notifyListener_l(MEDIA_INFO, MEDIA_INFO_METADATA_UPDATE, (int32_t)para);
		break;
	case CDA_EVENT_AUDIORAWPLAY:
    {
        int64_t token = IPCThreadState::self()->clearCallingIdentity();
	
    	static int32_t raw_data_test = 0;
    	String8 raw1 = String8("raw_data_output=1");
    	String8 raw0 = String8("raw_data_output=0");
    	const sp<IAudioFlinger>& af = AudioSystem::get_audio_flinger();
        if (af != 0) {
        	af->setParameters(0, para[0] ? raw1 : raw0);
        }
    	
        IPCThreadState::self()->restoreCallingIdentity(token);
    }
        break;

	case CDX_EVENT_IS_ENCRYPTED_MEDIA:
		if(mMuteDRMWhenHDMI) {
			mIsDRMMedia = !!(*para);
		}
		LOGV("is encrypted media %d", mIsDRMMedia);
		break;

#if (CEDARX_ANDROID_VERSION >= 7)
	case CDX_EVENT_TIMED_TEXT:
	{
		if (mListener == NULL)
        {
            LOGE("fatal error! why mListener==NULL!");
            ret = 0;
            break;
		}
        sp<MediaPlayerBase> listener = mListener.promote();
        if (listener == NULL) 
        {
            LOGE("%s: fatal error! Listener is NULL.", __FUNCTION__);
            ret = 0;
            break;
        }

        CedarXTimedText *pCedarXTimedText = (CedarXTimedText*)para;
        if(pCedarXTimedText)    //show or hide
        {
            Parcel parcel;
            if (pCedarXTimedText->subMode == 0) //SUB_MODE_TEXT
            {
            	/*
                LOGD("eric_sub:show subtitle:subMode[%d], startx[%d], starty[%d], endx[%d], endy[%d], subDispPos[0x%x],\
                \n startTime[%d], endTime[%d], subTextLen[%d], encodingType[%d],\
                \n subHasFontInfFlag[%d], fontStyle[%d], fontSize[%d], primaryColor[0x%x], secondaryColor[0x%x], subStyle[%d]\
                \n mSubShowFlag[%d]",
                  pCedarXTimedText->subMode, pCedarXTimedText->startx, pCedarXTimedText->starty, pCedarXTimedText->endx, pCedarXTimedText->endy, pCedarXTimedText->subDispPos,
                  pCedarXTimedText->startTime, pCedarXTimedText->endTime, pCedarXTimedText->subTextLen, pCedarXTimedText->encodingType,
                  pCedarXTimedText->subHasFontInfFlag, pCedarXTimedText->fontStyle, pCedarXTimedText->fontSize, pCedarXTimedText->primaryColor, pCedarXTimedText->secondaryColor, pCedarXTimedText->subStyle,
                  pCedarXTimedText->getSubShowFlag());
                LOGD("eric_sub:subTextBuf:[%s]", pCedarXTimedText->subTextBuf);
 	 	 	 	*/
                extractTextLocalDescriptions(pCedarXTimedText, &parcel);
                listener->sendEvent(MEDIA_TIMED_TEXT, 0, 0, &parcel);
                ret = 0;
            }
            else if (pCedarXTimedText->subMode == 1)    //SUB_MODE_BITMAP
            {
//                LOGD("eric_sub:SUB_MODE_BITMAP, subMode[%d], startTime[%d], endTime[%d], subBitmapBuf[%p], subPicWidth[%d], subPicHeight[%d], encodingType[%d]",
//                    pCedarXTimedText->subMode, pCedarXTimedText->startTime, pCedarXTimedText->endTime, 
//                    pCedarXTimedText->subBitmapBuf, pCedarXTimedText->subPicWidth, pCedarXTimedText->subPicHeight, pCedarXTimedText->encodingType); 
                extractBmpLocalDescriptions(pCedarXTimedText, &parcel);
                listener->sendEvent(MEDIA_TIMED_TEXT, 0, 0, &parcel);
                ret = 0;
            }
            else
            {
                LOGW("fatal error! subMode=%d, check code!", pCedarXTimedText->subMode);
                ret = 0;
            }
        }
        else    //hide all
        {
           //LOGD("eric_sub:CDX_EVENT_TIMED_TEXT_FOR_PLAYER hide subtitle"); 
           listener->sendEvent(MEDIA_TIMED_TEXT);
           ret = 0;
        }
        break;
    }
#endif


	case CDX_EVENT_OPEN_DIR:
		// int32_t CedarXPlayer::OpenDirCb(const char *pDirPath)
		{
			struct OpenDir *openDirObject = (struct OpenDir *)info;
			openDirObject->nDirId = OpenDirCb((const char *)openDirObject->pDirPath);
			break;
		}
	case CDX_EVENT_READ_DIR:
		//int32_t CedarXPlayer::ReadDirCb(int32_t nDirId, char *pFileName, int32_t nFileNameSize)
		{
			struct ReadDir *readDirObject = (struct ReadDir *)info;
			readDirObject->ret = ReadDirCb(readDirObject->nDirId, readDirObject->pFileName, readDirObject->nFileNameSize);
			break;
		}
	case CDX_EVENT_CLOSE_DIR:
		//int32_t CedarXPlayer::CloseDirCb(int32_t nDirId)
		{
			struct CloseDir *closeDirObject = (struct CloseDir *)info;
			closeDirObject->ret = CloseDirCb(closeDirObject->nDirId);
			break;
		}
	case CDX_EVENT_OPEN_FILE:
		//int32_t CedarXPlayer::OpenFileCb(const char *pFilePath)
		{
			struct OpenFile *openFileObject = (struct OpenFile *)info;
			openFileObject->nFileFd = OpenFileCb((const char *)openFileObject->pFilePath);
			break;
		}
	case CDX_EVENT_CHECK_ACCESS:
		//int32_t CedarXPlayer::CheckAccessCb(const char *pFilePath, int mode)
		{
			struct CheckAccess *checkAccessObject = (struct CheckAccess *)info;
			checkAccessObject->isAccessable = CheckAccessCb((const char *)checkAccessObject->pFilePath, checkAccessObject->mode);
			break;
		}
	default:
		break;
	}

	return ret;
}
/*******************************************************************************
Function name: android.CedarXPlayer.AccessCb
Description: 
    
Parameters: 
    
Return: 
    0:	is accessable
    -1: is not accessable.
Time: 2014/3/3
*******************************************************************************/
int32_t CedarXPlayer::CheckAccessCb(const char *pFilePath, int mode)
{
    if(pFilePath == NULL || strlen(pFilePath) <= 0)
    {
        LOGW("(f:%s, l:%d) pFilePath[%p] is invalid\n", __FUNCTION__, __LINE__, pFilePath);
        return -1;
    }
    LOGV("(f:%s, l:%d) CHECK_ACCESS_RIGHRS for file[%s], FilePathlen[%d]\n", __FUNCTION__, __LINE__, pFilePath, strlen(pFilePath));

    Parcel parcel;
    Parcel replyParcel;
    int isAccessable = -1;	
    if (mListener != NULL) 
    {
		sp<MediaPlayerBase> listener = mListener.promote();
		if (listener != NULL) 
        {
            // write path string as a byte array
            parcel.writeInt32(strlen(pFilePath));
            parcel.write(pFilePath, strlen(pFilePath));
			parcel.writeInt32(mode);
            listener->sendEvent(AWEXTEND_MEDIA_INFO, AWEXTEND_MEDIA_INFO_CHECK_ACCESS_RIGHRS, 0, &parcel, &replyParcel);
            replyParcel.setDataPosition(0);
            isAccessable = replyParcel.readInt32();
			if(isAccessable == -1)
			{
				LOGW("(f:%s, l:%d) The file can not be access\n", __FUNCTION__, __LINE__);
			}
		}
        else
        {
            LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener promote is NULL\n", __FUNCTION__, __LINE__);
        }
	}
    else
    {
        LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener is NULL\n", __FUNCTION__, __LINE__);
    }
    return isAccessable;
}


/*******************************************************************************
Function name: android.CedarXPlayer.OpenFileCb
Description: 
    
Parameters: 
    
Return: 
    fd >= 0;
    -1: fail.
Time: 2014/3/3
*******************************************************************************/
int32_t CedarXPlayer::OpenFileCb(const char *pFilePath)
{
//    int nFileFd;
//    char byteArray[1024];
//    int result;
//    nFileFd  = open("/mnt/sdcard/Movies/test.srt", O_RDONLY);
//    ALOGD("(f:%s, l:%d) open fd[%d], fail?", __FUNCTION__, __LINE__, nFileFd);
//    if(nFileFd >= 0)
//    {
//        result = read(nFileFd, byteArray, 100);
//        ALOGD("(f:%s, l:%d) read [%d]bytes", __FUNCTION__, __LINE__, result);
//        byteArray[100] = 0;
//        ALOGD("readStr[%s]", byteArray);
//        close(nFileFd);
//    }

	// star add
	if (mPlayerState == PLAYER_STATE_UNKOWN)
	{
		LOGW("mPlayerState is PLAYER_STATE_UNKOWN, should not open filed");
		return -1;
	}

    if(pFilePath == NULL || strlen(pFilePath) <= 0)
    {
        LOGW("(f:%s, l:%d) pFilePath[%p] is invalid\n", __FUNCTION__, __LINE__, pFilePath);
        return -1;
    }
    LOGV("(f:%s, l:%d) we try to get file[%s] fd, FilePathlen[%d]\n", __FUNCTION__, __LINE__, pFilePath, strlen(pFilePath));

    Parcel parcel;
    Parcel replyParcel;
    bool    bFdValid = false;
    int     nFileFd = -1;
    if (mListener != NULL) 
    {
		sp<MediaPlayerBase> listener = mListener.promote();
		if (listener != NULL) 
        {
            // write path string as a byte array
            parcel.writeInt32(strlen(pFilePath));
            parcel.write(pFilePath, strlen(pFilePath));
            listener->sendEvent(AWEXTEND_MEDIA_INFO, AWEXTEND_MEDIA_INFO_REQUEST_OPEN_FILE, 0, &parcel, &replyParcel);
            replyParcel.setDataPosition(0);
            bFdValid = replyParcel.readInt32();
            if(bFdValid == true)
            {
                nFileFd = dup(replyParcel.readFileDescriptor());
            }
            ALOGV("(f:%s, l:%d) get nFileFd[%d]", __FUNCTION__, __LINE__, nFileFd);
//            if(nFileFd >= 0)
//            {
//                char    byteArray[1024];
//                int     result;
//                result = read(nFileFd, byteArray, 100);
//                ALOGD("(f:%s, l:%d) read [%d]bytes", __FUNCTION__, __LINE__, result);
//                byteArray[100] = 0;
//                ALOGD("readStr[%s]", byteArray);
//                close(nFileFd);
//            }
		}
        else
        {
            LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener promote is NULL\n", __FUNCTION__, __LINE__);
        }
	}
    else
    {
        LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener is NULL\n", __FUNCTION__, __LINE__);
    }
    return nFileFd;
}

/*******************************************************************************
Function name: android.CedarXPlayer.OpenDirCb
Description: 
    
Parameters: 
    
Return: 
    dirId >= 0, it is defined by us.
    -1: fail
Time: 2014/3/3
*******************************************************************************/
int32_t CedarXPlayer::OpenDirCb(const char *pDirPath)
{
    if(pDirPath == NULL || strlen(pDirPath) <= 0)
    {
        LOGW("(f:%s, l:%d) pDirPath[%p] is invalid\n", __FUNCTION__, __LINE__, pDirPath);
        return -1;
    }
    LOGV("(f:%s, l:%d) we try to open dir[%s], dirPathlen[%d]\n", __FUNCTION__, __LINE__, pDirPath, strlen(pDirPath));

    Parcel parcel;
    Parcel replyParcel;
    int     nDirId = -1;
    if (mListener != NULL) 
    {
		sp<MediaPlayerBase> listener = mListener.promote();
		if (listener != NULL) 
        {
            // write path string as a byte array
            parcel.writeInt32(strlen(pDirPath));
            parcel.write(pDirPath, strlen(pDirPath));
            listener->sendEvent(AWEXTEND_MEDIA_INFO, AWEXTEND_MEDIA_INFO_REQUEST_OPEN_DIR, 0, &parcel, &replyParcel);
            replyParcel.setDataPosition(0);
            nDirId = replyParcel.readInt32();
            ALOGV("(f:%s, l:%d) get nDirId[%d]", __FUNCTION__, __LINE__, nDirId);
		}
        else
        {
            LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener promote is NULL\n", __FUNCTION__, __LINE__);
        }
	}
    else
    {
        LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener is NULL\n", __FUNCTION__, __LINE__);
    }
    return nDirId;
}

/*******************************************************************************
Function name: android.CedarXPlayer.ReadDirCb
Description: 
    
Parameters: 
    dirId >= 0, it is defined by us, return by CedarXPlayer::OpenDirCb().
    pFileName: used to fill filename string.
    nFileNameSize: the size of pFileName. file name len can't exceed it.
Return: 
    0:success;
    -1: fail
Time: 2014/3/3
*******************************************************************************/
int32_t CedarXPlayer::ReadDirCb(int32_t nDirId, char *pFileName, int32_t nFileNameSize)
{
    int32_t ret = -1;
    int32_t replyRet = -1;
    if(nDirId < 0)
    {
        LOGW("(f:%s, l:%d) nDirId[%d] is invalid\n", __FUNCTION__, __LINE__, nDirId);
        return -1;
    }
    LOGV("(f:%s, l:%d) we try to read dir, nDirId[%d]\n", __FUNCTION__, __LINE__, nDirId);

    Parcel parcel;
    Parcel replyParcel;
    int     nFileNameLen = -1;
    if (mListener != NULL) 
    {
		sp<MediaPlayerBase> listener = mListener.promote();
		if (listener != NULL) 
        {
            // write path string as a byte array
            parcel.writeInt32(nDirId);
            listener->sendEvent(AWEXTEND_MEDIA_INFO, AWEXTEND_MEDIA_INFO_REQUEST_READ_DIR, 0, &parcel, &replyParcel);
            replyParcel.setDataPosition(0);
            replyRet = replyParcel.readInt32();
            if(0 == replyRet)
            {
                nFileNameLen = replyParcel.readInt32();
                if(nFileNameLen < nFileNameSize && nFileNameLen > 0)
                {
                    const char* strdata = (const char*)replyParcel.readInplace(nFileNameLen);
                    memcpy(pFileName, strdata, nFileNameLen);
                    pFileName[nFileNameLen] = 0;
                    LOGV("(f:%s, l:%d) get filename[%s]", __FUNCTION__, __LINE__, pFileName);
                    ret = 0;
                }
                else
                {
                    LOGW("(f:%s, l:%d) fatal error! nFileNameLen[%d] >= nFileNameSize[%d]", __FUNCTION__, __LINE__, nFileNameLen, nFileNameSize);
                }
            }
		}
        else
        {
            LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener promote is NULL\n", __FUNCTION__, __LINE__);
        }
	}
    else
    {
        LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener is NULL\n", __FUNCTION__, __LINE__);
    }
    return ret;
}
/*******************************************************************************
Function name: android.CedarXPlayer.CloseDirCb
Description: 
    
Parameters: 
    
Return: 
    0: success;
    -1: fail
Time: 2014/3/3
*******************************************************************************/
int32_t CedarXPlayer::CloseDirCb(int32_t nDirId)
{
    int32_t ret = -1;
    if(nDirId < 0)
    {
        LOGW("(f:%s, l:%d) nDirId[%d] is invalid\n", __FUNCTION__, __LINE__, nDirId);
        return -1;
    }
    LOGV("(f:%s, l:%d) we try to close dir, nDirId[%d]\n", __FUNCTION__, __LINE__, nDirId);

    Parcel parcel;
    Parcel replyParcel;
    if (mListener != NULL) 
    {
		sp<MediaPlayerBase> listener = mListener.promote();
		if (listener != NULL) 
        {
            // write path string as a byte array
            parcel.writeInt32(nDirId);
            listener->sendEvent(AWEXTEND_MEDIA_INFO, AWEXTEND_MEDIA_INFO_REQUEST_CLOSE_DIR, 0, &parcel, &replyParcel);
            replyParcel.setDataPosition(0);
            ret = replyParcel.readInt32();
            LOGV("(f:%s, l:%d) closeDirId[%d] ret=[%d]", __FUNCTION__, __LINE__, nDirId, ret);
		}
        else
        {
            LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener promote is NULL\n", __FUNCTION__, __LINE__);
        }
	}
    else
    {
        LOGW("(f:%s, l:%d) fatal error! impossible CedarXPlayer->mListener is NULL\n", __FUNCTION__, __LINE__);
    }
    return ret;
}

extern "C" int32_t CedarXPlayerCallbackWrapper(void *cookie, int32_t event, void *info) //cookie:CedarXPlayer*;
{
	return ((android::CedarXPlayer *)cookie)->CedarXPlayerCallback(event, info);
}

} // namespace android
