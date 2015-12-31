
# define sources root directory before everything
SRC_ROOT := ../..

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
2glooptester_objs = mixer.o 2glooptester.o

# add your target(s) to all
.PHONY: all
all: 2glooptester

# define you target, the target will be output to dragonboard/output/bin
# directory
2glooptester: $(2glooptester_objs)
	$(LINK_MSG)
	$(LINKX)

# change 'example_objs' to your objects collection variable
$(2glooptester_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

