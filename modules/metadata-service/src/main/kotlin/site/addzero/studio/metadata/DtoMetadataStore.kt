package site.addzero.studio.metadata

import site.addzero.validation.compiler.LsiValidationRule
import site.addzero.validation.compiler.ValidationRuleMetadataCatalog
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.sql.ResultSet

private val DTO_CODE = Regex("[a-z][A-Za-z0-9]*")
private val DTO_CLASS_NAME = Regex("[A-Z][A-Za-z0-9_]*")
private val DTO_KINDS = setOf("INPUT", "OUTPUT", "STRUCTURE", "VIEW")
private val DTO_VISIBILITIES = setOf("PUBLIC", "INTERNAL")

internal fun MetadataSession.dtoList(): List<ObjectNode> = dtoIds().map(::dtoDetail)

internal fun MetadataSession.dtoDetail(id: Long): ObjectNode {
    val sql = """
        SELECT dto.id, dto.feature_id, dto.dto_code, dto.name, dto.class_name, dto.kind,
               dto.visibility, dto.selection_mode, dto.excluded_paths, dto.fields,
               dto.annotations, dto.super_types, dto.status, dto.version, dto.description,
               model.id AS source_model_id, model.model_code AS source_model_code,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
               definition.code AS contributor_id
        FROM $schema.lowcode_dto dto
        INNER JOIN $schema.library_feature feature ON feature.id = dto.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        LEFT JOIN $schema.lowcode_model model ON model.id = dto.source_model_id
        WHERE dto.id = ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("DTO 不存在: $id")
            }
            rows.toDto(mapper)
        }
    }
}

internal fun MetadataSession.validateDto(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val featureId = runCatching { command.requiredLong("featureId") }
        .getOrElse { cause ->
            errors += cause.message ?: "featureId 不合法"
            return MetadataValidationResult(false, errors)
        }
    requireEditableFeature(featureId)
    val dtoCode = command.optionalText("dtoCode")
    if (dtoCode == null || !DTO_CODE.matches(dtoCode)) {
        errors += "dtoCode 必须使用小驼峰标识"
    }
    if (command.optionalText("name") == null) {
        errors += "DTO 名称不能为空"
    }
    val className = command.optionalText("className")
    if (className == null || !DTO_CLASS_NAME.matches(className)) {
        errors += "className 不是合法 Kotlin 类型名"
    }
    val kind = command.optionalText("kind")
    if (kind !in DTO_KINDS) {
        errors += "DTO kind 不受支持"
    }
    if ((command.optionalText("visibility") ?: "PUBLIC") !in DTO_VISIBILITIES) {
        errors += "DTO visibility 不受支持"
    }
    if (command.intOrDefault("version", 1) <= 0) {
        errors += "DTO 版本必须大于 0"
    }
    val sourceModelCode = command.optionalText("sourceModelCode")
    if (kind == "STRUCTURE" && sourceModelCode != null) {
        errors += "结构 DTO 不能选择来源模型"
    }
    if (sourceModelCode != null && findModelId(sourceModelCode) == null) {
        errors += "DTO 来源模型不存在: $sourceModelCode"
    }
    val fields = command.arrayOrEmpty("fields", mapper)
    if (sourceModelCode == null && fields.isEmpty) {
        errors += "独立 DTO 至少需要一个字段"
    }
    validateDtoFields(fields, errors)
    val id = command.optionalLong("id")
    if (id != null) {
        requireEditableResource("lowcode_dto", id, "DTO")
    }
    if (dtoCode != null && hasOtherDto(id, dtoCode)) {
        errors += "dtoCode 已存在: $dtoCode"
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveDto(command: JsonNode): Any {
    val validation = validateDto(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val featureId = command.requiredLong("featureId")
    val sourceModelId = command.optionalText("sourceModelCode")?.let { code ->
        findModelId(code) ?: notFound("DTO 来源模型不存在: $code")
    }
    val excludedPaths = command.arrayOrEmpty("excludedPaths", mapper)
    val fields = command.arrayOrEmpty("fields", mapper)
    val annotations = command.arrayOrEmpty("annotations", mapper)
    val superTypes = command.arrayOrEmpty("superTypes", mapper)
    if (id == null) {
        return insertDto(command, featureId, sourceModelId, excludedPaths, fields, annotations, superTypes)
    }
    updateDto(id, command, featureId, sourceModelId, excludedPaths, fields, annotations, superTypes)
    return true
}

internal fun MetadataSession.deleteDtos(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableResource("lowcode_dto", id, "DTO")
        if (!deleteById("lowcode_dto", id)) {
            notFound("DTO 不存在: $id")
        }
    }
    return true
}

internal fun dtoValidationRules(): List<site.addzero.validation.compiler.LsiValidationRuleMetadata> =
    ValidationRuleMetadataCatalog.load().metadata

private fun MetadataSession.dtoIds(): List<Long> {
    val sql = "SELECT id FROM $schema.lowcode_dto ORDER BY dto_code"
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

private fun MetadataSession.insertDto(
    command: JsonNode,
    featureId: Long,
    sourceModelId: Long?,
    excludedPaths: ArrayNode,
    fields: ArrayNode,
    annotations: ArrayNode,
    superTypes: ArrayNode,
): Long {
    val sql = """
        INSERT INTO $schema.lowcode_dto
            (feature_id, dto_code, name, class_name, kind, visibility, source_model_id,
             selection_mode, excluded_paths, fields, annotations, super_types,
             status, version, description)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB),
                CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?)
        RETURNING id
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        bindDto(statement, command, featureId, sourceModelId, excludedPaths, fields, annotations, superTypes)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

