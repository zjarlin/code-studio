# Source generation

Kotlin Toolchain 插件从模块内固定的 `META-INF/code-studio/contributor.json` 读取稳定贡献者 ID，并从 `src/main/lowcode-metadata/metadata.json` 离线编译当前应用或库拥有的源码。插件不会扫描宿主仓库寻找其他模块。`generationTargetProfile` 指向 JSON 格式的 `GenerationTargetProfile`；生成结果中的宿主运行时符号必须由它显式覆盖。

Profile 的稳定语义键为 `runtime.persistence-model-package`、`runtime.lowcode-package`、`runtime.web-package`、`runtime.core-package`、`runtime.audit-principal` 和 `runtime.dictionary-annotation`。可选 Agent 扩展必须同时声明 `capabilities: ["agent"]`、`extension.agent-package` 和 `runtime.core-package`；未启用时不会生成 Agent 适配源码。

普通编译通过 `compileCodeStudioSources` 和 `generateCodeStudioSources` 写入任务输出目录，全程不创建数据源也不运行 Flyway。元数据变更后显式运行 `refreshCodeStudioMetadata`，重放核心迁移和当前 contributor 的 `manifest.requires` 传递闭包，再更新提交到 Git 的 canonical snapshot。源码 contributor 只从仓库级 `.code-studio/contributors.json` 读取；中央 contributor 由 Toolchain 独立 `Classpath` 解析并从 `resolvedFiles` 读取，不扫描仓库，也不自行实现 Maven 解析。相同 ID 同时出现在源码索引和中央 JAR 时直接失败。输入未变化时 Kotlin Toolchain 不会删除或重写任何生成输出。

若模型的业务 contributor 与实体源码 contributor 不同，模块必须在 `sourceMetadataSnapshots` 中显式列出包含该模型的 snapshot。该列表是 Kotlin Toolchain 任务输入：上游 snapshot 变更会精确使生成失效，但不会引入数据库连接或隐式全仓扫描。

索引格式固定为：

```json
{
  "formatVersion": 1,
  "contributors": {
    "example.library": "lib/example-library"
  }
}
```

`codeStudioSync` 是唯一写入宿主 Kotlin 源码树的入口，只用于首次创建可编辑 Controller、ServiceImpl 和约定文件：

```shell
./kotlin task ':module:codeStudioSync'
```

历史元数据 SQL 只从 `src/main/lowcode-metadata/db/studio/migration` 打包到 manifest 声明的 classpath location；原业务 `db/migration` 不会混入。每个 JAR 同时携带 `META-INF/code-studio/snapshots/<id>.json` 和 canonical `META-INF/code-studio/target-profile.json`。普通编译直接合并 JAR snapshot；refresh、候选迁移和 schema 校验先把中央迁移按排序后的安全相对路径解压到任务输出，中央 snapshot 始终只读。元数据数据库使用 `CODE_STUDIO_DB_*`；目标业务数据库使用 `LOWCODE_TARGET_DB_*`，两者不会隐式互换。
