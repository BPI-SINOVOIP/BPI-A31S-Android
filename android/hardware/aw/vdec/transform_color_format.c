
//#define LOG_NDEBUG 0
#define LOG_TAG "transform.c"
#include <utils/Log.h>

#include "transform_color_format.h"
#include <unistd.h>
#include <stdlib.h>
#include <memory.h>

enum FORMAT_CONVERT_COLORFORMAT
{
	CONVERT_COLOR_FORMAT_NONE = 0,
	CONVERT_COLOR_FORMAT_YUV420PLANNER,
	CONVERT_COLOR_FORMAT_YUV422PLANNER,
	CONVERT_COLOR_FORMAT_YUV420MB,
	CONVERT_COLOR_FORMAT_YUV422MB,
};

typedef struct ScalerParameter
{
	int mode; //0: YV12 1:thumb yuv420p
	int format_in;
	int format_out;

	int width_in;
	int height_in;

	int width_out;
	int height_out;

	void *addr_y_in;
	void *addr_c_in;
	unsigned int addr_y_out;
	unsigned int addr_u_out;
	unsigned int addr_v_out;
}ScalerParameter;

#if 0 //Don't HAVE_NEON
static void map32x32_to_yuv_Y(unsigned char* srcY, unsigned char* tarY, unsigned int coded_width, unsigned int coded_height)
{
	unsigned int i,j,l,m,n;
	unsigned int mb_width,mb_height,twomb_line,recon_width;
	unsigned long offset;
	unsigned char *ptr;

	ptr = srcY;
	mb_width = (coded_width+15)>>4;
	mb_height = (coded_height+15)>>4;
	twomb_line = (mb_height+1)>>1;
	recon_width = (mb_width+1)&0xfffffffe;

	for(i=0;i<twomb_line;i++)
	{
		for(j=0;j<recon_width;j+=2)
		{
			for(l=0;l<32;l++)
			{
				//first mb
				m=i*32 + l;
				n= j*16;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					memcpy(tarY+offset,ptr,16);
					ptr += 16;
				}
				else
					ptr += 16;

				//second mb
				n= j*16+16;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					memcpy(tarY+offset,ptr,16);
					ptr += 16;
				}
				else
					ptr += 16;
			}
		}
	}
}

static void map32x32_to_yuv_C(int mode,unsigned char* srcC,unsigned char* tarCb,unsigned char* tarCr,unsigned int coded_width,unsigned int coded_height)
{
	unsigned int i,j,l,m,n,k;
	unsigned int mb_width,mb_height,fourmb_line,recon_width;
	unsigned char line[16];
	unsigned long offset;
	unsigned char *ptr;

	ptr = srcC;
	mb_width = (coded_width+7)>>3;
	mb_height = (coded_height+7)>>3;
	fourmb_line = (mb_height+3)>>2;
	recon_width = (mb_width+1)&0xfffffffe;

	for(i=0;i<fourmb_line;i++)
	{
		for(j=0;j<recon_width;j+=2)
		{
			for(l=0;l<32;l++)
			{
				//first mb
				m=i*32 + l;
				n= j*8;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					memcpy(line,ptr,16);
					for(k=0;k<8;k++)
					{
						*(tarCb + offset + k) = 0xaa;//line[2*k];
						*(tarCr + offset + k) = 0x55; //line[2*k+1];
					}
					ptr += 16;
				}
				else
					ptr += 16;

				//second mb
				n= j*8+8;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					memcpy(line,ptr,16);
					for(k=0;k<8;k++)
					{
						*(tarCb + offset + k) = 0xaa;//line[2*k];
						*(tarCr + offset + k) = 0x55;//line[2*k+1];
					}
					ptr += 16;
				}
				else
					ptr += 16;
			}
		}
	}
}

static void map32x32_to_yuv_C_422(int mode,unsigned char* srcC,unsigned char* tarCb,unsigned char* tarCr,unsigned int coded_width,unsigned int coded_height) {
	;
}

#else


