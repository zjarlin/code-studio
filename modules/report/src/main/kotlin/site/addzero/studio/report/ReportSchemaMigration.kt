package site.addzero.studio.report

import org.flywaydb.core.Flyway
import site.addzero.studio.server.DEFAULT_STUDIO_SCHEMA
import site.addzero.studio.server.StudioSchemaMigration
import javax.sql.DataSource

internal const val REPORT_SCHEMA_HISTORY: String = "code_studio_report_history"
internal const val REPORT_MIGRATION_LOCATION: String = "classpath:db/studio/report"

class ReportSchemaMigration(
    private val dataSource: DataSource,
    private val classLoader: ClassLoader = ReportSchemaMigration::class.java.classLoader,
    private val schema: String = DEFAULT_STUDIO_SCHEMA,
) : StudioSchemaMigration {
    init {
        require(POSTGRESQL_SCHEMA_NAME.matches(schema)) {
            "报表 schema 不是安全的 PostgreSQL 标识符: $schema"
        }
    }

    override fun migrate() {
        Flyway(configuration()).migrate()
    }

    internal fun configuration() = Flyway.configure(classLoader)
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .table(REPORT_SCHEMA_HISTORY)
        .locations(REPORT_MIGRATION_LOCATION)
        .placeholders(mapOf("studioSchema" to schema))
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .validateMigrationNaming(true)
        .validateOnMigrate(true)
        .failOnMissingLocations(true)
        .cleanDisabled(true)

    private companion object {
        val POSTGRESQL_SCHEMA_NAME = Regex("[a-z_][a-z0-9_]{0,62}")
    }
}
