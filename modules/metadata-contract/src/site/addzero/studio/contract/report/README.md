# Report Contract

包含分页报表与原始工作簿保真保存、语义预览、变量绑定和自定义台账契约。

该功能包定义 JVM 与 Wasm 共用的报表文档、草稿资源和发布资源契约。

- `ReportDocument` 以 A4 页面、参数、数据集和布局行为单一文档边界。
- `ReportDatasetSpec` 只允许宿主模型和稳定 `operationId` 的 OpenAPI GET 数据源。
- `ReportBlockSpec` 通过唯一的 `kind` 字段区分文本、指标、表格、图表和图片块。
- 每个 `ReportRowSpec` 使用 12 列网格，表格必须独占一行。
- 参数绑定只允许报表参数或 JSON 字面量，字段定位统一使用 RFC 6901 JSON Pointer。
- 草稿与发布视图只暴露 `reportKey` 和修订号，不制造数据库中不存在的 id、时间或历史版本。

包内不定义存储、数据源执行或 HTTP 包络；这些责任由宿主和服务端实现。
