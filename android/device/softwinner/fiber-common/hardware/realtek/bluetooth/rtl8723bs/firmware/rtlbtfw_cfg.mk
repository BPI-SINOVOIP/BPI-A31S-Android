#
# Copyright (C) 2008 The Android Open Source Project
#
DIR := device/softwinner/fiber-common/hardware/realtek/bluetooth/rtl8723bs/firmware
PRODUCT_COPY_FILES += \
    $(DIR)/rtl8723b_fw:system/etc/firmware/rtlbt/rtl8723b_fw \
    $(DIR)/rtl8723b_config:system/etc/firmware/rtlbt/rtl8723b_config
########################
