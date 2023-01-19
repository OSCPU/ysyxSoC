
## CPU接口命名规范

| | | | | | |
| --- | --- | --- | --- | --- | --- |
| CPU文件名 |`ysyx_学号后六位.v`|如`ysyx_040228.v`|
| CPU顶层模块名 |`ysyx_学号后六位`|如`ysyx_040228`|
| CPU内部模块名 |`ysyx_学号后六位_模块名`|如`ysyx_040228_ALU`|
| 时钟|||
||`input`|`clock`|
| 复位(高电平有效)|||
||`input`|`reset`|
| 外部中断|||
||`input`|`io_interrupt`|
| AXI4 Master总线|||
|| `input`        | `io_master_awready` |
|| `output`       | `io_master_awvalid` |
|| `output[3:0]`  | `io_master_awid`    |
|| `output[31:0]` | `io_master_awaddr`  |
|| `output[7:0]`  | `io_master_awlen`   |
|| `output[2:0]`  | `io_master_awsize`  |
|| `output[1:0]`  | `io_master_awburst` |
|| `input`        | `io_master_wready`  |
|| `output`       | `io_master_wvalid`  |
|| `output[63:0]` | `io_master_wdata`   |
|| `output[7:0]`  | `io_master_wstrb`   |
|| `output`       | `io_master_wlast`   |
|| `output`       | `io_master_bready`  |
|| `input`        | `io_master_bvalid`  |
|| `input[3:0]`   | `io_master_bid`     |
|| `input[1:0]`   | `io_master_bresp`   |
|| `input`        | `io_master_arready` |
|| `output`       | `io_master_arvalid` |
|| `output[3:0]`  | `io_master_arid`    |
|| `output[31:0]` | `io_master_araddr`  |
|| `output[7:0]`  | `io_master_arlen`   |
|| `output[2:0]`  | `io_master_arsize`  |
|| `output[1:0]`  | `io_master_arburst` |
|| `output`       | `io_master_rready`  |
|| `input`        | `io_master_rvalid`  |
|| `input[3:0]`   | `io_master_rid`     |
|| `input[1:0]`   | `io_master_rresp`   |
|| `input[63:0]`  | `io_master_rdata`   |
|| `input`        | `io_master_rlast`   |
| AXI4 Slave总线 |||
|| `output`       | `io_slave_awready` |
|| `input`        | `io_slave_awvalid` |
|| `input[3:0]`   | `io_slave_awid`    |
|| `input[31:0]`  | `io_slave_awaddr`  |
|| `input[7:0]`   | `io_slave_awlen`   |
|| `input[2:0]`   | `io_slave_awsize`  |
|| `input[1:0]`   | `io_slave_awburst` |
|| `output`       | `io_slave_wready`  |
|| `input`        | `io_slave_wvalid`  |
|| `input[63:0]`  | `io_slave_wdata`   |
|| `input[7:0]`   | `io_slave_wstrb`   |
|| `input`        | `io_slave_wlast`   |
|| `input`        | `io_slave_bready`  |
|| `output`       | `io_slave_bvalid`  |
|| `output[3:0]`  | `io_slave_bid`     |
|| `output[1:0]`  | `io_slave_bresp`   |
|| `output`       | `io_slave_arready` |
|| `input`        | `io_slave_arvalid` |
|| `input[3:0]`   | `io_slave_arid`    |
|| `input[31:0]`  | `io_slave_araddr`  |
|| `input[7:0]`   | `io_slave_arlen`   |
|| `input[2:0]`   | `io_slave_arsize`  |
|| `input[1:0]`   | `io_slave_arburst` |
|| `input`        | `io_slave_rready`  |
|| `output`       | `io_slave_rvalid`  |
|| `output[3:0]`  | `io_slave_rid`     |
|| `output[1:0]`  | `io_slave_rresp`   |
|| `output[63:0]` | `io_slave_rdata`   |
|| `output`       | `io_slave_rlast`   |
| SRAM接口 |||
|| `output[5:0]  `| `io_sram0_addr`    |
|| `output       `| `io_sram0_cen`     |
|| `output       `| `io_sram0_wen`     |
|| `output[127:0]`| `io_sram0_wmask`   |
|| `output[127:0]`| `io_sram0_wdata`   |
|| `input[127:0]` | `io_sram0_rdata`   |
|| `output[5:0]  `| `io_sram1_addr`    |
|| `output       `| `io_sram1_cen`     |
|| `output       `| `io_sram1_wen`     |
|| `output[127:0]`| `io_sram1_wmask`   |
|| `output[127:0]`| `io_sram1_wdata`   |
|| `input[127:0]` | `io_sram1_rdata`   |
|| `output[5:0]  `| `io_sram2_addr`    |
|| `output       `| `io_sram2_cen`     |
|| `output       `| `io_sram2_wen`     |
|| `output[127:0]`| `io_sram2_wmask`   |
|| `output[127:0]`| `io_sram2_wdata`   |
|| `input[127:0]` | `io_sram2_rdata`   |
|| `output[5:0]  `| `io_sram3_addr`    |
|| `output       `| `io_sram3_cen`     |
|| `output       `| `io_sram3_wen`     |
|| `output[127:0]`| `io_sram3_wmask`   |
|| `output[127:0]`| `io_sram3_wdata`   |
|| `input[127:0]` | `io_sram3_rdata`   |
|| `output[5:0]  `| `io_sram4_addr`    |
|| `output       `| `io_sram4_cen`     |
|| `output       `| `io_sram4_wen`     |
|| `output[127:0]`| `io_sram4_wmask`   |
|| `output[127:0]`| `io_sram4_wdata`   |
|| `input[127:0]` | `io_sram4_rdata`   |
|| `output[5:0]  `| `io_sram5_addr`    |
|| `output       `| `io_sram5_cen`     |
|| `output       `| `io_sram5_wen`     |
|| `output[127:0]`| `io_sram5_wmask`   |
|| `output[127:0]`| `io_sram5_wdata`   |
|| `input[127:0]` | `io_sram5_rdata`   |
|| `output[5:0]  `| `io_sram6_addr`    |
|| `output       `| `io_sram6_cen`     |
|| `output       `| `io_sram6_wen`     |
|| `output[127:0]`| `io_sram6_wmask`   |
|| `output[127:0]`| `io_sram6_wdata`   |
|| `input[127:0]` | `io_sram6_rdata`   |
|| `output[5:0]  `| `io_sram7_addr`    |
|| `output       `| `io_sram7_cen`     |
|| `output       `| `io_sram7_wen`     |
|| `output[127:0]`| `io_sram7_wmask`   |
|| `output[127:0]`| `io_sram7_wdata`   |
|| `input[127:0]` | `io_sram7_rdata`   |
