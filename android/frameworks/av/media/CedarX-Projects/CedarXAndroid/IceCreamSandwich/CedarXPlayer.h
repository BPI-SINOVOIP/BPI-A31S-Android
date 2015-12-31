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

#ifndef CEDARX_PLAYER_H_

#define CEDARX_PLAYER_H_

#include <media/MediaPlayerInterface.h>
#include <media/stagefright/DataSource.h>
//#include <media/stagefright/OMXClient.h>
#include <media/stagefright/TimeSource.h>
#include <utils/threads.h>
#include "CedarXAudioPlayer.h"
#include <media/stagefright/MediaBuffer.h>
//#include <media/stagefright/MediaClock.h>
#include <media/mediaplayerinfo.h>

#include <CDX_PlayerAPI.h>

#include <include_sft/NuPlayerSource.h>

#include <HDMIListerner.h>
namespace android {

struct CedarXAudioPlayer;
struct DataSource;
struct MediaBuffer;
struct MediaExtractor;
struct MediaSource;
struct NuCachedSource2;
struct ISurfaceTexture;

struct ALooper;
struct AwesomePlayer;
struct CedarXPlayerAdapter;

struct CedarXRenderer : public RefBase {
    CedarXRenderer() {}

    virtual void render(const void *data, size_t size) = 0;
    //virtual void render(MediaBuffer *buffer) = 0;
    virtual int32_t perform(int32_t cmd, int32_t para) = 0;
    virtual int32_t dequeueFrame(ANativeWindowBufferCedarXWrapper *pObject) = 0;
    virtual int32_t enqueueFrame(ANativeWindowBufferCedarXWrapper *pObject) = 0;
    
private:
    CedarXRenderer(const CedarXRenderer &);
    CedarXRenderer &operator=(const CedarXRenderer &);
};

//for setParameter()
typedef enum tag_KeyofSetParameter{
    PARAM_KEY_ENCRYPT_FILE_TYPE_DISABLE          = 0x00,
    PARAM_KEY_ENCRYPT_ENTIRE_FILE_TYPE           = 0x01,
    PARAM_KEY_ENCRYPT_PART_FILE_TYPE             = 0x02,
    PARAM_KEY_ENABLE_BOOTANIMATION               = 100,

    //add by weihongqiang for IPTV.
    PARAM_KEY_SET_AV_SYNC			             = 0x100,
    PARAM_KEY_ENABLE_KEEP_FRAME				     = 0x101,
    PARAM_KEY_CLEAR_BUFFER				     	 = 0x102,
    //audio channel.
    PARAM_KEY_SWITCH_CHANNEL		     	 	 = 0x103,
    //for IPTV end.
    PARAM_KEY_,
}KeyofSetParameter;

enum {
    PLAYING             = 0x01,
    LOOPING             = 0x02,
    FIRST_FRAME         = 0x04,
    PREPARING           = 0x08,
    PREPARED            = 0x10,
    AT_EOS              = 0x20,
    PREPARE_CANCELLED   = 0x40,
    CACHE_UNDERRUN      = 0x80,
    AUDIO_AT_EOS        = 0x100,
    VIDEO_AT_EOS        = 0x200,
    AUTO_LOOPING        = 0x400,
    WAIT_TO_PLAY        = 0x800,
    WAIT_VO_EXIT        = 0x1000,
    CEDARX_LIB_INIT     = 0x2000,
    SUSPENDING          = 0x4000,
    PAUSING				= 0x8000,
    RESTORE_CONTROL_PARA= 0x10000,
    NATIVE_SUSPENDING   = 0x20000,
};

enum {
	SOURCETYPE_URL = 0,
	SOURCETYPE_FD ,
	SOURCETYPE_SFT_STREAM
};

typedef struct CedarXPlayerExtendMember_{
	int64_t mLastGetPositionTimeUs;
	int64_t mLastPositionUs;
	int32_t mOutputSetting;
	int32_t mUseHardwareLayer;
	int32_t mPlaybackNotifySend;

    //for thirdpart_fread of encrypt video file
    int32_t     encrypt_type;         // 0: disable; 1: encrypt entire file; 2: encrypt part file;
    uint32_t    encrypt_file_format;  //

    //for Bootanimation;
    uint32_t    bootanimation_enable;
}CedarXPlayerExtendMember;

struct CedarXPlayer { //don't touch this struct any more, you can extend members in CedarXPlayerExtendMember
    CedarXPlayer();
    ~CedarXPlayer();

