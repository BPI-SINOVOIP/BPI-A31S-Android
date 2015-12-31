#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <ctype.h>
#include <unistd.h>
#include "dragonboard_inc.h"

#define DEV_NODE_PATH		"/dev/wmtWifi"
int main(int argc, char *argv[])
{
	int fd = -1;
	char value  = '1';
	int ret;
	fd = open(DEV_NODE_PATH, O_WRONLY);
	if (fd < 0) {
		db_error("Open \"%s\" failed", DEV_NODE_PATH);
		goto out;
	}
	ret = write(fd, &value, 1);
	if (ret < 0) {
		db_error("Set \"%s failed", DEV_NODE_PATH);
		goto out;
	}
out:
	if (fd >= 0) close(fd);
	return 0;
}

