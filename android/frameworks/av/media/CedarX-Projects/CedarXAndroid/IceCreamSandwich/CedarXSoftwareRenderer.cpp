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
#define LOG_TAG "CedarXSoftwareRenderer"
#include <CDX_Debug.h>

#include <binder/MemoryHeapBase.h>

#if (CEDARX_ANDROID_VERSION < 7)
#include <binder/MemoryHeapPmem.h>
#include <surfaceflinger/Surface.h>
#include <ui/android_native_buffer.h>
#else
#include <system/window.h>
#endif

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MetaData.h>
#include <ui/GraphicBufferMapper.h>
//#include <gui/ISurfaceTexture.h>
#include <gui/Surface.h>

#include "CedarXSoftwareRenderer.h"

#include <hardware/hal_public.h>
#include <linux/ion.h>
#include <ion/ion.h>

extern "C" {
extern unsigned int cedarv_address_phy2vir(void *addr);
}

namespace android {

CedarXSoftwareRenderer::CedarXSoftwareRenderer(
        const sp<ANativeWindow> &nativeWindow, const sp<MetaData> &meta)
    : mYUVMode(None),
      mNativeWindow(nativeWindow) {
    int32_t halFormat;
    CHECK(meta->findInt32(kKeyColorFormat, &halFormat));
    CHECK(meta->findInt32(kKeyWidth, &mWidth));
    CHECK(meta->findInt32(kKeyHeight, &mHeight));

    int32_t nVdecInitRotation;  //clock wise.
    int32_t rotationDegrees = 0;    //anti-clock wise,
    if (!meta->findInt32(kKeyRotation, &nVdecInitRotation)) {
        LOGD("(f:%s, l:%d) find fail nVdecInitRotation[%d]", __FUNCTION__, __LINE__, nVdecInitRotation);
        rotationDegrees = 0;
    } else {
    	rotationDegrees = (360 - nVdecInitRotation*90)%360;
    }
    meta->findInt32(kKey3dDoubleStream, &m3dModeDoubleStreamFlag);
    
    int GpuBufHeight_num = m3dModeDoubleStreamFlag ? 2 : 1;;
    if(GpuBufHeight_num > 1)
    {
        ALOGD("(f:%s, l:%d) 3D double stream, but gpu buffer neednot double!", __FUNCTION__, __LINE__);
        GpuBufHeight_num = 1;
    }
    size_t bufWidth, bufHeight;
    size_t nGpuBufWidth, nGpuBufHeight;

    bufWidth = mWidth;
    bufHeight = mHeight;
    if(bufWidth != ((mWidth + 15) & ~15))
    {
        LOGW("(f:%s, l:%d) bufWidth[%d]!=display_width[%d]", __FUNCTION__, __LINE__, ((mWidth + 15) & ~15), mWidth);
    }
    if(bufHeight != ((mHeight + 15) & ~15))
    {
        LOGW("(f:%s, l:%d) bufHeight[%d]!=display_height[%d]", __FUNCTION__, __LINE__, ((mHeight + 15) & ~15), mHeight);
    }

    CHECK(mNativeWindow != NULL);
    int usage = 0;
    meta->findInt32(kKeyIsDRM, &usage);
    if(usage) {
    	LOGV("protected video");
    	usage = GRALLOC_USAGE_PROTECTED;
    }
   usage |= GRALLOC_USAGE_SW_READ_NEVER /*| GRALLOC_USAGE_SW_WRITE_OFTEN*/
            | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP | GRALLOC_USAGE_HW_2D;


    CHECK_EQ(0,
            native_window_set_usage(
            mNativeWindow.get(),
            usage));

    CHECK_EQ(0,
            native_window_set_scaling_mode(
            mNativeWindow.get(),
            NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW));

    nGpuBufWidth = mWidth;
    nGpuBufHeight = mHeight;
	
    //A31 GPU rect width should 8 align.
    nGpuBufWidth = (mWidth + 7) & ~7;

    CHECK_EQ(0, native_window_set_buffers_geometry(
                mNativeWindow.get(),
                nGpuBufWidth,
                nGpuBufHeight * GpuBufHeight_num,
                halFormat));
    uint32_t transform;
    switch (rotationDegrees) {
        case 0: transform = 0; break;
        case 90: transform = HAL_TRANSFORM_ROT_90; break;
        case 180: transform = HAL_TRANSFORM_ROT_180; break;
        case 270: transform = HAL_TRANSFORM_ROT_270; break;
        default: transform = 0; break;
    }

    if (transform) {
        LOGD("(f:%s, l:%d) transform[%d]", __FUNCTION__, __LINE__, transform);
        CHECK_EQ(0, native_window_set_buffers_transform(
                    mNativeWindow.get(), transform));
    }
    Rect crop;
    crop.left = 0;
    crop.top  = 0;
    crop.right = bufWidth;
    crop.bottom = bufHeight;
    mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SET_CROP, &crop);
}

