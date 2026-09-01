package site.addzero.studio.report.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.addzero.studio.contract.PageResult
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

internal class SpreadsheetTemplateStore(
    private val dataSource: DataSource,
    private val schemaName: String,
) {
    init {
        require(POSTGRESQL_SCHEMA.matches(schemaName)) {
            "电子表格模板 schema 不是安全的 PostgreSQL 标识符: $schemaName"
        }
    }

    suspend fun create(templateKey: String, sourceFile: ByteArray, document: String): SpreadsheetTemplateDocumentRecord =
        write { connection ->
            val sql = """
                INSERT INTO $schemaName.spreadsheet_template (template_key, source_file, document)
                VALUES (?, ?, CAST(? AS JSONB))
                RETURNING template_key, revision, document
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, templateKey)
                statement.setBytes(2, sourceFile)
                statement.setString(3, document)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.toSpreadsheetTemplateDocumentRecord()
                }
            }
        }

    suspend fun page(pageNo: Int, pageSize: Int): PageResult<SpreadsheetTemplateListRecord> = read { connection ->
        requirePage(pageNo, pageSize)
        val total = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM $schemaName.spreadsheet_template").use { rows ->
                check(rows.next())
                rows.getLong(1)
            }
        }
        val sql = """
            SELECT
                template_key,
                revision,
                document ->> 'name' AS name,
                document -> 'source' ->> 'fileName' AS file_name,
                COALESCE((document -> 'source' ->> 'macroEnabled')::boolean, false) AS macro_enabled
            FROM $schemaName.spreadsheet_template
            ORDER BY template_key
            LIMIT ? OFFSET ?
        """.trimIndent()
        val offset = (pageNo - 1).toLong() * pageSize
        val records = connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, pageSize)
            statement.setLong(2, offset)
            statement.executeQuery().use { rows -> rows.readSpreadsheetTemplateListRecords() }
        }
        PageResult(records, total, total.toPageCount(pageSize))
    }

    suspend fun detail(templateKey: String): SpreadsheetTemplateDocumentRecord = read { connection ->
        connection.findDocument(templateKey) ?: spreadsheetTemplateNotFound(templateKey)
    }

    suspend fun source(templateKey: String): SpreadsheetTemplateSourceRecord = read { connection ->
        val sql = """
            SELECT template_key, revision, source_file, document
            FROM $schemaName.spreadsheet_template
            WHERE template_key = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, templateKey)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toSpreadsheetTemplateSourceRecord() else spreadsheetTemplateNotFound(templateKey)
            }
        }
    }

    suspend fun update(
        templateKey: String,
        expectedRevision: Long,
        document: String,
    ): SpreadsheetTemplateDocumentRecord = write { connection ->
        val sql = """
            UPDATE $schemaName.spreadsheet_template
            SET revision = revision + 1, document = CAST(? AS JSONB)
            WHERE template_key = ? AND revision = ?
            RETURNING template_key, revision, document
        """.trimIndent()
        val updated = connection.prepareStatement(sql).use { statement ->
            statement.setString(1, document)
            statement.setString(2, templateKey)
            statement.setLong(3, expectedRevision)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toSpreadsheetTemplateDocumentRecord() else null
            }
        }
        updated ?: connection.failForMissingOrStale(templateKey, expectedRevision)
    }

    suspend fun delete(templateKey: String): Boolean = write { connection ->
        val sql = "DELETE FROM $schemaName.spreadsheet_template WHERE template_key = ?"
        val deleted = connection.prepareStatement(sql).use { statement ->
            statement.setString(1, templateKey)
            statement.executeUpdate() == 1
        }
        if (!deleted) spreadsheetTemplateNotFound(templateKey)
        true
    }

    private suspend fun <T> read(block: (Connection) -> T): T = transaction(true, block)

    private suspend fun <T> write(block: (Connection) -> T): T = transaction(false, block)

    private suspend fun <T> transaction(readOnly: Boolean, block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.isReadOnly = readOnly
                connection.autoCommit = false
                try {
                    val result = block(connection)
                    connection.commit()
                    result
                } catch (cause: Throwable) {
                    connection.rollback()
                    throw cause.toSpreadsheetTemplateException()
                }
            }
        }

    private fun Connection.findDocument(templateKey: String): SpreadsheetTemplateDocumentRecord? {
        val sql = """
            SELECT template_key, revision, document
            FROM $schemaName.spreadsheet_template
            WHERE template_key = ?
        """.trimIndent()
        return prepareStatement(sql).use { statement ->
            statement.setString(1, templateKey)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toSpreadsheetTemplateDocumentRecord() else null
            }
        }
    }

    private fun Connection.failForMissingOrStale(templateKey: String, expectedRevision: Long): Nothing {
        val currentRevision = prepareStatement(
            "SELECT revision FROM $schemaName.spreadsheet_template WHERE template_key = ?",
        ).use { statement ->
            statement.setString(1, templateKey)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        } ?: spreadsheetTemplateNotFound(templateKey)
        if (currentRevision != expectedRevision) {
            reportConflict(
                "电子表格模板 $templateKey revision 已变更，当前为 $currentRevision，提交为 $expectedRevision",
            )
        }
        error("电子表格模板 CAS 更新未返回结果: $templateKey")
    }
}

