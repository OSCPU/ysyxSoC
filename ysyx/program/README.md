
## 测试程序

### 文件介绍

```
ysyxSoC/ysyx/program
├── bin
│   ├── flash                      # 直接在flash上运行的程序, 其中可以对elf文件进行反汇编
│   │    ├── hello-flash.bin
│   │    ├── hello-flash.elf
│   │    ├── memtest-flash.bin
│   │    ├── memtest-flash.elf
│   │    ├── rtthread-flash.bin
│   │    └── rtthread-flash.elf
│   └── loader                     # 通过loader加载并运行的程序, 需要实现fence.i指令才能运行
│       ├── hello-loader.bin
│       ├── memtest-loader.bin
│       └── rtthread-loader.bin
├── README.md                      # 本文档
└── src                            # 测试程序的参考代码, 用户无需自行编译
    ├── hello                      # 输出Hello字符串
    ├── loader                     # flash加载器的参考实现
    ├── memtest                    # 对8KB数组进行读写测试
    └── rt-thread                  # RT-Thread编译方式说明
```

### flash程序参考输出

#### hello-flash.bin

运行指令总数为186, 参考输出如下:
```
Hello RISC-V
```

#### memtest-flash.bin

运行指令总数为37471, 参考输出如下:
```
START TEST...ALL DATA PREPARED
ALL TESTS PASSED!!
```

#### rtthread-flash.bin

运行指令总数为84570, 参考输出如下:
```
heap: [0x8000d486 - 0x8640d468]

 \ | /
- RT -     Thread Operating System
 / | \     4.0.4 build Aug 31 2021
 2006 - 2021 Copyright by rt-thread team
Hello RISC-V!
msh />
```
