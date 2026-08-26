package site.addzero.studio.metadata

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeMetadataDatabaseReader
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

private val POSTGRESQL_SCHEMA = Regex("[a-z_][a-z0-9_]{0,62}")
private val CONTRIBUTOR_ID = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")

internal class MetadataJdbcStore(
    private val dataSource: DataSource,
    val schema: String,
    val editableContributorId: String,
    val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    init {
        require(POSTGRESQL_SCHEMA.matches(schema)) {
            "Studio schema 不是安全的 PostgreSQL 标识符: $schema"
        }
        require(CONTRIBUTOR_ID.matches(editableContributorId)) {
            "editableContributorId 不是合法的 contributor id: $editableContributorId"
        }
    }

    suspend fun <T> read(block: MetadataSession.() -> T): T = transaction(readOnly = true, block)

    suspend fun <T> write(block: MetadataSession.() -> T): T = transaction(readOnly = false, block)

    suspend fun <T> compile(block: (LowcodeMetadata) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.schema = schema
            val metadata = LowcodeMetadataDatabaseReader.read(connection)
            block(metadata)
        }
    }

    private suspend fun <T> transaction(
        readOnly: Boolean,
        block: MetadataSession.() -> T,
    ): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.isReadOnly = readOnly
            connection.autoCommit = false
            connection.transactionIsolation = if (readOnly) {
                Connection.TRANSACTION_REPEATABLE_READ
            } else {
                Connection.TRANSACTION_READ_COMMITTED
            }
            try {
                val session = MetadataSession(connection, schema, editableContributorId, mapper)
                val result = session.block()
                connection.commit()
                result
            } catch (cause: Throwable) {
                connection.rollback()
                throw cause.toMetadataException()
            }
        }
    }
}

internal class MetadataSession(
    val connection: Connection,
    val schema: String,
    val editableContributorId: String,
    val mapper: ObjectMapper,
) {
    fun requireEditableLibrary(libraryId: Long) {
        val contributorId = libraryContributor(libraryId)
            ?: notFound("Library 不存在: $libraryId")
        requireEditable(contributorId)
    }

    fun requireEditableFeature(featureId: Long): FeatureLocation {
        val location = featureLocation(featureId)
            ?: notFound("Library 功能不存在: $featureId")
        requireEditable(location.contributorId)
        return location
    }

    fun requireEditableResource(
        table: String,
        id: Long,
        displayName: String,
    ): FeatureLocation {
        val sql = """
            SELECT feature.id, feature.library_id, feature.feature_code,
                   (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
                   definition.code AS contributor_id
            FROM $schema.$table resource
            INNER JOIN $schema.library_feature feature ON feature.id = resource.feature_id
            INNER JOIN $schema.library_definition library ON library.id = feature.library_id
            INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
            WHERE resource.id = ?
        """.trimIndent()
        val location = connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toFeatureLocation() else null
            }
        } ?: notFound("$displayName 不存在: $id")
        requireEditable(location.contributorId)
        return location
    }

    fun featureLocation(featureId: Long): FeatureLocation? {
        val sql = """
            SELECT feature.id, feature.library_id, feature.feature_code,
                   (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
                   definition.code AS contributor_id
            FROM $schema.library_feature feature
            INNER JOIN $schema.library_definition library ON library.id = feature.library_id
            INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
            WHERE feature.id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, featureId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toFeatureLocation() else null
            }
        }
    }

    fun libraryContributor(libraryId: Long): String? {
        val sql = """
            SELECT definition.code
            FROM $schema.library_definition library
            INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
            WHERE library.id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, libraryId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getString(1) else null
            }
        }
    }

    fun requireEditable(contributorId: String) {
        if (contributorId != editableContributorId) {
            forbidden("只允许修改 contributor $editableContributorId，当前资源属于 $contributorId")
        }
    }

    fun exists(sql: String, bind: PreparedStatement.() -> Unit): Boolean =
        connection.prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use(ResultSet::next)
        }

    fun deleteById(table: String, id: Long): Boolean {
        val sql = "DELETE FROM $schema.$table WHERE id = ?"
        return connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeUpdate() == 1
        }
    }

    private fun ResultSet.toFeatureLocation(): FeatureLocation = FeatureLocation(
        featureId = getLong("id"),
        libraryId = getLong("library_id"),
        featureCode = getString("feature_code"),
        packageName = getString("package_name"),
        contributorId = getString("contributor_id"),
    )
}

internal data class FeatureLocation(
    val featureId: Long,
    val libraryId: Long,
    val featureCode: String,
    val packageName: String,
    val contributorId: String,
)

private fun Throwable.toMetadataException(): Throwable {
    if (this is MetadataRequestException) {
        return this
    }
    if (this !is SQLException) {
        return this
    }
    return when (sqlState) {
        "23503" -> MetadataRequestException(HttpStatusCode.Conflict, "元数据仍被其他资源引用")
        "23505" -> MetadataRequestException(HttpStatusCode.Conflict, "元数据唯一约束冲突")
        "23514", "22P02" -> MetadataRequestException(HttpStatusCode.BadRequest, "元数据不符合数据库约束")
        else -> this
    }
}
