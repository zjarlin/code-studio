package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import java.sql.PreparedStatement
import java.sql.Types

private val FIELD_CODE = Regex("[a-z][A-Za-z0-9]*")
private val COLUMN_NAME = Regex("[a-z][a-z0-9_]*")

internal fun MetadataSession.insertModel(
    command: JsonNode,
    featureId: Long,
    entityConfig: JsonNode,
    routeConfig: JsonNode?,
): Long {
    val sql = """
        INSERT INTO $schema.lowcode_model
            (feature_id, model_code, name, class_name, table_name, model_type,
             status, version, remark, entity_config, route_config)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))
        RETURNING id
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        bindModel(statement, command, featureId, entityConfig, routeConfig)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

internal fun MetadataSession.updateModel(
    id: Long,
    command: JsonNode,
    featureId: Long,
    entityConfig: JsonNode,
    routeConfig: JsonNode?,
) {
    val sql = """
        UPDATE $schema.lowcode_model
        SET feature_id = ?, model_code = ?, name = ?, class_name = ?, table_name = ?,
            model_type = ?, status = ?, version = ?, remark = ?, entity_config = CAST(? AS JSONB),
            route_config = CAST(? AS JSONB), update_time = CURRENT_TIMESTAMP
        WHERE id = ?
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        bindModel(statement, command, featureId, entityConfig, routeConfig)
        statement.setLong(12, id)
        if (statement.executeUpdate() != 1) {
            notFound("模型不存在: $id")
        }
    }
}

private fun MetadataSession.bindModel(
    statement: PreparedStatement,
    command: JsonNode,
    featureId: Long,
    entityConfig: JsonNode,
    routeConfig: JsonNode?,
) {
    statement.setLong(1, featureId)
    statement.setString(2, command.requiredText("modelCode"))
    statement.setString(3, command.requiredText("name"))
    statement.setString(4, command.requiredText("className"))
    statement.setString(5, command.requiredText("tableName"))
    statement.setString(6, command.requiredText("modelType"))
    statement.setInt(7, command.intOrDefault("status", 1))
    statement.setInt(8, command.intOrDefault("version", 1))
    statement.setString(9, command.optionalText("remark"))
    statement.setString(10, mapper.writeValueAsString(entityConfig))
    statement.setString(11, routeConfig?.let(mapper::writeValueAsString))
}

internal fun MetadataSession.replaceModelChildren(modelId: Long, command: JsonNode) {
    listOf("lowcode_relation", "lowcode_query", "lowcode_field").forEach { table ->
        connection.prepareStatement("DELETE FROM $schema.$table WHERE model_id = ?").use { statement ->
            statement.setLong(1, modelId)
            statement.executeUpdate()
        }
    }
    command.arrayOrEmpty("fields", mapper).forEachIndexed { index, field ->
        insertField(modelId, field, index + 1)
    }
    command.arrayOrEmpty("queries", mapper).forEachIndexed { index, query ->
        insertQuery(modelId, query, index + 1)
    }
    command.arrayOrEmpty("relations", mapper).forEachIndexed { index, relation ->
        insertRelation(modelId, relation, index + 1)
    }
}

private fun MetadataSession.insertField(modelId: Long, field: JsonNode, fallbackOrder: Int) {
    val sql = """
        INSERT INTO $schema.lowcode_field
            (model_id, order_no, field_code, label, kotlin_type, db_column, required,
             list_visible, form_visible, form_control, dict_code, default_value, remark,
             serialized, max_length, enum_storage, natural_key, create_writable, update_writable)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, modelId)
        statement.setInt(2, field.intOrDefault("orderNo", fallbackOrder).takeIf { it > 0 } ?: fallbackOrder)
        statement.setString(3, field.requiredText("fieldCode"))
        statement.setString(4, field.requiredText("label"))
        statement.setString(5, field.requiredText("kotlinType"))
        statement.setString(6, field.requiredText("dbColumn"))
        statement.setBoolean(7, field.booleanOrDefault("required", false))
        statement.setBoolean(8, field.booleanOrDefault("listVisible", true))
        statement.setBoolean(9, field.booleanOrDefault("formVisible", true))
        statement.setString(10, field.optionalText("formControl") ?: "INPUT")
        statement.setString(11, field.optionalText("dictCode"))
        statement.setString(12, field.optionalText("defaultValue"))
        statement.setString(13, field.optionalText("remark"))
        statement.setBoolean(14, field.booleanOrDefault("serialized", false))
        statement.setNullableInt(15, field.get("maxLength")?.takeUnless(JsonNode::isNull)?.asInt())
        statement.setString(16, field.optionalText("enumStorage"))
        statement.setBoolean(17, field.booleanOrDefault("key", false))
        statement.setBoolean(18, field.booleanOrDefault("createWritable", true))
        statement.setBoolean(19, field.booleanOrDefault("updateWritable", true))
        statement.executeUpdate()
    }
}

private fun MetadataSession.insertQuery(modelId: Long, query: JsonNode, fallbackOrder: Int) {
    val sql = """
        INSERT INTO $schema.lowcode_query (model_id, order_no, query_code, label, query_logic)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id
    """.trimIndent()
    val queryId = connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, modelId)
        statement.setInt(2, query.intOrDefault("orderNo", fallbackOrder).takeIf { it > 0 } ?: fallbackOrder)
        statement.setString(3, query.requiredText("queryCode"))
        statement.setString(4, query.requiredText("label"))
        statement.setString(5, query.optionalText("logic") ?: "AND")
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
    query.arrayOrEmpty("items", mapper).forEachIndexed { index, item ->
        insertQueryItem(queryId, item, index + 1)
    }
}

private fun MetadataSession.insertQueryItem(queryId: Long, item: JsonNode, fallbackOrder: Int) {
    val sql = """
        INSERT INTO $schema.lowcode_query_condition
            (query_id, order_no, field_code, query_operator, value_type, param_name)
        VALUES (?, ?, ?, ?, ?, ?)
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, queryId)
        statement.setInt(2, item.intOrDefault("orderNo", fallbackOrder).takeIf { it > 0 } ?: fallbackOrder)
        statement.setString(3, item.requiredText("fieldCode"))
        statement.setString(4, item.optionalText("operator") ?: "EQ")
        statement.setString(5, item.optionalText("valueType") ?: "SINGLE")
        statement.setString(6, item.optionalText("paramName"))
        statement.executeUpdate()
    }
}

