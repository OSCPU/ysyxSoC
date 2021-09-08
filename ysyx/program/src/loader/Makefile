SRCS = loader.c payload.S
NAME = loader

# Note that the loader should be linked to the start address of flash.
# You should modify your linking option if you want to build the loader yourself.
# Then modify BIN_PATH to a binary starting at 0x80000000
BIN_PATH = hello-riscv64-mycpu-start-at-0x80000000.bin
ASFLAGS += -DBIN_PATH=\"$(BIN_PATH)\"

include $(AM_HOME)/Makefile

payload.S: $(BIN_PATH)
	touch $@

.PHONY: payload.S
