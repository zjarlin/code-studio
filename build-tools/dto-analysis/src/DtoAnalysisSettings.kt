package site.addzero.toolchain.dto

import org.jetbrains.amper.plugins.Configurable
import java.nio.file.Path

/**
 * DTO 分析插件设置。
 */
@Configurable
interface DtoAnalysisSettings {
    /**
     * 当前模块需要分析的 JVM 编译产物目录。
     */
    val compiledArtifactsDirectories: List<Path>
}
