# SoC集成测试Checklist

> 注意：从第四期第二批开始，为了对接PA中SoC部分的教学以及考核需求。**ysyxSoC框架不再提供仿真源程序，测试程序，运行时环境**，这些都需要同学们自己实现。但是为了方便第四期同学的测试，项目组暂时提供了一个名为`ysyx4_sim`的仓库用于存放仿真源程序和测试程序，**这个仓库到第五期时就会被私有并被移除**。在阅读以下内容之前，需要先下载`ysyx4_sim`到指定目录：先进入到`ysyxSoC/ysyx/utils`目录下，然后在该目录下运行`./setup.sh`来将一生一芯四期的Verilator仿真源码目录`sim`和测绘程序`prog`拷贝到`ysyxSoC/ysyx`目录下。

**进行SoC集成测试的所有步骤都可以在当前目录下完成**，ysyx的目录结构如下所示：
```sh
ysyxSoC/ysyx
├── img                # 预期运行结果截图
├── lint               # 代码lint检查
├── perip              # 外设模块
│   ├── kdb            # 键盘外设模拟
│   ├── ps2            # ps2控制器
│   ├── spi            # spi控制器
│   │   ├── doc
│   │   └── rtl
│   ├── spiFlash       # flash外设模拟
│   ├── uart16550      # uart控制器
│   │   ├── doc
│   │   └── rtl
│   └── vga            # vga控制器
├── prog               # 测试程序
│   ├── bin            # 编译好的二进制程序
│   │   ├── flash  
│   │   └── mem
│   └── src            # 测试程序源码
│       ├── ftom
│       ├── hello
│       ├── kdb
│       ├── loader
│       ├── memtest
│       ├── muldiv
│       └── rt-thread
├── ram                # ram替换模型
├── sim                # 仿真测试
│   └── src
├── soc                # soc框架代码和代码
├── stand              # 代码规范测试
├── submit             # 代码提交仓库
└── utils              # 工具类代码
```

同学们执行SoC集成的所有测试任务都可以运行当前目录下的`main.py`完成，我们提供的`main.py`脚本包含有端口命名检查、代码规范检查和Verilator程序编译与仿真测试的全部功能，可以输入`./main.py -h`来获得其支持的功能列表：
```sh
$> ./main.py -h
usage: main.py [-h] [-s] [-l] [-lu] [-c] [-fc] [-t TEST TEST TEST TEST] [-r] [-fr] [-su] [-y] [-p]

OSCPU Season 4 SoC Test

optional arguments:
  -h, --help            show this help message and exit
  -s, --stand           run interface standard check
  -l, --lint            run code lint check
  -lu, --lint_unused    run code lint with unused check
  -c, --comp            compile core with SoC in normal flash mode
  -fc, --fst_comp       compile core with SoC in fast flash mode
  -t TEST TEST TEST TEST, --test TEST TEST TEST TEST
                        Example: ./main.py -t [flash|mem] [hello|memtest|rtthread|muldiv|kdb] [cmd|gui] [no-wave|wave]. note: some programs dont support gui mode, so need to set right mode carefully
  -r, --regress         run all test in normal flash mode
  -fr, --fst_regress    run all test in fast flash mode
  -su, --submit         submit code and spec to CICD
  -y, --ysyx            compile ysyxSoCFull framework[NOT REQUIRED]
  -p, --prog            compile all test prog[NOT REQUIRED]
```

具体来说，每个同学都需要按照顺序进行：

>命名规范检查 -> CPU内部修改 -> 代码规范检查 -> Verilator仿真 -> 提交代码

细心的同学可能会发现，`main.py`其实也是分别调用各个子目录下的Makefile或者python脚本来实现的。另外，该集成任务Checklist是按照**任务先后顺序**排列的，**所以同学们要确保前面的任务完成后再进行下一个任务**。

> 注意：推荐用`python3`而非`python2`运行`main.py`。


## 一些准备工作
为了使用`main.py`进行测试，需要：
* 将CPU代码合并到一个`.v`文件，文件名为`ysyx_8位学号.v`，如`ysyx_22040228.v`。
    * 在Linux上可通过`cat`命令实现：
    ```sh
    $> cat CPU.v ALU.v regs.v ... > ysyx_22040228.v
    ```
