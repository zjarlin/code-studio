# Catalog

该功能从 `META-INF/code-studio/catalog.json` 读取约定编译产物，按 `routeKey` 或 `elementKey` 合并 PostgreSQL 中的可配置展示与权限字段，最后使用宿主权限策略过滤返回结果。路径、层级和条目类型始终由约定文件拥有，数据库不能改变路由结构。