private fun MetadataSession.updateDto(
    id: Long,
    command: JsonNode,
    featureId: Long,
    sourceModelId: Long?,
    excludedPaths: ArrayNode,
    fields: ArrayNode,
    annotations: ArrayNode,
    superTypes: ArrayNode,
) {
    val sql = """
        UPDATE $schema.lowcode_dto
        SET feature_id = ?, dto_code = ?, name = ?, class_name = ?, kind = ?, visibility = ?,
            source_model_id = ?, selection_mode = ?, excluded_paths = CAST(? AS JSONB),
            fields = CAST(? AS JSONB), annotations = CAST(? AS JSONB), super_types = CAST(? AS JSONB),
            status = ?, version = ?, description = ?, update_time = CURRENT_TIMESTAMP
        WHERE id = ?
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        bindDto(statement, command, featureId, sourceModelId, excludedPaths, fields, annotations, superTypes)
        statement.setLong(16, id)
        if (statement.executeUpdate() != 1) {
            notFound("DTO 不存在: $id")
        }
    }
}

private fun MetadataSession.bindDto(
    statement: java.sql.PreparedStatement,
    command: JsonNode,
    featureId: Long,
    sourceModelId: Long?,
    excludedPaths: ArrayNode,
    fields: ArrayNode,
    annotations: ArrayNode,
    superTypes: ArrayNode,
) {
    statement.setLong(1, featureId)
    statement.setString(2, command.requiredText("dtoCode"))
    statement.setString(3, command.requiredText("name"))
    statement.setString(4, command.requiredText("className"))
    statement.setString(5, command.requiredText("kind"))
    statement.setString(6, command.optionalText("visibility") ?: "PUBLIC")
    statement.setNullableLong(7, sourceModelId)
    statement.setString(8, command.optionalText("selectionMode") ?: "EXPLICIT")
    statement.setString(9, mapper.writeValueAsString(excludedPaths))
    statement.setString(10, mapper.writeValueAsString(fields))
    statement.setString(11, mapper.writeValueAsString(annotations))
    statement.setString(12, mapper.writeValueAsString(superTypes))
    statement.setInt(13, command.intOrDefault("status", 1))
    statement.setInt(14, command.intOrDefault("version", 1))
    statement.setString(15, command.optionalText("description"))
}

private fun MetadataSession.validateDtoFields(fields: ArrayNode, errors: MutableList<String>) {
    val names = mutableSetOf<String>()
    val catalog = ValidationRuleMetadataCatalog.load()
    fields.forEach { field ->
        val name = field.optionalText("name")
        if (name == null) {
            errors += "DTO 字段名称不能为空"
        } else if (!names.add(name)) {
            errors += "DTO 字段名称重复: $name"
        }
        field.arrayOrEmpty("validations", mapper).forEach { rule ->
            val code = rule.optionalText("code")
            if (code == null) {
                errors += "DTO 字段校验规则编码不能为空"
                return@forEach
            }
            val parameters = rule.objectOrEmpty("parameters", mapper).properties().associate { entry ->
                entry.key to entry.value.asString()
            }
            runCatching {
                catalog.requireValid(LsiValidationRule(code, rule.optionalText("message"), parameters))
            }.onFailure { cause ->
                errors += cause.message ?: "DTO 字段校验规则不合法: $code"
            }
        }
    }
}

private fun MetadataSession.findModelId(modelCode: String): Long? {
    val sql = "SELECT id FROM $schema.lowcode_model WHERE model_code = ?"
    return connection.prepareStatement(sql).use { statement ->
        statement.setString(1, modelCode)
        statement.executeQuery().use { rows ->
            if (rows.next()) rows.getLong(1) else null
        }
    }
}

private fun MetadataSession.hasOtherDto(id: Long?, dtoCode: String): Boolean {
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = "SELECT 1 FROM $schema.lowcode_dto WHERE dto_code = ? $idFilter"
    return exists(sql) {
        setString(1, dtoCode)
        if (id != null) {
            setLong(2, id)
        }
    }
}

private fun ResultSet.toDto(mapper: tools.jackson.databind.ObjectMapper): ObjectNode = mapper.createObjectNode().apply {
    put("id", getLong("id"))
    put("featureId", getLong("feature_id"))
    put("dtoCode", getString("dto_code"))
    put("name", getString("name"))
    put("packageName", getString("package_name"))
    put("className", getString("class_name"))
    put("kind", getString("kind"))
    put("visibility", getString("visibility"))
    put("selectionMode", getString("selection_mode"))
    putNode("excludedPaths", mapper.readArray(getString("excluded_paths")))
    putNode("fields", mapper.readArray(getString("fields")))
    putNode("annotations", mapper.readArray(getString("annotations")))
    putNode("superTypes", mapper.readArray(getString("super_types")))
    put("contributorId", getString("contributor_id"))
    put("status", getInt("status"))
    put("version", getInt("version"))
    putNullable("description", getString("description"))
    val sourceModelId = getLong("source_model_id").takeUnless { wasNull() }
    if (sourceModelId == null) {
        putNull("sourceModel")
        putNull("sourceModelCode")
    } else {
        val sourceModelCode = getString("source_model_code")
        put("sourceModelCode", sourceModelCode)
        putNode("sourceModel", mapper.createObjectNode().apply {
            put("id", sourceModelId)
            put("modelCode", sourceModelCode)
        })
    }
}
