# DTO analysis

Amper 插件只分析配置中显式声明的当前模块 JVM 编译产物、源码目录和 `src/main/lowcode-metadata/metadata.json` canonical snapshot，不扫描宿主仓库，也不连接数据库或运行 Flyway。

`analyzeDtoModels` 输出 DTO 结构复用候选；`analyzeBusinessSourceOwnership` 输出源码归属报告；`verifyBusinessSourceOwnership` 在当前模块存在未归属源码时失败。所有任务启用执行避让，输入只包含 contributor manifest、canonical snapshot、当前模块源码或编译产物。