static void map32x32_to_yuv_Y(unsigned char* srcY,
		                      unsigned char* tarY,
		                      unsigned int   coded_width,
		                      unsigned int   coded_height)
{
	unsigned int i,j,l,m,n;
	unsigned int mb_width,mb_height,twomb_line,recon_width;
	unsigned long offset;
	unsigned char *ptr;
	unsigned char *dst_asm,*src_asm;

	ptr = srcY;
	mb_width = (coded_width+15)>>4;
	mb_height = (coded_height+15)>>4;
	twomb_line = (mb_height+1)>>1;
	recon_width = (mb_width+1)&0xfffffffe;

	for(i=0;i<twomb_line;i++)
	{
		for(j=0;j<mb_width/2;j++)
		{
			for(l=0;l<32;l++)
			{
				//first mb
				m=i*32 + l;
				n= j*32;
				offset = m*coded_width + n;
				//memcpy(tarY+offset,ptr,32);
				dst_asm = tarY+offset;
				src_asm = ptr;
				asm volatile (
				        "vld1.8         {d0 - d3}, [%[src_asm]]              \n\t"
				        "vst1.8         {d0 - d3}, [%[dst_asm]]              \n\t"
				        : [dst_asm] "+r" (dst_asm), [src_asm] "+r" (src_asm)
				        :  //[srcY] "r" (srcY)
				        : "cc", "memory", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d16", "d17", "d18", "d19", "d20", "d21", "d22", "d23", "d24", "d28", "d29", "d30", "d31"
				        );

				ptr += 32;
			}
		}

		//LOGV("mb_width:%d",mb_width);
		if(mb_width & 1)
		{
			j = mb_width-1;
			for(l=0;l<32;l++)
			{
				//first mb
				m=i*32 + l;
				n= j*16;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					//memcpy(tarY+offset,ptr,16);
					dst_asm = tarY+offset;
					src_asm = ptr;
					asm volatile (
					        "vld1.8         {d0 - d1}, [%[src_asm]]              \n\t"
					        "vst1.8         {d0 - d1}, [%[dst_asm]]              \n\t"
					        : [dst_asm] "+r" (dst_asm), [src_asm] "+r" (src_asm)
					        :  //[srcY] "r" (srcY)
					        : "cc", "memory", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d16", "d17", "d18", "d19", "d20", "d21", "d22", "d23", "d24", "d28", "d29", "d30", "d31"
					        );
				}

				ptr += 16;
				ptr += 16;
			}
		}
	}
}

static void map32x32_to_yuv_C(int mode,
		                      unsigned char* srcC,
		                      unsigned char* tarCb,
		                      unsigned char* tarCr,
		                      unsigned int coded_width,
		                      unsigned int coded_height)
{
	unsigned int i,j,l,m,n,k;
	unsigned int mb_width,mb_height,fourmb_line,recon_width;
	unsigned long offset;
	unsigned char *ptr;
	unsigned char *dst0_asm,*dst1_asm,*src_asm;
	unsigned char line[16];
	int dst_stride = mode==0 ? (coded_width + 15) & (~15) : coded_width;

	ptr = srcC;
	mb_width = (coded_width+7)>>3;
	mb_height = (coded_height+7)>>3;
	fourmb_line = (mb_height+3)>>2;
	recon_width = (mb_width+1)&0xfffffffe;

	for(i=0;i<fourmb_line;i++)
	{
		for(j=0;j<mb_width/2;j++)
		{
			for(l=0;l<32;l++)
			{
				//first mb
				m=i*32 + l;
				n= j*16;
				if(m<coded_height && n<coded_width)
				{
					offset = m*dst_stride + n;

					dst0_asm = tarCb + offset;
					dst1_asm = tarCr+offset;
					src_asm = ptr;
//					for(k=0;k<16;k++)
//					{
//						dst0_asm[k] = src_asm[2*k];
//						dst1_asm[k] = src_asm[2*k+1];
//					}
					asm volatile (
					        "vld1.8         {d0 - d3}, [%[src_asm]]              \n\t"
							"vuzp.8         d0, d1              \n\t"
							"vuzp.8         d2, d3              \n\t"
							"vst1.8         {d0}, [%[dst0_asm]]!              \n\t"
							"vst1.8         {d2}, [%[dst0_asm]]!              \n\t"
							"vst1.8         {d1}, [%[dst1_asm]]!              \n\t"
							"vst1.8         {d3}, [%[dst1_asm]]!              \n\t"
					         : [dst0_asm] "+r" (dst0_asm), [dst1_asm] "+r" (dst1_asm), [src_asm] "+r" (src_asm)
					         :  //[srcY] "r" (srcY)
					         : "cc", "memory", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d16", "d17", "d18", "d19", "d20", "d21", "d22", "d23", "d24", "d28", "d29", "d30", "d31"
					         );
				}

				ptr += 32;
			}
		}

		if(mb_width & 1)
		{
			j= mb_width-1;
			for(l=0;l<32;l++)
			{
				m=i*32 + l;
				n= j*8;

				if(m<coded_height && n<coded_width)
				{
					offset = m*dst_stride + n;
					memcpy(line,ptr,16);
					for(k=0;k<8;k++)
					{
						*(tarCb + offset + k) = line[2*k];
						*(tarCr + offset + k) = line[2*k+1];
					}
				}

				ptr += 16;
				ptr += 16;
			}
		}
	}
}

