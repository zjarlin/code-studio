# API Docs

宿主 OpenAPI 契约的浏览与同源调试工作台。

- `catalog.ts` 通过生成客户端读取宿主配置和 OpenAPI 文档，并投影为共享核心模型。
- `operation-tree.tsx` 负责业务端点过滤、分组检索和历史定位。
- `request-panel.tsx` 负责参数、请求体、表单、文件和发送编排。
- `documentation-panel.tsx` 负责参数、Schema、权限和响应契约展示。
- `response-panel.tsx` 负责响应正文、响应头、cURL 和二进制下载。
- `auth-dialog.tsx` 只维护当前页面会话的临时 Bearer Token。
- `session.ts` 管理不持久化的请求草稿、响应和隐私历史元数据。