internal data class SpreadsheetTemplateDocumentRecord(
    val templateKey: String,
    val revision: Long,
    val document: String,
)

internal data class SpreadsheetTemplateListRecord(
    val templateKey: String,
    val revision: Long,
    val name: String,
    val fileName: String,
    val macroEnabled: Boolean,
)

internal data class SpreadsheetTemplateSourceRecord(
    val templateKey: String,
    val revision: Long,
    val sourceFile: ByteArray,
    val document: String,
)

private fun ResultSet.readSpreadsheetTemplateListRecords(): List<SpreadsheetTemplateListRecord> = buildList {
    while (next()) {
        add(
            SpreadsheetTemplateListRecord(
                templateKey = getString("template_key"),
                revision = getLong("revision"),
                name = getString("name"),
                fileName = getString("file_name"),
                macroEnabled = getBoolean("macro_enabled"),
            ),
        )
    }
}

private fun ResultSet.toSpreadsheetTemplateDocumentRecord(): SpreadsheetTemplateDocumentRecord = SpreadsheetTemplateDocumentRecord(
    templateKey = getString("template_key"),
    revision = getLong("revision"),
    document = getString("document"),
)

private fun ResultSet.toSpreadsheetTemplateSourceRecord(): SpreadsheetTemplateSourceRecord = SpreadsheetTemplateSourceRecord(
    templateKey = getString("template_key"),
    revision = getLong("revision"),
    sourceFile = getBytes("source_file"),
    document = getString("document"),
)

private fun requirePage(pageNo: Int, pageSize: Int) {
    if (pageNo <= 0 || pageSize !in 1..1000) reportBadRequest("分页参数不合法")
}

private fun Long.toPageCount(pageSize: Int): Long = if (this == 0L) 0 else (this + pageSize - 1) / pageSize

private fun spreadsheetTemplateNotFound(templateKey: String): Nothing =
    throw ReportRequestException(io.ktor.http.HttpStatusCode.NotFound, "电子表格模板不存在: $templateKey")

private fun Throwable.toSpreadsheetTemplateException(): Throwable {
    if (this is ReportRequestException || this !is SQLException) return this
    return when (sqlState) {
        "23505" -> ReportRequestException(io.ktor.http.HttpStatusCode.Conflict, "电子表格模板键已存在")
        "23514", "22P02" -> ReportRequestException(
            io.ktor.http.HttpStatusCode.BadRequest,
            "电子表格模板数据不符合约束",
        )
        else -> this
    }
}

private val POSTGRESQL_SCHEMA = Regex("[a-z_][a-z0-9_]{0,62}")
