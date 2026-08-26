# Source generation

Amper 插件从模块内固定的 `META-INF/code-studio/contributor.json` 读取稳定贡献者 ID，并从 `src/main/lowcode-metadata/metadata.json` 离线编译当前应用或库拥有的源码。插件不会扫描宿主仓库寻找其他模块。`generationTargetProfile` 指向 JSON 格式的 `GenerationTargetProfile`；生成结果中的宿主运行时符号必须由它显式覆盖。

Profile 的稳定语义键为 `runtime.persistence-model-package`、`runtime.lowcode-package`、`runtime.web-package`、`runtime.core-package`、`runtime.audit-principal` 和 `runtime.dictionary-annotation`。可选 Agent 扩展必须同时声明 `capabilities: ["agent"]`、`extension.agent-package` 和 `runtime.core-package`；未启用时不会生成 Agent 适配源码。

普通编译通过 `compileCodeStudioSources` 和 `generateCodeStudioSources` 写入任务输出目录，全程不创建数据源也不运行 Flyway。元数据变更后显式运行 `refreshCodeStudioMetadata`，重放核心迁移和当前 contributor 的 `manifest.requires` 传递闭包，再更新提交到 Git 的 canonical snapshot。依赖位置只从仓库级 `.code-studio/contributors.json` 读取；索引内路径相对仓库根目录，插件不会扫描目录。输入未变化时 Amper 不会删除或重写任何生成输出。

索引格式固定为：

```json
{
  "formatVersion": 1,
  "contributors": {
    "example.library": "lib/example-library"
  }
}
```

`codeStudioSync` 是唯一写入宿主 Kotlin 源码树的入口，只用于首次创建可编辑 Controller 和 ServiceImpl：

```shell
./kotlin task ':module:codeStudioSync'
```

历史元数据 SQL 只从 `src/main/lowcode-metadata/db/studio/migration` 打包到 manifest 声明的 classpath location；原业务 `db/migration` 不会混入。相同的 target profile 会 canonical 打包为 `META-INF/code-studio/target-profile.json`，运行时发现多个资源时必须完全一致。数据库候选迁移和校验分别使用 `generateCodeStudioMigration`、`verifyCodeStudioSchema`。元数据数据库使用 `CODE_STUDIO_DB_*`；目标业务数据库使用 `LOWCODE_TARGET_DB_*`，两者不会隐式互换。
