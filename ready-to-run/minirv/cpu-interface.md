# CPU接口命名规范(E阶段)

| | | | | | |
| --- | --- | --- | --- | --- | --- |
| CPU文件名 | | `ysyx_8位学号.v`          | 如`ysyx_23060000.v`   |
| CPU顶层模块名 | | `ysyx_8位学号`        | 如`ysyx_23060000`     |
| CPU内部模块名 | | `ysyx_8位学号_模块名` | 如`ysyx_23060000_ALU` |
| 时钟 | input | `clock` |
| 复位(高电平有效) | input | `reset` |
| SimpleBus总线 |   |                     |
| `output` |          | `io_ifu_reqValid`   |
| `output` | `[31:0]` | `io_ifu_addr`       |
| `input`  |          | `io_ifu_respValid`  |
| `input`  | `[31:0]` | `io_ifu_rdata`      |
| `output` |          | `io_lsu_reqValid`   |
| `output` | `[31:0]` | `io_lsu_addr`       |
| `output` | `[1:0]`  | `io_lsu_size`       |
| `output` |          | `io_lsu_wen`        |
| `output` | `[31:0]` | `io_lsu_wdata`      |
| `output` | `[3:0]`  | `io_lsu_wmask`      |
| `input`  |          | `io_lsu_respValid`  |
| `input`  | `[31:0]` | `io_lsu_rdata`      |
