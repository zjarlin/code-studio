package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.sql.PreparedStatement
import java.sql.ResultSet

private val MODEL_CODE = Regex("[a-z][A-Za-z0-9]*")
private val CLASS_NAME = Regex("[A-Z][A-Za-z0-9_]*")
private val TABLE_NAME = Regex("[a-z][a-z0-9_]*")
private val MODEL_TYPES = setOf("ENTITY", "MAPPED_SUPERCLASS", "EMBEDDABLE")

internal fun MetadataSession.modelPage(request: JsonNode): ObjectNode {
    val pageNumber = request.intOrDefault("pageNumber", 1)
    val pageSize = request.intOrDefault("pageSize", 20)
    if (pageNumber <= 0 || pageSize !in 1..1000) {
        badRequest("模型分页参数不合法")
    }
    val condition = request.objectOrEmpty("condition", mapper)
    val filter = condition.toModelPageFilter()
    val total = modelCount(filter)
    val offset = (pageNumber - 1).toLong() * pageSize
    val rows = modelPageRows(filter, pageSize, offset)
    attachModelChildren(rows)
    val pages = if (total == 0L) 0L else (total + pageSize - 1) / pageSize
    return mapper.createObjectNode().apply {
        putNode("rows", mapper.valueToTree(rows))
        put("totalRowCount", total)
        put("totalPageCount", pages)
    }
}

internal fun MetadataSession.modelDetail(id: Long): ObjectNode {
    val sql = """
        SELECT model.id, model.feature_id, model.model_code, model.name, model.class_name,
               model.table_name, model.model_type, model.status, model.version, model.remark,
               model.entity_config, model.route_config,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
               definition.code AS contributor_id
        FROM $schema.lowcode_model model
        INNER JOIN $schema.library_feature feature ON feature.id = model.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        WHERE model.id = ?
    """.trimIndent()
    val model = connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("模型不存在: $id")
            }
            rows.toModel(mapper)
        }
    }
    model.applyComputedRouteFields()
    attachModelChildren(listOf(model))
    return model
}