    void setListener(const wp<MediaPlayerBase> &listener);
    void setUID(uid_t uid);

    status_t setDataSource(
            const char *uri,
            const KeyedVector<String8, String8> *headers = NULL);

    status_t setDataSource(int32_t fd, int64_t offset, int64_t length);
    status_t setDataSource(const sp<IStreamSource> &source);

    void reset();

    status_t prepare();
    status_t prepare_l();
    status_t prepareAsync();
    status_t prepareAsync_l();

    status_t play();
    status_t pause();
    status_t stop_l();
    status_t stop();

    bool isPlaying() const;

    status_t setSurface(const sp<Surface> &surface);
//    status_t setSurfaceTexture(const sp<ISurfaceTexture> &surfaceTexture);
    status_t setSurfaceTexture(const sp<IGraphicBufferProducer> &bufferProducer);
    void setAudioSink(const sp<MediaPlayerBase::AudioSink> &audioSink);
    status_t setLooping(bool shouldLoop);

    status_t getDuration(int64_t *durationUs);
    status_t getPosition(int64_t *positionUs);

    status_t setParameter(int32_t key, const Parcel &request);
    status_t getParameter(int32_t key, Parcel *reply);
    status_t setCacheStatCollectFreq(const Parcel &request);
    status_t seekTo(int64_t timeUs);

    status_t getVideoDimensions(int32_t *width, int32_t *height) const;

    status_t suspend();
    status_t resume();
    int32_t setVps(int32_t vpsspeed);    //set play speed,  aux = -40~100,=0-normal; <0-slow; >0-fast, so speed scope: (100-40)% ~ (100+100)%, ret = OK
#ifndef __CHIP_VERSION_F20
    int32_t  getMeidaPlayerState();
    status_t setSubCharset(const char *charset);
    status_t getSubCharset(char *charset);
    status_t setSubDelay(int32_t time);
    int32_t  getSubDelay();

    status_t enableScaleMode(bool enable, int32_t width, int32_t height);

    //TODO:remove useless code
    status_t getVideoEncode(char *encode){return -1;}
    int32_t  getVideoFrameRate(){return -1;}
    status_t getAudioEncode(char *encode){return -1;}
    int32_t  getAudioBitRate(){return -1;}
    int32_t  getAudioSampleRate(){return -1;}
    //status_t setSubGate(bool showSub){return -1;}
    //bool getSubGate(){return -1;}
    //status_t setScreen(int32_t screen){return -1;}
    //int32_t  getSubCount(){return -1;}
    //int32_t  getSubList(MediaPlayer_SubInfo *infoList, int32_t count){return -1;}
    //int32_t  getCurSub(){return -1;}
    //status_t switchSub(int32_t index){return -1;}
    //status_t setSubColor(int32_t color){return -1;}
    //int32_t  getSubColor(){return -1;}
    //status_t setSubFrameColor(int32_t color){return -1;}
    //int32_t  getSubFrameColor(){return -1;}
    //status_t setSubFontSize(int32_t size){return -1;}
    //int32_t  getSubFontSize(){return -1;}
    //status_t setSubPosition(int32_t percent){return -1;}
    //int32_t  getSubPosition(){return -1;}
    //int32_t  getTrackCount(){return -1;}
    //int32_t  getTrackList(MediaPlayer_TrackInfo *infoList, int32_t count){return -1;}
    //int32_t  getCurTrack(){return -1;}
    //status_t switchTrack(int32_t index){return -1;}
    //status_t setInputDimensionType(int32_t type){return -1;}
    //int32_t  getInputDimensionType(){return -1;}
    //status_t setOutputDimensionType(int32_t type){return -1;}
    //int32_t  getOutputDimensionType(){return -1;}
    //status_t setAnaglaghType(int32_t type){return -1;}
    //int32_t  getAnaglaghType(){return -1;}
    //status_t setVppGate(bool enableVpp){return OK;}
    //status_t setLumaSharp(int32_t value){return OK;}
    //status_t setChromaSharp(int32_t value){return OK;}
    //status_t setWhiteExtend(int32_t value){return OK;}
    //status_t setBlackExtend(int32_t value){return OK;}
    /*remove interface above*/

