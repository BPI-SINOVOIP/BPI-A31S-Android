
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
stp_uart_launcher_objs = stp_uart_launcher.o

.PHONY: all
all: stp_uart_launcher
	cp wifitester.sh *.bin WIFI WIFI_RAM_CODE WMT.cfg $(BINDIR)/

# define you target, the target will be output to dragonboard/output/bin
# directory
stp_uart_launcher: $(stp_uart_launcher_objs)
	$(LINK_MSG)
	$(LINKX)

# change 'gsensortester_objs' to your objects collection variable
$(stp_uart_launcher_objs): %.o: %.c
	$(COMPILE_MSG)
	$(COMPILEX)