static void map32x32_to_yuv_C_422(int mode,
		                          unsigned char* srcC,
		                          unsigned char* tarCb,
		                          unsigned char* tarCr,
		                          unsigned int coded_width,
		                          unsigned int coded_height)
{
	unsigned int i,j,l,m,n,k;
	unsigned int mb_width,mb_height,twomb_line,recon_width;
	unsigned long offset;
	unsigned char *ptr;
	unsigned char *dst0_asm,*dst1_asm,*src_asm;
	unsigned char line[16];

	ptr = srcC;
	mb_width = (coded_width+7)>>3;
	mb_height = (coded_height+7)>>3;
	twomb_line = (mb_height+1)>>1;
	recon_width = (mb_width+1)&0xfffffffe;

	for(i=0;i<twomb_line;i++)
	{
		for(j=0;j<mb_width/2;j++)
		{
			for(l=0;l<16;l++)
			{
				//first mb
				m=i*16 + l;
				n= j*16;
				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;

					dst0_asm = tarCb + offset;
					dst1_asm = tarCr+offset;
					src_asm = ptr;
//					for(k=0;k<16;k++)
//					{
//						dst0_asm[k] = src_asm[2*k];
//						dst1_asm[k] = src_asm[2*k+1];
//					}
					asm volatile (
					        "vld1.8         {d0 - d3}, [%[src_asm]]              \n\t"
							"vuzp.8         d0, d1              \n\t"
							"vuzp.8         d2, d3              \n\t"
							"vst1.8         {d0}, [%[dst0_asm]]!              \n\t"
							"vst1.8         {d2}, [%[dst0_asm]]!              \n\t"
							"vst1.8         {d1}, [%[dst1_asm]]!              \n\t"
							"vst1.8         {d3}, [%[dst1_asm]]!              \n\t"
					         : [dst0_asm] "+r" (dst0_asm), [dst1_asm] "+r" (dst1_asm), [src_asm] "+r" (src_asm)
					         :  //[srcY] "r" (srcY)
					         : "cc", "memory", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d16", "d17", "d18", "d19", "d20", "d21", "d22", "d23", "d24", "d28", "d29", "d30", "d31"
					         );
				}

				ptr += 32;
				ptr += 32;
			}
		}

		if(mb_width & 1)
		{
			j= mb_width-1;
			for(l=0;l<16;l++)
			{
				m=i*32 + l;
				n= j*8;

				if(m<coded_height && n<coded_width)
				{
					offset = m*coded_width + n;
					memcpy(line,ptr,16);
					for(k=0;k<8;k++)
					{
						*(tarCb + offset + k) = line[2*k];
						*(tarCr + offset + k) = line[2*k+1];
					}
				}

				ptr += 32;
				ptr += 32;
			}
		}
	}
}
#endif


