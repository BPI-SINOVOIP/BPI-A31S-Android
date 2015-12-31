
# define sources root directory before everything
SRC_ROOT := ../../..

# change compiler and linker option before you include rule.mk
#
# link to libscript.a when you need to fetch configuration
# from test_script
#
#CFLAGS := $(CFLAGS) -g
LDFLAGS := -lscript

# include rule.mk
include $(SRC_ROOT)/rule.mk

# define objects collection variable
write_devnode_objs = write_devnode.o

.PHONY: all
all: write_devnode

# define you target, the target will be output to dragonboard/output/bin
# directory
write_devnode: $(write_devnode_objs)
	$(LINK_MSG)
	$(LINKX)

# change 'gsensortester_objs' to your objects collection variable
$(write_devnode_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

