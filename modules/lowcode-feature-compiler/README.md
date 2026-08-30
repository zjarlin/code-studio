# 低代码功能编译器

本模块根据功能、模型和路由 LSI 生成类型化 Service、实现样板、Controller 和约定文件。Service 与定时任务约定文件只首次物化，方法、依赖、调度表达式和执行逻辑由业务代码在 IDE 中维护。

可编辑脚手架通过 `SourceTemplateCatalog` 渲染。宿主可配置 Controller、实体 Service 实现、约定 Service 和定时任务模板；实体 Service 接口仍由 LSI 确定性编译，不开放文本模板。
