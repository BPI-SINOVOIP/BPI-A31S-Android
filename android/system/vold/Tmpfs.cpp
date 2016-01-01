#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/wait.h>

#include <linux/kdev_t.h>

#define LOG_TAG "Vold"

#include <cutils/log.h>
#include <cutils/properties.h>

#include <logwrap/logwrap.h>

#include "Tmpfs.h"
#include "VoldUtil.h"

extern "C" int mount(const char *, const char *, const char *, unsigned long, const void *);

int Tmpfs::doMount(const char *mountPoint,
                 int ownerUid, int ownerGid, int permMask, bool createLost) {
    int rc;
    unsigned long flags;
    char mountData[255];

    flags = MS_NODEV | MS_NOSUID;

    sprintf(mountData,
            "uid=%d,gid=%d",
            ownerUid, ownerGid, permMask, permMask);

    rc = mount("tmpfs", mountPoint, "tmpfs", flags, mountData);

    if (rc && errno == EROFS) {
        flags |= MS_RDONLY;
        rc = mount("tmpfs", mountPoint, "tmpfs", flags, mountData);
    }

    return rc;
}
