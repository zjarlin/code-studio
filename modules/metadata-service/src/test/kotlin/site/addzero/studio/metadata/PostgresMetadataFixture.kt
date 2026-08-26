package site.addzero.studio.metadata

import org.junit.jupiter.api.Assumptions.assumeTrue
import site.addzero.dto.compiler.DtoAnalysisSnapshot
import site.addzero.dto.compiler.DtoStructureAnalyzer
import site.addzero.dto.compiler.DtoStructureOrigin
import site.addzero.dto.compiler.LsiDataStructure
import site.addzero.dto.compiler.LsiDtoProperty
import site.addzero.dto.compiler.LsiDtoType
import site.addzero.dto.compiler.dtoStructureFingerprint
import site.addzero.platform.lowcode.generator.GenerationTargetSymbols
import site.addzero.studio.runtime.GenerationTargetProfile
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_FORMAT_VERSION
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.server.StudioMigrator
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLFeatureNotSupportedException
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource

internal const val EDITABLE_ID = "editable-library"
private const val DEPENDENCY_ID = "dependency-library"

internal data class PostgresFixture(
    val dependencyLibraryId: Long,
    val dependencyFeatureId: Long,
    val editableLibraryId: Long,
    val editableFeatureId: Long,
    val controller: StudioMetadataController,
)

internal fun withPostgresFixture(resources: Path, block: (PostgresFixture) -> Unit) {
    val jdbcUrl = System.getenv("CODE_STUDIO_TEST_DB_JDBC_URL")
    val username = System.getenv("CODE_STUDIO_TEST_DB_USERNAME")
    val password = System.getenv("CODE_STUDIO_TEST_DB_PASSWORD")
    assumeTrue(!jdbcUrl.isNullOrBlank(), "未配置 PostgreSQL 集成测试数据源")
    assumeTrue(!username.isNullOrBlank(), "未配置 PostgreSQL 集成测试用户")
    assumeTrue(password != null, "未配置 PostgreSQL 集成测试密码")

    val dataSource = DriverManagerDataSource(jdbcUrl, username, password)
    val schema = "code_studio_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    writeContributorMigration(resources, DEPENDENCY_ID, "V10__dependency.sql", "example.dependency")
    writeContributorMigration(resources, EDITABLE_ID, "V20__editable.sql", "example.editable")
    URLClassLoader(arrayOf(resources.toUri().toURL()), StudioMetadataControllerTest::class.java.classLoader).use {
        classLoader ->
        try {
            val dependency = contributor(DEPENDENCY_ID)
            val editable = contributor(EDITABLE_ID, listOf(DEPENDENCY_ID))
            StudioMigrator(dataSource, classLoader, schema).migrate(listOf(dependency, editable))
            insertAnalysisSnapshot(dataSource, schema)
            val fixture = PostgresFixture(
                dependencyLibraryId = definitionId(dataSource, schema, DEPENDENCY_ID),
                dependencyFeatureId = featureId(dataSource, schema, DEPENDENCY_ID),
                editableLibraryId = definitionId(dataSource, schema, EDITABLE_ID),
                editableFeatureId = featureId(dataSource, schema, EDITABLE_ID),
                controller = StudioMetadataController(dataSource, schema, EDITABLE_ID, testTargetProfile()),
            )
            block(fixture)
        } finally {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
                }
            }
        }
    }
}

internal fun testTargetProfile(): GenerationTargetProfile = GenerationTargetProfile(
    id = "metadata-service-test",
    symbols = GenerationTargetSymbols.keys.associateWith { key ->
        "example.runtime.${key.replace(Regex("[^A-Za-z0-9_]"), "_")}"
    },
)

