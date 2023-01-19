# Warning汇总

### Warning-UNUSED
* **描述：** ysyx_xxxxxx.v:3299:17: Signal is not used: 'io_interrupt'
* **不清理原因：** 未实现外部中断，因此不使用该信号
* **备注：** 无

### Warning-UNUSED
* **描述：** ysyx_xxxxxx.v:2808:16: Bits of signal are not used: '_sll_T_3'[126:64]
* **不清理原因：** 移位指令实现时丢弃的高位
* **备注：** 无

### Warning-UNUSED
* **描述：** ysyx_xxxxxx.v:1647:17: Bits of signal are not used: 'io_inst'[6:0]
* **不清理原因：** 立即数生成模块输入的32位指令的低位部分弃用
* **备注：** 无

### Warning-UNUSED
* **描述：** ysyx_xxxxxx.v:4343:15: Bits of signal are not used: 'sdMask'[14:8]
* **不清理原因：** 为了保留chisel代码可读性
* **备注：** 无