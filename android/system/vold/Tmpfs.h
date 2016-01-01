#ifndef _TMPFS_H
#define _TMPFS_H

#include <unistd.h>

class Tmpfs {
public:
    static int doMount(const char *mountPoint,
                 int ownerUid, int ownerGid, int permMask, bool createLost);
};

#endif
