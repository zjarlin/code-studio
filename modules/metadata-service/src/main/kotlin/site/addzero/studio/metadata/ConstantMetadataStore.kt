package site.addzero.studio.metadata

import site.addzero.constant.compiler.KotlinConstantSourceGenerator
import site.addzero.constant.compiler.LsiConstant
import site.addzero.constant.compiler.LsiConstantGroup
import site.addzero.constant.compiler.LsiConstantType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.sql.ResultSet

private val CONSTANT_GROUP_CODE = Regex("[a-z][A-Za-z0-9]*")

internal fun MetadataSession.constantList(request: JsonNode): List<ObjectNode> {
    val featureId = request.optionalLong("featureId")
    val filter = if (featureId == null) "" else "WHERE feature_id = ?"
    val sql = "SELECT id FROM $schema.lowcode_constant_group $filter ORDER BY object_name"
    val ids = connection.prepareStatement(sql).use { statement ->
        if (featureId != null) {
            statement.setLong(1, featureId)
        }
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(rows.getLong(1))
                }
            }
        }
    }
    return ids.map(::constantDetail)
}

internal fun MetadataSession.constantDetail(id: Long): ObjectNode {
    val sql = """
        SELECT constant_group.id, constant_group.feature_id, constant_group.group_code,
               constant_group.object_name, constant_group.description,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
               definition.code AS contributor_id
        FROM $schema.lowcode_constant_group constant_group
        INNER JOIN $schema.library_feature feature ON feature.id = constant_group.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        WHERE constant_group.id = ?
    """.trimIndent()
    val group = connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("常量组不存在: $id")
            }
            rows.toConstantGroup(mapper)
        }
    }
    group.putNode("constants", constantItems(id))
    return group
}

internal fun MetadataSession.validateConstant(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val featureId = runCatching { command.requiredLong("featureId") }
        .getOrElse { cause ->
            errors += cause.message ?: "featureId 不合法"
            return MetadataValidationResult(false, errors)
        }
    val location = requireEditableFeature(featureId)
    val groupCode = command.optionalText("groupCode")
    if (groupCode == null || !CONSTANT_GROUP_CODE.matches(groupCode)) {
        errors += "groupCode 必须使用小驼峰标识"
    }
    val metadata = runCatching { command.toConstantMetadata(location.packageName, mapper) }
        .getOrElse { cause ->
            errors += cause.message ?: "常量定义不合法"
            null
        }
    if (metadata != null) {
        runCatching { KotlinConstantSourceGenerator.validate(metadata) }
            .onFailure { cause -> errors += cause.message ?: "常量定义不合法" }
    }
    val id = command.optionalLong("id")
    if (id != null) {
        requireEditableResource("lowcode_constant_group", id, "常量组")
    }
    if (groupCode != null && hasOtherConstantGroup(id, groupCode)) {
        errors += "groupCode 已存在: $groupCode"
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveConstant(command: JsonNode): ObjectNode {
    val validation = validateConstant(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val featureId = command.requiredLong("featureId")
    val savedId = if (id == null) {
        insertConstantGroup(command, featureId)
    } else {
        updateConstantGroup(id, command, featureId)
        connection.prepareStatement("DELETE FROM $schema.lowcode_constant_item WHERE constant_group_id = ?").use {
            statement ->
            statement.setLong(1, id)
            statement.executeUpdate()
        }
        id
    }
    insertConstantItems(savedId, command.arrayOrEmpty("constants", mapper))
    return constantDetail(savedId)
}

internal fun MetadataSession.deleteConstants(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableResource("lowcode_constant_group", id, "常量组")
        if (!deleteById("lowcode_constant_group", id)) {
            notFound("常量组不存在: $id")
        }
    }
    return true
}

private fun MetadataSession.insertConstantGroup(command: JsonNode, featureId: Long): Long {
    val sql = """
        INSERT INTO $schema.lowcode_constant_group
            (feature_id, group_code, object_name, description, status)
        VALUES (?, ?, ?, ?, 1)
        RETURNING id
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, featureId)
        statement.setString(2, command.requiredText("groupCode"))
        statement.setString(3, command.requiredText("objectName"))
        statement.setString(4, command.requiredText("description"))
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

private fun MetadataSession.updateConstantGroup(id: Long, command: JsonNode, featureId: Long) {
    val sql = """
        UPDATE $schema.lowcode_constant_group
        SET feature_id = ?, group_code = ?, object_name = ?, description = ?,
            status = 1, update_time = CURRENT_TIMESTAMP
        WHERE id = ?
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, featureId)
        statement.setString(2, command.requiredText("groupCode"))
        statement.setString(3, command.requiredText("objectName"))
        statement.setString(4, command.requiredText("description"))
        statement.setLong(5, id)
        if (statement.executeUpdate() != 1) {
            notFound("常量组不存在: $id")
        }
    }
}

