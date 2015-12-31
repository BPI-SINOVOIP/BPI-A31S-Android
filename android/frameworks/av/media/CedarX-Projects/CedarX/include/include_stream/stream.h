/*
*******************************************************************************
*                                 CedarX-Player
*
*           Copyright(C), 2013-2016, All Winner Technology Co., Ltd.
*                              All Rights Reserved
*
* File Name     : stream.h
*
* Author        : Chen-Xiaochuan
*
* Version       : 1.0
*
* Data          : 2013.08.29
*
* Description   : This file define data structure and interface of the stream
*                 module.
*
* History       :
*
*   <Author>            <time>          <version>   <description>
*   Chen-Xiaochuan      2013.08.29      1.0         Create this file.
*
*******************************************************************************
*/

#ifndef _STREAM_H
#define _STREAM_H

#include "common.h"


#ifdef __cplusplus
extern "C" {
#endif

//***********************************************************//
//* Define seek reference for the seek operation.
//***********************************************************//
#define STREAM_SEEK_SET 0           //* offset from the file start.
#define STREAM_SEEK_CUR 1           //* offset from current file position.
#define STREAM_SEEK_END 2           //* offset from the file end.


//***********************************************************//
//* Define IO status.
//***********************************************************//
typedef enum CedarXIOState
{
    CEDARX_IO_STATE_OK      = 0,    //* nothing wrong of the data accession.
    CEDARX_IO_STATE_ERROR   = -1,   //* unknown error, can't access the media file.
    CEDARX_IO_STATE_BLOCK   = -2,   //* blocked in a operation for more than 1 seconds.
    CEDARX_IO_STATE_END     = -3    //* all media data has been read.
}CedarXIOState;


//***********************************************************//
//* Define return value of the Read operation.
//***********************************************************//
#define STREAM_END      0           //* all data has been read, file end.
#define STREAM_EAGAIN   -1          //* can not access data, you can wait for a while and try again.
#define STREAM_EIO      -2          //* can not access data, can not connect to the media file.


//***********************************************************//
//* Define commands for the CedarXStreamInfo->Control method.
//***********************************************************//
typedef enum CedarXStreamCommand
{
    STREAM_CMD_READ_DIRECT      = 0x100,    //* Read data directly to the specific buffer, defined for the Http Core module.
                                            //* param is an argument array, bufAddr = param[0], size = param[1];
                                            //* return the size read to the buffer.
    STREAM_CMD_GET_DURATION     = 0x101,    //* Get the duration of the media file.
                                            //* param is a pointer to a int64_t variable, duration = *(int64_t*)param.
                                            //* return 0 if OK, return -1 if not supported by the stream handler.

    //* To Add More commands here.
}CedarXStreamCommand;


//***********************************************************//
//* Define the CedarXStreamInfo structure.
//***********************************************************//
typedef struct CedarXStreamInfo CedarXStreamInfo;
struct CedarXStreamInfo
{
    //**************************
    //* attributes.
    //**************************
    int     bIsNetworkStream;       //* whether the media source file is a network stream.
    
    //**************************
    //* operations.
    //**************************

    //* @brief Read data.
    //* @param buf [IN]: read data to this buffer.
    //* @param size [IN]: how many bytes to read.
    //* @param stream [IN]: the stream handler.
    //* @return an integer
    //* @retval positive values (>0): how many bytes read.
    //* @retval STREAM_END (==0)    : means stream end.
    //* @retval STREAM_EAGAIN (==-1): one read operation fails, you can try to read again.
    //* @retval STREAM_EIO (==-2)   : disconnected to the media source, can not access data any more.
    int (*Read)(void* buf, int size, CedarXStreamInfo* stream);

    //* @brief Seek to a specific file position.
    //* @param stream [IN]: the stream handler.
    //* @param offset [IN]: offset according to the reference point specified by 'whence'.
    //* @param whence [IN]: reference position.
    //* @return an integer
    //* @retval >= 0: new file position.
    //* @retval ==-1: seek operation fails.
    int64_t (*Seek)(CedarXStreamInfo* stream, int64_t offset, int whence);
    
    //* @brief Tell the current file position.
    //* @param stream [IN]: the stream handler.
    //* @return an integer
    //* @retval >= 0: the current file position.
    int64_t (*Tell)(CedarXStreamInfo* stream);
    
    //* @brief Is the stream end.
    //* @param stream [IN]: the stream handler.
    //* @return ==1: file end, all data read.
    //* @retval ==0: not end yet.
    int (*Eof)(CedarXStreamInfo* stream);

    //* @brief Seek to a specific time position.
    //* @param stream [IN]: the stream handler.
    //* @param timeUs [IN]: time position in unit of micro second.
    //* @return time option after this operation.
    //* @retval >=0: time option after seek.
    //* @retval ==-1: seek operation fails.
    int64_t (*SeekToTime)(CedarXStreamInfo* stream, int64_t timeUs);
    
    //* @brief Get the file size.
    //* @param stream [IN]: the stream handler.
    //* @return file size.
    //* @retval >0: file size in unit of bytes.
    //* @retval ==0: unlimited file size, it is a live source.
    int64_t (*Size)(CedarXStreamInfo* stream);

    //* @brief How many bytes cached by the stream handler.
    //* @param stream [IN]: the stream handler.
    //* @return data size in cache.
    int (*CachedSize)(CedarXStreamInfo* stream);

    //* @brief Set the buffer size of the stream handler.
    //* @param stream [IN]: the stream handler.
    //* @param bufSize [IN]: size of the stream handler's buffer.
    //* @return an integer value.
    //* @retval ==0: OK.
    //* @retval ==-1: fail to set the buffer size.
    int (*SetBufferSize)(CedarXStreamInfo* stream, int bufSize);
    
    //* @brief Get the buffer size of the stream handler.
    //* @param stream [IN]: the stream handler.
    //* @return size of the buffer.
    int (*GetBufferSize)(CedarXStreamInfo* stream);

    //* @brief Close the stream handler.
    //* @param stream [IN]: the stream handler.
    //* @return an integer value.
    //* @return None.
    void (*Close)(CedarXStreamInfo* stream);
    
    //* @brief Check the IO status of the stream handler, whether the media source is accessible.
    //* @param stream [IN]: the stream handler.
    //* @return IO status of the stream handler, it describes the accessibility of the media source.
    //* @retval ==CEDARX_IO_STATE_OK: nothing wrong of the data accession.
    //* @retval ==CEDARX_IO_STATE_ERROR: unknown error, can't access the media file.
    //* @retval ==CEDARX_IO_STATE_BLOCK: blocked in a operation for more than 1 seconds.
    //* @retval ==CEDARX_IO_STATE_END: all media data has been read.
    enum CedarXIOState  (*CheckIOState)(CedarXStreamInfo* stream);

    //* @brief Extend functions of a stream handler.
    //* @param stream [IN]: the stream handler.
    //* @param cmd [IN]: command stand for a extended function.
    //* @param param [IN]: parameter for the specific command.
    //* @return according to the specific command.
    int (*Control)(CedarXStreamInfo* stream, int cmd, void* param);

    int (*ReadSegment)(void* buf, int size, CedarXStreamInfo* stream, int type);
	int64_t (*SeekSegment)(CedarXStreamInfo* stream, int64_t offset, int whence, int type);
	int64_t (*TellSegment)(CedarXStreamInfo* stream, int type);
	void (*Stop)(CedarXStreamInfo* stream);

};


//***********************************************************//
//* Define the CedarXDataSource structure.
//***********************************************************//
typedef struct CedarXDataSource CedarXDataSource;
struct CedarXDataSource
{
    char*               strUrl;             //* file location or URL.
    int                 nFd;                //* file descriptor, == -1 if data source if not a file descriptor.
    int64_t             nFdOffset;          //* original offset of the file specific by the file descriptor nFd.
    int64_t             nFdLength;          //* file size of the file specific by the file descriptor nFd.
    void*               pDataInterface;     //* data interface handle if media data is passed by a data interface.
    int                 nDataInterfaceId;   //* ID of the data interface, used to find parser in parser list.
    int                 bIsBluerayDisk;     //* whether the media source is a blueray disk.
    int                 nHttpHeaderSize;    //* size of the http header if need to pass extended http header, in unit of bytes.
    char***             pHttpHeader;        //* the extended http header.
    void*               pHttpCore;          //* handler of a http core stream.
    CedarXStreamInfo*   sStreamInfo;        //* the stream handler.
};


#ifdef __cplusplus
}
#endif

#endif