private fun MetadataSession.insertRelation(modelId: Long, relation: JsonNode, fallbackOrder: Int) {
    val targetModelId = relation.optionalLong("targetModelId")
        ?: relation.optionalText("targetModelCode")?.let(::modelIdByCode)
        ?: badRequest("关联 ${relation.optionalText("relationCode").orEmpty()} 缺少目标模型")
    val sql = """
        INSERT INTO $schema.lowcode_relation
            (model_id, order_no, relation_code, label, relation_type, target_model_id,
             join_column, mapped_by, join_table, join_table_join_column, join_table_inverse_column,
             join_table_filter_column, join_table_filter_values, dissociate_action, required,
             list_visible, form_visible, create_writable, update_writable)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?)
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, modelId)
        statement.setInt(2, relation.intOrDefault("orderNo", fallbackOrder).takeIf { it > 0 } ?: fallbackOrder)
        statement.setString(3, relation.requiredText("relationCode"))
        statement.setString(4, relation.requiredText("label"))
        statement.setString(5, relation.requiredText("relationType"))
        statement.setLong(6, targetModelId)
        statement.setString(7, relation.optionalText("joinColumn"))
        statement.setString(8, relation.optionalText("mappedBy"))
        statement.setString(9, relation.optionalText("joinTable"))
        statement.setString(10, relation.optionalText("joinTableJoinColumn"))
        statement.setString(11, relation.optionalText("joinTableInverseColumn"))
        statement.setString(12, relation.optionalText("joinTableFilterColumn"))
        val filterValues = relation.arrayOrEmpty("joinTableFilterValues", mapper)
        statement.setString(13, mapper.writeValueAsString(filterValues))
        statement.setString(14, relation.optionalText("dissociateAction") ?: "NONE")
        statement.setBoolean(15, relation.booleanOrDefault("required", false))
        statement.setBoolean(16, relation.booleanOrDefault("listVisible", true))
        statement.setBoolean(17, relation.booleanOrDefault("formVisible", true))
        statement.setBoolean(18, relation.booleanOrDefault("createWritable", true))
        statement.setBoolean(19, relation.booleanOrDefault("updateWritable", true))
        statement.executeUpdate()
    }
}

internal fun MetadataSession.validateModelChildren(command: JsonNode, errors: MutableList<String>) {
    val fields = command.arrayOrEmpty("fields", mapper)
    val fieldCodes = mutableSetOf<String>()
    val columns = mutableSetOf<String>()
    fields.forEach { field ->
        val code = field.optionalText("fieldCode")
        if (code == null || !FIELD_CODE.matches(code)) {
            errors += "字段 fieldCode 不合法"
        } else if (!fieldCodes.add(code)) {
            errors += "字段编码重复: $code"
        }
        val column = field.optionalText("dbColumn")
        if (column == null || !COLUMN_NAME.matches(column)) {
            errors += "字段 dbColumn 不合法"
        } else if (!columns.add(column)) {
            errors += "数据库列重复: $column"
        }
        if (field.optionalText("label") == null || field.optionalText("kotlinType") == null) {
            errors += "字段注释和 Kotlin 类型不能为空"
        }
    }
    duplicateCodes(command.arrayOrEmpty("queries", mapper), "queryCode", "查询", errors)
    duplicateCodes(command.arrayOrEmpty("relations", mapper), "relationCode", "关联", errors)
}

private fun duplicateCodes(
    values: ArrayNode,
    field: String,
    displayName: String,
    errors: MutableList<String>,
) {
    val codes = mutableSetOf<String>()
    values.forEach { value ->
        val code = value.optionalText(field)
        if (code == null) {
            errors += "$displayName $field 不能为空"
        } else if (!codes.add(code)) {
            errors += "$displayName 编码重复: $code"
        }
    }
}

internal fun MetadataSession.hasOtherModel(id: Long?, column: String, value: String): Boolean {
    require(column == "model_code" || column == "table_name")
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = "SELECT 1 FROM $schema.lowcode_model WHERE $column = ? $idFilter"
    return exists(sql) {
        setString(1, value)
        if (id != null) {
            setLong(2, id)
        }
    }
}

private fun MetadataSession.modelIdByCode(code: String): Long {
    val sql = "SELECT id FROM $schema.lowcode_model WHERE model_code = ?"
    return connection.prepareStatement(sql).use { statement ->
        statement.setString(1, code)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("目标模型不存在: $code")
            }
            rows.getLong(1)
        }
    }
}

private fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
    if (value == null) {
        setNull(index, Types.INTEGER)
    } else {
        setInt(index, value)
    }
}
