# UI

React 界面的公共源码包。`shadcn add` 只允许在本包执行，上游原件统一生成到 `src/components/generated/shadcn`，业务包通过 `@platform/ui` 的导出子路径引用。

从 `studio` 目录添加组件：

```shell
pnpm --dir packages/ui shadcn add <component>
```
