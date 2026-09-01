# React版studio(主)

React/TanStack Start 管理界面。导航和界面操作由 `/console/api/catalog` 元数据渲染，生产构建不使用前端默认目录。

开发命令：

```shell
pnpm dev
pnpm test
pnpm build
```

`pnpm generate:api` 先从后端共享传输模型刷新 OpenAPI，再生成 TanStack Query + Fetch 客户端。所有生成物位于 `dist/generated`，源码目录不保留手写接口路径。
