package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/**
 * 低代码源码生成插件设置。
 */
@Configurable
interface LowcodeSourceBuildSettings {
    /**
     * 生成目标运行时符号映射文件。
     */
    val generationTargetProfile: Path

    /**
     * Studio 核心控制面迁移目录。
     */
    val platformMigrationDirectory: Path

    /**
     * 仓库级 contributor ID 到模块相对路径的显式索引。
     * 当前模块由插件自动加入，依赖只按 manifest.requires 解析，不扫描仓库。
     */
    val contributorIndex: Path

    /**
     * 本地开发运行缺少显式环境变量时使用的 Studio 数据源配置。
     */
    val developmentDatabaseConfig: Path
}
