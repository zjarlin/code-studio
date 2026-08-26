# Studio 运行时契约

本模块只定义宿主配置、访问策略、生成目标和元数据贡献清单契约。应用和库在 `META-INF/code-studio/contributor.json` 声明自己及显式依赖，运行时按依赖拓扑顺序返回贡献。

清单格式：

```json
{
  "formatVersion": 1,
  "id": "example-library",
  "migrationLocation": "classpath:db/studio/metadata/example-library",
  "requires": []
}
```

`id` 必须是小写稳定标识，迁移位置只能是 `classpath:db/studio/metadata/<id>`，清单不接受其他字段。`MetadataContributors.uniqueRoot` 用于从已校验的 classpath 依赖闭包中确定唯一应用根。
