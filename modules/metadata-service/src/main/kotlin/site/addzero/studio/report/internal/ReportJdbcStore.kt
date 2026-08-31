package site.addzero.studio.report.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.addzero.studio.contract.PageResult
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

internal class ReportJdbcStore(
    private val dataSource: DataSource,
    private val schema: String,
) {
    init {
        require(POSTGRESQL_SCHEMA.matches(schema)) {
            "报表 schema 不是安全的 PostgreSQL 标识符: $schema"
        }
    }

    suspend fun create(reportKey: String, draftDocument: String): ReportRecord = write { connection ->
        val sql = """
            INSERT INTO $schema.report_definition (report_key, draft_document)
            VALUES (?, CAST(? AS JSONB))
            RETURNING report_key, revision, draft_document, published_revision, published_document
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, reportKey)
            statement.setString(2, draftDocument)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.toReportRecord()
            }
        }
    }

    suspend fun page(pageNo: Int, pageSize: Int): PageResult<ReportRecord> = read { connection ->
        requirePage(pageNo, pageSize)
        val total = connection.count("SELECT count(*) FROM $schema.report_definition")
        val sql = """
            SELECT report_key, revision, draft_document, published_revision, published_document
            FROM $schema.report_definition
            ORDER BY report_key
            LIMIT ? OFFSET ?
        """.trimIndent()
        val offset = (pageNo - 1).toLong() * pageSize
        val rows = connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, pageSize)
            statement.setLong(2, offset)
            statement.executeQuery().use { result -> result.readReportRecords() }
        }
        val totalPages = total.toPageCount(pageSize)
        PageResult(rows = rows, totalRowCount = total, totalPageCount = totalPages)
    }

    suspend fun detail(reportKey: String): ReportRecord = read { connection ->
        connection.find(reportKey) ?: reportNotFound(reportKey)
    }

    suspend fun update(
        reportKey: String,
        expectedRevision: Long,
        draftDocument: String,
    ): ReportRecord = write { connection ->
        val sql = """
            UPDATE $schema.report_definition
            SET revision = revision + 1, draft_document = CAST(? AS JSONB)
            WHERE report_key = ? AND revision = ?
            RETURNING report_key, revision, draft_document, published_revision, published_document
        """.trimIndent()
        val updated = connection.prepareStatement(sql).use { statement ->
            statement.setString(1, draftDocument)
            statement.setString(2, reportKey)
            statement.setLong(3, expectedRevision)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toReportRecord() else null
            }
        }
        updated ?: connection.failForMissingOrStale(reportKey, expectedRevision)
    }

    suspend fun delete(reportKey: String): Boolean = write { connection ->
        val sql = "DELETE FROM $schema.report_definition WHERE report_key = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, reportKey)
            statement.executeUpdate() == 1
        }
    }

    suspend fun publish(
        reportKey: String,
        expectedRevision: Long,
        publishedDocument: String,
    ): ReportRecord = write { connection ->
        val current = connection.lock(reportKey) ?: reportNotFound(reportKey)
        current.requireRevision(expectedRevision)
        if (current.publishedRevision == current.revision) {
            return@write current
        }
        val sql = """
            UPDATE $schema.report_definition
            SET published_revision = revision, published_document = CAST(? AS JSONB)
            WHERE report_key = ?
            RETURNING report_key, revision, draft_document, published_revision, published_document
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, publishedDocument)
            statement.setString(2, reportKey)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.toReportRecord()
            }
        }
    }

    suspend fun withdraw(reportKey: String): Boolean = write { connection ->
        val sql = """
            UPDATE $schema.report_definition
            SET published_revision = NULL, published_document = NULL
            WHERE report_key = ? AND published_revision IS NOT NULL
        """.trimIndent()
        val changed = connection.prepareStatement(sql).use { statement ->
            statement.setString(1, reportKey)
            statement.executeUpdate() == 1
        }
        if (changed) {
            return@write true
        }
        if (connection.find(reportKey) == null) {
            reportNotFound(reportKey)
        }
        true
    }

    suspend fun publishedPage(pageNo: Int, pageSize: Int): PageResult<ReportRecord> = read { connection ->
        requirePage(pageNo, pageSize)
        val where = "published_revision IS NOT NULL"
        val total = connection.count("SELECT count(*) FROM $schema.report_definition WHERE $where")
        val sql = """
            SELECT report_key, revision, draft_document, published_revision, published_document
            FROM $schema.report_definition
            WHERE $where
            ORDER BY report_key
            LIMIT ? OFFSET ?
        """.trimIndent()
        val offset = (pageNo - 1).toLong() * pageSize
        val rows = connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, pageSize)
            statement.setLong(2, offset)
            statement.executeQuery().use { result -> result.readReportRecords() }
        }
        val totalPages = total.toPageCount(pageSize)
        PageResult(rows = rows, totalRowCount = total, totalPageCount = totalPages)
    }

    suspend fun publishedDetail(reportKey: String): ReportRecord = read { connection ->
        val record = connection.find(reportKey) ?: reportNotFound(reportKey)
        if (record.publishedRevision == null) {
            reportNotFound(reportKey)
        }
        record
    }

    private suspend fun <T> read(block: (Connection) -> T): T = transaction(readOnly = true, block)

    private suspend fun <T> write(block: (Connection) -> T): T = transaction(readOnly = false, block)

    private suspend fun <T> transaction(
        readOnly: Boolean,
        block: (Connection) -> T,
    ): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.isReadOnly = readOnly
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (cause: Throwable) {
                connection.rollback()
                throw cause.toReportException()
            }
        }
    }

    private fun Connection.find(reportKey: String): ReportRecord? {
        val sql = """
            SELECT report_key, revision, draft_document, published_revision, published_document
            FROM $schema.report_definition
            WHERE report_key = ?
        """.trimIndent()
        return prepareStatement(sql).use { statement ->
            statement.setString(1, reportKey)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toReportRecord() else null
            }
        }
    }

    private fun Connection.lock(reportKey: String): ReportRecord? {
        val sql = """
            SELECT report_key, revision, draft_document, published_revision, published_document
            FROM $schema.report_definition
            WHERE report_key = ?
            FOR UPDATE
        """.trimIndent()
        return prepareStatement(sql).use { statement ->
            statement.setString(1, reportKey)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toReportRecord() else null
            }
        }
    }

    private fun Connection.failForMissingOrStale(reportKey: String, expectedRevision: Long): Nothing {
        val current = find(reportKey) ?: reportNotFound(reportKey)
        current.requireRevision(expectedRevision)
        error("报表 CAS 更新未返回结果: $reportKey")
    }
}

