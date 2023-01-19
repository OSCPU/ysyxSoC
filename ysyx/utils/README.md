## 使用说明
1. 将`AddModulePrefix.scala`加入scala源码, 并修改其中的`yourpackage`
2. 在生成verilog的源文件中添加`import yourpackage._`
3. 在`ChiselStage.execute`方法的第二个参数`AnnotationSeq`中加入两个元素:
```scala
firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix())
ModulePrefixAnnotation("yourprefix")
```
  例如, 若使用`https://github.com/OpenXiangShan/chisel-playground`项目作为模板, 可进行如下修改:
```diff
--- chisel-playground/playground/src/Elaborate.scala
+++ chisel-playground/playground/src/Elaborate.scala
@@ -1,3 +1,8 @@
+package yourpackage
 object Elaborate extends App {
-  (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new GCD())))
+  (new chisel3.stage.ChiselStage).execute(args, Seq(
+    chisel3.stage.ChiselGeneratorAnnotation(() => new GCD()),
+    firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix()),
+    ModulePrefixAnnotation("ysyx_000000_")
+  ))
 }
```

> 感谢中科院计算所的[蔺嘉炜](https://github.com/ljwljwljwljw)同学提供firrtl transform源码
