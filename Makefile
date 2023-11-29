VSIM_PATH = rocket-chip/vsim
FINAL_V = freechips.rocketchip.system.DefaultConfig.v

all:
	@echo "This Makefile is used for development. Do not use it if you are not going to change the scala code."

verilog:
	$(MAKE) -C $(VSIM_PATH) verilog
	cp $(VSIM_PATH)/generated-src/$(FINAL_V) soc/generated/ysyxSoCFull.v

dev-init:
	git submodule update --init --recursive

.PHONY: verilog dev-init
