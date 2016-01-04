
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PREBUILT_LIBS := libcnr.so

LOCAL_MODULE_TAGS := optional
include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw

LOCAL_LDLIBS := -llog

LOCAL_SHARED_LIBRARIES:= \
    libbinder \
    libutils \
    libcutils \
    libcamera_client \
    libui \
	
# cedarx libraries
LOCAL_SHARED_LIBRARIES += \
	libfacedetection \
	libsunxi_alloc \
	libjpgenc \
	libcedarv_adapter \
	libcedarv_base \
	libcedarxosal \
	libutils \
	libcutils \
	libdl \
	libcedarv \
	libcnr \
	libve \
	libcedarxbase
	
LOCAL_C_INCLUDES += 								\
	frameworks/base/core/jni/android/graphics 		\
	frameworks/native/include/media/openmax			\
	hardware/libhardware/include/hardware			\
	frameworks/native/include						\
	frameworks/av/media/CedarX-Projects/CedarX/include	\
	frameworks/av/media/CedarX-Projects/CedarX/include/include_system	\
	frameworks/av/media/CedarX-Projects/CedarX/include/include_camera 	\
	$(TARGET_HARDWARE_INCLUDE)

LOCAL_SRC_FILES := \
	HALCameraFactory.cpp \
	PreviewWindow.cpp \
	CallbackNotifier.cpp \
	CCameraConfig.cpp \
	BufferListManager.cpp \
	OSAL_Mutex.c \
	OSAL_Queue.c \
	Libve_Decoder.c \
	CameraList.cpp \
	scaler.c

# choose hal for new driver or old
SUPPORT_NEW_DRIVER := Y

ifeq ($(SUPPORT_NEW_DRIVER),Y)
LOCAL_CFLAGS += -DSUPPORT_NEW_DRIVER
LOCAL_SRC_FILES += \
	CameraHardware2.cpp \
	V4L2CameraDevice2.cpp
else
LOCAL_SRC_FILES += \
	CameraHardware.cpp \
	V4L2CameraDevice.cpp
endif

ifneq ($(filter nuclear%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN5I__
endif

ifneq ($(filter crane%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN4I__
endif

ifneq ($(filter fiber%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN6I__
endif

# for bpi target device
ifneq ($(filter bpi%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN6I__
endif

LOCAL_MODULE := camera.$(TARGET_BOARD_PLATFORM)

LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
