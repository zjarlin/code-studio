# Studio 嵌入式服务

本模块接收宿主 `DataSource`、`StudioConfig` 和 `StudioAccessPolicy`，在应用自己的 Ktor 进程中安装 `/studio/` UI、`/studio/config` 和 `/studio/api/*`。宿主可以通过公共 `Controller` 将自有元数据 Controller 挂载到同一 API 边界。核心结构与模块元数据分别使用 `code_studio_core_history` 和 `code_studio_metadata_history`，两者都位于宿主 PostgreSQL 的 `code_studio` schema。

`StudioConfig.enabled` 默认为 `false`；宿主必须显式启用并提供访问策略。宿主还需安装自己的 JSON 内容协商插件。

模块元数据按 contributor 依赖顺序逐个执行；每次只解析当前 contributor 的 location，但共用 metadata history。Flyway 向脚本注入 `studioSchema` 和 `contributorId` placeholder，且允许后续 contributor 使用全局唯一的较早版本号。
