# Uncomment the following line if you want to modify the Chisel code.
#USE_CHISEL = 1

all:
	@echo "This Makefile is used for development. Do not use it if you are not going to change the Chisel code."

ifeq ($(USE_CHISEL),1)

FINAL_V = rocket-chip/out/emulator/freechips.rocketchip.system.TestHarness/freechips.rocketchip.system.DefaultConfig/mfccompiler/compile.dest/TestHarness.sv
YSYXSOCFULL_V = generated/ysyxSoCFull.v
ROCKET_CHIP_YSYXSOC_PATH  = rocket-chip/src/main/scala/ysyxSoC
ROCKET_CHIP_CHIPLINK_PATH = rocket-chip/src/main/scala/chiplink

$(YSYXSOCFULL_V):
	cp $(abspath soc)/* $(ROCKET_CHIP_YSYXSOC_PATH)
	cp $(abspath chiplink)/* $(ROCKET_CHIP_CHIPLINK_PATH)
	$(MAKE) -C rocket-chip verilog
	cp $(FINAL_V) $@
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $@
	sed -i '/firrtl_black_box_resource_files.f/, $$d' $@

verilog: $(YSYXSOCFULL_V)

clean:
	-$(MAKE) -C rocket-chip clean
	-rm $(YSYXSOCFULL_V)

dev-init:
	git submodule update --init --recursive
	cd rocket-chip && git apply ../patch/rocket-chip.patch
	mkdir -p $(ROCKET_CHIP_YSYXSOC_PATH) $(ROCKET_CHIP_CHIPLINK_PATH)

.PHONY: verilog $(YSYXSOCFULL_V) clean dev-init

else

%: all
	
endif
