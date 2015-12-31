
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <cutils/android_reboot.h>

extern int write_misc(char *reason);

int main(int argc, char** argv) {

	if ( write_misc("efex")){		
		printf("go_efex err!\n");
		return -1;
	}
	sync();
	sleep(10);
	printf("go_efex ok!\n");
	android_reboot(ANDROID_RB_RESTART,0,0);
	return 0;
}
