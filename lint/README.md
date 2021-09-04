
## 使用Verilator进行代码规范检查步骤说明

1. 将文件`ysyx_学号.v`复制到本目录下
1. 将本目录中`Makefile`中的`ID`变量修改为学号
1. 运行`make lint`, Verilator将会报告除`DECLFILENAME`和`UNUSED`之外所有类别的Warning,
   你需要修改代码来清理它们. Warning的含义可以参考[Verilator手册的说明][verilator warning]
1. 运行`make lint-unused`, Verilator将会额外报告`UNUSED`类别的Warning,
   你需要修改代码来尽最大可能清理它们
1. 若某些`UNUSED`类别的Warning无法清理,
   需要填写本目录中的文件[verilator-warning.txt][txt]并给出原因,
   用于向SoC团队和后端设计团队提供参考

[verilator warning]: https://veripool.org/guide/latest/warnings.html#list-of-warnings
[txt]: ./verilator-warning.txt
