# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

# cpu stuff
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_CPU_SMP := true
TARGET_ARCH := arm
TARGET_CPU_VARIANT := cortex-a7
TARGET_ARCH_VARIANT := armv7-a-neon
ARCH_ARM_HAVE_TLS_REGISTER := true

TARGET_BOARD_PLATFORM := fiber
TARGET_BOOTLOADER_BOARD_NAME := exdroid
TARGET_GPU_USE_POWERVR := true
USE_OPENGL_RENDERER := true
USE_HW_COMPOSER := true

# use our own init.rc
TARGET_PROVIDES_INIT_RC :=true

# no hardware camera
USE_CAMERA_STUB := true
# Set /system/bin/sh to ash, not mksh, to make sure we can switch back.
# TARGET_SHELL := ash

# audio & camera & cedarx
CEDARX_CHIP_VERSION := F33
CEDARX_USE_SWAUDIO := Y

#widevine
BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 3

# image related
TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := true
TARGET_NO_KERNEL := false

INSTALLED_KERNEL_TARGET := kernel
BOARD_KERNEL_BASE := 0x40000000
BOARD_KERNEL_CMDLINE := console=ttyS0,115200 rw init=/init loglevel=7
BOARD_KERNEL_CMDLINE += selinux=0
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_FLASH_BLOCK_SIZE := 4096
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 805306368
#TARGET_USERIMAGES_SPARSE_EXT_DISABLED := true
#BOARD_USERDATAIMAGE_PARTITION_SIZE := 1073741824
TARGET_RUNNING_WITHOUT_SYNC_FRAMEWORK := true

# recovery IR controll
SW_BOARD_IR_RECOVERY := true

# hardware module include file path
TARGET_HARDWARE_INCLUDE := $(TOP)/device/softwinner/fiber-common/hardware/include

BOARD_SEPOLICY_DIRS := \
        device/softwinner/fiber-common/sepolicy

BOARD_SEPOLICY_UNION := \
        genfs_contexts \
        file_contexts \
        file.te \
        vold.te \
        system.te \
        healthd.te \
        netd.te \
        app.te \
        untrusted_app.te
