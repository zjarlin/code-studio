# Report

独立承载报表和电子表格模板前端资源、运行时、Controller 与后续数据库迁移；通过 `ReportFeature` 自动贡献给宿主，Studio Starter 不引用报表实现。公共契约位于 `metadata-contract`，Excel 格式实现仅保留在本模块内部。已发布的核心 V6 只作为历史兼容保留，新迁移使用本模块的独立 Flyway 历史。
