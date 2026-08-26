# 常量编译器

本模块将注解提供的结构化常量元数据收敛为 `LsiConstantGroup`，再生成无反射的 Kotlin `object`。生成器不依赖 KSP，KSP 只是元数据输入边界。

宿主在类、接口或对象上声明 `@GenerateConstants`，生成结果固定位于宿主包名的 `.generated` 子包。目前支持 `Boolean`、`Int`、`Long` 和 `String` 编译期常量；编译器会拒绝空常量组、重复名称、非法标识符和类型不匹配的值。

宿主模块需同时添加模块依赖和 KSP 处理器：

```yaml
dependencies:
  - //lib/compiler/constant-compiler

settings:
  kotlin:
    ksp:
      processors:
        - //lib/compiler/constant-compiler
```
