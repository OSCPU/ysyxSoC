
## 接口与流片用RAM一致的简化行为模型

### 不带写掩码的128bitx64单口RAM
文件名为`S011HD1P_X32Y2D128.v`

| 端口 | 说明 |
| --- | --- |
| output [127:0] Q   | 读数据 |
| input          CLK | 时钟 |
| input          CEN | 使能信号, 低电平有效 |
| input          WEN | 写使能信号, 低电平有效 |
| input  [5:0]   A   | 读写地址 |
| input  [127:0] D   | 写数据 |

### 带写掩码的128bitx64单口RAM
文件名为`S011HD1P_X32Y2D128_BW.v`

| 端口 | 说明 |
| --- | --- |
| output [127:0] Q    | 读数据 |
| input          CLK  | 时钟 |
| input          CEN  | 使能信号, 低电平有效 |
| input          WEN  | 写使能信号, 低电平有效 |
| input  [127:0] BWEN | 写掩码信号, 掩码粒度为1bit, 低电平有效 |
| input  [5:0]   A    | 读写地址 |
| input  [127:0] D    | 写数据 |