static void SoftwarePictureScaler(ScalerParameter *cdx_scaler_para)
{
	map32x32_to_yuv_Y(cdx_scaler_para->addr_y_in,
			          (unsigned char*)cdx_scaler_para->addr_y_out,
			          cdx_scaler_para->width_out,
			          cdx_scaler_para->height_out);

	if (cdx_scaler_para->format_in == CONVERT_COLOR_FORMAT_YUV422MB)
		map32x32_to_yuv_C_422(cdx_scaler_para->mode,
				              cdx_scaler_para->addr_c_in,
				              (unsigned char*)cdx_scaler_para->addr_u_out,
				              (unsigned char*)cdx_scaler_para->addr_v_out,
				              cdx_scaler_para->width_out / 2,
				              cdx_scaler_para->height_out / 2);
	else
		map32x32_to_yuv_C(cdx_scaler_para->mode,
				          cdx_scaler_para->addr_c_in,
				          (unsigned char*)cdx_scaler_para->addr_u_out,
				          (unsigned char*)cdx_scaler_para->addr_v_out,
				          cdx_scaler_para->width_out / 2,
				          cdx_scaler_para->height_out / 2);

	return;
}

void TransformToYUVPlaner(cedarv_picture_t* pict, void* ybuf)
{
	ScalerParameter cdx_scaler_para;
	int             display_height_align;
	int             display_width_align;
	int             dst_c_stride;
	int             dst_y_size;
	int             dst_c_size;
	int             alloc_size;

	if(pict == NULL)
		return;

	pict->display_height = (pict->display_height + 7) & (~7);
	display_height_align = (pict->display_height + 1) & (~1);
	display_width_align  = (pict->display_width + 15) & (~15);
	dst_y_size           = display_width_align * display_height_align;
	dst_c_stride         = (pict->display_width/2 + 15) & (~15);
	dst_c_size           = dst_c_stride * (display_height_align/2);
	alloc_size           = dst_y_size + dst_c_size * 2;

	cdx_scaler_para.mode       = 0;
	cdx_scaler_para.format_in  = (pict->pixel_format == CEDARV_PIXEL_FORMAT_AW_YUV422) ? CONVERT_COLOR_FORMAT_YUV422MB : CONVERT_COLOR_FORMAT_YUV420MB;
	cdx_scaler_para.format_out = CONVERT_COLOR_FORMAT_YUV420PLANNER;
	cdx_scaler_para.width_in   = pict->width;
	cdx_scaler_para.height_in  = pict->height;
	cdx_scaler_para.addr_y_in  = (void*)pict->y;
	cdx_scaler_para.addr_c_in  = (void*)pict->u;
#if 0
	cedarx_cache_op(cdx_scaler_para.addr_y_in, cdx_scaler_para.addr_y_in+pict->size_y, CEDARX_DCACHE_FLUSH);
	cedarx_cache_op(cdx_scaler_para.addr_c_in, cdx_scaler_para.addr_c_in+pict->size_u, CEDARX_DCACHE_FLUSH);
#endif
	cdx_scaler_para.width_out  = display_width_align;
	cdx_scaler_para.height_out = display_height_align;

	cdx_scaler_para.addr_y_out = (unsigned int)ybuf;
	cdx_scaler_para.addr_v_out = cdx_scaler_para.addr_y_out + dst_y_size;
	cdx_scaler_para.addr_u_out = cdx_scaler_para.addr_v_out + dst_c_size;

    //* use neon accelarator instruction to transform the pixel format, slow if buffer is not cached(DMA mode).
	SoftwarePictureScaler(&cdx_scaler_para);

	return;
}

