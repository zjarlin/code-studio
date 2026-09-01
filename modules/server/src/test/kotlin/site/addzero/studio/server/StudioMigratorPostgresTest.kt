package site.addzero.studio.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.server.catalog.JdbcCatalogOverrideReader
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_FORMAT_VERSION
import site.addzero.studio.runtime.MetadataContributor
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

class StudioMigratorPostgresTest {
    @Test
    fun `核心与 contributor DDL 在真实 PostgreSQL 中使用两张历史表`(@TempDir resources: Path) {
        val jdbcUrl = System.getenv("CODE_STUDIO_TEST_DB_JDBC_URL")
        val username = System.getenv("CODE_STUDIO_TEST_DB_USERNAME")
        val password = System.getenv("CODE_STUDIO_TEST_DB_PASSWORD")
        assumeTrue(!jdbcUrl.isNullOrBlank(), "未配置 PostgreSQL 集成测试数据源")
        assumeTrue(!username.isNullOrBlank(), "未配置 PostgreSQL 集成测试用户")
        assumeTrue(password != null, "未配置 PostgreSQL 集成测试密码")

        val schema = "code_studio_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val dataSource = DriverManagerDataSource(jdbcUrl, username, password)
        val dependency = MetadataContributor(
            formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
            id = "integration-dependency",
            migrationLocation = "classpath:db/studio/metadata/integration-dependency",
        )
        val contributor = MetadataContributor(
            formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
            id = "integration-test",
            migrationLocation = "classpath:db/studio/metadata/integration-test",
            requires = listOf(dependency.id),
        )
        val dependencyMigration = resources.resolve(
            "db/studio/metadata/integration-dependency/V20260826_000001__probe.sql",
        )
        Files.createDirectories(dependencyMigration.parent)
        Files.writeString(
            dependencyMigration,
            """
            CREATE TABLE ${'$'}{studioSchema}.integration_probe (
                id INTEGER PRIMARY KEY,
                contributor_id TEXT NOT NULL
            );
            INSERT INTO ${'$'}{studioSchema}.integration_probe (id, contributor_id)
            VALUES (1, '${'$'}{contributorId}');
            """.trimIndent(),
        )
        val contributorMigration = resources.resolve(
            "db/studio/metadata/integration-test/V20260826_000002__probe.sql",
        )
        Files.createDirectories(contributorMigration.parent)
        Files.writeString(
            contributorMigration,
            """
            INSERT INTO ${'$'}{studioSchema}.integration_probe (id, contributor_id)
            VALUES (2, '${'$'}{contributorId}');
            """.trimIndent(),
        )

        URLClassLoader(arrayOf(resources.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            try {
                val migrator = StudioMigrator(dataSource, classLoader, schema)
                val contributors = listOf(contributor, dependency)
                migrator.migrate(contributors)
                migrator.migrate(contributors)

                val tables = dataSource.connection.use { connection ->
                    connection.prepareStatement(
                        "SELECT tablename FROM pg_tables WHERE schemaname = ? ORDER BY tablename",
                    ).use { statement ->
                        statement.setString(1, schema)
                        statement.executeQuery().use { rows ->
                            buildSet {
                                while (rows.next()) {
                                    add(rows.getString(1))
                                }
                            }
                        }
                    }
                }
                assertTrue("integration_probe" in tables)
                assertTrue(STUDIO_CORE_HISTORY in tables)
                assertTrue(STUDIO_METADATA_HISTORY in tables)
                assertTrue("catalog_route_override" in tables)
                assertTrue("catalog_element_override" in tables)
                assertTrue("report_definition" in tables)
                assertTrue(JdbcCatalogOverrideReader(dataSource, schema).read().routes.isEmpty())
                assertEquals(6, appliedMigrationCount(dataSource, schema, STUDIO_CORE_HISTORY))
                assertEquals(2, appliedMigrationCount(dataSource, schema, STUDIO_METADATA_HISTORY))
                assertEquals(
                    setOf("integration-dependency", "integration-test"),
                    probeContributorIds(dataSource, schema),
                )
                assertTrue(insertDefinitionWithGeneratedId(dataSource, schema) > 0)
            } finally {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
                    }
                }
            }
        }
    }

    private fun appliedMigrationCount(
        dataSource: DataSource,
        schema: String,
        historyTable: String,
    ): Int = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM $schema.$historyTable WHERE success AND type = 'SQL'",
            ).use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
    }

    private fun probeContributorIds(
        dataSource: DataSource,
        schema: String,
    ): Set<String> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT contributor_id FROM $schema.integration_probe ORDER BY id",
            ).use { rows ->
                buildSet {
                    while (rows.next()) {
                        add(rows.getString(1))
                    }
                }
            }
        }
    }

    private fun insertDefinitionWithGeneratedId(
        dataSource: DataSource,
        schema: String,
    ): Long = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "INSERT INTO $schema.lowcode_definition (code, display_name) VALUES (?, ?) RETURNING id",
        ).use { statement ->
            statement.setString(1, "integration-test")
            statement.setString(2, "Integration Test")
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getLong(1)
            }
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
