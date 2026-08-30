# 源码模板

这些模板控制 `codeStudioSync` 首次物化的可编辑脚手架：Controller、实体 Service 实现、约定 Service 和定时任务。

所有模板必须以 `{{header}}` 开头。`{{header}}` 由编译器提供生成标记、元数据签名、package 和动态 imports；可按需保留 `{{documentation}}`。

| 文件 | 必需变量 |
| --- | --- |
| `controller.kt.tpl` | `className`、`serviceName`、`controllerTypes`、`routeKey` |
| `service-implementation.kt.tpl` | `className`、`implementationType`、`entityName`、`serviceName` |
| `service.kt.tpl` | `className` |
| `scheduled-job.kt.tpl` | `className` |

模板只影响尚未物化的文件。已经进入源码树并由业务代码维护的文件不会被覆盖。
