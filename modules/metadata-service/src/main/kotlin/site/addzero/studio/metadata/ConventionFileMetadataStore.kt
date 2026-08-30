package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.sql.ResultSet

private val CONVENTION_FILE_CODE = Regex("[a-z][A-Za-z0-9]*")
private val CONVENTION_CLASS_NAME = Regex("[A-Z][A-Za-z0-9_]*")
private val CONVENTION_KINDS = mapOf(
    "SERVICE" to ConventionKind("Service", "service"),
    "SCHEDULED_JOB" to ConventionKind("Job", "job"),
)

internal fun MetadataSession.conventionFileList(): List<ObjectNode> = conventionFileIds().map(::conventionFileDetail)

internal fun MetadataSession.conventionFileDetail(id: Long): ObjectNode {
    val sql = """
        SELECT file.id, file.feature_id, file.file_code, file.name, file.class_name,
               file.kind, file.status, file.description,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS feature_package_name,
               definition.code AS contributor_id
        FROM $schema.convention_file file
        INNER JOIN $schema.library_feature feature ON feature.id = file.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        WHERE file.id = ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("约定文件不存在: $id")
            }
            rows.toConventionFile(mapper)
        }
    }
}

internal fun MetadataSession.validateConventionFile(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val featureId = runCatching { command.requiredLong("featureId") }
        .getOrElse { cause ->
            errors += cause.message ?: "featureId 不合法"
            return MetadataValidationResult(false, errors)
        }
    requireEditableFeature(featureId)
    val fileCode = command.optionalText("fileCode")
    if (fileCode == null || !CONVENTION_FILE_CODE.matches(fileCode)) {
        errors += "fileCode 必须使用小驼峰标识"
    }
    if (command.optionalText("name") == null) {
        errors += "约定文件注释不能为空"
    }
    val kindName = command.optionalText("kind")
    val kind = CONVENTION_KINDS[kindName]
    if (kind == null) {
        errors += "约定文件 kind 不受支持"
    }
    val className = command.optionalText("className")
    if (className == null || !CONVENTION_CLASS_NAME.matches(className)) {
        errors += "Kotlin 类名不是合法标识"
    } else if (kind != null && !className.endsWith(kind.classNameSuffix)) {
        errors += "Kotlin 类名必须以 ${kind.classNameSuffix} 结尾"
    }
    if (command.intOrDefault("status", 1) !in 0..1) {
        errors += "status 只能是 0 或 1"
    }
    val id = command.optionalLong("id")
    if (id != null) {
        requireEditableResource("convention_file", id, "约定文件")
    }
    if (fileCode != null && kindName != null && hasOtherConventionFileCode(id, featureId, kindName, fileCode)) {
        errors += "同一功能下的约定文件编码已存在: $fileCode"
    }
    if (className != null && hasOtherConventionClassName(id, featureId, className)) {
        errors += "同一功能下的 Kotlin 类名已存在: $className"
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveConventionFile(command: JsonNode): Any {
    val validation = validateConventionFile(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    if (id == null) {
        return insertConventionFile(command)
    }
    updateConventionFile(id, command)
    return true
}

internal fun MetadataSession.deleteConventionFiles(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableResource("convention_file", id, "约定文件")
        if (!deleteById("convention_file", id)) {
            notFound("约定文件不存在: $id")
        }
    }
    return true
}

private fun MetadataSession.conventionFileIds(): List<Long> {
    val sql = "SELECT id FROM $schema.convention_file ORDER BY kind, file_code"
    return connection.createStatement().use { statement ->
        statement.executeQuery(sql).use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getLong(1))
                }
            }
        }
    }
}

private fun MetadataSession.insertConventionFile(command: JsonNode): Long {
    val sql = """
        INSERT INTO $schema.convention_file
            (feature_id, file_code, name, class_name, kind, status, description)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING id
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, command.requiredLong("featureId"))
        statement.setString(2, command.requiredText("fileCode"))
        statement.setString(3, command.requiredText("name"))
        statement.setString(4, command.requiredText("className"))
        statement.setString(5, command.requiredText("kind"))
        statement.setInt(6, command.intOrDefault("status", 1))
        statement.setString(7, command.optionalText("description"))
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

private fun MetadataSession.updateConventionFile(id: Long, command: JsonNode) {
    val sql = """
        UPDATE $schema.convention_file
        SET feature_id = ?, file_code = ?, name = ?, class_name = ?, kind = ?,
            status = ?, description = ?, update_time = CURRENT_TIMESTAMP
        WHERE id = ?
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, command.requiredLong("featureId"))
        statement.setString(2, command.requiredText("fileCode"))
        statement.setString(3, command.requiredText("name"))
        statement.setString(4, command.requiredText("className"))
        statement.setString(5, command.requiredText("kind"))
        statement.setInt(6, command.intOrDefault("status", 1))
        statement.setString(7, command.optionalText("description"))
        statement.setLong(8, id)
        if (statement.executeUpdate() != 1) {
            notFound("约定文件不存在: $id")
        }
    }
}

private fun MetadataSession.hasOtherConventionFileCode(
    id: Long?,
    featureId: Long,
    kind: String,
    fileCode: String,
): Boolean {
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = """
        SELECT 1 FROM $schema.convention_file
        WHERE feature_id = ? AND kind = ? AND file_code = ? $idFilter
    """.trimIndent()
    return exists(sql) {
        setLong(1, featureId)
        setString(2, kind)
        setString(3, fileCode)
        if (id != null) {
            setLong(4, id)
        }
    }
}

private fun MetadataSession.hasOtherConventionClassName(id: Long?, featureId: Long, className: String): Boolean {
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = "SELECT 1 FROM $schema.convention_file WHERE feature_id = ? AND class_name = ? $idFilter"
    return exists(sql) {
        setLong(1, featureId)
        setString(2, className)
        if (id != null) {
            setLong(3, id)
        }
    }
}

private fun ResultSet.toConventionFile(mapper: tools.jackson.databind.ObjectMapper): ObjectNode {
    val kindName = getString("kind")
    val kind = requireNotNull(CONVENTION_KINDS[kindName]) { "数据库包含未知约定文件类型: $kindName" }
    val featurePackageName = getString("feature_package_name")
    return mapper.createObjectNode().apply {
        put("id", getLong("id"))
        put("featureId", getLong("feature_id"))
        put("fileCode", getString("file_code"))
        put("name", getString("name"))
        put("className", getString("class_name"))
        put("kind", kindName)
        put("status", getInt("status"))
        putNullable("description", getString("description"))
        put("packageName", "$featurePackageName.${kind.packageSuffix}")
        put("contributorId", getString("contributor_id"))
    }
}

private data class ConventionKind(
    val classNameSuffix: String,
    val packageSuffix: String,
)
