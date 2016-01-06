#!/bin/bash
# (c) 2015, Leo Xu <otakunekop@banana-pi.org.cn>
# Build script for BPI-M3-BSP 2015.07.29

TARGET_DEVICE=
VARIANT=

echo "=========================================="
echo "       BPI A31S Android Build              "
echo "=========================================="
echo
#echo "This tool support following BPI board(s):"
echo "------------------------------------------"
echo "	1. BPI_M2_HDMI"
echo "	2. BPI_M2_LCD"
echo "------------------------------------------"

read -p "Please choose a target(1-6): " board
echo

if [ -z "$board" ]; then
	echo -e "\033[31m No target choose, using BPI_M2_HDMI default   \033[0m"
	board=1
fi

case $board in
	1) TARGET_DEVICE="bpi_m2_hdmi";;
	2) TARGET_DEVICE="bpi_m2_lcd";;
esac

#echo "This tool support following building variant"
echo "--------------------------------------------------------------------------------"
echo "	1. userdebug"
echo "	2. eng"
echo "	3. user"
echo "--------------------------------------------------------------------------------"

read -p "Please choose a variant(1-3): " mode
echo

if [ -z "$mode" ]; then
        echo -e "\033[31m No build variant choose, using userdebug default   \033[0m"
        mode=1
fi

case $mode in
	1) VARIANT="userdebug";;
	2) VARIANT="eng";;
	3) VARIANT="user";;
esac

echo -e "\033[31m TARGET_DEVICE=$TARGET_DEVICE VARIANT=$VARIANT \033[0m"
echo

cd ./lichee
./build.sh -p sun6i_fiber_a31s
cd ../android
source build/envsetup.sh
lunch ${TARGET_DEVICE}-${VARIANT}
extract-bsp
make -j8
pack
cd ../lichee/tools/pack
ls -l


echo -e "\033[31m Build success!\033[0m"
echo
