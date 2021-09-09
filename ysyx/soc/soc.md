
## ysyxSoC集成步骤说明

1. 通过以下命令克隆本项目
   ```
   git clone --depth 1 https://github.com/OSCPU/ysyxSoC.git
   ```
1. 将`ysyxSoC/ysyx/peripheral`目录及其子目录下的所有`.v`文件加入verilator的Verilog文件列表
1. 将`ysyxSoC/ysyx/soc/ysyxSoCFull.v`文件加入verilator的Verilog文件列表
1. 将处理器Verilog文件加入verilator的Verilog文件列表
1. 将`ysyxSoC/ysyx/peripheral/uart16550/rtl`和`ysyxSoC/ysyx/peripheral/spi/rtl`两个目录加入包含路径中
   (使用verilator的`-I`选项)
1. 将`ysyxSoC/ysyx/peripheral/spiFlash/spiFlash.cpp`文件加入verilator的C++文件列表
1. 将处理器的复位PC设置为`0x3000_0000`
1. 在verilator编译选项中添加`--timescale "1ns/1ns"`
1. 在verilator初始化时对flash进行初始化, 有以下两种方式:
   * 调用`spiFlash.cpp`中的`flash_init(img)`函数, 用于将bin文件中的指令序列放置在flash中,
     其中参数`img`是bin文件的路径, 在`ysyxSoC/ysyx/program/bin/flash`和
     `ysyxSoC/ysyx/program/bin/loader`目录下提供了一些示例
   * 调用`spiFlash.cpp`中的`flash_memcpy(src, len)`函数, 用于将已经读入内存的指令序列放置在flash中,
     其中参数`src`是指令序列的地址, `len`是指令序列的长度
1. 将`ysyxSoCFull`模块(在`ysyxSoC/ysyx/soc/ysyxSoCFull.v`中定义)设置为verilator仿真的顶层
1. 将`ysyxSoC/ysyx/soc/ysyxSoCFull.v`中的`ysyx_000000`模块名修改为自己的处理器模块名
1. 通过verilator进行仿真即可
