VSIM_PATH = ../../vsim
FINAL_V = freechips.rocketchip.system.DefaultConfig.final.v

all:
	$(MAKE) -C $(VSIM_PATH) verilog
	cp $(VSIM_PATH)/generated-src/$(FINAL_V) ysyxSoCFull.v

.PHONY: all