* 将CPU顶层命名修改为`ysyx_8位学号.v`，如`ysyx_22040228.v`。
* 按照[CPU端口命名规范](./stand/interface.md)修改CPU顶层端口名。
* 为CPU内的所有模块名添加前缀`ysyx_8位学号_`。
    * 如`module ALU`修改为`module ysyx_22040228_ALU`。
    * Chisel福利：我们提供一个[firrtl transform](./utils/AddModulePrefix.scala)来自动添加模块名前缀，使用方法参考[相关说明文档](./utils/README.md)。
* 对于使用Verilog/SystemVerilog代码实现的处理器核，目前暂时无法进行模块名前缀的自动添加，请手动进行添加。
* 为CPU内所有的`define`添加前缀`ysyx_8位学号_`，这是为了避免后端物理设计时出现多个同学的核变量定义重名情况的出现。
* **将改好的`ysyx_8位学号.v`，如`ysyx_22040228.v`放到的./soc目录下**。
* 将`main.py`中的`stud_id`**设置为8位学号**，比如学号为22040228的同学，设置`stud_id='22040228'`。
> 注意：使用Verilog/SystemVerilog开发的同学在合并代码时要删除或注释掉`include`行，要保证核的所有代码和参数定义都在且仅在`ysyx_8位学号.v`文件中。同时删除或注释掉接入difftest时可能引入的DPI-C函数。合并代码的具体操作可以自己写个脚本实现。

## 命名规范检查(北京时间 2022/10/07 23:59:59前完成)
运行脚本执行命名规范检查，该脚本会检查同学们实现的.v文件是否符合命名规范，并会生成日志文件`check.log`。可执行的测试环境为`Debian10`、`Ubuntu 20.04`、`WSL2-Ubuntu 20.04`和`Windows10`。
* 在当前目录下运行`./main.py -s`。
* 最后可以在终端看到检查结果，如果检查通过，则会在终端打印出：
    ```sh
    $> Your core is FINE in module name and signal interface
    ```
