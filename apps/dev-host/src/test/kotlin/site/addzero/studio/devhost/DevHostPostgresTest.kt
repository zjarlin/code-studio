package site.addzero.studio.devhost

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import site.addzero.studio.server.StudioMigrator
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource

class DevHostPostgresTest {
    @Test
    fun `库依赖闭包的自治 baseline 可以共享 metadata history`() {
        val workspaceValue = System.getenv("CODE_STUDIO_TEST_WORKSPACE")
        val moduleValues = System.getenv("CODE_STUDIO_TEST_MODULES")
        val jdbcUrl = System.getenv("CODE_STUDIO_TEST_DB_JDBC_URL")
        val username = System.getenv("CODE_STUDIO_TEST_DB_USERNAME")
        val password = System.getenv("CODE_STUDIO_TEST_DB_PASSWORD")
        assumeTrue(!workspaceValue.isNullOrBlank(), "未配置宿主工作区")
        assumeTrue(!moduleValues.isNullOrBlank(), "未配置需验证的库模块")
        assumeTrue(!jdbcUrl.isNullOrBlank(), "未配置 PostgreSQL 集成测试数据源")
        assumeTrue(!username.isNullOrBlank(), "未配置 PostgreSQL 集成测试用户")
        assumeTrue(password != null, "未配置 PostgreSQL 集成测试密码")

        val workspace = Path.of(workspaceValue).toRealPath()
        val modules = moduleValues.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(workspace::resolve)
        assumeTrue(modules.isNotEmpty(), "未配置需验证的库模块")

        val schema = "code_studio_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val dataSource = createTestDataSource(jdbcUrl, username, password)
        val expectedContributorIds = linkedSetOf<String>()
        try {
            modules.forEach { module ->
                DevHostModule.load(workspace, module).use { loaded ->
                    val contributors = MetadataContributors.load(loaded.classLoader)
                    assertEquals(loaded.contributor.id, MetadataContributors.uniqueRoot(contributors).id)
                    expectedContributorIds += contributors.map(MetadataContributor::id)
                    StudioMigrator(dataSource, loaded.classLoader, schema).migrate(contributors)
                }
            }

            assertEquals(expectedContributorIds, registeredContributorIds(dataSource, schema))
            assertTrue(appliedMetadataMigrationCount(dataSource, schema) >= expectedContributorIds.size)
        } finally {
            runCatching {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
                    }
                }
            }
            dataSource.close()
        }
    }

    private fun registeredContributorIds(
        dataSource: DataSource,
        schema: String,
    ): Set<String> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM $schema.metadata_contributor ORDER BY id").use { rows ->
                buildSet {
                    while (rows.next()) {
                        add(rows.getString(1))
                    }
                }
            }
        }
    }

    private fun appliedMetadataMigrationCount(
        dataSource: DataSource,
        schema: String,
    ): Int = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM $schema.code_studio_metadata_history WHERE success AND type = 'SQL'",
            ).use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
    }
}

private fun createTestDataSource(
    jdbcUrl: String,
    username: String,
    password: String,
): HikariDataSource {
    val config = HikariConfig()
    config.jdbcUrl = jdbcUrl
    config.username = username
    config.password = password
    config.maximumPoolSize = 2
    config.minimumIdle = 0
    config.poolName = "studio-dev-host-test"
    return HikariDataSource(config)
}