CedarXSoftwareRenderer::~CedarXSoftwareRenderer() {
}
/*
static int ALIGN(int x, int y) {
    // y must be a power of 2.
    return (x + y - 1) & ~(y - 1);
}*/

int32_t CedarXSoftwareRenderer::setVideoBuffersInfo(int32_t para)
{
	CedarXRenderInfo *info = (CedarXRenderInfo *)para;
	CHECK(info != NULL);
#if 1//((defined(__CHIP_VERSION_F33)))
//    ALOGD("(f:%s, l:%d), addr[0x%x][0x%x][0x%x][0x%x][0x%x][0x%x], format[0x%x], is_progressive[%d], top_field_first[%d]", __FUNCTION__, __LINE__, 
//        info->addr0[0], info->addr0[1], info->addr0[2], info->addr1[0], info->addr1[1], info->addr1[2], info->format,
//        info->is_progressive, info->top_field_first);
	mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SET_VIDEO_BUFFERS_INFO,
			info->addr0[0], info->addr0[1], info->addr0[2],
			info->addr1[0], info->addr1[1], info->addr1[2], info->format);
    mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_SET_DIT_INFO, info->is_progressive?0:1, info->top_field_first);
#endif
	return 0;
}

int32_t CedarXSoftwareRenderer::setVideoBuffersDimensions(int32_t para)
{
	int32_t *info = (int32_t *)para;
	CHECK(info != NULL);
	LOGD("f %s, width %d, height %d", __FUNCTION__, info[0], info[1]);
	return mNativeWindow->perform(mNativeWindow.get(), 
								  NATIVE_WINDOW_SET_BUFFERS_DIMENSIONS,
								  info[0], 
								  info[1]);
}

int32_t CedarXSoftwareRenderer::perform(int32_t cmd, int32_t para)
{
	int32_t ret = -1;
	switch(cmd) {
	case CDX_RENDER_GET_OUTPUT_TYPE:
#if 1//((defined(__CHIP_VERSION_F33)))
	   ret = mNativeWindow->perform(mNativeWindow.get(), NATIVE_WINDOW_GET_OUTPUT_TYPE);
#else
	   ret = 0;
#endif
	   break;
	case CDX_RENDER_SET_VIDEO_BUFFERS_INFO:
		ret = setVideoBuffersInfo(para);
		break;
	case CDX_RENDER_SET_BUFFERS_DIMENSIONS:
		ret = setVideoBuffersDimensions(para);
	default:
		break;
	}

	return ret;
}

