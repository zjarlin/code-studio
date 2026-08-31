package site.addzero.studio.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_FORMAT_VERSION
import site.addzero.studio.runtime.MetadataContributor
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger
import javax.sql.DataSource

class StudioMigratorTest {
    private val migrator = StudioMigrator(UnconnectedDataSource())

    @Test
    fun `核心 Flyway 使用固定 schema 和独立历史表`() {
        val configuration = migrator.coreConfiguration()

        assertEquals(DEFAULT_STUDIO_SCHEMA, configuration.defaultSchema)
        assertEquals(listOf(DEFAULT_STUDIO_SCHEMA), configuration.schemas.toList())
        assertEquals(STUDIO_CORE_HISTORY, configuration.table)
        assertEquals(
            listOf(STUDIO_CORE_MIGRATION_LOCATION),
            configuration.locations.map { location -> location.descriptor },
        )
        CORE_MIGRATIONS.forEach { migration ->
            assertNotNull(javaClass.classLoader.getResource("db/studio/core/$migration"))
        }
    }

    @Test
    fun `元数据 Flyway 使用独立历史表和拓扑迁移位置`() {
        val contributor = contributor("feature", requires = listOf("core"))

        val configuration = migrator.metadataConfiguration(contributor)

        assertEquals(DEFAULT_STUDIO_SCHEMA, configuration.defaultSchema)
        assertEquals(STUDIO_METADATA_HISTORY, configuration.table)
        assertTrue(configuration.isOutOfOrder)
        assertTrue(configuration.isBaselineOnMigrate)
        assertEquals("0", configuration.baselineVersion.version)
        assertEquals(
            listOf("classpath:db/studio/metadata/feature"),
            configuration.locations.map { location -> location.descriptor },
        )
        assertEquals("feature", configuration.placeholders["contributorId"])
        assertEquals(
            listOf("*:missing", "*:future"),
            configuration.ignoreMigrationPatterns.map { pattern -> pattern.toString() },
        )
    }

    @Test
    fun `元数据迁移按依赖拓扑隔离 location 且共用历史表`() {
        val configurations = migrator.metadataConfigurations(
            listOf(
                contributor("application", requires = listOf("feature")),
                contributor("feature", requires = listOf("core")),
                contributor("core"),
            ),
        )

        assertEquals(
            listOf("core", "feature", "application"),
            configurations.map { configuration -> configuration.placeholders.getValue("contributorId") },
        )
        assertTrue(configurations.all { configuration -> configuration.table == STUDIO_METADATA_HISTORY })
        assertEquals(
            listOf(
                listOf("classpath:db/studio/metadata/core"),
                listOf("classpath:db/studio/metadata/feature"),
                listOf("classpath:db/studio/metadata/application"),
            ),
            configurations.map { configuration ->
                configuration.locations.map { location -> location.descriptor }
            },
        )
    }

    @Test
    fun `库开发宿主只能使用安全的隔离 schema`() {
        val schema = "code_studio_dev_example_library"
        val isolatedMigrator = StudioMigrator(UnconnectedDataSource(), schema = schema)

        val configuration = isolatedMigrator.coreConfiguration()

        assertEquals(schema, configuration.defaultSchema)
        assertEquals(schema, configuration.placeholders["studioSchema"])
        assertThrows(IllegalArgumentException::class.java) {
            StudioMigrator(UnconnectedDataSource(), schema = "code_studio;DROP SCHEMA public")
        }
    }

