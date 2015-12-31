/*
*******************************************************************************
*                                 CedarX-Player
*
*           Copyright(C), 2013-2016, All Winner Technology Co., Ltd.
*                              All Rights Reserved
*
* File Name     : common.h
*
* Author        : Chen-Xiaochuan
*
* Version       : 1.0
*
* Data          : 2013.08.29
*
* Description   : This file define macro definitions and data types commonly
*                 used by parsers, decoders and components.
*
* History       :
*
*   <Author>            <time>          <version>   <description>
*   Chen-Xiaochuan      2013.08.29      1.0         Create this file.
*
*******************************************************************************
*/

#ifndef _COMMON_H
#define _COMMON_H

#ifdef __cplusplus
extern "C" {
#endif

/*   
#ifndef int64_t
    typedef long long int int64_t;
#endif
*/
//***********************************************************//
//* The maximum string length in CedarXMediaInfo structure.
//***********************************************************//
#define MAX_STRING_LENGTH 1024

//***********************************************************//
//* Define container formats
//***********************************************************//
typedef enum Container
{
    CONTAINER_UNKNOWN = 0,
    CONTAINER_TS,               //* 13818-1 transport stream, .ts/.tp/.m2ts/.mts files.
    CONTAINER_MPG,              //* 13818-1 program stream, .mpg/.mpeg/.vob files.
    CONTAINER_MP4,              //* Quick Time file format, .mp4/.mov/.3gp/.3gpp/.m4a/.m4r files.
    CONTAINER_MKV,              //* Matroska file format, .mkv/.webm files.
    CONTAINER_PMP,              //* Sony PSP multimedia file format, .pmp files.
    CONTAINER_FLV,              //* Adobe Flash video file format, .flv file.
    CONTAINER_ASF,              //* Windows advance stream format, .asf/.wmv file.
    CONTAINER_RTSP,             //* For RTSP Protocol.
    CONTAINER_RM,               //* RealMedia file format, .rm/.ra/.rmvb files.
    CONTAINER_OGM,              //* OGM file format, .ogm file.

    //* To Add More.
}Container;


//***********************************************************//
//* Define video stream format.
//***********************************************************//
typedef enum VideoCodec
{
    VIDEO_CODEC_UNKNOWN = 0,
    VIDEO_CODEC_AVC,            //* Advance video Coding, H264/MVC stream.
    VIDEO_CODEC_MPEG1,          //* ISO 11172-2, mpeg video stream.
    VIDEO_CODEC_MPEG2,          //* ISO 13818-2, mpeg2 video stream.
    VIDEO_CODEC_MPEG4,          //* ISO 14496-2, mpeg4 video stream.
    VIDEO_CODEC_MSMPEG4V1,      //* Microsoft Mpeg4v1 
    VIDEO_CODEC_MSMPEG4V2,      //* Microsoft Mpeg4v2 
    VIDEO_CODEC_DIVX3,          //* DIVX 3.11
    VIDEO_CODEC_DIVX4,          //* DIVX 4
    VIDEO_CODEC_DIVX5,          //* DIVX 5
    VIDEO_CODEC_DIVX6,          //* DIVX 6
    VIDEO_CODEC_XVID,           //* OpenXVID 
    VIDEO_CODEC_VP6,            //* On2 VP6
    VIDEO_CODEC_VP8,            //* On2 VP8
    VIDEO_CODEC_RV2,            //* Real Media RV2
    VIDEO_CODEC_RV3,            //* Real Media RV3(RV8)
    VIDEO_CODEC_RV4,            //* Real Media RV4(RV9/RV10)
    VIDEO_CODEC_H263,           //* ITU-T H263
    VIDEO_CODEC_SORENSON_H263,  //* Sorenson H263 Video.
    VIDEO_CODEC_SVQ,            //* SVQ1(Sorenson Vector Quantizer 1) and SVQ3(Sorenson Vector Quantizer 3).
    VIDEO_CODEC_WMV1,           //* Windows Media Video 7, WMV7
    VIDEO_CODEC_WMV2,           //* Windows Media Video 8, WMV8
    VIDEO_CODEC_WMV3,           //* Windows Media Video 9, WMV9, VC-1

    //* To Add More.
}VideoCodec;


//***********************************************************//
//* Define audio stream format.
//***********************************************************//
typedef enum AudioCodec
{
    AUDIO_CODEC_UNKNOWN = 0,
    AUDIO_CODEC_MP1,            //* MPEG audio layer 1.
    AUDIO_CODEC_MP2,            //* MPEG audio layer 2.
    AUDIO_CODEC_MP3,            //* MPEG audio layer 3.
    AUDIO_CODEC_AAC,            //* MPEG2 Audio, Advance Audio Coding.
    AUDIO_CODEC_AC3,            //* Dolby Surround Audio Coding-3
    AUDIO_CODEC_ADPCM,          //* 
    AUDIO_CODEC_AMR,            //* Adaptive Multi-Rate, AMR-WB/AMR-NB
    AUDIO_CODEC_APE,
    AUDIO_CODEC_ATRAC,          //* Adaptive Transform Acoustic Coding, for MD device.
    AUDIO_CODEC_COOK,
    AUDIO_CODEC_DTS,
    AUDIO_CODEC_FLAC,
    AUDIO_CODEC_MIDI,
    AUDIO_CODEC_MLP,
    AUDIO_CODEC_OGG,
    AUDIO_CODEC_PCM,
    AUDIO_CODEC_RA,
    AUDIO_CODEC_SIPR,
    AUDIO_CODEC_WMA,

    //* To Add More.

}AudioCodec;




//***********************************************************//
//* Maximum video/audio/subtitle stream number supported.
//***********************************************************//


//***********************************************************//
//* Define the media information structure.
//***********************************************************//
typedef struct CedarXMediaInfo
{
    int                         nVideoStreamNum;                                //* how many video streams in the file.
    int                         nAudioStreamNum;                                //* how many audio streams in the file.
    int                         nSubtitleStreamNum;                             //* how many subtitle streams in the file.
    int                         bSeekable;                                      //* dose the media file support seek operation.
    int64_t                     nDuration;                                      //* duration of the media file in unit of micro second.
    int64_t                     nFileSize;                                      //* file size of the media file, in unit of byte.
    char                        strAuthor[MAX_STRING_LENGTH];                   //* author name.
    char                        strCopyRight[MAX_STRING_LENGTH];                //* copy right string.
    char                        strTitle[MAX_STRING_LENGTH];                    //* title of the media.
    char                        strGenre[MAX_STRING_LENGTH];                    //* genre of the media.
#if 0
    CedarXVideoStreamInfo       sVideoStreamInfo[MAX_VIDEO_STREAM_NUM];         //* video stream information.
    CedarXAudioStreamInfo       sAudioStreamInfo[MAX_AUDIO_STREAM_NUM];         //* list of audio streams' information.
    CedarXSubtitleStreamInfo    sSubtitleStreamInfo[MAX_SUBTITLE_STREAM_NUM];   //* list of subtitle streams' information.
#endif
}CedarXMediaInfo;
    
#ifdef __cplusplus
}
#endif

#endif

