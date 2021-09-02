
## CPU接口命名规范

| | | | | | |
| --- | --- | --- | --- | --- | --- |
| CPU文件名 | | `ysyx_学号后六位.v`          | 如`ysyx_210000.v`   |
| CPU顶层模块名 | | `ysyx_学号后六位`        | 如`ysyx_210000`     |
| CPU内部模块名 | | `ysyx_学号后六位_模块名` | 如`ysyx_210000_ALU` |
| 时钟 | input | `clock` |
| 复位(高电平有效) | input | `reset` |
| 中断 | input | `io_interrupt` |
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
在处理器核目录下运行cpu-check.py脚本，会检测设计.v文件是否符合命名规范，脚本会生成日志文件cpu-check.log。

### 操作步骤

首先， 将脚本文件cpu-check.py移动到存放处理器核所在目录下：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-01-%E6%96%87%E4%BB%B6%E5%87%86%E5%A4%87.png?raw=true)

并在该目录下增加文件的执⾏权限：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-02-%E8%84%9A%E6%9C%AC%E5%8A%A0%E6%89%A7%E8%A1%8C%E6%9D%83%E9%99%90.png?raw=true)

然后，执行脚本，并按照提⽰输⼊学号，回⻋，如下图：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-03-%E8%BF%90%E8%A1%8C%E8%84%9A%E6%9C%AC.png?raw=true)

最后，可以在终端看到检测结果，如果检查通过，如下图：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-04-%E8%BE%93%E5%85%A5%E5%AD%A6%E5%8F%B7%E5%9B%9E%E8%BD%A6-%E6%A3%80%E6%9F%A5%E9%80%9A%E8%BF%87%E4%BF%A1%E6%81%AF.png?raw=true)

同时，在该目录下会生成cpu-check.log的日志信息：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-06-%E7%94%9F%E6%88%90log%E6%96%87%E4%BB%B6.png?raw=true)

终端检测未通过，会给出错误信息，如下图所示：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-05-%E8%BE%93%E5%85%A5%E5%AD%A6%E5%8F%B7%E5%9B%9E%E8%BD%A6-%E6%A3%80%E6%9F%A5%E6%9C%AA%E9%80%9A%E8%BF%87%E6%8A%A5Error.png?raw=true)

可以打开⽬录下⽣成的log⽂件查看报错原因，如下图：
![](https://github.com/AllenChenChao/ysyxSoC/blob/master/ysyx/soc/png/cpu-check/soc-cpu-check-07-%E7%94%9F%E6%88%90log%E6%96%87%E4%BB%B6%E6%9F%A5%E7%9C%8B%E6%8A%A5%E9%94%99%E4%BF%A1%E6%81%AF.png?raw=true)