private fun MetadataSession.insertConstantItems(groupId: Long, constants: ArrayNode) {
    val sql = """
        INSERT INTO $schema.lowcode_constant_item
            (constant_group_id, order_no, constant_name, value_type, constant_value, description, status)
        VALUES (?, ?, ?, ?, ?, ?, 1)
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        constants.forEachIndexed { index, constant ->
            statement.setLong(1, groupId)
            statement.setInt(2, index + 1)
            statement.setString(3, constant.requiredText("name"))
            statement.setString(4, constant.requiredText("type"))
            statement.setString(5, constant.requiredText("value"))
            statement.setString(6, constant.requiredText("description"))
            statement.addBatch()
        }
        statement.executeBatch()
    }
}

private fun MetadataSession.constantItems(groupId: Long): ArrayNode {
    val sql = """
        SELECT id, constant_name, value_type, constant_value, description
        FROM $schema.lowcode_constant_item
        WHERE constant_group_id = ? AND status = 1
        ORDER BY order_no, id
    """.trimIndent()
    val result = mapper.createArrayNode()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, groupId)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                result.add(mapper.createObjectNode().apply {
                    put("id", rows.getLong("id"))
                    put("name", rows.getString("constant_name"))
                    put("type", rows.getString("value_type"))
                    put("value", rows.getString("constant_value"))
                    put("description", rows.getString("description"))
                })
            }
        }
    }
    return result
}

private fun JsonNode.toConstantMetadata(
    packageName: String,
    mapper: tools.jackson.databind.ObjectMapper,
): LsiConstantGroup = LsiConstantGroup(
    packageName = "$packageName.generated.constants",
    objectName = requiredText("objectName"),
    description = requiredText("description"),
    constants = buildList {
        arrayOrEmpty("constants", mapper).forEach { constant ->
            add(
                LsiConstant(
                    name = constant.requiredText("name"),
                    type = runCatching { LsiConstantType.valueOf(constant.requiredText("type")) }
                        .getOrElse { badRequest("常量类型不受支持") },
                    value = constant.requiredText("value"),
                    description = constant.requiredText("description"),
                ),
            )
        }
    },
)

private fun MetadataSession.hasOtherConstantGroup(id: Long?, groupCode: String): Boolean {
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = "SELECT 1 FROM $schema.lowcode_constant_group WHERE group_code = ? $idFilter"
    return exists(sql) {
        setString(1, groupCode)
        if (id != null) {
            setLong(2, id)
        }
    }
}

private fun ResultSet.toConstantGroup(mapper: tools.jackson.databind.ObjectMapper): ObjectNode =
    mapper.createObjectNode().apply {
        put("id", getLong("id"))
        put("featureId", getLong("feature_id"))
        put("groupCode", getString("group_code"))
        put("featurePackageName", getString("package_name"))
        put("contributorId", getString("contributor_id"))
        put("objectName", getString("object_name"))
        put("description", getString("description"))
    }
