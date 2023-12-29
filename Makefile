FINAL_V = rocket-chip/out/emulator/freechips.rocketchip.system.TestHarness/freechips.rocketchip.system.DefaultConfig/mfccompiler/compile.dest/TestHarness.sv
YSYXSOCFULL_V = generated/ysyxSoCFull.v

all:
	@echo "This Makefile is used for development. Do not use it if you are not going to change the scala code."

$(YSYXSOCFULL_V):
	$(MAKE) -C rocket-chip verilog
	cp $(FINAL_V) $@
	sed -i -e 's/_\(aw\|ar\|w\|r\|b\)_\(\|bits_\)/_\1/g' $@
	sed -i '/firrtl_black_box_resource_files.f/, $$d' $@

verilog: $(YSYXSOCFULL_V)

dev-init:
	git submodule update --init --recursive
	cd rocket-chip && git apply ../patch/rocket-chip.patch
	cp -r $(abspath soc) rocket-chip/src/main/scala/ysyxSoC
	cp -r $(abspath chiplink) rocket-chip/src/main/scala/chiplink

.PHONY: verilog $(YSYXSOCFULL_V) dev-init