void TransformToVirtualBuffer(cedarv_picture_t* pict, void* ybuf)
{
	int i;
	int widthAlign;
	int heightAlign;
	int cHeight;
	int cWidth;
	int dstCStride;
    int extraHeight;    //the interval between heightalign and display_height
	unsigned char* dstPtr;
	unsigned char* srcPtr;
	dstPtr = (unsigned char*)ybuf;
    srcPtr = pict->y;
    widthAlign = (pict->display_width+15)&~15;  //hw_decoder is 16pixel align
	heightAlign = (pict->display_height+15)&~15;
    dstCStride = (pict->display_width + 15)&~15;   //16pixel align

	// y
	for(i=0; i<(int)pict->display_height; i++)
	{
		memcpy(dstPtr, srcPtr, dstCStride);
		dstPtr += dstCStride;
		srcPtr += widthAlign;
	}
    //skip hw decoder's extra line of y
    extraHeight = heightAlign - pict->display_height;
    if(extraHeight > 0)
    {
//      ALOGD("extraHeight[%d],heightAlign[%d],display_height[%d], need skip hwdecoderBuffer extra lines",
//          extraHeight, heightAlign, pict->display_height);
        for(i=0; i<extraHeight; i++)
        {
            srcPtr += widthAlign;
        }
    }
    
	//cWidth = (pict->display_width/2 + 15) & ~15;    //equal to dstCStride/2. hw_decoder's uv is 16pixel align
	cWidth = ((pict->display_width + 15) & ~15)/2;    //now a31_hw_decoder's uv is 8pixel align
	cHeight = heightAlign;
	int dst_size_u_v = dstCStride / 2 * pict->display_height / 2;
	dstPtr += dst_size_u_v;
	//v
	for(i=0; i<((int)pict->display_height)/2; i++)
	{
        memcpy(dstPtr, srcPtr, dstCStride/2);
        dstPtr += dstCStride/2;    //a31 gpu, uv is half of y
        srcPtr += cWidth;
	}
    extraHeight = heightAlign/2 - (pict->display_height+1)/2;
    if(extraHeight > 0)
    {
//      ALOGD("uv extraHeight[%d],heightAlign[%d],display_height[%d], need skip hwdecoderBuffer uv extra lines",
//          extraHeight, heightAlign, pict->display_height);
        for(i=0;i<extraHeight;i++)
        {
            srcPtr += cWidth;
        }
    }
    //u
    dstPtr -= dst_size_u_v * 2;
    for(i=0; i<((int)pict->display_height)/2; i++)
	{
        memcpy(dstPtr, srcPtr, dstCStride/2);
        dstPtr += dstCStride/2;    //a31 gpu, uv is half of y
        srcPtr += cWidth;
	}
        
    return;
}

void TransformToGPUBuffer(cedarv_picture_t* pict, void* ybuf)
{
	ScalerParameter cdx_scaler_para;
	int             display_height_align;
	int             display_width_align;
	int             dst_c_stride;
	int             dst_y_size;
	int             dst_c_size;
	int             alloc_size;

//    ALOGD("omx transformtyv12, pict->display_width[%d], pict->display_height[%d], pict:size_y[%d],size_u[%d]",
//        pict->display_width, pict->display_height, pict->size_y, pict->size_u);
    //memcpy(ybuf, pict->y, pict->size_y + pict->size_u); 

    {
    	int i;
    	int widthAlign;
    	int heightAlign;
    	int cHeight;
    	int cWidth;
    	int dstCStride;
        int GPUFBStride;
        int extraHeight;    //the interval between heightalign and display_height
    	unsigned char* dstPtr;
    	unsigned char* srcPtr;
    	dstPtr = (unsigned char*)ybuf;
    	//srcPtr = (unsigned char*)cedarv_address_phy2vir((void*)pOverlayParam->addr[0]);
        srcPtr = pict->y;
    	//widthAlign = (mWidth + 15) & ~15;
    	//heightAlign = (mHeight + 15) & ~15;
        widthAlign = (pict->display_width+15)&~15;  //hw_decoder is 16pixel align
    	heightAlign = (pict->display_height+15)&~15;
        GPUFBStride = (pict->display_width + 15)&~15;   //gpu is 32pixel align
    	for(i=0; i<(int)pict->display_height; i++)
    	{
    		memcpy(dstPtr, srcPtr, widthAlign);
    		dstPtr += GPUFBStride;
    		srcPtr += widthAlign;
    	}
        //skip hw decoder's extra line of y
        extraHeight = heightAlign - pict->display_height;
        if(extraHeight > 0)
        {
//            ALOGD("extraHeight[%d],heightAlign[%d],display_height[%d], need skip hwdecoderBuffer extra lines",
//                extraHeight, heightAlign, pict->display_height);
            for(i=0; i<extraHeight; i++)
            {
                srcPtr += widthAlign;
            }
        }
        
    	//cWidth = (mWidth/2 + 15) & ~15;
    	//cWidth = (pict->display_width/2 + 15) & ~15;    //equal to GPUFBStride/2. hw_decoder's uv is 16pixel align
    	cWidth = ((pict->display_width + 15) & ~15)/2;    //not equal to GPUFBStride/2. hw_decoder's uv is 8pixel align
    	cHeight = heightAlign;
    	//v
    	for(i=0; i<((int)pict->display_height+1)/2; i++)
    	{
            memcpy(dstPtr, srcPtr, cWidth);
            dstPtr += GPUFBStride/2;    //a31 gpu, uv is half of y
            srcPtr += cWidth;
    	}
        extraHeight = heightAlign/2 - (pict->display_height+1)/2;
        if(extraHeight > 0)
        {
//            ALOGD("uv extraHeight[%d],heightAlign[%d],display_height[%d], need skip hwdecoderBuffer uv extra lines",
//                extraHeight, heightAlign, pict->display_height);
            for(i=0;i<extraHeight;i++)
            {
                srcPtr += cWidth;
            }
        }
        //u
        for(i=0; i<((int)pict->display_height+1)/2; i++)
    	{
            memcpy(dstPtr, srcPtr, cWidth);
            dstPtr += GPUFBStride/2;    //a31 gpu, uv is half of y
            srcPtr += cWidth;
    	}
        //extraHeight = heightAlign/2 - (pict->display_height+1)/2;
        //if(extraHeight > 0)
        //{
        //    ALOGD("uv extraHeight[%d],heightAlign[%d],display_height[%d], need skip hwdecoderBuffer uv extra lines",
        //        extraHeight, heightAlign, pict->display_height);
        //    for(i=0;i<extraHeight;i++)
        //    {
        //        srcPtr += cWidth;
        //    }
        //}
    }
    return;
}