* 同时，在该`./stand`下会生成日志文件`check.log`。如果检测未通过，则会给出错误信息，并提示是`module name`错误还是`signal interface`错误。也可以打开⽣成的`check.log`日志⽂件查看报错原因和提示。
> 注意：处理器核的端口定义要严格按照[CPU端口命名规范](./stand/interface.md)来修改，**不能在端口中有多余的注释，不能在`input`和`output`后加多余的`wire`。另外接口中也不允许有其他的自定义信号**，否则可能会导致后续的测试出现问题。另外一生一芯四期SoC采用共享SRAM的方法实现Cache，所以需在CPU端口中额外添加8组SRAM接口，具体方法见下[CPU内部修改](#cpu内部修改北京时间-20221007-235959前完成)。

## CPU内部修改(北京时间 2022/10/07 23:59:59前完成)
* 所有触发器都需要带复位端，使其复位后带初值。
    * **Chisel福利：可以通过以下命令对编译生成的`.fir`文件进行扫描，找出不带复位端的寄存器：**
    ```sh
    $> grep -rn "^ *reg " -A1 myCPU.fir | sed ":a;N;s/:\n//g;ba" | sed ":a;N;s/--\n//g;ba" | grep -v "reset =>"
    ```
    其中`xxx.fir`的文件名与顶层模块名相关，通常位于`./build`目录下。若上述命令无输出，说明所有寄存器已经带上复位端。如果上述存在输出，需要按照行号到`xxx.fir`中指定行查看，由于reg的`reset =>`可能会换行，这个换行也会导致命令行输出。所以还需再检查下一行的内容中是否存在`reset =>`。
    > 注意: chisel只根据firrtl的reset可能无法很准确地判断寄存器的初始化情况，因为reset有时候只会初始化寄存器的部分位，如果初始化的寄存器中是一个bundle，则问题更明显。所以建议使用chisel实现代码的同学，还是需要**自己手动检查下是否所有的寄存器都正确地复位了**。只通过上面`grep reset`的方法可能无法检查出所有问题。

* 对于Cache来说，需要：
    * 确认ICache和DCache的data array的大小均不大于4KB，总和不大于8KB。
    * 确认ICache和DCache的data array均采用单口RAM。
    * 对data array进行端口替换：我们提供了与流片用RAM端口一致的简化行为模型，采用的是[带写掩码的单口RAM模型](./ram/S011HD1P_X32Y2D128_BW.v)。`ysyxSoCFull.v`已经在顶层集成了8个**带写掩码**的RAM，每个RAM的规格是128X64bits，共8KB。**同学们需要将Cache替换成访问核顶层的SRAM端口**。
    >注意：实现大于8KB Cache的核要对Cache大小进行删减，小于8KB的核仍要保留核顶层中不用的端口。实现小于8KB的核将核顶层不用的SRAM端口使能接口置无效，输入悬空，输出地址、数据信号置`0`。**RAM的写掩码为低有效**，如果同学们在接入我们提供的**带写掩码**的RAM时不需要使用该RAM的写掩码，则需将该RAM的写掩码置`1`(置无效)。另外tag array无需替换，**而且不允许在核内自行例化RAM进行其他设计**，同时需要同学们自行维护程序加载时的Cache一致性。具体RAM的端口定义请见[这里](./ram/README.md)。
* 若采用Verilog/SystemVerilog开发，则需要：
    * 确认代码中的锁存器(Latch)已经去除。
        * **Chisel福利：Chisel不会生成锁存器**
    * 确认代码中的异步复位触发器已经去除，或已经实现同步撤离。
        * **Chisel福利：Chisel默认生成同步复位触发器**
* 对于除了SRAM口之外，其他不使用的核顶层端口(io_interrupt和AXI4 slave)，需要将输出端口置`0`，输入端口悬空。
>注意：虽然开源的Verilator的仿真效率要远高于商业的仿真器(比如VCS)。但是Verilator对SystemVerilog的支持还不是很完整，对RTL代码的检查也偏乐观。为此我们在正式开始后端设计前还会使用VCS对同学们提交的核再次进行仿真。为了避免出现由于**Verilator与VCS的仿真行为不一致**而导致的仿真问题，<sup>[[2]](#id_verilator_sim)</sup>**请`避免`在同学们自己的核中使用如下内容：**
* 不可综合的语法，例如延时和DPI-C。
* initial语句。
* unpacked数组、结构体。
* interface、package、class。
* 小端序位标号，如 [0:31]。
* 由于缺失else导致生成锁存器
* logic类型的X状态和高阻抗Z状态。
* 使用时钟下降沿触发。
* 异步reset和跨时钟域。
* 尝试屏蔽全局时钟信号。

> 注意，再强调一下：
> 1. 所有的寄存器**都必须要复位**，不然同学们的核可能通过了Verilator测试，但是过不了VCS测试。比如在VCS仿真阶段，同学们核的Cache可能仿真会出现问题。一个可能的原因是Cache在进行写回操作时寄存器忘记复位导致出现未知态而无法被选择。
> 2. AXI4总线**不要只用ready作为切换状态的信号**，**因为在VCS上进行仿真的SoC默认ready是一直拉高的**，可能会导致同学们的核在VCS仿真下出现AXI4握手和状态机切换问题。

## 代码规范检查(北京时间 2022/10/07 23:59:59前完成)
对代码进行规范检查，并清除报告中的Warning。具体步骤如下：
* 运行`./main.py -l`，Verilator将会报告除`DECLFILENAME`和`UNUSED`之外所有类别的Warning，你需要修改代码来清理它们。Warning的含义可以参考[Verilator手册的说明](https://veripool.org/guide/latest/warnings.html#list-of-warnings)。
* 运行`./main.py -lu`，Verilator将会额外报告`UNUSED`类别的Warning，你需要修改代码来尽最大可能清理它们。
* 若某些`UNUSED`类别的Warning无法清理，或者**存在一些同学们无法自行决定是否可以清除**的Warning时，需要填写`./lint`目录中的[warning.md](./lint/warning.md)并给出原因，用于向SoC团队和后端设计团队提供参考。其中[warning.md](./lint/warning.md)中已经给出了格式范例，同学们填写时可以自行删除。
>注意：<sup>[[1]](#id_verilator_unopt)</sup>Verilator对于**组合逻辑环**通常会报`UNOPT`或者`UNOPTFLAT`警告，这是因为组合逻辑环需要多次迭代后才能得到最终的结果(收敛)。这两种警告的区别在于，一个是Verilator生成`flatten netlist`前报告的，一个是生成后报告的。虽然Verilator声称忽略这些警告不会影响仿真的正确性，但是也有一种可能是同学们的核内确实存在有组合逻辑环。如果有，很可能是核内有地方写错了。根据我们的经验，大家在写流水线的hazard部分时比较容易写出组合逻辑环。

> 对于Verilator报告的`UNOPT`警告，**某些情况下不一定是真的存在组合逻辑环**。这是因为，出于仿真性能上的考虑，Verilator并不是按信号的每一位来单独计算的，通常会把很多信号放一起计算。**此时如果确定处理器核内确实不存在组合逻辑环的话，可以使用`/* verilator split_var */`来消除警告，并继续进行下面的测试过程。** 组合逻辑环与UNPOT的具体例子可以参见<sup>[[1]](#id_verilator_unopt)</sup>。

## Verilator仿真(北京时间 2022/10/07 23:59:59前完成)
> <sup>[[3]](#id_verilator_cycle)</sup>Verilator是一个支持Verilog/SystemVerilog的周期精确(cycle-accurate)的开源仿真器，但是它不能代替Vivado xsim这些事件驱动的仿真器。<sup>[[4]](#id_verilator_intro)</sup>所谓周期精确仿真，是在确定模块输入的情况下，计算出模块在足够长时间后的输出。因此在周期精确仿真中没有延时的概念。可以理解为每次更新都是计算模块在无穷久后处于稳态时的输出。对于CPU这种由一个时钟信号驱动的设计，外层代码(C++代码)只需要通过反复变动时钟信号的值(从0变1，再从1变0)，就能得到每个周期内CPU的状态输出。

><sup>[[4]](#id_verilator_intro)</sup>由于Verilator是一个基于周期的仿真器，这意味着它不会评估单个周期内的时间，也不会模拟精确的电路时序。因此无法从波形中观察到一个时钟周期内的毛刺，也不支持定时信号延迟。另外由于Verilator是基于周期驱动的仿真器，它不能用于时序仿真、反向注释网表、异步(无时钟)逻辑，或者一般来说任何涉及时间概念的信号变化，也即每当Verilator评估电路时，所有输出都会立即切换。正是由于时钟边沿之间的一切都被忽略了，Verilator的仿真速度才能做到非常快，故Verilator非常适合仿真具有一个或多个时钟的同步数字逻辑电路的功能，或者用于从Verilog/SystemVerilog代码创建软件模型。

一生一芯四期的SoC框架会对同学们的处理器核代码使用Verilator进行集成测试与仿真，其中SoC的地址空间分配如下：
| 设备 | 地址空间 |
| --- | --- |
| Reserve           | `0x0000_0000~0x01ff_ffff`|
| CLINT             | `0x0200_0000~0x0200_ffff`|
| Reserve           | `0x0201_0000~0x0fff_ffff`|
| UART16550         | `0x1000_0000~0x1000_0fff`|
| SPI               | `0x1000_1000~0x1000_1fff`|
| VGA               | `0x1000_2000~0x1000_2fff`|
| PS2               | `0x1000_3000~0x1000_3fff`|
| Ethernet          | `0x1000_4000~0x1000_4fff`|
| Reserve           | `0x1000_5000~0x1bff_ffff`|
| Frame Buffer      | `0x1c00_0000~0x2fff_ffff`|
| SPI-flash XIP Mode| `0x3000_0000~0x3fff_ffff`|
| ChipLink MMIO     | `0x4000_0000~0x7fff_ffff`|
| MEM               | `0x8000_0000~0xfbff_ffff`|
| SDRAM             | `0xfc00_0000~0xffff_ffff`|

其中:
* 处理器的复位PC需设置为`0x3000_0000`，第一条指令从flash中取出。
* CLINT模块位于处理器核内部，SoC不提供，需要同学们自行实现。
* 接入外部中断需要同学们**自行设计仲裁逻辑**(核的top层已经预留有io_interrupt接口， 该口会从SoC引出并通过ChipLink接入到FPGA中。同学们需要自行在FPGA上实现PLIC。核在接收到中断会jump到异常处理程序，之后通过读ChipLink MMIO的相关寄存器来查看中断源信息并响应。异常处理完后可以通过写ChipLink MMIO的相关寄存器来清除中断源，**外部中断功能是可选实现的，但不实现的话仍需保留io_interrupt接口**)。
* MMIO地址的VGA和Frame Buffer范围都会访问vga ctrl。0x1000_2000~0x1000_2fff是访问vga的axi4从口来进行功能配置。vga的帧缓冲实际上保存在内存中，但我们希望对帧缓冲的写入通过MMIO总线进行，所以在vga模块中加入了一个映射模块，处理器将对某个像素的写入发送到MMIO Frame Buffer的地址上，vga对这个地址加上偏移量，从而获取到像素位于内存的地址，并通过自身的axi4主机口将读写请求转发给内存。
* 接入同学们自行设计的设备需要核内实现并将设备寄存器分配到**Reserve地址范围内**。
> 注意：四期SoC的地址空间中没有设置与SoC时钟和管脚相关的功能寄存器，**即不支持通过软件访问某个确定地址来设置SoC相关参数**。

### Verilator仿真要求如下：
* 使用Verilator将自己的核`ysyx_8位学号.v`和`ysyxSoCFull.v`正确编译成可执行仿真程序`emu`。
* 确认清除Warning后的代码可以成功启动hello、memtest和rtthread等程序。**四期SoC添加了新的测试程序，测试程序的具体内容和要求请见[这里](./prog/README.md)**。
* 通过快速模式(跳过SPI传输，不可综合，适合快速调试和迭代)对flash进行模拟，运行并通过本框架提供的测试程序。为了打开flash的快速模式，你需要在`./perip/spi/rtl/spi.v`的开头定义宏`FAST_FLASH`：
  ```Verilog
  // define this macro to enable fast behavior simulation
  // for flash by skipping SPI transfers
  `define FAST_FLASH
  ```
  > 注意：**事实上同学们不需要真正去添加`FAST_FLASH`宏，因为我们已经添加好了，并且我们已经在`main.py`中维护了自动切换`FAST_FLASH`宏的功能**，这一节只是在给同学们介绍Verilator仿真的过程。并不需要同学们上手修改代码，而下一节[Verilator仿真具体步骤](#verilator仿真具体步骤如下)才是同学们需要实际操作的部分。

  具体来说，该模式下spi控制器会直接使用DPI-C函数将需要的程序和数据读到AXI4总线侧，而避免原先`AXI4<--->SPI<--->DPI-C`中的AXI4到SPI协议的转换过程，提高了程序仿真的速度。对于每个同学来说，都需要通过：
  * 直接在flash上运行的程序(位于`./prog/bin/flash`目录下)：
    * hello-flash.bin
    * memtest-flash.bin
    * rtthread-flash.bin
    * ...
  * 通过loader把程序加载到memory，然后跳转运行(位于`./prog/bin/mem`目录下)。**注意需要额外实现`fence.i`指令**
    * hello-mem.bin
    * memtest-mem.bin
    * rtthread-mem.bin
    * ...
* 通过正常模式(不跳过SPI传输，仿真速度慢，用于最终的系统测试)对flash进行模拟，重新运行上述测试程序。你需要在`./perip/spi/rtl/spi.v`的开头取消对宏`FAST_FLASH`的定义：
  ```Verilog
  // define this macro to enable fast behavior simulation
  // for flash by skipping SPI transfers
  // `define FAST_FLASH
  ```
    * 然后再分别重新运行上面提到的flash、mem：
        * hello-flash.bin
        * memtest-flash.bin
        * rtthread-flash.bin
        * hello-mem.bin
        * memtest-mem.bin
        * rtthread-mem.bin
        * ...

### Verilator仿真具体步骤如下：
前面的小节[Verilator仿真要求](#verilator仿真要求如下)介绍了整个测试程序的结构，但是为了方便同学们进行Verilator测试，我们**已经在`main.py`中实现了Verilator编译、仿真测试和回归测试功能**，并且可以使得Verilator仿真程序`emu`自动在快速模式和正常模式间进行切换，不需要同学们手动修改`define FAST_FLASH`。同学们只需要：

* 运行`./main.py -c`就可以编译生成flash正常模式下的仿真可执行文件`emu`，运行`./main.py -fc`可以编译生成flash快速模式下的仿真可执行文件`emu`。为了提高编译速度，可以修改`./sim/Makefile`中`build`的`-j6`选项。
* 在生成`emu`之后，使用：
    ```sh
    $> ./main.py -t APP_TYPE APP_NAME SOC_SIM_MODE SOC_WAV_MODE
    ```
    来对某个特定测试程序进行仿真，其中`APP_TYPE`可选值为`flash`和`mem`，分别表示flash和memory加载两种启动方式。`APP_NAME`的可选值有`hello`、`memtest`和`rtthread`等。所有的支持的程序名见`./main.py -h`中的`-t`选项的列表。`SOC_SIM_MODE`的可选值有`cmd`和`gui`，分别表示仿真的执行环境，`cmd`表示命令行执行环境，程序会在命令行输出仿真结果。`gui`表示图形执行环境，程序会使用SDL2将RTL的数据进行图形化交互展示。`SOC_WAV_MODE`的可选值有`no-wave`和`wave`。比如运行`./main.py -t flash hello cmd no-wave`可以仿真flash模式下的命令行执行环境的hello测试程序，并且不输出波形。运行`./main.py -t mem hello cmd wave`可以仿真flash模式下的命令行执行环境的hello测试程序，并且输出波形，波形文件的路径为`./ysyx/soc.wave`。波形的默认格式是`FST`，FST是GTKWave自己开发的一种二进制波形格式，相比VCD文件体积更小。运行`./main.py -t mem kdb gui`可以仿真mem加载模式下图形执行环境的键盘测试程序。
    > 注意：**所有的测试程序只能在一种执行环境下运行，具体在哪个环境下运行见[文档](./prog/README.md)**。如果需要输出VCD格式的波形文件，只需要在[./sim/Makefile](./sim/Makefile)的开头把`WAVE_FORMAT ?= FST`修改成`WAVE_FORMAT ?= VCD`，然后重新编译即可。**需要强调的一点是使用`wave`选项开启波形输出后，程序运行时间会变长，如果程序没有跑出结果就结束的话，请自行修改下面小节介绍的"预设运行时间"。**

* 运行`./main.py -r`和`./main.py -fr`就可以依次运行flash正常模式与快速模式的回归测试，回归测试只测试在`cmd`执行环境下的程序，`gui`执行环境下的程序不进行回归测试。
* 在测试过程中我们对于每个测试都设置了**预设运行时间**，当程序超过**预设运行时间**后会自行停止运行，同学们可以修改`./main.py`中的：
    ```python
    app = [('hello', 40), ('memtest', 70), ('rtthread', 450)...]
    ```
    数字部分以适应自己核的运行，其中数字表示**预设运行时间**，单位为秒。由于mem测试程序需要加载，所需时间会比直接在flash中运行的要长一些，故这里的预设运行时间是以仿真程序在mem下的运行时间为基准设置的，如想提前终止程序运行，可以直接输入`ctrl-c`。想要仿真不停止可以通过设置一个较大的数字来实现，数字至少是int32类型的。另外为了保证测试时代码总是最新的，**回归测试时会对代码进行重新编译，编译之后再测试**。
> 建议：当调试通过单个测试程序后，可以直接运行回归测试指令 `./main.py -r`和`./main.py -fr`来测试所有的命令行执行环境下的程序。

> 注意：若为了正确运行测试程序而对处理器核进行了修改，**需要重新按照上述流程从头开始依次测试**。

## 提交代码(北京时间 2022/10/07 23:59:59前完成)
>注意：此处提交是为了尽快运行综合流程并发现新问题，此后可以继续调试处理器核的实现。

在接入ysyxSoC本框架并完成上述所有测试后，可以开始代码提交流程。提交前请确保所有触发器可复位。具体需要准备的工作如下：
* 将成功运行本框架的flash正常模式`rtthread-mem.bin`的截图文件`rtthread-mem.png`放置于`./submit`目录下。
* 填写`./submit`目录下的cache规格文档[cache_spec.md](./submit/cache_spec.md)。
* 确认已经根据代码规范检查并在`./lint`目录下填写完[warning.md](./lint/warning.md)。
* 制作一份带数据流向的处理器架构图，并对图中各模块做简单说明，整理成`ysyx_8位学号.pdf`文件并放置于`./submit`目录下。
* 创建自己的gitee开源仓库，确认仓库的默认主分支是`master`。
* 确认仓库通过ssh的方式clone到`./submit`目录下并填写完成git的`user.email`和`user.name`。然后运行`./main.py -su`，该脚本会先检查上述提交文件是否齐全，齐全的话会将文件拷贝到本地clone的仓库中，并推送到远端gitee仓库。
  > 注意：除了clone的`./submit`的gitee仓库外，不要在`./submit`中添加额外的文件夹，因为提交脚本是通过`os.path.isdir()`来自动确定本地clone的仓库名字的，如果`./submit`中存在多个文件夹，则程序无法分辨哪个是本地clone的仓库了。
* 将自己仓库的`HTTPS格式的URL`和`ysyx_8位学号`发送给组内助教以完成第一次代码提交。后续提交只需要重新运行`./main.py -su`命令即可。

* 代码提交后会被CI/CD程序自动拉取，并进行相应的测试。代码也可以手动提交，**但是需要确保每次提交都要以`dc & vcs`作为commit信息**，CI/CD程序只会识别`dc & vcs`的commit信息。

> 注意：后续提交不可修改Cache规格，只能根据report反馈修复bug。SoC和后端团队将使用CI/CD程序定期检查新提交的代码，进行综合和仿真测试，并将结果以日志报告的形式上传至ysyx_submit仓库的**ysyx4分支**，具体说明请参考[ysyx_submit仓库的说明文档](https://gitee.com/OSCPU/ysyx_submit/blob/ysyx4)。


## 协助SoC团队在流片仿真环境中启动RT-Thread(北京时间 2022/11/07 23:59:59前完成)
提交代码后，请及时关注SoC团队的反馈。

> 注意：**本项目中的SoC只用于在Verilator中验证，不参与流片环节！** 此外本项目与流片SoC仿真环境仍然有少数不同，在本项目中通过测试并**不代表也能通过流片SoC仿真环境的测试**，在流片SoC仿真环境中的运行结果，以SoC团队的反馈为准，因此请大家务必重视SoC团队的反馈。

具体来说，相较于基于流片SoC仿真环境的仿真，基于Verilator的仿真:
* 没有不定态(x态)信号传播的问题。
* 没有跨时钟域和异步桥。
* 没有PLL。
* 没有真实器件的延时信息。


> **当完成到这一步，即同时通过Verilator和VCS的仿真测试，且dc综合也满足时序和面积要求的同学，即可获得预流片资格。后续当通过项目组组织的在线考核后将正式获得一生一芯第四期的流片资格。**

## 扩展内容
> 注意：**以下内容不是同学们必须要完成的任务，而是给那些对我们提供的SoC仿真框架有定制化需求的同学们提供的。下面分别介绍了编译和生成自己的测试程序、生成自己的Verilator仿真程序所需要的必要步骤和使用chisel生成ysyxSoCFull框架的过程和注意要点**。

## 生成自己的测试程序

从第四期第二批开始，项目组**不再提供适配ysyxSoC环境的`abstract-machine`**，程序的运行时环境(am)需要同学们自己实现，但是框架提供了实现参考，具体如下：
* 正确设置`AM_HOME`环境变量。
* 将自己的测试程序源码目录放到`./prog/src`下，源码目录下需要有个Makefile，其内容格式可以参考`./prog/src/hello/Makefile`：
    ```Makefile
    SRCS = hello.c # 所有的源码路径
    NAME = hello   # 生成的可执行程序名

    include $(AM_HOME)/Makefile
    ```
* 然后切换到`./prog/src`，修改`run.py`中的`APP_NAME`和`APP_TYPE`的值。其中`APP_NAME`修改为上一个步骤中Makefile中填写的`NAME`，`APP_TYPE`修改为`flash`或者`mem`，表示生成的程序的加载类型，`flash`表示程序从flash直接执行，`mem`表示程序先从flash加载到mem中，然后再执行。
* 执行`./prog/src/run.py`，编译通过的话就可以在`./prog/bin/$(FLASH_TYPE)`下得到可执行程序。

## ysyxSoCFull定制集成步骤
* 将`ysyxSoC/ysyx/perip`目录及其子目录下的所有`.v`文件加入verilator的Verilog/SystemVerilog文件列表。
* 将`ysyxSoC/ysyx/soc/ysyxSoCFull.v`文件加入verilator的Verilog/SystemVerilog文件列表。
* 将处理器Verilog/SystemVerilog文件加入verilator的Verilog/SystemVerilog文件列表。
* 将`ysyxSoC/ysyx/perip/uart16550/rtl`和`ysyxSoC/ysyx/perip/spi/rtl`两个目录加入包含路径中(使用verilator的`-I`选项)。
* 将`ysyxSoC/ysyx/perip/spiFlash/spiFlash.cpp`文件加入verilator的C++文件列表。
* 将处理器的复位PC设置为`0x3000_0000`。
* 在verilator编译选项中添加`--timescale "1ns/1ns"`。
* 在verilator初始化时对flash进行初始化，有以下两种方式:
   * 调用`spiFlash.cpp`中的`flash_init(img)`函数，用于将bin文件中的指令序列放置在flash中，其中参数`img`是bin文件的路径，在`ysyxSoC/ysyx/prog/bin/flash`和`ysyxSoC/ysyx/prog/bin/mem`目录下提供了一些示例。
   * 调用`spiFlash.cpp`中的`flash_memcpy(src, len)`函数，用于将已经读入内存的指令序列放置在flash中，其中参数`src`是指令序列的地址，`len`是指令序列的长度。
* 将`ysyxSoCFull`模块(在`ysyxSoC/ysyx/soc/ysyxSoCFull.v`中定义)设置为verilator仿真的顶层。
* 将`ysyxSoC/ysyx/soc/ysyxSoCFull.v`中的`ysyx_000000`模块名修改为自己的处理器模块名。
* 通过Verilator进行仿真即可。


## 自己编译并生成ysyxSoCFull.v步骤
* 更新并拉取当前仓库的子模块：
    ```sh
    $> git submodule update --init
    ```
* 指定RISCV环境变量为工具链的安装目录，为`riscv64-unknown-elf-xxx`开发版的根目录：
    ```sh
    $> export RISCV=/path/to/riscv/toolchain/installation
    ```
* 进入到`./ysyx`目录下执行`./main.py -y`编译SoC框架，框架的源码结构如下所示：
    ```sh
    ysyxSoC/src/main/scala/ysyx
    ├── chiplink
    │   └── ...                        # ChipLink的实现
    └── ysyx
        ├── AXI4ToAPB.scala            # AXI4-APB的转接桥，不支持burst，且只支持4字节以下的访问
        ├── ChipLinkBridge.scala       # ChipLink-AXI4的转接桥
        ├── CPU.scala                  # CPU wrapper，将会按照SoC端口规范实例化一个CPU实例
        ├── PS2.scala                  # PS2 wrapper，将会实例化Verilog版本的PS2控制器
        ├── RAM.scala                  # RAM wrapper，将会实例化Verilog版本的RAM仿真模型
        ├── SoC.scala                  # SoC顶层
        ├── SPI.scala                  # SPI wrapper，将会实例化Verilog版本的SPI控制器
        └── Uart16550.scala            # UART16550 wrapper，将会实例化Verilog版本的UART16550控制器
    ```
> 注意：编译时需要使用Java 11，高版本的Java会抛出异常，具体见：https://github.com/chipsalliance/rocket-chip/issues/2789

## 致谢和声明
* AXI4 crossbar (来源于Rocket Chip项目, 已在计算所团队的项目中经过流片验证)。
* ChipLink (来源于[sifive-blocks](https://github.com/sifive/sifive-blocks/tree/master/src/main/scala/devices/chiplink), 已在计算所团队的项目中经过流片验证)。
* UART16550 (来源于OpenCores, 已在计算所团队的项目中经过流片验证)。
* SPI控制器 (来源于OpenCores, 已在计算所团队的项目中经过流片验证)。
* SoC集成 (基于diplomacy DSL实现)。
* 感谢[李国旗(ysyx_22040228)](https://github.com/xunqianxun)同学的对接测试，李国旗帮忙测试出flash版本乘除法测试的访存宽度bug，总结了AXI调试过程中的经验。在介绍本框架时也是使用李国旗同学的核进行举例的。
* 感谢[郑永煜(ysyx_22040450)](./)同学对代码规范检查脚本提出的修改意见。
* 感谢[万子琦(ysyx_22040698)](./)同学对README.md中的错别字的提示。
* 感谢[丁亚伟(ysyx_22040561)](./)同学指出ysyxSoCFull.v文件中的核顶层文件名错误和提交脚本换行的问题。
* 感谢[卢琨(ysyx_22041812)](./)同学提出一个更好对chisel中的寄存器进行复位检查的命令。
* 感谢[汪洵(ysyx_22040053)](./)同学发现sdl2图片加载地址的非法内存访问bug并提供了代码修改。
* 感谢[卢琨(ysyx_22041812)](./)同学发现chisel中只使用`grep reset`检查的不完备性并提出相应的调试建议。

## 参考
[1] [FDU NSCSCC 附加资料：组合逻辑环与UNOPT(GPL-3.0)](https://fducslg.github.io/ICS-2021Spring-FDU/misc/unopt.html)<span id="id_verilator_unopt"></span>

[2] [FDU NSCSCC 附加资料：Verilator仿真(GPL-3.0)](https://fducslg.github.io/ICS-2021Spring-FDU/misc/verilate.html?highlight=sdl#verilator-%E4%BB%BF%E7%9C%9F)<span id="id_verilator_sim"></span>

[3] [FDU NSCSCC 附加资料：周期精确仿真(GPL-3.0)](https://fducslg.github.io/ICS-2021Spring-FDU/misc/verilate.html?highlight=sdl#%E5%91%A8%E6%9C%9F%E7%B2%BE%E7%A1%AE%E4%BB%BF%E7%9C%9F)<span id="id_verilator_cycle"></span>

[4] [KuangjuX Blog Verilator学习笔记(CC BY-NC-SA 4.0)](http://blog.kuangjux.top/2022/02/20/verilator-learning/)<span id="id_verilator_intro"></span>