package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.sql.ResultSet

private val CONTRACT_CODE = Regex("[a-z][A-Za-z0-9]*")
private val CONTRACT_CLASS_NAME = Regex("[A-Z][A-Za-z0-9_]*Service")
private val HTTP_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
private val OPERATION_TRANSPORTS = setOf("HTTP", "SSE", "WEBSOCKET")

internal fun MetadataSession.contractList(): List<ObjectNode> = contractIds().map(::contractDetail)

internal fun MetadataSession.contractDetail(id: Long): ObjectNode {
    val sql = """
        SELECT contract.id, contract.feature_id, contract.contract_code, contract.name,
               contract.class_name, contract.path, contract.status, contract.version,
               contract.description, contract.operations, contract.agent_exposure,
               (library.spec ->> 'packagePrefix') || '.' || feature.feature_code AS package_name,
               definition.code AS contributor_id
        FROM $schema.lowcode_api_contract contract
        INNER JOIN $schema.library_feature feature ON feature.id = contract.feature_id
        INNER JOIN $schema.library_definition library ON library.id = feature.library_id
        INNER JOIN $schema.lowcode_definition definition ON definition.id = library.id
        WHERE contract.id = ?
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { rows ->
            if (!rows.next()) {
                notFound("Service 契约不存在: $id")
            }
            rows.toContract(mapper)
        }
    }
}

internal fun MetadataSession.validateContract(command: JsonNode): MetadataValidationResult {
    val errors = mutableListOf<String>()
    val featureId = runCatching { command.requiredLong("featureId") }
        .getOrElse { cause ->
            errors += cause.message ?: "featureId 不合法"
            return MetadataValidationResult(false, errors)
        }
    requireEditableFeature(featureId)
    val contractCode = command.optionalText("contractCode")
    if (contractCode == null || !CONTRACT_CODE.matches(contractCode)) {
        errors += "contractCode 必须使用小驼峰标识"
    }
    if (command.optionalText("name") == null) {
        errors += "Service 契约名称不能为空"
    }
    val className = command.optionalText("className")
    if (className == null || !CONTRACT_CLASS_NAME.matches(className)) {
        errors += "Service 接口类名必须是合法标识且以 Service 结尾"
    }
    val path = command.optionalText("path")
    if (path == null || !path.startsWith('/')) {
        errors += "Service 基础路径必须以 / 开头"
    }
    if (command.intOrDefault("version", 1) <= 0) {
        errors += "Service 契约版本必须大于 0"
    }
    validateOperations(command.arrayOrEmpty("operations", mapper), path, errors)
    val id = command.optionalLong("id")
    if (id != null) {
        requireEditableResource("lowcode_api_contract", id, "Service 契约")
    }
    if (contractCode != null && hasOtherContract(id, contractCode)) {
        errors += "contractCode 已存在: $contractCode"
    }
    return MetadataValidationResult(errors.isEmpty(), errors.distinct())
}

internal fun MetadataSession.saveContract(command: JsonNode): Any {
    val validation = validateContract(command)
    if (!validation.valid) {
        badRequest(validation.errors.joinToString("；"))
    }
    val id = command.optionalLong("id")
    val featureId = command.requiredLong("featureId")
    val operations = command.arrayOrEmpty("operations", mapper)
    val agentExposure = command.objectOrEmpty("agentExposure", mapper)
    if (id == null) {
        return insertContract(command, featureId, operations, agentExposure)
    }
    updateContract(id, command, featureId, operations, agentExposure)
    return true
}

internal fun MetadataSession.deleteContracts(ids: List<Long>): Boolean {
    ids.forEach { id ->
        requireEditableResource("lowcode_api_contract", id, "Service 契约")
        if (!deleteById("lowcode_api_contract", id)) {
            notFound("Service 契约不存在: $id")
        }
    }
    return true
}

private fun MetadataSession.contractIds(): List<Long> {
    val sql = "SELECT id FROM $schema.lowcode_api_contract ORDER BY contract_code"
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

private fun MetadataSession.insertContract(
    command: JsonNode,
    featureId: Long,
    operations: ArrayNode,
    agentExposure: ObjectNode,
): Long {
    val sql = """
        INSERT INTO $schema.lowcode_api_contract
            (feature_id, contract_code, name, class_name, path, status, version,
             description, operations, agent_exposure)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))
        RETURNING id
    """.trimIndent()
    return connection.prepareStatement(sql).use { statement ->
        bindContract(statement, command, featureId, operations, agentExposure)
        statement.executeQuery().use { rows ->
            rows.next()
            rows.getLong(1)
        }
    }
}

private fun MetadataSession.updateContract(
    id: Long,
    command: JsonNode,
    featureId: Long,
    operations: ArrayNode,
    agentExposure: ObjectNode,
) {
    val sql = """
        UPDATE $schema.lowcode_api_contract
        SET feature_id = ?, contract_code = ?, name = ?, class_name = ?, path = ?,
            status = ?, version = ?, description = ?, operations = CAST(? AS JSONB),
            agent_exposure = CAST(? AS JSONB), update_time = CURRENT_TIMESTAMP
        WHERE id = ?
    """.trimIndent()
    connection.prepareStatement(sql).use { statement ->
        bindContract(statement, command, featureId, operations, agentExposure)
        statement.setLong(11, id)
        if (statement.executeUpdate() != 1) {
            notFound("Service 契约不存在: $id")
        }
    }
}

private fun MetadataSession.bindContract(
    statement: java.sql.PreparedStatement,
    command: JsonNode,
    featureId: Long,
    operations: ArrayNode,
    agentExposure: ObjectNode,
) {
    statement.setLong(1, featureId)
    statement.setString(2, command.requiredText("contractCode"))
    statement.setString(3, command.requiredText("name"))
    statement.setString(4, command.requiredText("className"))
    statement.setString(5, command.requiredText("path"))
    statement.setInt(6, command.intOrDefault("status", 1))
    statement.setInt(7, command.intOrDefault("version", 1))
    statement.setString(8, command.optionalText("description"))
    statement.setString(9, mapper.writeValueAsString(operations))
    statement.setString(10, mapper.writeValueAsString(agentExposure))
}

private fun validateOperations(
    operations: ArrayNode,
    contractPath: String?,
    errors: MutableList<String>,
) {
    if (operations.isEmpty) {
        errors += "Service 契约至少需要一个操作"
        return
    }
    val codes = mutableSetOf<String>()
    operations.forEach { operation ->
        val code = operation.optionalText("operationCode")
        if (code == null || !CONTRACT_CODE.matches(code)) {
            errors += "operationCode 必须使用小驼峰标识"
        } else if (!codes.add(code)) {
            errors += "operationCode 重复: $code"
        }
        val path = operation.optionalText("path")
        if (path == null || !path.startsWith('/')) {
            errors += "操作 ${code.orEmpty()} 的 path 必须以 / 开头"
        } else if (contractPath != null && path != contractPath && !path.startsWith("$contractPath/")) {
            errors += "操作 ${code.orEmpty()} 的 path 必须以契约路径 $contractPath 为前缀"
        }
        if (operation.optionalText("method") !in HTTP_METHODS) {
            errors += "操作 ${code.orEmpty()} 的 HTTP method 不受支持"
        }
        if ((operation.optionalText("transport") ?: "HTTP") !in OPERATION_TRANSPORTS) {
            errors += "操作 ${code.orEmpty()} 的 transport 不受支持"
        }
        val authenticated = operation.booleanOrDefault("authenticated", true)
        if (authenticated && operation.booleanOrDefault("callContext", false) && operation.optionalText("permission") == null) {
            errors += "传递调用上下文的操作 ${code.orEmpty()} 必须配置权限标识"
        }
    }
}

private fun MetadataSession.hasOtherContract(id: Long?, contractCode: String): Boolean {
    val idFilter = if (id == null) "" else "AND id <> ?"
    val sql = "SELECT 1 FROM $schema.lowcode_api_contract WHERE contract_code = ? $idFilter"
    return exists(sql) {
        setString(1, contractCode)
        if (id != null) {
            setLong(2, id)
        }
    }
}

private fun ResultSet.toContract(mapper: tools.jackson.databind.ObjectMapper): ObjectNode =
    mapper.createObjectNode().apply {
        put("id", getLong("id"))
        put("featureId", getLong("feature_id"))
        put("contractCode", getString("contract_code"))
        put("name", getString("name"))
        put("packageName", getString("package_name"))
        put("className", getString("class_name"))
        put("path", getString("path"))
        put("contributorId", getString("contributor_id"))
        put("status", getInt("status"))
        put("version", getInt("version"))
        putNullable("description", getString("description"))
        putNode("operations", mapper.readArray(getString("operations")))
        putNode("agentExposure", mapper.readObject(getString("agent_exposure")))
    }
