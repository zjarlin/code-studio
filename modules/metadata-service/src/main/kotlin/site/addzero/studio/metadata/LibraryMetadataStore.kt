package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.sql.ResultSet

private val PACKAGE_NAME = Regex("[a-z_][A-Za-z0-9_]*(?:\\.[a-z_][A-Za-z0-9_]*)*")
private val FEATURE_CODE = Regex("[a-z][A-Za-z0-9_]*(?:\\.[a-z][A-Za-z0-9_]*)*")

internal fun MetadataSession.libraryPage(pageNumber: Int, pageSize: Int): ObjectNode {
    requirePage(pageNumber, pageSize)
    val totalSql = "SELECT count(*) FROM $schema.library_definition"
    val total = connection.createStatement().use { statement ->
        statement.executeQuery(totalSql).use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
    val offset = (pageNumber - 1).toLong() * pageSize
    val idSql = """
        SELECT library.id
        FROM $schema.library_definition library
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        ORDER BY definition.code
        LIMIT ? OFFSET ?
    """.trimIndent()
    val ids = connection.prepareStatement(idSql).use { statement ->
        statement.setInt(1, pageSize)
        statement.setLong(2, offset)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getLong(1))
                }
            }
        }
    }
    val list = mapper.createArrayNode()
    ids.forEach { id -> list.add(libraryDetail(id, includeFeatures = false)) }
    return mapper.createObjectNode().apply {
        putNode("list", list)
        put("total", total)
    }
}

internal fun MetadataSession.libraryDetail(
    id: Long,
    includeFeatures: Boolean = true,
): ObjectNode {
    val sql = """
        SELECT definition.id, definition.code, definition.display_name,
               definition.version, definition.status, library.spec
        FROM $schema.library_definition library
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        WHERE library.id = ?
    """.trimIndent()
    val result = connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("Library 不存在: $id")
            }
            rows.toLibrary(mapper)
        }
    }
    val features = if (includeFeatures) {
        libraryFeatures(id)
    } else {
        mapper.createArrayNode()
    }
    result.putNode("features", features)
    return result
}