#if 0
void TransformToGPUBuffer0(cedarv_picture_t* pict, void* ybuf)
{
	ScalerParameter cdx_scaler_para;
	int             display_height_align;
	int             display_width_align;
	int             dst_c_stride;
	int             dst_y_size;
	int             dst_c_size;
	int             alloc_size;

//    ALOGD("omx transformtyv12, pict->display_width[%d], pict->display_height[%d], pict:size_y[%d],size_u[%d]",
//        pict->display_width, pict->display_height, pict->size_y, pict->size_u);
    //memcpy(ybuf, pict->y, pict->size_y + pict->size_u); 

    {
    	int i;
    	int widthAlign;
    	int heightAlign;
    	int cHeight;
    	int cWidth;
    	int dstCStride;
        int GPUFBStride;
    	unsigned char* dstPtr;
    	unsigned char* srcPtr;
    	dstPtr = (unsigned char*)ybuf;
    	//srcPtr = (unsigned char*)cedarv_address_phy2vir((void*)pOverlayParam->addr[0]);
        srcPtr = pict->y;
    	//widthAlign = (mWidth + 15) & ~15;
    	//heightAlign = (mHeight + 15) & ~15;
        widthAlign = (pict->display_width+15)&~15;  //hw_decoder is 16pixel align
    	heightAlign = (pict->display_height+15)&~15;
        GPUFBStride = (pict->display_width + 31)&~31;   //gpu is 32pixel align
    	for(i=0; i<heightAlign; i++)
    	{
    		memcpy(dstPtr, srcPtr, widthAlign);
    		dstPtr += GPUFBStride;
    		srcPtr += widthAlign;
    	}
    	//cWidth = (mWidth/2 + 15) & ~15;
    	cWidth = (pict->display_width/2 + 15) & ~15;    //equal to GPUFBStride/2. hw_decoder's uv is 16pixel align
    	cHeight = heightAlign;
    	for(i=0; i<cHeight; i++)
    	{
    		memcpy(dstPtr, srcPtr, cWidth);
    		dstPtr += cWidth;
    		srcPtr += cWidth;
    	}
    }
    return;
}
#endif