    status_t setChannelMuteMode(int32_t muteMode);
    int32_t getChannelMuteMode();
    status_t extensionControl(int32_t command, int32_t para0, int32_t para1);
    status_t generalInterface(int32_t cmd, int32_t int1, int32_t int2, int32_t int3, void *p);
#endif
    // This is a mask of MediaExtractor::Flags.
    uint32_t flags() const;

    void postAudioEOS();
    void postAudioSeekComplete();
#if (CEDARX_ANDROID_VERSION >= 7)
    //added by weihongqiang.
    status_t invoke(const Parcel &request, Parcel *reply);
#endif

    int32_t CedarXPlayerCallback(int32_t event, void *info);

    //TODO:donot add interface like this, use get/setParameter instead.
    int32_t getMaxCacheSize();
    int32_t getMinCacheSize();
    int32_t getStartPlayCacheSize();
    int32_t getCachedDataSize();
    int32_t getCachedDataDuration();
    int32_t getStreamBitrate();
    // add by yaosen 2013-1-22
	int32_t getCacheSize(int32_t *nCacheSize);
	int32_t getCacheDuration(int32_t *nCacheDuration);
    bool setCacheSize(int32_t nMinCacheSize,int32_t nStartCacheSize,int32_t nMaxCacheSize);
    bool setCacheParams(int32_t nMaxCacheSize, int32_t nStartPlaySize, int32_t nMinCacheSize, int32_t nCacheTime, bool bUseDefaultPolicy);
    void getCacheParams(int32_t* pMaxCacheSize, int32_t* pStartPlaySize, int32_t* pMinCacheSize, int32_t* pCacheTime, bool* pUseDefaultPolicy);

private:
    friend struct CedarXEvent;

    mutable Mutex mLock;
    mutable Mutex mLockNativeWindow;

    CDXPlayer *mPlayer;
    AwesomePlayer *mAwesomePlayer;
    int32_t mStreamType;

    wp<MediaPlayerBase> mListener;
    bool mUIDValid;
    uid_t mUID;

    sp<ANativeWindow> mNativeWindow;
    sp<MediaPlayerBase::AudioSink> mAudioSink;

    String8 mUri;
    int32_t mSourceType;
    KeyedVector<String8, String8> mUriHeaders;

    sp<CedarXRenderer> mVideoRenderer;
    sp<Source> mSftSource;

    CedarXMediaInformations mMediaInfo;
    CedarXAudioPlayer *mAudioPlayer;
    int64_t mDurationUs;

    uint32_t mFlags;
    bool 	mIsCedarXInitialized;
    int32_t mDisableXXXX;

    int32_t mVideoWidth;
    int32_t mVideoHeight;
    int32_t mFirstFrame; //mVideoWidth:the video width told to app
    int32_t mCanSeek;
    int32_t mDisplayWidth;
    int32_t mDisplayHeight;
    int32_t mDisplayFormat;  //mDisplayWidth=vdeclib_display_width, mDisplayHeight=vdeclib_display_height,so when vdeclib rotate, we should careful! mDisplayFormat:destination format to send out. HWC_FORMAT_MBYUV422 or HAL_PIXEL_FORMAT_YV12

    int64_t mVideoTimeUs;

    int32_t  mTagPlay; //0: none 1:first TagePlay 2: Seeding TagPlay

    int64_t mSeekTimeUs;    //unit:us
    int64_t mBitrate;  // total bitrate of the file (in bps) or -1 if unknown.

    bool mSeeking;
    bool mSeekNotificationSent;
    bool mIsAsyncPrepare;

    int32_t mPlayerState;

    sp<ALooper> mLooper;

    int64_t mSuspensionPositionUs;

    struct SuspensionState {
        String8 mUri;
        KeyedVector<String8, String8> mUriHeaders;
        sp<DataSource> mFileSource;

        uint32_t mFlags;
        int64_t mPositionUs;
    } mSuspensionState;

    /*user defined parameters*/
    uint8_t mAudioTrackIndex;
    uint8_t mSubTrackIndex;
    int32_t mMaxOutputWidth;
    int32_t mMaxOutputHeight;

