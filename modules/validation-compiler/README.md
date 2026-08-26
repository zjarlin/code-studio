# 校验编译器

本模块定义语言无关的校验 LSI、基于 `ServiceLoader` 的规则元数据 SPI，以及 Kotlin 校验扩展生成器。元数据提供端只声明规则，业务 DTO 不依赖具体 Web、序列化或持久化框架。

默认提供 `notBlank`、`notEmpty` 与 `noBlankElements` 三条纯值校验规则。扩展规则通过实现 `ValidationRuleMetadataProvider` 并注册到 `META-INF/services` 提供；生成器会拒绝重复编码和不支持的字段类型。
