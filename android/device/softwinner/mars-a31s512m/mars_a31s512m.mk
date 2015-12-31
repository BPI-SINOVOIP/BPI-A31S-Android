$(call inherit-product, device/softwinner/fiber-common/fiber-common.mk)
$(call inherit-product, frameworks/native/build/tablet-dalvik-heap.mk)

# init.rc, kernel
PRODUCT_COPY_FILES += \
        device/softwinner/mars-a31s512m/kernel:kernel \
        device/softwinner/mars-a31s512m/init.sun6i.rc:root/init.sun6i.rc \
        device/softwinner/mars-a31s512m/ueventd.sun6i.rc:root/ueventd.sun6i.rc \
        device/softwinner/mars-a31s512m/modules/modules/nand.ko:root/nand.ko \
        device/softwinner/mars-a31s512m/initlogo.rle:root/initlogo.rle \
        device/softwinner/mars-a31s512m/fstab.sun6i:root/fstab.sun6i \
        device/softwinner/mars-a31s512m/needfix.rle:root/needfix.rle \
        device/softwinner/mars-a31s512m/media/bootanimation.zip:system/media/bootanimation.zip \
        device/softwinner/mars-a31s512m/media/bootlogo.bmp:system/media/bootlogo.bmp \
        device/softwinner/mars-a31s512m/media/initlogo.bmp:system/media/initlogo.bmp \
        device/softwinner/mars-a31s512m/media/boot.wav:system/media/boot.wav \
        device/softwinner/mars-a31s512m/init.recovery.sun6i.rc:root/init.recovery.sun6i.rc \
        device/softwinner/mars-a31s512m/init.sun6i.usb.rc:root/init.sun6i.usb.rc \
        device/softwinner/mars-a31s512m/modules/modules/sun6i-ir.ko:root/sun6i-ir.ko \
        device/softwinner/mars-a31s512m/modules/modules/gpio-sunxi.ko:root/gpio-sunxi.ko

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
        device/softwinner/mars-a31s512m/configs/powervr.ini:system/etc/powervr.ini

#key and tp config file
PRODUCT_COPY_FILES += \
        device/softwinner/mars-a31s512m/configs/sw-keyboard.kl:system/usr/keylayout/sw-keyboard.kl \
        device/softwinner/mars-a31s512m/configs/tp.idc:system/usr/idc/tp.idc \
        device/softwinner/mars-a31s512m/configs/gsensor.cfg:system/usr/gsensor.cfg \
        device/softwinner/mars-a31s512m/configs/sun6i-ir.kl:system/usr/keylayout/sun6i-ir.kl

#copy touch and keyboard driver to recovery randisk
PRODUCT_COPY_FILES += \
        device/softwinner/mars-a31s512m/modules/modules/sw-keyboard.ko:obj/keyboard.ko \
        device/softwinner/mars-a31s512m/modules/modules/sw_device.ko:obj/sw_device.ko \
        device/softwinner/mars-a31s512m/modules/modules/gt82x.ko:obj/gt82x.ko

#recovery config
PRODUCT_COPY_FILES += \
        device/softwinner/mars-a31s512m/recovery.fstab:recovery.fstab

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
        device/softwinner/mars-a31s512m/configs/camera.cfg:system/etc/camera.cfg \
        device/softwinner/mars-a31s512m/configs/media_profiles.xml:system/etc/media_profiles.xml \
        device/softwinner/mars-a31s512m/configs/cfg-AWGallery.xml:system/etc/cfg-AWGallery.xml \
        frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
        frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
        frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
        device/softwinner/mars-a31s512m/configs/cameralist.cfg:system/etc/cameralist.cfg

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

# 3G Data Card usb modeswitch File
PRODUCT_COPY_FILES += \
        $(call find-copy-subdir-files,*,device/softwinner/fiber-common/rild/usb_modeswitch.d,system/etc/usb_modeswitch.d)

# a31st logger
PRODUCT_COPY_FILES += \
       device/softwinner/mars-a31s512m/tools/logger.sh:system/bin/logger.sh \
       device/softwinner/mars-a31s512m/tools/memtester:system/bin/memtester
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
        ro.config.low_ram=true \
	ro.config.max_starting_bg=3 \
        ro.am.max_recent_tasks=4 \
        audio.output.active=AUDIO_CODEC \
        audio.input.active=AUDIO_CODEC

PRODUCT_PROPERTY_OVERRIDES += \
        ro.carrier=wifi-only \
	ro.sw.directlypoweroff=false

$(call inherit-product-if-exists, device/softwinner/mars-a31s512m/modules/modules.mk)
include device/softwinner/fiber-common/prebuild/framework_aw/framework_aw.mk

#include device/softwinner/fiber-common/prebuild/google/products/gms.mk

DEVICE_PACKAGE_OVERLAYS := \
        device/softwinner/mars-a31s512m/overlay \
        $(DEVICE_PACKAGE_OVERLAYS)
PRODUCT_CHARACTERISTICS := tablet

# Overrides
PRODUCT_BRAND  := softwinners
PRODUCT_NAME   := mars_a31s512m
PRODUCT_DEVICE := mars-a31s512m
PRODUCT_MODEL  := Softwinner