    int32_t mVideoScalingMode;
    int32_t mVpsspeed;    //set play speed,  aux = -40~100,=0-normal; <0-slow; >0-fast, so speed scope: (100-40)% ~ (100+100)%, ret = OK
    int32_t mDynamicRotation;   //vdeclib dynamic rotate, clock wise. 0: no rotate, 1: 90 degree (clock wise), 2: 180, 3: 270, 4: horizon flip, 5: vertical flig; reverse to mediaplayerinfo.h, VideoRotateAngle_0.
    int32_t mInitRotation;    //record vdeclib init rotate. clock wise.

    int32_t m3dModeDoubleStreamFlag;    //indicate if the file is 3d double stream.
    bool mIsDRMMedia;
    bool mHDMIPlugged;
    HDMIListerner * mHDMIListener;
	bool mMuteDRMWhenHDMI;

    struct SubtitleParameter {
		int32_t mSubtitleCharset;
		int32_t mSubtitleDelay;
    } mSubtitleParameter;

    CedarXPlayerExtendMember *mExtendMember;

    status_t play_l(int32_t command);
    void reset_l();
    void partial_reset_l();
    status_t seekTo_l(int64_t timeUs);
    status_t pause_l(bool at_eos = false);

    void notifyListener_l(int32_t msg, int32_t ext1 = 0, int32_t ext2 = 0);
    void abortPrepare(status_t err);
    void finishAsyncPrepare_l(int32_t err);
    void finishSeek_l(int32_t err);
    bool getCachedDuration_l(int64_t *durationUs, bool *eos);

    status_t finishSetDataSource_l();

    static bool ContinuePreparation(void *cookie);

    bool getBitrate(int64_t *bitrate);
    int32_t nativeSuspend();

    status_t setNativeWindow_l(const sp<ANativeWindow> &native);
    void initRenderer_l(bool all = true);
    int32_t StagefrightVideoRenderInit(int32_t width, int32_t height, int32_t format, void *frame_info);
    void StagefrightVideoRenderExit();
    void StagefrightVideoRenderData(void *frame_info, int32_t frame_id);
    int32_t StagefrightVideoRenderPerform(int32_t param_key, void *data);
    int32_t StagefrightVideoRenderDequeueFrame(void *frame_info, ANativeWindowBufferCedarXWrapper *pANativeWindowBuffer);
    int32_t StagefrightVideoRenderEnqueueFrame(ANativeWindowBufferCedarXWrapper *pANativeWindowBuffer);

    int32_t StagefrightAudioRenderInit(int32_t samplerate, int32_t channels, int32_t format);
    void StagefrightAudioRenderExit(int32_t immed);
    int32_t StagefrightAudioRenderData(void* data, int32_t len);
    int32_t StagefrightAudioRenderGetSpace(void);
    int32_t StagefrightAudioRenderGetDelay(void);
    int32_t StagefrightAudioRenderFlushCache(void);
    int32_t StagefrightAudioRenderPause(void);
#if (CEDARX_ANDROID_VERSION >= 7)
    //add by weihongqiang
    status_t selectTrack(size_t trackIndex, bool select);
    status_t getTrackInfo(Parcel *reply) const;
    size_t countTracks() const;
#endif
    status_t setDataSource_pre();
    status_t setDataSource_post();
    status_t setVideoScalingMode(int32_t mode);
    status_t setVideoScalingMode_l(int32_t mode);
    status_t setSubGate_l(bool showSub);
    bool getSubGate_l();
    status_t switchSub_l(int32_t index);
    status_t switchTrack_l(int32_t index);
    void 	 setHDMIState(bool state);
    static void HDMINotify(void* cookie, bool state);
    int32_t OpenFileCb(const char *pFilePath);
    int32_t OpenDirCb(const char *pDirPath);
    int32_t ReadDirCb(int32_t nDirId, char *pFileName, int32_t nFileNameSize);
    int32_t CloseDirCb(int32_t nDirId);
    int32_t CheckAccessCb(const char *pFilePath, int mode);

    CedarXPlayer(const CedarXPlayer &);
    CedarXPlayer &operator=(const CedarXPlayer &);

};



}  // namespace android

#endif  // AWESOME_PLAYER_H_

