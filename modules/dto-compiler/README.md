# DTO 编译器

本模块只接收中性的 `LsiDto*`，负责生成确定性的 Kotlin `data class`，并对已经归一化的数据结构计算复用候选。数据库、KSP、Studio、OpenAPI 和具体构建系统都属于外层适配器，不能进入本模块。

```kotlin
val source = KotlinDtoSourceGenerator.generate(
    LsiDtoDefinition(
        packageName = "example.generated.dto",
        className = "ExampleValue",
        description = "示例值。",
        properties = listOf(
            LsiDtoProperty(
                name = "name",
                type = LsiDtoType.STRING,
                description = "名称。",
            ),
        ),
    ),
)
```

`DtoStructureAnalyzer` 只输出候选和度量，不自动合并、删除或改写源码。相同全限定名的多个来源会先按 `METADATA > GENERATED > SOURCE` 合并，避免把元数据与其生成结果误报为重复建模。
