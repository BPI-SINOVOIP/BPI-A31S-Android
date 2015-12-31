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

#ifndef SOFTWARE_RENDERER_H_

#define SOFTWARE_RENDERER_H_

#include <utils/RefBase.h>
#if (CEDARX_ANDROID_VERSION < 7)
#include <ui/android_native_buffer.h>
#endif
#include <CDX_PlayerAPI.h>
#define ADAPT_A10_GPU_RENDER (1)

namespace android {

struct MetaData;

//for perform function of CedarXRenderer
enum {
	CDX_RENDER_GET_OUTPUT_TYPE = 1,
	CDX_RENDER_SET_VIDEO_BUFFERS_INFO = 2,
	CDX_RENDER_SET_BUFFERS_DIMENSIONS = 3
};

enum {
    /* Do not change these values (starting with CDX_PIXEL_FORMAT),
     * keep them in sync with system/core/include/system/graphics.h.
     */
	CDX_PIXEL_FORMAT_RGBA_8888          = 1,
	CDX_PIXEL_FORMAT_RGBX_8888          = 2,
	CDX_PIXEL_FORMAT_RGB_888            = 3,
	CDX_PIXEL_FORMAT_RGB_565            = 4,
	CDX_PIXEL_FORMAT_BGRA_8888          = 5,
	CDX_PIXEL_FORMAT_RGBA_5551          = 6,
	CDX_PIXEL_FORMAT_RGBA_4444          = 7,
	CDX_PIXEL_FORMAT_AW_NV12		    = 0x101,
	CDX_PIXEL_FORMAT_AW_MB420  		    = 0x102,
	CDX_PIXEL_FORMAT_AW_MB411  		    = 0x103,
	CDX_PIXEL_FORMAT_AW_MB422  		    = 0x104,
	CDX_PIXEL_FORMAT_AW_YUV_PLANNER420  = 0x105,

	CDX_PIXEL_FORMAT_YV12   				= 0x32315659, // YCrCb 4:2:0 Planar
	CDX_PIXEL_FORMAT_RAW_SENSOR 			= 0x20,
	CDX_PIXEL_FORMAT_BLOB 					= 0x21,
	CDX_PIXEL_FORMAT_IMPLEMENTATION_DEFINED = 0x22,
	CDX_PIXEL_FORMAT_YCbCr_422_SP       = 0x10, // NV16
	CDX_PIXEL_FORMAT_YCrCb_420_SP       = 0x11, // NV21
	CDX_PIXEL_FORMAT_YCbCr_422_I        = 0x14, // YUY2
};

typedef struct CedarXRenderInfo {
	uint8_t *addr0[3];
	//for double stream.
	uint8_t *addr1[3];
	int32_t format;

    //cedarv_picture_t for deinterlace;
    uint8_t                      is_progressive;         //* progressive or interlace picture;
    uint8_t                      top_field_first;        //* display top field first;
}CedarXRenderInfo;

class CedarXSoftwareRenderer {
public:
    CedarXSoftwareRenderer(
            const sp<ANativeWindow> &nativeWindow, const sp<MetaData> &meta);

    ~CedarXSoftwareRenderer();
    int32_t perform(int32_t cmd, int32_t para);
    int dequeueFrame(ANativeWindowBufferCedarXWrapper *pObject);
    int enqueueFrame(ANativeWindowBufferCedarXWrapper *pObject);

private:
    enum YUVMode {
        None,
    };

    YUVMode mYUVMode;
    sp<ANativeWindow> mNativeWindow;
    int32_t mWidth, mHeight;
    int32_t m3dModeDoubleStreamFlag;    //if file is double stream.
    int32_t setVideoBuffersInfo(int32_t para);
	int32_t setVideoBuffersDimensions(int32_t para);
    CedarXSoftwareRenderer(const CedarXSoftwareRenderer &);
    CedarXSoftwareRenderer &operator=(const CedarXSoftwareRenderer &);
};

}  // namespace android

#endif  // SOFTWARE_RENDERER_H_