internal data class ReportRecord(
    val reportKey: String,
    val revision: Long,
    val draftDocument: String,
    val publishedRevision: Long?,
    val publishedDocument: String?,
)

private fun ReportRecord.requireRevision(expectedRevision: Long) {
    if (revision != expectedRevision) {
        reportConflict("报表 $reportKey revision 已变更，当前为 $revision，提交为 $expectedRevision")
    }
}

private fun requirePage(pageNo: Int, pageSize: Int) {
    if (pageNo <= 0 || pageSize !in 1..1000) {
        reportBadRequest("分页参数不合法")
    }
}

private fun Connection.count(sql: String): Long = createStatement().use { statement ->
    statement.executeQuery(sql).use { rows ->
        check(rows.next())
        rows.getLong(1)
    }
}

private fun ResultSet.readReportRecords(): List<ReportRecord> = buildList {
    while (next()) {
        add(toReportRecord())
    }
}

private fun ResultSet.toReportRecord(): ReportRecord = ReportRecord(
    reportKey = getString("report_key"),
    revision = getLong("revision"),
    draftDocument = getString("draft_document"),
    publishedRevision = getLong("published_revision").takeUnless { wasNull() },
    publishedDocument = getString("published_document"),
)

private fun Long.toPageCount(pageSize: Int): Long = if (this == 0L) 0 else (this + pageSize - 1) / pageSize

private fun Throwable.toReportException(): Throwable {
    if (this is ReportRequestException || this !is SQLException) {
        return this
    }
    return when (sqlState) {
        "23505" -> ReportRequestException(io.ktor.http.HttpStatusCode.Conflict, "报表键已存在")
        "23514", "22P02" -> ReportRequestException(io.ktor.http.HttpStatusCode.BadRequest, "报表数据不符合约束")
        else -> this
    }
}

private val POSTGRESQL_SCHEMA = Regex("[a-z_][a-z0-9_]{0,62}")
