package site.addzero.studio.report

import org.junit.jupiter.api.Assumptions.assumeTrue
import site.addzero.studio.server.StudioMigrator
import java.io.PrintWriter
import java.sql.DriverManager
import java.sql.SQLFeatureNotSupportedException
import java.util.UUID
import java.util.logging.Logger
import javax.sql.DataSource

internal data class PostgresReportFixture(
    val dataSource: DataSource,
    val schema: String,
)

internal fun withPostgresReportFixture(block: (PostgresReportFixture) -> Unit) {
    val jdbcUrl = System.getenv("CODE_STUDIO_TEST_DB_JDBC_URL")
    val username = System.getenv("CODE_STUDIO_TEST_DB_USERNAME")
    val password = System.getenv("CODE_STUDIO_TEST_DB_PASSWORD")
    assumeTrue(!jdbcUrl.isNullOrBlank(), "未配置 PostgreSQL 集成测试数据源")
    assumeTrue(!username.isNullOrBlank(), "未配置 PostgreSQL 集成测试用户")
    assumeTrue(password != null, "未配置 PostgreSQL 集成测试密码")
    val dataSource = DriverManagerDataSource(jdbcUrl, username, password)
    val schema = "report_test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    try {
        StudioMigrator(dataSource, schema = schema).migrate(emptyList())
        ReportSchemaMigration(dataSource, schema = schema).migrate()
        block(PostgresReportFixture(dataSource, schema))
    } finally {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
            }
        }
    }
}

private class DriverManagerDataSource(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
) : DataSource {
    override fun getConnection() = DriverManager.getConnection(jdbcUrl, username, password)
    override fun getConnection(username: String, password: String) = DriverManager.getConnection(jdbcUrl, username, password)
    override fun getLogWriter(): PrintWriter? = DriverManager.getLogWriter()
    override fun setLogWriter(out: PrintWriter?) = DriverManager.setLogWriter(out)
    override fun setLoginTimeout(seconds: Int) = DriverManager.setLoginTimeout(seconds)
    override fun getLoginTimeout(): Int = DriverManager.getLoginTimeout()
    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw SQLFeatureNotSupportedException()
    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