private fun insertAnalysisSnapshot(dataSource: DataSource, schema: String) {
    val sourceStructures = listOf(
        LsiDataStructure(
            qualifiedName = "example.SourceCatalogView",
            properties = listOf(
                LsiDtoProperty("title", LsiDtoType.STRING, "Title"),
                LsiDtoProperty("status", LsiDtoType.STRING, "Status"),
            ),
            origins = setOf(DtoStructureOrigin.SOURCE),
        ),
    )
    val metadataStructures = emptyList<LsiDataStructure>()
    val snapshot = DtoAnalysisSnapshot(
        sourceFingerprint = sourceStructures.dtoStructureFingerprint(),
        metadataFingerprint = metadataStructures.dtoStructureFingerprint(),
        generatedAtEpochMillis = 1_787_210_540_478,
        sourceStructures = sourceStructures,
        metadataStructures = metadataStructures,
        report = DtoStructureAnalyzer.analyze(sourceStructures),
    )
    val sql = """
        INSERT INTO $schema.lowcode_structure_analysis_snapshot
            (id, schema_version, source_fingerprint, metadata_fingerprint, generated_at_epoch_millis, report)
        VALUES (1, ?, ?, ?, ?, CAST(? AS JSONB))
    """.trimIndent()
    dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, snapshot.schemaVersion)
            statement.setString(2, snapshot.sourceFingerprint)
            statement.setString(3, snapshot.metadataFingerprint)
            statement.setLong(4, snapshot.generatedAtEpochMillis)
            statement.setString(5, jacksonObjectMapper().writeValueAsString(snapshot))
            statement.executeUpdate()
        }
    }
}

private fun writeContributorMigration(
    resources: Path,
    contributorId: String,
    fileName: String,
    packagePrefix: String,
) {
    val migration = resources.resolve("db/studio/metadata/$contributorId/$fileName")
    Files.createDirectories(migration.parent)
    Files.writeString(
        migration,
        """
        INSERT INTO ${'$'}{studioSchema}.lowcode_definition
            (code, display_name, version, status, definition_type)
        VALUES ('${'$'}{contributorId}', '${contributorId.replace('-', ' ')}', 1, 1, 'LIBRARY');

        INSERT INTO ${'$'}{studioSchema}.library_definition (id, spec)
        SELECT id, '{
          "schemaVersion": 3,
          "contributorId": "${'$'}{contributorId}",
          "packagePrefix": "$packagePrefix",
          "scanPackage": "$packagePrefix",
          "kind": "BUSINESS",
          "runtimeDependencies": [],
          "supportedIdentityModes": ["LOCAL"],
          "applicationSelectable": true,
          "dataScope": {"tenantScoped": false, "userScoped": false, "departmentScoped": false}
        }'::JSONB
        FROM ${'$'}{studioSchema}.lowcode_definition
        WHERE code = '${'$'}{contributorId}';

        INSERT INTO ${'$'}{studioSchema}.library_feature
            (library_id, parent_id, feature_code, name, description)
        SELECT id, NULL, 'root', 'Root', 'Root feature'
        FROM ${'$'}{studioSchema}.lowcode_definition
        WHERE code = '${'$'}{contributorId}';
        """.trimIndent(),
    )
}

private fun contributor(id: String, requires: List<String> = emptyList()): MetadataContributor = MetadataContributor(
    formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
    id = id,
    migrationLocation = "classpath:db/studio/metadata/$id",
    requires = requires,
)

private fun definitionId(dataSource: DataSource, schema: String, contributorId: String): Long =
    dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT id FROM $schema.lowcode_definition WHERE code = ?").use { statement ->
            statement.setString(1, contributorId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getLong(1)
            }
        }
    }

private fun featureId(dataSource: DataSource, schema: String, contributorId: String): Long =
    dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT feature.id
            FROM $schema.library_feature feature
            INNER JOIN $schema.lowcode_definition definition ON definition.id = feature.library_id
            WHERE definition.code = ? AND feature.feature_code = 'root'
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, contributorId)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getLong(1)
            }
        }
    }

private class DriverManagerDataSource(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
) : DataSource {
    override fun getConnection(): Connection = DriverManager.getConnection(jdbcUrl, username, password)

    override fun getConnection(username: String, password: String): Connection =
        DriverManager.getConnection(jdbcUrl, username, password)

    override fun getLogWriter(): PrintWriter? = DriverManager.getLogWriter()

    override fun setLogWriter(out: PrintWriter?) = DriverManager.setLogWriter(out)

    override fun setLoginTimeout(seconds: Int) = DriverManager.setLoginTimeout(seconds)

    override fun getLoginTimeout(): Int = DriverManager.getLoginTimeout()

    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()

    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()

    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}

internal class UnconnectedDataSource : DataSource {
    override fun getConnection(): Connection = error("schema 校验不应连接数据库")
    override fun getConnection(username: String?, password: String?): Connection = error("schema 校验不应连接数据库")
    override fun getLogWriter(): PrintWriter? = null
    override fun setLogWriter(out: PrintWriter?) = Unit
    override fun setLoginTimeout(seconds: Int) = Unit
    override fun getLoginTimeout(): Int = 0
    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()
    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
