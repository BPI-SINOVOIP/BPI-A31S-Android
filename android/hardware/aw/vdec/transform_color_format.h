
#ifndef TRANSFORM_COLOR_FORMAT_H
#define TRANSFORM_COLOR_FORMAT_H

#include "libcedarv.h"

#ifdef __cplusplus
extern "C" {
#endif

extern unsigned int cedarv_address_vir2phy(void *addr);
extern unsigned int cedarv_address_phy2vir(void *addr);
void TransformToYUVPlaner(cedarv_picture_t* pict, void* ybuf);
void TransformToGPUBuffer(cedarv_picture_t* pict, void* ybuf);
void TransformToVirtualBuffer(cedarv_picture_t* pict, void* ybuf);

#ifdef __cplusplus
}
#endif

#endif