internal fun MetadataSession.validateModel(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val featureId = runCatching { command.requiredLong("featureId") }
        .getOrElse { cause ->
            errors += cause.message ?: "featureId 不合法"
            return MetadataValidationResult(false, errors)
        }
    requireEditableFeature(featureId)
    val modelCode = command.optionalText("modelCode")
    if (modelCode == null || !MODEL_CODE.matches(modelCode)) {
        errors += "modelCode 必须使用小驼峰标识"
    }
    if (command.optionalText("name") == null) {
        errors += "模型名称不能为空"
    }
    val className = command.optionalText("className")
    if (className == null || !CLASS_NAME.matches(className)) {
        errors += "className 不是合法 Kotlin 类型名"
    }
    val tableName = command.optionalText("tableName")
    if (tableName == null || !TABLE_NAME.matches(tableName)) {
        errors += "tableName 必须使用小写下划线命名"
    }
    if (command.optionalText("modelType") !in MODEL_TYPES) {
        errors += "modelType 不受支持"
    }
    if (command.intOrDefault("version", 1) <= 0) {
        errors += "模型版本必须大于 0"
    }
    validateModelChildren(command, errors)
    command.get("routeConfig")?.takeUnless(JsonNode::isNull)?.let { route ->
        if (!route.isObject) {
            errors += "routeConfig 必须是 JSON 对象"
        } else {
            val path = route.optionalText("path")
            if (path == null || !path.startsWith('/')) {
                errors += "routeConfig.path 必须以 / 开头"
            }
        }
    }
    val id = command.optionalLong("id")
    if (id != null) {
        requireEditableResource("lowcode_model", id, "模型")
    }
    if (modelCode != null && hasOtherModel(id, "model_code", modelCode)) {
        errors += "modelCode 已存在: $modelCode"
    }
    if (tableName != null && hasOtherModel(id, "table_name", tableName)) {
        errors += "tableName 已存在: $tableName"
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveModel(command: JsonNode): Any {
    val validation = validateModel(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val featureId = command.requiredLong("featureId")
    val entityConfig = command.objectOrEmpty("entityConfig", mapper)
    val routeConfig = command.get("routeConfig")?.takeUnless(JsonNode::isNull)
    val savedId = if (id == null) {
        insertModel(command, featureId, entityConfig, routeConfig)
    } else {
        updateModel(id, command, featureId, entityConfig, routeConfig)
        id
    }
    replaceModelChildren(savedId, command)
    return if (id == null) savedId else true
}

internal fun MetadataSession.deleteModels(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableResource("lowcode_model", id, "模型")
        if (!deleteById("lowcode_model", id)) {
            notFound("模型不存在: $id")
        }
    }
    return true
}

private fun MetadataSession.modelCount(filter: ModelPageFilter): Long {
    val sql = """
        SELECT count(*)
        FROM $schema.lowcode_model model
        INNER JOIN $schema.library_feature feature ON feature.id = model.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        ${filter.whereSql()}
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.bind(filter)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

private fun MetadataSession.modelPageRows(
    filter: ModelPageFilter,
    pageSize: Int,
    offset: Long,
): List<ObjectNode> {
    val sql = """
        SELECT model.id, model.feature_id, model.model_code, model.name, model.class_name,
               model.table_name, model.model_type, model.status, model.version, model.remark,
               model.entity_config, model.route_config,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
               definition.code AS contributor_id
        FROM $schema.lowcode_model model
        INNER JOIN $schema.library_feature feature ON feature.id = model.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        ${filter.whereSql()}
        ORDER BY model.model_code
        LIMIT ? OFFSET ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        var index = statement.bind(filter)
        statement.setInt(index++, pageSize)
        statement.setLong(index, offset)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    val model = rows.toModel(mapper)
                    model.applyComputedRouteFields()
                    add(model)
                }
            }
        }
    }
}

private fun MetadataSession.attachModelChildren(models: List<ObjectNode>) {
    val modelIds = models.map { model -> model["id"].asLong() }
    if (modelIds.isEmpty()) {
        return
    }
    val fields = modelFields(modelIds)
    val queries = modelQueries(modelIds)
    val relations = modelRelations(modelIds)
    models.forEach { model ->
        val modelId = model["id"].asLong()
        model.putNode("fields", fields.getValue(modelId))
        model.putNode("queries", queries.getValue(modelId))
        model.putNode("relations", relations.getValue(modelId))
    }
}

private fun MetadataSession.modelFields(modelIds: List<Long>): Map<Long, ArrayNode> {
    val sql = """
        SELECT model_id, id, order_no, field_code, label, kotlin_type, db_column, required,
               list_visible, form_visible, form_control, dict_code, default_value, remark,
               serialized, max_length, enum_storage, natural_key, create_writable, update_writable
        FROM $schema.lowcode_field
        WHERE model_id IN (${modelIds.placeholders()})
        ORDER BY model_id, order_no, id
    """.trimIndent()
    val result = modelIds.associateWith { mapper.createArrayNode() }
    connection.prepareStatement(sql).use { statement ->
        statement.bind(modelIds)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                result.getValue(rows.getLong("model_id")).add(rows.toField(mapper))
            }
        }
    }
    return result
}

private fun MetadataSession.modelQueries(modelIds: List<Long>): Map<Long, ArrayNode> {
    val sql = """
        SELECT model_id, id, order_no, query_code, label, query_logic
        FROM $schema.lowcode_query
        WHERE model_id IN (${modelIds.placeholders()})
        ORDER BY model_id, order_no, id
    """.trimIndent()
    val result = modelIds.associateWith { mapper.createArrayNode() }
    val queriesById = mutableMapOf<Long, ObjectNode>()
    connection.prepareStatement(sql).use { statement ->
        statement.bind(modelIds)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                val modelId = rows.getLong("model_id")
                val queryId = rows.getLong("id")
                val query = mapper.createObjectNode().apply {
                    put("id", queryId)
                    put("orderNo", rows.getInt("order_no"))
                    put("queryCode", rows.getString("query_code"))
                    put("label", rows.getString("label"))
                    put("logic", rows.getString("query_logic"))
                    putNode("items", mapper.createArrayNode())
                }
                queriesById[queryId] = query
                result.getValue(modelId).add(query)
            }
        }
    }
    attachQueryItems(queriesById)
    return result
}

