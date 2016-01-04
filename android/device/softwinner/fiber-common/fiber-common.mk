$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

DEVICE_PACKAGE_OVERLAYS := \
    	device/softwinner/fiber-common/overlay

include device/softwinner/fiber-common/prebuild/tools/tools.mk

# ext4 filesystem utils
PRODUCT_PACKAGES += \
	e2fsck \
	libext2fs \
	libext2_blkid \
	libext2_uuid \
	libext2_profile \
	libext2_com_err \
	libext2_e2p \
	make_ext4fs

PRODUCT_PACKAGES += \
	audio.primary.fiber \
	audio.a2dp.default \
	audio.usb.default \
	audio.r_submix.default

PRODUCT_PACKAGES += \
	libcedarxbase \
	libcedarxosal \
	libcedarv \
	libcedarv_base \
	libcedarv_adapter \
	libve \
	libaw_audio \
	libaw_audioa \
	libswdrm \
	libstagefright_soft_cedar_h264dec \
	libfacedetection \
	libthirdpartstream \
	libcedarxsftstream \
	libsunxi_alloc \
	libsrec_jni \
	libjpgenc \
	libstagefrighthw \
	libOmxCore \
	libOmxVdec \
	libOmxVenc \
	libaw_h264enc \
	libI420colorconvert \
	libcnr

PRODUCT_PACKAGES += \
	libjni_hmm_shared_engine \
	libjni_delight \
	libgpio_jni \
	libgpioservice \
	libjni_swos \
	libgnustl_shared
#libjni_googlepinyinime_latinime_5 \
#libjni_googlepinyinime_5 \

PRODUCT_COPY_FILES += \
	device/softwinner/fiber-common/media_codecs.xml:system/etc/media_codecs.xml \
	device/softwinner/fiber-common/hardware/audio/audio_policy.conf:system/etc/audio_policy.conf \
	device/softwinner/fiber-common/hardware/audio/phone_volume.conf:system/etc/phone_volume.conf

#exdroid HAL
PRODUCT_PACKAGES += \
   	camera.fiber \
   	lights.fiber \
   	sensors.fiber \
   	keystore.fiber

#common System APK
PRODUCT_PACKAGES += \
   	Camera2 \
   	TvdFileManager \
   	TvdSettings \
   	TvdVideo \
   	SettingsAssist \
   	MiracastReceiver \
   	AllCast.apk \
   	com.google.android.apps.inputmethod.zhuyin-1.apk \
   	com.google.android.inputmethod.pinyin_403232.apk \
   	kodi-14.2-Helix-armeabi-v7a.apk
   	#TvdLauncher \

#install apk's lib to system/lib
PRODUCT_PACKAGES +=  \
   	libjni_mosaic.so \
   	libjni_WFDisplay.so \
	libwfdrtsp.so \
	libwfdplayer.so \
	libwfdmanager.so \
	libwfdutils.so \
	libwfduibc.so \
	#libjni_eglfence_awgallery.so \


PRODUCT_COPY_FILES += \
	device/softwinner/fiber-common/hardware/audio/audio_policy.conf:system/etc/audio_policy.conf \
	device/softwinner/fiber-common/hardware/audio/phone_volume.conf:system/etc/phone_volume.conf

#sensor
PRODUCT_COPY_FILES += \
       	device/softwinner/fiber-common/sensors.sh:system/bin/sensors.sh

PRODUCT_COPY_FILES += \
	device/softwinner/fiber-common/media_codecs.xml:system/etc/media_codecs.xml \

# wfd no invite
PRODUCT_COPY_FILES += \
    	device/softwinner/fiber-common/wfd_blacklist.conf:system/etc/wfd_blacklist.conf

# init.rc
PRODUCT_COPY_FILES += \
	device/softwinner/fiber-common/init.rc:root/init.rc

# table core hardware
PRODUCT_COPY_FILES += \
    	device/softwinner/fiber-common/tablet_core_hardware.xml:system/etc/permissions/tablet_core_hardware.xml

PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.strictmode.visual=0 \
	persist.sys.strictmode.disable=1

PRODUCT_PROPERTY_OVERRIDES += \
	ro.opengles.version=131072

PRODUCT_PROPERTY_OVERRIDES += \
	ro.kernel.android.checkjni=0

#factory tools	
PRODUCT_PROPERTY_OVERRIDES += \
	ro.sw.testapkpackage=com.softwinner.dragonbox \
	ro.sw.testapkclass=com.softwinner.dragonbox.Main \
	ro.sw.testapkconfigclass=com.softwinner.dragonbox.Configuration \
	ro.sw.agingtestpackage=com.softwinner.agingdragonbox \
	ro.sw.agingtestclass=com.softwinner.agingdragonbox.Main \
	ro.sw.snwritepackage=com.allwinnertech.dragonsn \
	ro.sw.snwriteclass=com.allwinnertech.dragonsn.DragonSNActivity
	
	
PRODUCT_TAGS += dalvik.gc.type-precise

PRODUCT_PROPERTY_OVERRIDES += \
	ro.reversion.aw_sdk_tag=homlet-mars44-v4.4.2 \
	ro.sys.cputype=QuadCore-A31Series

PRODUCT_PROPERTY_OVERRIDES += \
	ro.crypto.sw2hwkeymaster=true \
	ro.build.selinux=true \
	wifi.interface=wlan0 \
	wifi.supplicant_scan_interval=15 \
	keyguard.no_require_sim=true

PRODUCT_PROPERTY_OVERRIDES += \
	persist.demo.hdmirotationlock=0

BUILD_NUMBER := $(shell date +%Y%m%d)

DISPLAY_BUILD_NUMBER := true

#PRODUCT_PACKAGES += \
#    PartnerBookmarksProvider

# for drm
PRODUCT_PROPERTY_OVERRIDES += \
    	drm.service.enabled=true

PRODUCT_PACKAGES += \
    	com.google.widevine.software.drm.xml \
    	com.google.widevine.software.drm \
    	libdrmwvmplugin \
    	libwvdrmengine \
    	libwvm \
    	libWVStreamControlAPI_L3 \
    	libwvdrm_L3 \
    	libdrmdecrypt

#Homlet additional api
#isomount && securefile && gpioservice
PRODUCT_PACKAGES += \
    	isomountmanagerservice \
    	libisomountmanager_jni \
    	libisomountmanagerservice \
    	systemmixservice \
	gpioservice \
    	libsystemmix_jni \
    	libsystemmixservice \
    	libsecurefile_jni \
    	libsecurefileservice \
    	securefileserver


# pppoe
PRODUCT_PACKAGES += \
	PPPoEService \
	pppoe

PRODUCT_COPY_FILES += \
	external/ppp/pppoe/script/ip-up-pppoe:system/etc/ppp/ip-up-pppoe \
	external/ppp/pppoe/script/ip-down-pppoe:system/etc/ppp/ip-down-pppoe \
	external/ppp/pppoe/script/pppoe-connect:system/bin/pppoe-connect \
	external/ppp/pppoe/script/pppoe-disconnect:system/bin/pppoe-disconnect
