# Studio Contract

稳定的 Studio JSON 包络、宿主配置和功能命令。

`LsiCatalogEntry` 是约定文件扫描后的跨框架目录中间态。场景与路由使用 `routeKey`，界面元素使用 `elementKey`；父级、路径和类型只能由约定编译产物声明。`CatalogContributions` 负责确定性编解码、全局去重和父级引用校验。