private fun MetadataSession.attachQueryItems(queriesById: Map<Long, ObjectNode>) {
    if (queriesById.isEmpty()) {
        return
    }
    val queryIds = queriesById.keys.toList()
    val sql = """
        SELECT query_id, id, order_no, field_code, query_operator, value_type, param_name
        FROM $schema.lowcode_query_condition
        WHERE query_id IN (${queryIds.placeholders()})
        ORDER BY query_id, order_no, id
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        statement.bind(queryIds)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                val item = mapper.createObjectNode().apply {
                    put("id", rows.getLong("id"))
                    put("orderNo", rows.getInt("order_no"))
                    put("fieldCode", rows.getString("field_code"))
                    put("operator", rows.getString("query_operator"))
                    put("valueType", rows.getString("value_type"))
                    putNullable("paramName", rows.getString("param_name"))
                }
                val query = queriesById.getValue(rows.getLong("query_id"))
                val items = query["items"] as ArrayNode
                items.add(item)
            }
        }
    }
}

private fun MetadataSession.modelRelations(modelIds: List<Long>): Map<Long, ArrayNode> {
    val sql = """
        SELECT relation.model_id, relation.id, relation.order_no, relation.relation_code, relation.label,
               relation.relation_type, relation.target_model_id, target.model_code AS target_model_code,
               relation.join_column, relation.mapped_by, relation.join_table,
               relation.join_table_join_column, relation.join_table_inverse_column,
               relation.join_table_filter_column, relation.join_table_filter_values,
               relation.dissociate_action, relation.required, relation.list_visible,
               relation.form_visible, relation.create_writable, relation.update_writable
        FROM $schema.lowcode_relation relation
        LEFT JOIN $schema.lowcode_model target ON target.id = relation.target_model_id
        WHERE relation.model_id IN (${modelIds.placeholders()})
        ORDER BY relation.model_id, relation.order_no, relation.id
    """.trimIndent()
    val result = modelIds.associateWith { mapper.createArrayNode() }
    connection.prepareStatement(sql).use { statement ->
        statement.bind(modelIds)
        statement.executeQuery().use { rows ->
            while (rows.next()) {
                result.getValue(rows.getLong("model_id")).add(rows.toRelation(mapper))
            }
        }
    }
    return result
}

private data class ModelPageFilter(
    val featureId: Long?,
    val contributorId: String?,
    val keyword: String?,
    val modelCode: String?,
) {
    fun whereSql(): String {
        val predicates = buildList {
            if (featureId != null) {
                add("model.feature_id = ?")
            }
            if (contributorId != null) {
                add("definition.code = ?")
            }
            if (keyword != null) {
                add("concat_ws(' ', model.model_code, model.name, model.class_name, model.table_name, definition.code) ILIKE ?")
            }
            if (modelCode != null) {
                add("model.model_code = ?")
            }
        }
        return if (predicates.isEmpty()) "" else "WHERE ${predicates.joinToString(" AND ")}"
    }
}

private fun JsonNode.toModelPageFilter(): ModelPageFilter = ModelPageFilter(
    featureId = optionalLong("featureId"),
    contributorId = optionalText("contributorId"),
    keyword = optionalText("keyword"),
    modelCode = optionalText("modelCode"),
)

private fun PreparedStatement.bind(filter: ModelPageFilter): Int {
    var index = 1
    filter.featureId?.let { featureId -> setLong(index++, featureId) }
    filter.contributorId?.let { contributorId -> setString(index++, contributorId) }
    filter.keyword?.let { keyword -> setString(index++, "%$keyword%") }
    filter.modelCode?.let { modelCode -> setString(index++, modelCode) }
    return index
}

private fun List<Long>.placeholders(): String = joinToString(",") { "?" }

private fun PreparedStatement.bind(ids: List<Long>) {
    ids.forEachIndexed { index, id -> setLong(index + 1, id) }
}

private fun ObjectNode.applyComputedRouteFields() {
    val entityConfig = get("entityConfig") as ObjectNode
    val routeConfig = get("routeConfig") as? ObjectNode ?: return
    val packageName = get("packageName").asString()
    val className = get("className").asString()
    val sourceQualifiedName = entityConfig.optionalText("sourceQualifiedName")
    routeConfig.put("packageName", packageName)
    routeConfig.put("featurePackageName", packageName)
    routeConfig.put("qualifiedName", sourceQualifiedName ?: "$packageName.generated.entity.$className")
    routeConfig.put("className", className)
    routeConfig.put("modelCode", get("modelCode").asString())
}

private fun ResultSet.toModel(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("featureId", getLong("feature_id"))
    put("modelCode", getString("model_code"))
    put("name", getString("name"))
    put("packageName", getString("package_name"))
    put("className", getString("class_name"))
    put("tableName", getString("table_name"))
    put("modelType", getString("model_type"))
    put("status", getInt("status"))
    put("version", getInt("version"))
    putNullable("contributorId", getString("contributor_id"))
    putNullable("remark", getString("remark"))
    putNode("entityConfig", mapper.readObject(getString("entity_config")))
    val route = getString("route_config")
    if (route == null) {
        putNull("routeConfig")
    } else {
        putNode("routeConfig", mapper.readObject(route))
    }
}

private fun ResultSet.toField(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("orderNo", getInt("order_no"))
    put("fieldCode", getString("field_code"))
    put("label", getString("label"))
    put("kotlinType", getString("kotlin_type"))
    put("dbColumn", getString("db_column"))
    put("required", getBoolean("required"))
    put("listVisible", getBoolean("list_visible"))
    put("formVisible", getBoolean("form_visible"))
    put("formControl", getString("form_control"))
    putNullable("dictCode", getString("dict_code"))
    putNullable("defaultValue", getString("default_value"))
    putNullable("remark", getString("remark"))
    put("serialized", getBoolean("serialized"))
    val maxLength = getInt("max_length").takeUnless { wasNull() }
    if (maxLength == null) putNull("maxLength") else put("maxLength", maxLength)
    putNullable("enumStorage", getString("enum_storage"))
    put("key", getBoolean("natural_key"))
    put("createWritable", getBoolean("create_writable"))
    put("updateWritable", getBoolean("update_writable"))
}

private fun ResultSet.toRelation(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("orderNo", getInt("order_no"))
    put("relationCode", getString("relation_code"))
    put("label", getString("label"))
    put("relationType", getString("relation_type"))
    putNullable("targetModelId", getLong("target_model_id").takeUnless { wasNull() })
    putNullable("targetModelCode", getString("target_model_code"))
    putNullable("joinColumn", getString("join_column"))
    putNullable("mappedBy", getString("mapped_by"))
    putNullable("joinTable", getString("join_table"))
    putNullable("joinTableJoinColumn", getString("join_table_join_column"))
    putNullable("joinTableInverseColumn", getString("join_table_inverse_column"))
    putNullable("joinTableFilterColumn", getString("join_table_filter_column"))
    putNode("joinTableFilterValues", mapper.readArray(getString("join_table_filter_values")))
    put("dissociateAction", getString("dissociate_action"))
    put("required", getBoolean("required"))
    put("listVisible", getBoolean("list_visible"))
    put("formVisible", getBoolean("form_visible"))
    put("createWritable", getBoolean("create_writable"))
    put("updateWritable", getBoolean("update_writable"))
}
