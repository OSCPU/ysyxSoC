# modify to your ID. DO NOT add any trailing space!!!
ID = 00000000

TOP = ysyx_$(ID)
V_FILE = $(TOP).v
LINT_FLAGS = -Wall -Wno-DECLFILENAME

# lint without UNUSED
lint: $(V_FILE)
	verilator --lint-only --top-module $(TOP) $(LINT_FLAGS) -Wno-UNUSED $(V_FILE)

# lint with UNUSED
lint-unused: $(V_FILE)
	verilator --lint-only --top-module $(TOP) $(LINT_FLAGS) $(V_FILE)

.PHONY: lint lint-unused