    @Test
    fun `核心 DDL 只创建 normalized 元数据与 contributor 归属`() {
        val definitionSql = coreMigration(CORE_MIGRATIONS[0])
        val modelSql = coreMigration(CORE_MIGRATIONS[1])
        val contractSql = coreMigration(CORE_MIGRATIONS[2])
        val conventionFileSql = coreMigration(CORE_MIGRATIONS[3])
        val catalogSql = coreMigration(CORE_MIGRATIONS[4])
        val reportSql = coreMigration(CORE_MIGRATIONS[5])
        val allSql = listOf(definitionSql, modelSql, contractSql, conventionFileSql, catalogSql, reportSql)
            .joinToString("\n")

        assertTrue(definitionSql.contains("lowcode_definition"))
        assertTrue(definitionSql.contains("library_feature"))
        assertFalse(definitionSql.contains("lowcode_model"))
        assertTrue(modelSql.contains("lowcode_model"))
        assertTrue(modelSql.contains("lowcode_dictionary"))
        assertTrue(modelSql.contains("lowcode_constant_group"))
        assertTrue(modelSql.contains("target_model_id BIGINT,"))
        assertFalse(modelSql.contains("target_model_id BIGINT REFERENCES"))
        assertFalse(modelSql.contains("lowcode_dto"))
        assertTrue(contractSql.contains("lowcode_dto"))
        assertTrue(contractSql.contains("lowcode_api_contract"))
        assertTrue(contractSql.contains("lowcode_route_binding"))
        assertTrue(contractSql.contains("lowcode_runtime_contract"))
        assertTrue(contractSql.contains("lowcode_structure_analysis_snapshot"))
        assertTrue(contractSql.contains("agent_exposure JSONB NOT NULL DEFAULT '{\"operations\":{}}'::JSONB"))
        assertTrue(contractSql.contains("contributor_id"))
        assertTrue(conventionFileSql.contains("convention_file"))
        assertTrue(conventionFileSql.contains("'SERVICE', 'SCHEDULED_JOB'"))
        assertTrue(catalogSql.contains("catalog_route_override"))
        assertTrue(catalogSql.contains("catalog_element_override"))
        assertFalse(catalogSql.contains("path TEXT"))
        assertFalse(catalogSql.contains("parent_key"))
        assertTrue(reportSql.contains("report_definition"))
        assertTrue(reportSql.contains("draft_document JSONB NOT NULL"))
        assertTrue(reportSql.contains("published_document JSONB"))
        assertEquals(1, Regex("CREATE TABLE").findAll(reportSql).count())
        assertFalse(reportSql.contains("report_publication"))
        assertFalse(reportSql.contains("tenant_id"))
        assertFalse(reportSql.contains("owner"))
        assertFalse(reportSql.contains("create_time"))
        assertEquals(
            15,
            Regex("id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY").findAll(allSql).count(),
        )
        assertFalse(allSql.contains("metadata_document"))
        assertFalse(allSql.contains("application_definition"))
        assertFalse(allSql.contains("target_module"))
        assertFalse(allSql.contains("gateway"))
        assertFalse(Regex("\\bagent\\b").containsMatchIn(allSql))
    }

    private fun coreMigration(fileName: String): String {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/studio/core/$fileName"))
        return resource.readText()
    }

    private fun contributor(
        id: String,
        requires: List<String> = emptyList(),
    ): MetadataContributor = MetadataContributor(
        formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
        id = id,
        migrationLocation = "classpath:db/studio/metadata/$id",
        requires = requires,
    )
}

private val CORE_MIGRATIONS = listOf(
    "V1__create_definition_catalog.sql",
    "V2__create_model_catalog.sql",
    "V3__create_contract_catalog.sql",
    "V4__create_convention_file_catalog.sql",
    "V5__create_catalog_overrides.sql",
    "V6__create_report_definition.sql",
)

internal class UnconnectedDataSource : DataSource {
    override fun getConnection(): Connection = error("配置测试不应连接数据库")

    override fun getConnection(username: String?, password: String?): Connection =
        error("配置测试不应连接数据库")

    override fun getLogWriter(): PrintWriter? = null

    override fun setLogWriter(out: PrintWriter?) = Unit

    override fun setLoginTimeout(seconds: Int) = Unit

    override fun getLoginTimeout(): Int = 0

    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()

    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
