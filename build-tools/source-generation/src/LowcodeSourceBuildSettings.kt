package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.Classpath
import java.nio.file.Path

/**
 * 低代码源码生成插件设置。
 */
@Configurable
interface LowcodeSourceBuildSettings {
    /**
     * 由 Toolchain 独立解析的中央 contributor 制品，禁止包含当前源码模块。
     */
    val contributorClasspath: Classpath

    /**
     * 生成目标运行时符号映射文件。
     */
    val generationTargetProfile: Path

    /**
     * Controller、Service 和定时任务的可编辑源码模板目录。
     */
    val sourceTemplateDirectory: Path

    /**
     * 显式提供由当前 contributor 产出实体源码的外部模型快照。
     */
    val sourceMetadataSnapshots: List<Path>

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
