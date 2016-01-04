#!/bin/bash
cd ./lichee
./build.sh -p sun6i_fiber_a31s
cd ../android
source build/envsetup.sh
lunch bpi_m2_lcd-userdebug
extract-bsp
make -j8
pack
cd ../lichee/tools/pack
ls -l
