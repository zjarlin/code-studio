package site.addzero.studio.report

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportSchemaMigrationTest {
    @Test
    fun `报表模块独立迁移电子表格模板`() {
        withPostgresReportFixture { fixture ->
            val configuration = ReportSchemaMigration(
                dataSource = fixture.dataSource,
                schema = fixture.schema,
            ).configuration()
            assertEquals(REPORT_SCHEMA_HISTORY, configuration.table)
            assertEquals(
                listOf(REPORT_MIGRATION_LOCATION),
                configuration.locations.map { location -> location.descriptor },
            )
            fixture.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT tablename FROM pg_tables WHERE schemaname = '${fixture.schema}'",
                    ).use { rows ->
                        val tables = buildSet {
                            while (rows.next()) add(rows.getString(1))
                        }
                        assertTrue("spreadsheet_template" in tables)
                        assertTrue(REPORT_SCHEMA_HISTORY in tables)
                    }
                }
            }
        }
    }
}
