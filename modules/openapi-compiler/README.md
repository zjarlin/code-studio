# OpenAPI 元数据编译器

该模块把 `LsiOpenApiContract` 确定性编译为 OpenAPI JSON。模块只依赖 LSI 和 Jackson，
不依赖 Ktor、KSP、数据库或具体宿主。

Models 路由和独立 Contracts 先通过 `toLsiOpenApiContract()` 收敛到统一输入；宿主已有的
OpenAPI 文档作为基础文档传入，编译器只补充元数据能够确定的 Schema、CRUD 操作和自定义操作。

```kotlin
val compilerInput = routes.map(LsiLowcodeRoute::toLsiOpenApiContract) +
    contracts.map(LsiLowcodeContract::toLsiOpenApiContract)
val document = OpenApiCompiler.compile(baseDocument, compilerInput)
```

AI 只负责生成受约束的 Contracts 草稿；校验和本模块编译过程必须保持确定性。

LSI 使用 `integer/int64` 表达逻辑上的 Kotlin `Long`，供源码生成和运行时参数转换消费。
平台 JSON 层会把 `Long` 序列化为字符串，因此本模块在最终 OpenAPI 发布边界将这类 Schema
统一输出为 `type: string`；请求端仍兼容数字输入，但生成客户端应始终使用字符串以避免精度损失。
