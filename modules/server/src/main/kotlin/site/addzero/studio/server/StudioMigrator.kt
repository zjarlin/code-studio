package site.addzero.studio.server

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.Configuration
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import java.sql.Connection
import javax.sql.DataSource

const val DEFAULT_STUDIO_SCHEMA: String = "code_studio"
internal const val STUDIO_CORE_HISTORY: String = "code_studio_core_history"
internal const val STUDIO_METADATA_HISTORY: String = "code_studio_metadata_history"
internal const val STUDIO_CORE_MIGRATION_LOCATION: String = "classpath:db/studio/core"
private val POSTGRESQL_SCHEMA_NAME = Regex("[a-z_][a-z0-9_]{0,62}")

/** 在宿主数据源中协调 Studio 核心与模块元数据迁移。 */
class StudioMigrator(
    private val dataSource: DataSource,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
        ?: StudioMigrator::class.java.classLoader,
    private val schema: String = DEFAULT_STUDIO_SCHEMA,
) {
    init {
        require(POSTGRESQL_SCHEMA_NAME.matches(schema)) {
            "Studio schema 不是安全的 PostgreSQL 标识符: $schema"
        }
    }

    fun migrate(contributors: Iterable<MetadataContributor>) {
        val orderedContributors = MetadataContributors.resolve(contributors)
        Flyway(coreConfiguration()).migrate()
        register(orderedContributors)
        orderedContributors.forEach { contributor ->
            Flyway(metadataConfiguration(contributor)).migrate()
        }
    }

    internal fun metadataConfigurations(
        contributors: Iterable<MetadataContributor>,
    ): List<Configuration> = MetadataContributors.resolve(contributors).map(::metadataConfiguration)

    internal fun coreConfiguration(): Configuration = baseConfiguration()
        .table(STUDIO_CORE_HISTORY)
        .locations(STUDIO_CORE_MIGRATION_LOCATION)

    internal fun metadataConfiguration(contributor: MetadataContributor): Configuration = baseConfiguration(
        additionalPlaceholders = mapOf("contributorId" to contributor.id),
    )
        .table(STUDIO_METADATA_HISTORY)
        .locations(contributor.migrationLocation)
        .outOfOrder(true)
        .ignoreMigrationPatterns("*:missing", "*:future")
        .baselineOnMigrate(true)
        .baselineVersion("0")

    private fun baseConfiguration(
        additionalPlaceholders: Map<String, String> = emptyMap(),
    ) = Flyway.configure(classLoader)
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .placeholders(mapOf("studioSchema" to schema) + additionalPlaceholders)
        .validateMigrationNaming(true)
        .validateOnMigrate(true)
        .failOnMissingLocations(true)
        .cleanDisabled(true)

    private fun register(contributors: List<MetadataContributor>) {
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                upsertContributors(connection, contributors)
                connection.commit()
            } catch (cause: Throwable) {
                connection.rollback()
                throw cause
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }

    private fun upsertContributors(
        connection: Connection,
        contributors: List<MetadataContributor>,
    ) {
        val sql = """
            INSERT INTO $schema.metadata_contributor (id, format_version, migration_location)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                format_version = EXCLUDED.format_version,
                migration_location = EXCLUDED.migration_location
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            contributors.forEach { contributor ->
                statement.setString(1, contributor.id)
                statement.setInt(2, contributor.formatVersion)
                statement.setString(3, contributor.migrationLocation)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
