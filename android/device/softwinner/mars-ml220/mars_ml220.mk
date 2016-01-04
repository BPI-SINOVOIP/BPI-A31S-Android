$(call inherit-product, device/softwinner/fiber-common/fiber-common.mk)
$(call inherit-product, frameworks/native/build/tablet-dalvik-heap.mk)

# init.rc, kernel
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/kernel:kernel \
        device/softwinner/mars-ml220/init.sun6i.rc:root/init.sun6i.rc \
        device/softwinner/mars-ml220/ueventd.sun6i.rc:root/ueventd.sun6i.rc \
        device/softwinner/mars-ml220/modules/modules/nand.ko:root/nand.ko \
        device/softwinner/mars-ml220/initlogo.rle:root/initlogo.rle \
        device/softwinner/mars-ml220/fstab.sun6i:root/fstab.sun6i \
        device/softwinner/mars-ml220/needfix.rle:root/needfix.rle \
        device/softwinner/mars-ml220/media/bootanimation.zip:system/media/bootanimation.zip \
        device/softwinner/mars-ml220/media/bootlogo.bmp:system/media/bootlogo.bmp \
        device/softwinner/mars-ml220/media/initlogo.bmp:system/media/initlogo.bmp \
        device/softwinner/mars-ml220/media/boot.wav:system/media/boot.wav \
        device/softwinner/mars-ml220/init.recovery.sun6i.rc:root/init.recovery.sun6i.rc \
        device/softwinner/mars-ml220/init.sun6i.usb.rc:root/init.sun6i.usb.rc \
        device/softwinner/mars-ml220/modules/modules/sun6i-ir.ko:root/sun6i-ir.ko \
        device/softwinner/mars-ml220/modules/modules/gpio-sunxi.ko:root/gpio-sunxi.ko

# wifi & bt config file
PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
        frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml
#       frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml

# touch screen
PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml

# usb accessory
PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml
PRODUCT_PACKAGES += \
        com.android.future.usb.accessory

# usb host
PRODUCT_COPY_FILES += \
        frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml

# GPU buffer size configs
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/configs/powervr.ini:system/etc/powervr.ini

#key and tp config file
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/configs/sw-keyboard.kl:system/usr/keylayout/sw-keyboard.kl \
        device/softwinner/mars-ml220/configs/tp.idc:system/usr/idc/tp.idc \
        device/softwinner/mars-ml220/configs/gsensor.cfg:system/usr/gsensor.cfg \
        device/softwinner/mars-ml220/configs/sun6i-ir.kl:system/usr/keylayout/sun6i-ir.kl

#copy touch and keyboard driver to recovery randisk
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/modules/modules/sw-keyboard.ko:obj/keyboard.ko \
        device/softwinner/mars-ml220/modules/modules/sw_device.ko:obj/sw_device.ko \
        device/softwinner/mars-ml220/modules/modules/gt82x.ko:obj/gt82x.ko

#recovery config
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/recovery.fstab:recovery.fstab

PRODUCT_PROPERTY_OVERRIDES += \
        persist.sys.timezone=Asia/Shanghai \
        persist.sys.language=zh \
        persist.sys.country=CN

PRODUCT_PROPERTY_OVERRIDES += \
        config.disable_bluetooth=true \
        config.disable_telephony=true
#        config.disable_location=true

# camera
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/configs/camera.cfg:system/etc/camera.cfg \
        device/softwinner/mars-ml220/configs/media_profiles.xml:system/etc/media_profiles.xml \
        device/softwinner/mars-ml220/configs/cfg-AWGallery.xml:system/etc/cfg-AWGallery.xml \
        frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
        frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
        frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
        device/softwinner/mars-ml220/configs/cameralist.cfg:system/etc/cameralist.cfg

# 3G Data Card Packages
PRODUCT_PACKAGES += \
        chat \
        rild \
        pppd

# 3G Data Card Configuration Flie
PRODUCT_COPY_FILES += \
        device/softwinner/fiber-common/rild/ip-down:system/etc/ppp/ip-down \
        device/softwinner/fiber-common/rild/ip-up:system/etc/ppp/ip-up \
        device/softwinner/fiber-common/rild/3g_dongle.cfg:system/etc/3g_dongle.cfg \
        device/softwinner/fiber-common/rild/usb_modeswitch:system/bin/usb_modeswitch \
        device/softwinner/fiber-common/rild/call-pppd:system/xbin/call-pppd \
        device/softwinner/fiber-common/rild/usb_modeswitch.sh:system/xbin/usb_modeswitch.sh \
        device/softwinner/fiber-common/rild/apns-conf_sdk.xml:system/etc/apns-conf.xml \
        device/softwinner/fiber-common/rild/libsoftwinner-ril.so:system/lib/libsoftwinner-ril.so

