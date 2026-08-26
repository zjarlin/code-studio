# 元数据服务

该模块在宿主提供的 `DataSource` 上暴露 Studio `/lowcode` 元数据 API。读取覆盖全部可见 contributor；新增、更新和删除会沿 `Library -> Feature -> Definition` 归属链校验，只允许修改当前 `editableContributorId` 的数据。

公开装配入口：

```kotlin
StudioMetadataController(
    dataSource = dataSource,
    schema = metadataSchema,
    editableContributorId = contributorId,
    targetProfile = generationTargetProfile,
)
```

`targetProfile` 必须由宿主加载并显式注入，预览和下载不会生成未解析的宿主符号。模块只使用
JDBC、Ktor、Jackson 和仓库内编译器，不依赖宿主 ORM 或依赖注入框架。
