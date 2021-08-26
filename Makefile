VSIM_PATH = rocket-chip/vsim
FINAL_V = freechips.rocketchip.system.DefaultConfig.v

all:
	@echo "This Makefile is used for development. Do not use it if you are not going to change the scala code."

verilog:
	#$(MAKE) -C $(VSIM_PATH) verilog MODEL=YsyxSoCTestHarness
	$(MAKE) -C $(VSIM_PATH) verilog
	cp $(VSIM_PATH)/generated-src/$(FINAL_V) soc/generated/ysyxSoCFull.v

dev-init:
	git submodule update --init --recursive
	cd rocket-chip && git apply ../patch/rocket-chip.patch
	ln -s -T $(abspath soc) rocket-chip/src/main/scala/ysyxSoC
	ln -s -T $(abspath chiplink) rocket-chip/src/main/scala/chiplink

.PHONY: verilog dev-init
