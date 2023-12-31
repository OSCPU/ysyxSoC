# CPU接口命名规范

| | | | | | |
| --- | --- | --- | --- | --- | --- |
| CPU文件名 | | `ysyx_8位学号.v`          | 如`ysyx_23060000.v`   |
| CPU顶层模块名 | | `ysyx_8位学号`        | 如`ysyx_23060000`     |
| CPU内部模块名 | | `ysyx_8位学号_模块名` | 如`ysyx_23060000_ALU` |
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
