package site.addzero.studio.server

import site.addzero.platform.web.Controller
import javax.sql.DataSource

/** 宿主提供给可选 Studio 功能的运行时边界。 */
data class StudioFeatureContext(
    val dataSource: DataSource,
    val schema: String,
    val classLoader: ClassLoader,
)

/** 可选功能对 Studio 宿主贡献的传输与迁移能力。 */
data class StudioFeatureContribution(
    val rootControllers: List<Controller> = emptyList(),
    val consoleApiControllers: List<Controller> = emptyList(),
    val schemaMigrations: List<StudioSchemaMigration> = emptyList(),
)

/** 由功能 JAR 通过依赖注入图贡献 Studio 能力。 */
fun interface StudioFeature {
    fun contribute(context: StudioFeatureContext): StudioFeatureContribution
}
