# Studio UI Build

同时构建 Vue Studio 和 React Console。构建器扫描 `console/src/**/catalog.convention.json`，通过共用契约合并、校验和排序后，打包为 `META-INF/code-studio/catalog.json`。该资源是后端目录、数据库覆盖和权限过滤的唯一结构来源。
