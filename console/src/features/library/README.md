# Library

Library 目录、详情与最小创建流程。本 feature 的路由与操作元数据位于 `catalog.convention.json`，页面仅通过 `routeKey` 与目录绑定。

HTTP 操作与传输类型由后端 OpenAPI 生成到 `dist/generated`；`commands.ts` 只保留输入归一化和 Library 创建编排。
