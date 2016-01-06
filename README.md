 A31s Android 4.4.2 Source code

----------

###**Prepare**

 'Ubuntu 12.04.x TLS' recommended, following command for environment building:

`$ sudo apt-get install python-software-properties`

`$ sudo add-apt-repository ppa:webupd8team/java`

`$ sudo apt-get update`

`$ sudo apt-get install oracle-java6-installer`

`$ sudo apt-get install libglapi-mesa:i386`

`$ sudo apt-get install git gnupg flex bison gperf build-essential zip curl libc6-dev libncurses5-dev:i386 x11proto-core-dev libx11-dev:i386 libreadline6-dev:i386 libgl1-mesa-glx:i386 libgl1-mesa-dev g++-multilib mingw32 tofrodos python-markdown libxml2-utils xsltproc zlib1g-dev:i386`

`$ sudo ln -s /usr/lib/i386-linux-gnu/mesa/libGL.so.1 /usr/lib/i386-linux-
gnu/libGL.so`

`$ sudo apt-get install uboot-mkimage`

`$ sudo apt-get install xserver-xorg`


----------


###**Build**

Run build.sh to select a target build.

> $ build.sh
> 
> = ==============================
> 
> BPI A31s Android Build
> 
> = ==============================
> 
> 
> 1. BPI_M2_HDMI
> 2. BPI_M2_LCD
> 
> Please choose a target(1-6): 1
> 
> 1. userdebug
> 2. eng
> 3. user
>
>Please choose a variant(1-3): 2
> 
> TARGET_DEVICE=bpi_m2_hdmi VARIANT=eng







