LOCAL_PATH:= $(call my-dir)

##################################################
include $(CLEAR_VARS)
ifeq ($(TARGET_ARCH),arm)

LOCAL_MODULE := com.google.widevine.software.drm.xml
LOCAL_SRC_FILES := $(LOCAL_MODULE)
LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_CLASS := ETC

# This will install the file in /system/etc/permissions
#
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

#include $(BUILD_PREBUILT)

########################
# Dummy library used to indicate availability of widevine drm

include $(CLEAR_VARS)
LOCAL_MODULE := com.google.widevine.software.drm
LOCAL_MODULE_SUFFIX := .jar
LOCAL_SRC_FILES := $(LOCAL_MODULE)$(LOCAL_MODULE_SUFFIX)
LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_CLASS := JAVA_LIBRARIES

#include $(BUILD_PREBUILT)
#####################################################################
#libWVStreamControlAPI_LX.so
include $(CLEAR_VARS)

LOCAL_MODULE := libWVStreamControlAPI_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
LOCAL_SRC_FILES := $(LOCAL_MODULE)$(LOCAL_MODULE_SUFFIX)
LOCAL_PROPRIETARY_MODULE := true
#LOCAL_STRIP_MODULE := true

LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)

#####################################################################
#libwvdrm_LX.so
include $(CLEAR_VARS)
LOCAL_MODULE := libwvdrm_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
LOCAL_SRC_FILES := $(LOCAL_MODULE)$(LOCAL_MODULE_SUFFIX)
LOCAL_PROPRIETARY_MODULE := true
#LOCAL_STRIP_MODULE := true

LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)

#####################################################################
#libwvdrmengine.so
include $(CLEAR_VARS)
LOCAL_MODULE := libwvdrmengine
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_SUFFIX := .so
LOCAL_SRC_FILES := $(LOCAL_MODULE)$(LOCAL_MODULE_SUFFIX)
LOCAL_PROPRIETARY_MODULE := true
#LOCAL_STRIP_MODULE := true
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_SHARED_LIBRARIES)/mediadrm
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)

#####################################################################
# liboemcrypto.a, lib1
include $(CLEAR_VARS)
LOCAL_PREBUILT_LIBS := \
	liboemcrypto.a \
	libdrmwvmcommon.a \
	libwvmcommon.a \
	libwvocs_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL).a \
	libwvdecryptcommon.a 
	
LOCAL_MODULE_TAGS := optional
include $(BUILD_MULTI_PREBUILT)

#####################################################################
# libdrmwvmplugin.so
include $(CLEAR_VARS)
LOCAL_C_INCLUDES:= \
    $(TOP)/bionic \
    $(TOP)/bionic/libstdc++/include \
    $(TOP)/external/stlport/stlport \
    $(TOP)/device/widevine/proprietary/streamcontrol/include \
    $(TOP)/device/widevine/proprietary/drmwvmplugin/include \
    $(TOP)/frameworks/av/drm/libdrmframework/include \
    $(TOP)/frameworks/av/drm/libdrmframework/plugins/common/include \
    $(TOP)/frameworks/av/include
	
LOCAL_WHOLE_STATIC_LIBRARIES := \
	libdrmframeworkcommon \
	libdrmwvmcommon \
	libwvocs_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL)

LOCAL_SHARED_LIBRARIES := \
    libbinder \
    libutils \
    libcutils \
    libstlport \
    libz \
    libwvdrm_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
    libWVStreamControlAPI_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
    libdl

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL),1)
LOCAL_SHARED_LIBRARIES += \
		#libmsg_deliver
endif

LOCAL_MODULE := libdrmwvmplugin
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_SHARED_LIBRARIES)/drm
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_LIBRARIES += liboemcrypto
LOCAL_PRELINK_MODULE := false
include $(BUILD_SHARED_LIBRARY)

#####################################################################
# libwvm.so
include $(CLEAR_VARS)
LOCAL_C_INCLUDES:=                      \
    bionic                              \
    bionic/libstdc++                    \
    external/stlport/stlport            \
    frameworks/av/media/libstagefright/include \
    device/widevine/proprietary/streamcontrol/include \
    device/widevine/proprietary/wvm/include
	
LOCAL_WHOLE_STATIC_LIBRARIES := \
	libwvmcommon
	
LOCAL_SHARED_LIBRARIES := \
    libstlport \
    libstagefright \
    libWVStreamControlAPI_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
    libdrmframework \
    libcutils \
    liblog \
    libutils \
    libz

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL),1)
LOCAL_SHARED_LIBRARIES += \
	#	libmsg_deliver
endif

LOCAL_MODULE := libwvm
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_LIBRARIES += liboemcrypto
LOCAL_PRELINK_MODULE := false
include $(BUILD_SHARED_LIBRARY)

#####################################################################
#libdrmdecrypt.so
include $(CLEAR_VARS)

LOCAL_C_INCLUDES:=                      \
    bionic                              \
    bionic/libstdc++                    \
    external/stlport/stlport            \
    frameworks/av/media/libstagefright/include \
    device/widevine/proprietary/streamcontrol/include \
    device/widevine/proprietary/wvm/include
	
LOCAL_WHOLE_STATIC_LIBRARIES := \
	libwvdecryptcommon
	
LOCAL_SHARED_LIBRARIES := \
	libstagefright_foundation \
	liblog \
	libcutils

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL),1)
LOCAL_SHARED_LIBRARIES += \
		#libmsg_deliver
endif

LOCAL_MODULE := libdrmdecrypt
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_LIBRARIES += liboemcrypto
LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

endif