int CedarXSoftwareRenderer::dequeueFrame(ANativeWindowBufferCedarXWrapper *pObject)
{
    ANativeWindowBuffer *buf;
    ANativeWindowBufferCedarXWrapper *pANativeWindowBuffer = (ANativeWindowBufferCedarXWrapper*)pObject;
    int err;

#if ((CEDARX_ANDROID_VERSION == 8) || (CEDARX_ANDROID_VERSION == 9) || (CEDARX_ANDROID_VERSION == 10))
    if ((err = mNativeWindow->dequeueBuffer_DEPRECATED(mNativeWindow.get(), &buf)) != 0) {
        LOGW("Surface::dequeueBuffer returned error %d", err);
        return -1;
    }
    CHECK_EQ(0, mNativeWindow->lockBuffer_DEPRECATED(mNativeWindow.get(), buf));
#else
    if ((err = mNativeWindow->dequeueBuffer(mNativeWindow.get(), &buf)) != 0)
    {
        LOGW("Surface::dequeueBuffer returned error %d", err);
        return -1;
    }
    CHECK_EQ(0, mNativeWindow->lockBuffer(mNativeWindow.get(), buf));
#endif

    GraphicBufferMapper &mapper = GraphicBufferMapper::get();

    int GpuBufHeight_num = 0;
    GpuBufHeight_num = m3dModeDoubleStreamFlag ? 2 : 1;
    if(GpuBufHeight_num > 1)
    {
        GpuBufHeight_num = 1;
    }
    Rect bounds(mWidth, mHeight*GpuBufHeight_num);

    void *dst;
    void *dstPhyAddr;
    int phyaddress;
    CHECK_EQ(0, mapper.lock(buf->handle, GRALLOC_USAGE_SW_WRITE_OFTEN, bounds, &dst));

    if(buf->handle)
	{
		IMG_native_handle_t* hnd = (IMG_native_handle_t*)(buf->handle);
		int fd = ion_open();
		struct ion_handle *handle_ion;

		if(fd != -1)
		{
			ion_import(fd, hnd->fd[0], &handle_ion);
			phyaddress = ion_getphyadr(fd, handle_ion);
			//LOGD("++++++++phyaddress: %x\n", (unsigned int)phyaddress);

			ion_close(fd);
		}
		else
		{
			LOGE("ion_open fail");
			return -1;
		}
	}
	else
	{
		LOGE("buf->handle is null");
		return -1;
	}

    pANativeWindowBuffer->pBufferHandle = buf->handle;
    pANativeWindowBuffer->pNativeWindow = mNativeWindow.get();

    pANativeWindowBuffer->width     = buf->width;
    pANativeWindowBuffer->height    = buf->height;
    pANativeWindowBuffer->stride    = buf->stride;
    pANativeWindowBuffer->format    = buf->format;
    pANativeWindowBuffer->usage     = buf->usage;
    pANativeWindowBuffer->dst       = dst;
    pANativeWindowBuffer->dstPhy    = (void*)phyaddress;
    pANativeWindowBuffer->pObjANativeWindowBuffer = (void*)buf;
    //LOGV("dequeued frame %p", buf->handle);
    return 0;
}

int CedarXSoftwareRenderer::enqueueFrame(ANativeWindowBufferCedarXWrapper *pObject)
{
    int err;
    ANativeWindowBuffer *buf = (ANativeWindowBuffer*)pObject->pObjANativeWindowBuffer;
    GraphicBufferMapper &mapper = GraphicBufferMapper::get();
    CHECK_EQ(0, mapper.unlock(buf->handle));

#if ((CEDARX_ANDROID_VERSION == 8) || (CEDARX_ANDROID_VERSION == 9) || (CEDARX_ANDROID_VERSION == 10))
    if ((err = mNativeWindow->queueBuffer_DEPRECATED(mNativeWindow.get(), buf)) != 0) {
        LOGW("Surface::queueBuffer returned error %d", err);
    }

    //LOGV("enqueued frame %p", buf->handle);
    buf = NULL;
#else 
    if ((err = mNativeWindow->queueBuffer(mNativeWindow.get(), buf)) != 0) {
        LOGW("Surface::queueBuffer returned error %d", err);
    }
    buf = NULL;
#endif
    return 0;
}

}  // namespace android