internal fun MetadataSession.validateLibrary(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val code = command.optionalText("code")
    if (code == null) {
        errors += "Library 标识不能为空"
    } else if (code != editableContributorId) {
        errors += "Library 标识必须等于当前 contributor: $editableContributorId"
    }
    if (command.optionalText("displayName") == null) {
        errors += "Library 名称不能为空"
    }
    if (command.intOrDefault("version", 1) <= 0) {
        errors += "Library 版本必须大于 0"
    }
    val spec = runCatching { command.requiredObject("spec") }
        .getOrElse { cause ->
            errors += cause.message ?: "Library spec 不合法"
            return MetadataValidationResult(false, errors.distinct())
        }
    val contributorId = spec.optionalText("contributorId")
    if (contributorId != editableContributorId) {
        errors += "spec.contributorId 必须等于当前 contributor: $editableContributorId"
    }
    listOf("packagePrefix", "scanPackage").forEach { name ->
        val value = spec.optionalText(name)
        if (value == null || !PACKAGE_NAME.matches(value)) {
            errors += "spec.$name 不是合法包名"
        }
    }
    command.optionalLong("id")?.let { id ->
        val owner = libraryContributor(id)
        if (owner == null) {
            errors += "Library 不存在: $id"
        } else if (owner != editableContributorId) {
            forbidden("只允许修改 contributor $editableContributorId，当前 Library 属于 $owner")
        }
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveLibrary(command: JsonNode): Any {
    val validation = validateLibrary(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val displayName = command.requiredText("displayName")
    val version = command.intOrDefault("version", 1)
    val status = command.intOrDefault("status", 1)
    val spec = command.requiredObject("spec").deepCopy()
    spec.put("contributorId", editableContributorId)
    val specJson = mapper.writeValueAsString(spec)
    if (id == null) {
        val definitionSql = """
            INSERT INTO $schema.lowcode_definition
                (code, display_name, version, status, definition_type)
            VALUES (?, ?, ?, ?, 'LIBRARY')
            RETURNING id
        """.trimIndent()
        val createdId = connection.prepareStatement(definitionSql).use { statement ->
            statement.setString(1, editableContributorId)
            statement.setString(2, displayName)
            statement.setInt(3, version)
            statement.setInt(4, status)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }
        val librarySql = "INSERT INTO $schema.library_definition (id, spec) VALUES (?, CAST(? AS JSONB))"
        connection.prepareStatement(librarySql).use { statement ->
            statement.setLong(1, createdId)
            statement.setString(2, specJson)
            statement.executeUpdate()
        }
        return createdId
    }

    requireEditableLibrary(id)
    val definitionSql = """
        UPDATE $schema.lowcode_definition
        SET display_name = ?, version = ?, status = ?, update_time = CURRENT_TIMESTAMP
        WHERE id = ? AND code = ?
    """.trimIndent()
    connection.prepareStatement(definitionSql).use { statement ->
        statement.setString(1, displayName)
        statement.setInt(2, version)
        statement.setInt(3, status)
        statement.setLong(4, id)
        statement.setString(5, editableContributorId)
        if (statement.executeUpdate() != 1) {
            notFound("Library 不存在: $id")
        }
    }
    val librarySql = "UPDATE $schema.library_definition SET spec = CAST(? AS JSONB) WHERE id = ?"
    connection.prepareStatement(librarySql).use { statement ->
        statement.setString(1, specJson)
        statement.setLong(2, id)
        statement.executeUpdate()
    }
    return true
}

internal fun MetadataSession.deleteLibraries(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableLibrary(id)
        if (!deleteById("lowcode_definition", id)) {
            notFound("Library 不存在: $id")
        }
    }
    return true
}

internal fun MetadataSession.libraryFeaturePage(
    libraryId: Long?,
    pageNumber: Int,
    pageSize: Int,
): ObjectNode {
    requirePage(pageNumber, pageSize)
    val filter = if (libraryId == null) "" else "WHERE library_id = ?"
    val countSql = "SELECT count(*) FROM $schema.library_feature $filter"
    val total = connection.prepareStatement(countSql).use { statement ->
        if (libraryId != null) {
            statement.setLong(1, libraryId)
        }
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
    val sql = """
        SELECT id, library_id, parent_id, feature_code, name, description
        FROM $schema.library_feature
        $filter
        ORDER BY feature_code
        LIMIT ? OFFSET ?
    """.trimIndent()
    val list = mapper.createArrayNode()
    connection.prepareStatement(sql).use { statement ->
        var index = 1
        if (libraryId != null) {
            statement.setLong(index++, libraryId)
        }
        statement.setInt(index++, pageSize)
        statement.setLong(index, (pageNumber - 1).toLong() * pageSize)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                list.add(rows.toFeature(mapper))
            }
        }
    }
    return mapper.createObjectNode().apply {
        putNode("list", list)
        put("total", total)
    }
}

internal fun MetadataSession.libraryFeatureDetail(id: Long): ObjectNode {
    val sql = """
        SELECT id, library_id, parent_id, feature_code, name, description
        FROM $schema.library_feature
        WHERE id = ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("Library 功能不存在: $id")
            }
            rows.toFeature(mapper)
        }
    }
}

internal fun MetadataSession.validateLibraryFeature(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val libraryId = runCatching { command.requiredLong("libraryId") }
        .getOrElse { cause ->
            errors += cause.message ?: "libraryId 不合法"
            return MetadataValidationResult(false, errors)
        }
    requireEditableLibrary(libraryId)
    val featureCode = command.optionalText("featureCode")
    if (featureCode == null || !FEATURE_CODE.matches(featureCode)) {
        errors += "featureCode 不是合法的功能编码"
    }
    if (command.optionalText("name") == null) {
        errors += "功能名称不能为空"
    }
    val id = command.optionalLong("id")
    if (id != null) {
        val current = requireEditableFeature(id)
        if (current.libraryId != libraryId) {
            errors += "不能把功能移动到另一个 Library"
        }
    }
    command.optionalLong("parentId")?.let { parentId ->
        if (parentId == id) {
            errors += "功能不能以自身作为父级"
        } else {
            val parent = requireEditableFeature(parentId)
            if (parent.libraryId != libraryId) {
                errors += "父功能必须属于同一个 Library"
            }
        }
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveLibraryFeature(command: JsonNode): ObjectNode {
    val validation = validateLibraryFeature(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val libraryId = command.requiredLong("libraryId")
    val parentId = command.optionalLong("parentId")
    val featureCode = command.requiredText("featureCode")
    val name = command.requiredText("name")
    val description = command.optionalText("description")
    val savedId = if (id == null) {
        val sql = """
            INSERT INTO $schema.library_feature
                (library_id, parent_id, feature_code, name, description)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, libraryId)
            statement.setNullableLong(2, parentId)
            statement.setString(3, featureCode)
            statement.setString(4, name)
            statement.setString(5, description)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }
    } else {
        val sql = """
            UPDATE $schema.library_feature
            SET parent_id = ?, feature_code = ?, name = ?, description = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setNullableLong(1, parentId)
            statement.setString(2, featureCode)
            statement.setString(3, name)
            statement.setString(4, description)
            statement.setLong(5, id)
            if (statement.executeUpdate() != 1) {
                notFound("Library 功能不存在: $id")
            }
        }
        id
    }
    return libraryFeatureDetail(savedId)
}

internal fun MetadataSession.deleteLibraryFeature(id: Long): Boolean {
    requireEditableFeature(id)
    if (!deleteById("library_feature", id)) {
        notFound("Library 功能不存在: $id")
    }
    return true
}

internal fun MetadataSession.libraryFeatures(libraryId: Long): ArrayNode {
    val sql = """
        SELECT id, library_id, parent_id, feature_code, name, description
        FROM $schema.library_feature
        WHERE library_id = ?
        ORDER BY feature_code
    """.trimIndent()
    val result = mapper.createArrayNode()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, libraryId)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                result.add(rows.toFeature(mapper))
            }
        }
    }
    return result
}

private fun MetadataSession.requirePage(pageNumber: Int, pageSize: Int) {
    if (pageNumber <= 0) {
        badRequest("页码必须大于 0")
    }
    if (pageSize !in 1..1000) {
        badRequest("分页大小必须位于 1..1000")
    }
}

private fun ResultSet.toLibrary(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("code", getString("code"))
    put("displayName", getString("display_name"))
    put("version", getInt("version"))
    put("status", getInt("status"))
    putNode("spec", mapper.readObject(getString("spec")))
}

private fun ResultSet.toFeature(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("libraryId", getLong("library_id"))
    val parentId = getLong("parent_id").takeUnless { wasNull() }
    putNullable("parentId", parentId)
    put("featureCode", getString("feature_code"))
    put("name", getString("name"))
    putNullable("description", getString("description"))
}

internal fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, java.sql.Types.BIGINT)
    } else {
        setLong(index, value)
    }
}
