# 低代码编译器聚合模块

本模块只聚合 `lowcode-metadata`、`lowcode-source-compiler`、`lowcode-feature-compiler`、`ddl-compiler`、`lowcode-contract-migration-compiler` 和 `lowcode-documentation-compiler`，为现有调用方保留单一依赖入口。具体实现按输入模型和产物类型位于独立模块中。

仓库内部模块应优先直接依赖所需的叶子模块，避免把源码、DDL 和文档编译能力带入运行时；已有外部调用方可继续使用本聚合坐标迁移。

```kotlin
val metadata = LowcodeMetadataDatabaseReader.read(databaseConfig)
val modelFiles = metadata.models.flatMap(LowcodeSourceCompiler::generate)
val contractFiles = metadata.contracts.flatMap { contract ->
    LowcodeSourceCompiler.generate(contract, metadata.models)
}
```