PRODUCT_COPY_FILES += \
        device/softwinner/mars-a31s512m/tools/logger.sh:system/bin/logger.sh \
        device/softwinner/mars-a31s/tools/memtester:system/bin/memtester:system/bin/memtester

# 3G Data Card usb modeswitch File
PRODUCT_COPY_FILES += \
        $(call find-copy-subdir-files,*,device/softwinner/fiber-common/rild/usb_modeswitch.d,system/etc/usb_modeswitch.d)

#egl
PRODUCT_COPY_FILES += \
        device/softwinner/mars-ml220/egl/pvrsrvctl:system/vendor/bin/pvrsrvctl \
        device/softwinner/mars-ml220/egl/libusc.so:system/vendor/lib/libusc.so \
        device/softwinner/mars-ml220/egl/libglslcompiler.so:system/vendor/lib/libglslcompiler.so \
        device/softwinner/mars-ml220/egl/libIMGegl.so:system/vendor/lib/libIMGegl.so \
        device/softwinner/mars-ml220/egl/libpvr2d.so:system/vendor/lib/libpvr2d.so \
        device/softwinner/mars-ml220/egl/libpvrANDROID_WSEGL.so:system/vendor/lib/libpvrANDROID_WSEGL.so \
        device/softwinner/mars-ml220/egl/libPVRScopeServices.so:system/vendor/lib/libPVRScopeServices.so \
        device/softwinner/mars-ml220/egl/libsrv_init.so:system/vendor/lib/libsrv_init.so \
        device/softwinner/mars-ml220/egl/libsrv_um.so:system/vendor/lib/libsrv_um.so \
        device/softwinner/mars-ml220/egl/libEGL_POWERVR_SGX544_115.so:system/vendor/lib/egl/libEGL_POWERVR_SGX544_115.so \
        device/softwinner/mars-ml220/egl/libGLESv1_CM_POWERVR_SGX544_115.so:system/vendor/lib/egl/libGLESv1_CM_POWERVR_SGX544_115.so \
        device/softwinner/mars-ml220/egl/libGLESv2_POWERVR_SGX544_115.so:system/vendor/lib/egl/libGLESv2_POWERVR_SGX544_115.so \
        device/softwinner/mars-ml220/egl/gralloc.sun6i.so:system/vendor/lib/hw/gralloc.sun6i.so \
        device/softwinner/mars-ml220/egl/hwcomposer.sun6i.so:system/vendor/lib/hw/hwcomposer.sun6i.so \
        device/softwinner/mars-ml220/egl/egl.cfg:system/lib/egl/egl.cfg \

PRODUCT_PROPERTY_OVERRIDES += \
        ro.sf.lcd_density=160 \
        ro.sf.showhdmisettings=7 \
        persist.sys.ui.hw=true \
        persist.sys.usb.config=mass_storage,adb \
        ro.product.firmware=v4.4.2 \
        ro.udisk.lable=mars \
        ro.softmouse.left.code=6 \
        ro.softmouse.right.code=14 \
        ro.softmouse.top.code=67 \
        ro.softmouse.bottom.code=10 \
        ro.softmouse.leftbtn.code=2 \
        ro.softmouse.midbtn.code=-1 \
        ro.softmouse.rightbtn.code=-1 \
        ro.sw.defaultlauncherpackage=com.softwinner.launcher \
        ro.sw.defaultlauncherclass=com.softwinner.launcher.Launcher \
        audio.output.active=AUDIO_CODEC \
        audio.input.active=AUDIO_CODEC

PRODUCT_PROPERTY_OVERRIDES += \
        ro.carrier=wifi-only \
	ro.sw.directlypoweroff=false

$(call inherit-product-if-exists, device/softwinner/mars-ml220/modules/modules.mk)
#include device/softwinner/fiber-common/prebuild/google/products/gms.mk
include device/softwinner/fiber-common/prebuild/framework_aw/framework_aw.mk

DEVICE_PACKAGE_OVERLAYS := \
        device/softwinner/mars-ml220/overlay \
        $(DEVICE_PACKAGE_OVERLAYS)

PRODUCT_CHARACTERISTICS := tablet

# Overrides
PRODUCT_BRAND  := softwinners
PRODUCT_NAME   := mars_ml220
PRODUCT_DEVICE := mars-ml220
PRODUCT_MODEL  := Softwinner



