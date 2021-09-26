
## CPU接口命名规范

| | | | | | |
| --- | --- | --- | --- | --- | --- |
| CPU文件名 | | `ysyx_学号后六位.v`          | 如`ysyx_210000.v`   |
| CPU顶层模块名 | | `ysyx_学号后六位`        | 如`ysyx_210000`     |
| CPU内部模块名 | | `ysyx_学号后六位_模块名` | 如`ysyx_210000_ALU` |
| 时钟 | input | `clock` |
| 复位(高电平有效) | input | `reset` |
| 外部中断 | input | `io_interrupt` |
| AXI4 Master总线 |   |                     | AXI4 Slave总线 |    |                    |
| `input`  |          | `io_master_awready` | `output` |          | `io_slave_awready` |
| `output` |          | `io_master_awvalid` | `input`  |          | `io_slave_awvalid` |
| `output` | `[31:0]` | `io_master_awaddr`  | `input`  | `[31:0]` | `io_slave_awaddr`  |
| `output` | `[3:0]`  | `io_master_awid`    | `input`  | `[3:0]`  | `io_slave_awid`    |
| `output` | `[7:0]`  | `io_master_awlen`   | `input`  | `[7:0]`  | `io_slave_awlen`   |
| `output` | `[2:0]`  | `io_master_awsize`  | `input`  | `[2:0]`  | `io_slave_awsize`  |
| `output` | `[1:0]`  | `io_master_awburst` | `input`  | `[1:0]`  | `io_slave_awburst` |
| `input`  |          | `io_master_wready`  | `output` |          | `io_slave_wready`  |
| `output` |          | `io_master_wvalid`  | `input`  |          | `io_slave_wvalid`  |
| `output` | `[63:0]` | `io_master_wdata`   | `input`  | `[63:0]` | `io_slave_wdata`   |
| `output` | `[7:0]`  | `io_master_wstrb`   | `input`  | `[7:0]`  | `io_slave_wstrb`   |
| `output` |          | `io_master_wlast`   | `input`  |          | `io_slave_wlast`   |
| `output` |          | `io_master_bready`  | `input`  |          | `io_slave_bready`  |
| `input`  |          | `io_master_bvalid`  | `output` |          | `io_slave_bvalid`  |
| `input`  | `[1:0]`  | `io_master_bresp`   | `output` | `[1:0]`  | `io_slave_bresp`   |
| `input`  | `[3:0]`  | `io_master_bid`     | `output` | `[3:0]`  | `io_slave_bid`     |
| `input`  |          | `io_master_arready` | `output` |          | `io_slave_arready` |
| `output` |          | `io_master_arvalid` | `input`  |          | `io_slave_arvalid` |
| `output` | `[31:0]` | `io_master_araddr`  | `input`  | `[31:0]` | `io_slave_araddr`  |
| `output` | `[3:0]`  | `io_master_arid`    | `input`  | `[3:0]`  | `io_slave_arid`    |
| `output` | `[7:0]`  | `io_master_arlen`   | `input`  | `[7:0]`  | `io_slave_arlen`   |
| `output` | `[2:0]`  | `io_master_arsize`  | `input`  | `[2:0]`  | `io_slave_arsize`  |
| `output` | `[1:0]`  | `io_master_arburst` | `input`  | `[1:0]`  | `io_slave_arburst` |
| `output` |          | `io_master_rready`  | `input`  |          | `io_slave_rready`  |
| `input`  |          | `io_master_rvalid`  | `output` |          | `io_slave_rvalid`  |
| `input`  | `[1:0]`  | `io_master_rresp`   | `output` | `[1:0]`  | `io_slave_rresp`   |
| `input`  | `[63:0]` | `io_master_rdata`   | `output` | `[63:0]` | `io_slave_rdata`   |
| `input`  |          | `io_master_rlast`   | `output` |          | `io_slave_rlast`   |
| `input`  | `[3:0]`  | `io_master_rid`     | `output` | `[3:0]`  | `io_slave_rid`     |

## 命名规范自查脚本使用说明
在处理器核所在目录下运行cpu-check.py脚本，会检测设计的.v文件是否符合命名规范，脚本会生成日志文件cpu-check.log。

测试环境：`Debian10`, `Ubuntu 20.04`, `WSL2-Ubuntu 20.04`, `Windows10`


### 操作步骤

首先， 将脚本文件cpu-check.py移动到存放处理器核所在目录下，

并在该目录下增加文件的执⾏权限：

```shell
chmod +755 cpu-check.py
```

然后，执行脚本，并按照提⽰输⼊学号，回⻋：

```shell
python3 cpu-check.py
```

推荐用`python3`而非`python2`运行脚本

学号不需要手动补`0`，例如学号是`888`的同学，直接按照脚本提示输入`888`即可

```
#如果没有python3环境，也可以用Python2运行脚本，不过需要注意输入学号时不要手动补0
python2 cpu-check.py
```

最后，可以在终端看到检测结果，如果检查通过，会在终端打印：
```shell
Your core is fine in module name and signal interface
```

同时，在该目录下会生成日志文件cpu-check.log

如果检测未通过，则会给出错误信息，并提示是`module name`错误还是`signal interface`错误。

也可以打开⽬录下⽣成的cpu-check.log日志⽂件查看报错原因和提示。
