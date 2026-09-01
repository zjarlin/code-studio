package site.addzero.studio.development

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.koin.core.annotation.KoinApplication
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.plugin.module.dsl.withConfiguration
import site.addzero.studio.metadata.StudioMetadataController
import site.addzero.studio.runtime.GenerationTargetProfiles
import site.addzero.studio.runtime.MetadataContributors
import site.addzero.studio.runtime.StudioAccessPolicy
import site.addzero.studio.runtime.StudioConfig
import site.addzero.studio.runtime.StudioPermissionPolicy
import site.addzero.studio.server.DEFAULT_STUDIO_SCHEMA
import site.addzero.studio.server.StudioFeature
import site.addzero.studio.server.installStudio
import java.nio.file.Path

@KoinApplication
class DevelopmentHostGraph

/** 运行应用初始壳的开发 Studio 宿主。 */
fun runApplicationDevelopmentHost() {
    val workspace = Path.of("").toAbsolutePath().normalize()
    val classLoader = Thread.currentThread().contextClassLoader
        ?: DevelopmentHostConfigLoader::class.java.classLoader
    val contributors = MetadataContributors.load(classLoader)
    val contributor = MetadataContributors.uniqueRoot(contributors)
    val targetProfile = GenerationTargetProfiles.load(classLoader)
    val config = DevelopmentHostConfigLoader.load(
        workspace = workspace,
        targetProfile = targetProfile,
    )
    runDevelopmentHost(
        contributorId = contributor.id,
        classLoader = classLoader,
        schema = DEFAULT_STUDIO_SCHEMA,
        config = config,
    )
}

/** 为无启动类的 library 运行隔离的开发 Studio 宿主。 */
fun runLibraryDevelopmentHost(arguments: Array<String>) {
    val parsedArguments = DevelopmentHostArguments.parse(arguments)
    val config = DevelopmentHostConfigLoader.load(parsedArguments.workspace)
    DevelopmentModule.load(parsedArguments.workspace, parsedArguments.module).use { module ->
        val schema = developmentSchema(module.contributor.id)
        validateRequestedSchema(schema, System.getenv())
        runDevelopmentHost(
            contributorId = module.contributor.id,
            classLoader = module.classLoader,
            schema = schema,
            config = config,
        )
    }
}

private fun runDevelopmentHost(
    contributorId: String,
    classLoader: ClassLoader,
    schema: String,
    config: DevelopmentHostConfig,
) {
    createDataSource(config).use { dataSource ->
        embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = config.port,
        ) {
            install(Koin) {
                withConfiguration<DevelopmentHostGraph>()
            }
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                DevelopmentApiController.install(this)
            }
            val studioConfig = developmentStudioConfig(contributorId)
            val metadataController = StudioMetadataController(
                dataSource = dataSource,
                schema = schema,
                editableContributorId = contributorId,
                targetProfile = config.targetProfile,
            )
            val features = getKoin().getAll<StudioFeature>()
            installStudio(
                dataSource = dataSource,
                config = studioConfig,
                accessPolicy = StudioAccessPolicy { true },
                classLoader = classLoader,
                metadataSchema = schema,
                apiControllers = listOf(metadataController),
                permissionPolicy = StudioPermissionPolicy { _, _ -> true },
                features = features,
            )
        }.start(wait = true)
    }
}

internal fun developmentStudioConfig(contributorId: String): StudioConfig = StudioConfig(
    contributorId = contributorId,
    apiBaseUrl = "/",
    displayName = contributorId,
    openApiPath = "/v3/api-docs",
    capabilities = setOf("metadata", "api"),
    enabled = true,
)

private fun createDataSource(config: DevelopmentHostConfig): HikariDataSource {
    val hikariConfig = HikariConfig()
    hikariConfig.jdbcUrl = config.database.jdbcUrl
    hikariConfig.username = config.database.username
    hikariConfig.password = config.database.password
    hikariConfig.maximumPoolSize = config.database.maximumPoolSize
    hikariConfig.minimumIdle = 0
    hikariConfig.poolName = "studio-development-host"
    return HikariDataSource(hikariConfig)
}

internal fun validateRequestedSchema(
    expectedSchema: String,
    environment: Map<String, String>,
) {
    val requestedSchema = environment["CODE_STUDIO_SCHEMA"]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return
    require(requestedSchema == expectedSchema) {
        "CODE_STUDIO_SCHEMA 必须等于由 contributorId 推导的 schema: $expectedSchema"
    }
}
