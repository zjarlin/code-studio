package site.addzero.studio.devhost

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import site.addzero.studio.metadata.StudioMetadataController
import site.addzero.studio.runtime.StudioAccessPolicy
import site.addzero.studio.runtime.StudioConfig
import site.addzero.studio.server.installStudio

fun main(arguments: Array<String>) {
    val arguments = DevHostArguments.parse(arguments)
    val localConfig = DevHostConfigLoader.load(arguments.workspace)
    DevHostModule.load(arguments.workspace, arguments.module).use { module ->
        val schema = devSchema(module.contributor.id)
        validateRequestedSchema(schema, System.getenv())
        createDataSource(localConfig).use { dataSource ->
            embeddedServer(
                factory = Netty,
                host = "127.0.0.1",
                port = localConfig.port,
            ) {
                install(ContentNegotiation) {
                    jackson()
                }
                val studioConfig = StudioConfig(
                    contributorId = module.contributor.id,
                    apiBaseUrl = "/",
                    displayName = module.contributor.id,
                    openApiPath = "/v3/api-docs",
                    capabilities = setOf("metadata"),
                    enabled = true,
                )
                val accessPolicy = StudioAccessPolicy { true }
                val metadataController = StudioMetadataController(
                    dataSource = dataSource,
                    schema = schema,
                    editableContributorId = module.contributor.id,
                    targetProfile = localConfig.targetProfile,
                )
                installStudio(
                    dataSource = dataSource,
                    config = studioConfig,
                    accessPolicy = accessPolicy,
                    classLoader = module.classLoader,
                    metadataSchema = schema,
                    apiControllers = listOf(metadataController),
                )
            }.start(wait = true)
        }
    }
}

private fun createDataSource(config: DevHostConfig): HikariDataSource {
    val hikariConfig = HikariConfig()
    hikariConfig.jdbcUrl = config.database.jdbcUrl
    hikariConfig.username = config.database.username
    hikariConfig.password = config.database.password
    hikariConfig.maximumPoolSize = config.database.maximumPoolSize
    hikariConfig.minimumIdle = 0
    hikariConfig.poolName = "studio-dev-host"
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
