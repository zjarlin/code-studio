# 库开发宿主

本应用只用于打开没有启动类的库元数据，不应注册到消费仓库的 `project.yaml`。CLI 使用固定入口：

```shell
./kotlin run --module=dev-host -- \
  --workspace /path/to/workspace \
  --module lib/example-library
```

数据库连接优先读取 `CODE_STUDIO_DB_JDBC_URL`、`CODE_STUDIO_DB_USERNAME`、`CODE_STUDIO_DB_PASSWORD`，否则读取 `<workspace>/.code-studio/local.yaml` 的 `database.url/username/password`。端口通过 `CODE_STUDIO_PORT` 或 `server.port` 设置，默认为 `8080`。

工作区必须提供版本化的 `<workspace>/.code-studio/target-profile.json`。开发宿主使用与构建期相同的严格解码器加载该文件，并将宿主符号映射传给预览和下载编译器；缺失、未知字段或非法符号会直接阻止启动。

宿主只监听 `127.0.0.1`，并将所选贡献存入 `code_studio_dev_<contributorId>` schema。
启动时只解析所选 contributor 的传递 `requires`，优先将这些模块的 `src/main/lowcode-metadata/db/studio/migration` 复制到系统临时 classpath 的标准位置；仅在自治迁移不存在时兼容旧 `db/migration`。进程关闭时自动删除临时目录，不会修改消费仓库。
