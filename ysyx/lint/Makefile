ID ?= 00000000

TOP = ysyx_$(ID)
V_FILE = ../soc/$(TOP).v
RAM_FILE := ../ram/S011HD1P_X32Y2D128.v
RAM_FILE += ../ram/S011HD1P_X32Y2D128_BW.v
LINT_FLAGS = -Wall -Wno-DECLFILENAME

# lint without UNUSED
lint: $(V_FILE)
	verilator --lint-only --top-module $(TOP) $(LINT_FLAGS) -Wno-UNUSED $(V_FILE)

# lint with UNUSED
lint-unused: $(V_FILE)
	verilator --lint-only --top-module $(TOP) $(LINT_FLAGS) $(V_FILE)

.PHONY: lint lint-unused
