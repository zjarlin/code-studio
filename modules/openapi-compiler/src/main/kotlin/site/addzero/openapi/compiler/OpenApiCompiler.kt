package site.addzero.openapi.compiler

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import site.addzero.platform.lowcode.generator.LsiLowcodeApiSchema
import site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoRef
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoSchema
import site.addzero.platform.lowcode.generator.LowcodeOperationTransport
import site.addzero.platform.lowcode.generator.componentSchemaName

/**
 * 将结构化契约元数据编译为 OpenAPI 文档。
 */
object OpenApiCompiler {
    private val objectMapper = ObjectMapper()

    fun compile(
        baseDocument: JsonNode,
        contracts: List<LsiOpenApiContract>,
    ): ObjectNode {
        require(baseDocument is ObjectNode) { "OpenAPI 基础文档必须是 JSON 对象" }
        val document = baseDocument.deepCopy()
        document.installPlatformSchemas()
        document.installContracts(contracts)
        JsonWireSchemaNormalizer.normalize(document)
        return document
    }

    private fun ObjectNode.installPlatformSchemas() {
        val schemas = objectNode("components").objectNode("schemas")
        schemas.setIfAbsent("CommonResult", commonResultSchema())
        schemas.setIfAbsent("CrudTreeMoveCommand", crudTreeMoveCommandSchema())
        schemas.setIfAbsent("CrudTreeChangeParentCommand", crudTreeChangeParentCommandSchema())
    }

    private fun ObjectNode.installContracts(contracts: List<LsiOpenApiContract>) {
        val paths = objectNode("paths")
        val schemas = objectNode("components").objectNode("schemas")
        contracts.sortedWith(compareBy(LsiOpenApiContract::path, LsiOpenApiContract::schemaName))
            .forEach { contract ->
                contract.dtoSchemas.forEach { dto ->
                    schemas.setIfAbsent(dto.schemaName, dto.toOpenApiSchema())
                }
                schemas.set(contract.schemaName, contract.toSchema())
                contract.modelCode?.let { modelCode ->
                    schemas.setIfAbsent(
                        LsiLowcodeDtoRef(modelCode = modelCode).componentSchemaName(),
                        objectMapper.createObjectNode().put("\$ref", "#/components/schemas/${contract.schemaName}"),
                    )
                }
                if ("LIST_BY_CONDITION" in contract.enabledOperations) {
                    schemas.set(contract.conditionSchemaName, contract.toConditionSchema())
                }
                if ("CREATE" in contract.enabledOperations || "UPSERT" in contract.enabledOperations) {
                    schemas.set(contract.createSchemaName, contract.toCreateSchema())
                }
                if ("UPDATE" in contract.enabledOperations || "UPSERT" in contract.enabledOperations) {
                    schemas.set(contract.updateSchemaName, contract.toUpdateSchema())
                }
                contract.operations().forEach { operation ->
                    paths.pathItem(operation.path)
                        .mergeOperation(operation.method, operation.toOpenApiOperation(contract))
                }
                contract.customOperations
                    .filter { operation -> operation.transport != LowcodeOperationTransport.INTERNAL }
                    .sortedBy(LsiLowcodeCustomOperation::operationCode)
                    .forEach { operation ->
                    paths.pathItem(operation.path)
                        .mergeOperation(operation.openApiMethod(), operation.toOpenApiOperation(contract))
                    }
            }
    }

    private fun ObjectNode.objectNode(name: String): ObjectNode =
        (get(name) as? ObjectNode) ?: putObject(name)

    private fun ObjectNode.pathItem(path: String): ObjectNode =
        (get(path) as? ObjectNode) ?: objectMapper.createObjectNode().also { value ->
            set(path, value)
        }

    /** 元数据定义 operation，仅保留其尚不能表达的额外响应状态与响应头。 */
    private fun ObjectNode.mergeOperation(method: String, metadataOperation: ObjectNode) {
        val routeOperation = get(method) as? ObjectNode
        val mergedOperation = metadataOperation.deepCopy()
        if (routeOperation != null) {
            mergedOperation.mergeRouteResponseExtensions(routeOperation)
        }
        set(method, mergedOperation)
    }

    private fun ObjectNode.mergeRouteResponseExtensions(routeOperation: ObjectNode) {
        val routeResponses = routeOperation["responses"] as? ObjectNode ?: return
        val metadataResponses = (get("responses") as? ObjectNode) ?: putObject("responses")
        routeResponses.properties().forEach { (status, routeResponse) ->
            val metadataResponse = metadataResponses[status]
            if (metadataResponse == null) {
                metadataResponses.set(status, routeResponse.deepCopy())
                return@forEach
            }
            if (routeResponse is ObjectNode && metadataResponse is ObjectNode) {
                metadataResponse.mergeRouteHeaders(routeResponse)
            }
        }
    }

    private fun ObjectNode.mergeRouteHeaders(routeResponse: ObjectNode) {
        val routeHeaders = routeResponse["headers"] as? ObjectNode ?: return
        val metadataHeaders = (get("headers") as? ObjectNode) ?: putObject("headers")
        routeHeaders.properties().forEach { (name, routeHeader) ->
            val metadataHeader = metadataHeaders[name]
            val mergedHeader = if (routeHeader is ObjectNode && metadataHeader is ObjectNode) {
                routeHeader.mergedWith(metadataHeader)
            } else {
                metadataHeader ?: routeHeader.deepCopy()
            }
            metadataHeaders.set(name, mergedHeader)
        }
    }

    private fun ObjectNode.mergedWith(metadata: ObjectNode): ObjectNode = deepCopy().apply {
        metadata.properties().forEach { (name, value) ->
            val existing = get(name)
            val merged = if (existing is ObjectNode && value is ObjectNode) {
                existing.mergedWith(value)
            } else {
                value.deepCopy()
            }
            set(name, merged)
        }
    }

    private fun ObjectNode.setIfAbsent(name: String, value: ObjectNode) {
        if (!has(name)) {
            set(name, value)
        }
    }

    private fun commonResultSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        putArray("required").add("code").add("msg").add("data")
        putObject("properties").apply {
            putObject("code").apply {
                put("type", "integer")
                put("format", "int32")
                put("description", "业务状态码，0 表示成功，非 0 表示业务错误。")
            }
            putObject("msg").apply {
                put("type", "string")
                put("description", "业务提示信息。")
            }
            putObject("data").apply {
                putArray("type").add("object").add("null")
                put("description", "业务响应数据。")
            }
        }
    }

    private fun crudTreeMoveCommandSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        putArray("required").add("sourceId").add("targetId").add("dropType")
        putObject("properties").apply {
            putObject("sourceId").put("type", "integer").put("format", "int64")
            putObject("targetId").put("type", "integer").put("format", "int64")
            putObject("dropType").put("type", "string").put("description", "拖拽位置：before、after 或 inner。")
        }
    }

    private fun crudTreeChangeParentCommandSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        putArray("required").add("nodeId")
        putObject("properties").apply {
            putObject("nodeId").put("type", "integer").put("format", "int64")
            putObject("parentId").apply {
                putArray("type").add("integer").add("null")
                put("format", "int64")
            }
        }
    }

    private fun LsiOpenApiContract.toSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        description?.let { value -> put("description", value) }
        val propertiesNode = putObject("properties")
        properties.forEach { property ->
            propertiesNode.set(property.name, property.toSchema())
        }
        val requiredNames = properties.filter(LsiOpenApiProperty::required).map(LsiOpenApiProperty::name)
        if (requiredNames.isNotEmpty()) {
            val requiredNode = putArray("required")
            requiredNames.forEach(requiredNode::add)
        }
        discriminator?.let { value ->
            val oneOfNode = putArray("oneOf")
            value.mapping.toSortedMap().values.distinct().forEach { ref ->
                oneOfNode.addObject().put("\$ref", "#/components/schemas/${ref.componentSchemaName()}")
            }
            putObject("discriminator").apply {
                put("propertyName", value.propertyName)
                val mappingNode = putObject("mapping")
                value.mapping.toSortedMap().forEach { (key, ref) ->
                    mappingNode.put(key, "#/components/schemas/${ref.componentSchemaName()}")
                }
            }
        }
    }

    private fun LsiOpenApiContract.toConditionSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("description", "未提供的字段不参与筛选。")
        val propertiesNode = putObject("properties")
        properties.filterNot { property -> property.name in CONDITION_IGNORED_PROPERTIES }
            .forEach { property -> propertiesNode.set(property.name, property.toSchema()) }
    }

    /** 新增请求不暴露由服务端生成的标识和审计属性。 */
    private fun LsiOpenApiContract.toCreateSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("additionalProperties", false)
        put("description", "新增请求；标识和审计字段由服务端维护。")
        val writableProperties = properties.filter { property ->
            property.createWritable && !property.identifier
        }
        val propertiesNode = putObject("properties")
        writableProperties.forEach { property ->
            propertiesNode.set(property.name, property.toSchema())
        }
        val requiredProperties = writableProperties.filter(LsiOpenApiProperty::required)
        if (requiredProperties.isNotEmpty()) {
            val requiredNode = putArray("required")
            requiredProperties.forEach { property -> requiredNode.add(property.name) }
        }
    }

    /** 更新请求只暴露允许修改的属性，并且只强制要求实体标识。 */
    private fun LsiOpenApiContract.toUpdateSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("additionalProperties", false)
        put("description", "更新请求；审计字段由服务端维护。")
        val writableProperties = properties.filter(LsiOpenApiProperty::updateWritable)
        val propertiesNode = putObject("properties")
        writableProperties.forEach { property ->
            propertiesNode.set(property.name, property.toSchema())
        }
        val identifiers = writableProperties.filter(LsiOpenApiProperty::identifier)
        if (identifiers.isNotEmpty()) {
            val requiredNode = putArray("required")
            identifiers.forEach { property -> requiredNode.add(property.name) }
        }
    }

    private fun LsiOpenApiProperty.toSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        if (required) {
            put("type", type)
        } else {
            putArray("type").add(type).add("null")
        }
        format?.let { value -> put("format", value) }
        description?.let { value -> put("description", value) }
        maxLength?.let { value -> put("maxLength", value) }
        referenceTargetModelCode?.let { targetModelCode ->
            putObject("x-lowcode-reference").apply {
                put("targetModelCode", targetModelCode)
                referencePropertyName?.let { value -> put("propertyName", value) }
            }
        }
        if (type == "array") {
            putObject("items").put("type", arrayItemType ?: "object")
        }
        if (enumValues.isNotEmpty()) {
            val enumNode = putArray("enum")
            enumValues.forEach(enumNode::add)
        }
    }

    private fun LsiOpenApiContract.operations(): List<LsiOpenApiOperation> =
        paths.flatMap { basePath -> operations(basePath) }

    private fun LsiOpenApiContract.operations(basePath: String): List<LsiOpenApiOperation> = buildList {
        if ("CREATE" in enabledOperations) {
            add(
                LsiOpenApiOperation(
                    "$basePath/create",
                    "post",
                    "新增$displayName",
                    BodyKind.CREATE_ENTITY,
                    bodyDescription = "填写要新增的${displayName}数据。",
                    response = ResponseKind.ENTITY,
                ),
            )
        }
        if ("UPSERT" in enabledOperations) {
            add(
                LsiOpenApiOperation(
                    "$basePath/upsert",
                    "post",
                    "保存$displayName",
                    BodyKind.UPSERT_ENTITY,
                    bodyDescription = "填写要保存的${displayName}数据；有主键时更新，无主键时新增。",
                    response = ResponseKind.ENTITY,
                ),
            )
        }
        if ("UPDATE" in enabledOperations) {
            add(
                LsiOpenApiOperation(
                    "$basePath/update",
                    "put",
                    "更新$displayName",
                    BodyKind.UPDATE_ENTITY,
                    bodyDescription = "填写要更新的${displayName}数据，并提供记录主键。",
                    response = ResponseKind.INTEGER,
                ),
            )
        }
        if ("DELETE" in enabledOperations) {
            add(LsiOpenApiOperation("$basePath/delete", "delete", "删除$displayName", parameters = listOf(QueryParameter("id", "integer", "int64")), response = ResponseKind.BOOLEAN))
        }
        if ("DELETE_LIST" in enabledOperations) {
            add(LsiOpenApiOperation("$basePath/delete-list", "delete", "批量删除$displayName", parameters = listOf(QueryParameter("ids", "array", itemType = "integer")), response = ResponseKind.BOOLEAN))
        }
        if ("GET" in enabledOperations) {
            add(LsiOpenApiOperation("$basePath/get", "get", "获取$displayName", parameters = listOf(QueryParameter("id", "integer", "int64")), response = ResponseKind.ENTITY))
        }
        if ("PAGE" in enabledOperations) {
            val parameters = listOf(
                QueryParameter("pageNo", "integer", "int32", false),
                QueryParameter("pageSize", "integer", "int32", false),
            ) + queryFields.flatMap { field -> field.toQueryParameters() }.distinctBy(QueryParameter::name)
            add(LsiOpenApiOperation("$basePath/page", "get", "分页查询$displayName", parameters = parameters, response = ResponseKind.PAGE))
        }
        if ("SIMPLE_LIST" in enabledOperations) {
            val parameters = queryFields.flatMap { field -> field.toQueryParameters() }
                .distinctBy(QueryParameter::name)
            SIMPLE_LIST_PATHS.forEach { path ->
                add(
                    LsiOpenApiOperation(
                        "$basePath$path",
                        "get",
                        "按条件获取$displayName 列表",
                        parameters = parameters,
                        response = ResponseKind.LIST,
                    ),
                )
            }
        }
        if ("LIST_BY_CONDITION" in enabledOperations) {
            add(
                LsiOpenApiOperation(
                    "$basePath/list-by-condition",
                    "post",
                    "按条件查询$displayName 列表",
                    BodyKind.OPTIONAL_ENTITY,
                    bodyDescription = "未提供的字段不参与筛选，空对象表示不附加实体条件。",
                    response = ResponseKind.LIST,
                ),
            )
        }
        if ("TREE" in enabledOperations) {
            addTreeOperations(basePath, displayName)
        }
        excel?.let { excel -> addExcelOperations(basePath, displayName, excel, queryFields) }
    }

    private fun MutableList<LsiOpenApiOperation>.addTreeOperations(
        basePath: String,
        displayName: String,
    ) {
        add(
            LsiOpenApiOperation(
                "$basePath/tree",
                "get",
                "获取$displayName 树",
                parameters = listOf(QueryParameter("keyword", "string", required = false)),
                response = ResponseKind.LIST,
            ),
        )
        add(
            LsiOpenApiOperation(
                "$basePath/breadcrumb",
                "get",
                "获取$displayName 面包屑",
                parameters = listOf(QueryParameter("nodeId", "integer", "int64", description = "节点编号")),
                response = ResponseKind.BREADCRUMB,
            ),
        )
        add(
            LsiOpenApiOperation(
                "$basePath/move",
                "put",
                "移动$displayName 节点",
                BodyKind.TREE_MOVE,
                bodyDescription = "提供源节点、目标节点和拖拽位置。",
                response = ResponseKind.BOOLEAN,
            ),
        )
        add(
            LsiOpenApiOperation(
                "$basePath/change-parent",
                "put",
                "修改$displayName 父节点",
                BodyKind.TREE_CHANGE_PARENT,
                bodyDescription = "提供节点编号和新的父节点编号；父节点为空表示移动到根级。",
                response = ResponseKind.BOOLEAN,
            ),
        )
    }

    private fun MutableList<LsiOpenApiOperation>.addExcelOperations(
        basePath: String,
        displayName: String,
        excel: LsiOpenApiExcel,
        queryFields: List<LsiOpenApiQueryField>,
    ) {
        if (excel.exportEnabled) {
            val parameters = queryFields.flatMap { field -> field.toQueryParameters() }
                .distinctBy(QueryParameter::name)
            add(
                LsiOpenApiOperation(
                    "$basePath/export-excel",
                    "get",
                    "导出$displayName Excel",
                    parameters = parameters,
                    response = ResponseKind.FILE,
                    responseContentType = excel.fileName.excelContentType(),
                ),
            )
        }
        if (excel.importEnabled) {
            add(
                LsiOpenApiOperation(
                    "$basePath/get-import-template",
                    "get",
                    "获取$displayName 导入模板",
                    response = ResponseKind.FILE,
                    responseContentType = excel.templateFileName.excelContentType(),
                ),
            )
            add(
                LsiOpenApiOperation(
                    "$basePath/import",
                    "post",
                    "导入$displayName Excel",
                    BodyKind.EXCEL_FILE,
                    bodyDescription = "上传符合导入模板的 Excel 文件。",
                    parameters = listOf(QueryParameter("updateSupport", "boolean", required = false, description = "是否更新已存在数据")),
                    response = ResponseKind.INTEGER,
                ),
            )
        }
    }

    private fun LsiOpenApiQueryField.toQueryParameters(): List<QueryParameter> = when (operator) {
        "IN", "NOT_IN", "TIME_RANGE" ->
            listOf(
                QueryParameter(
                    parameterName,
                    "array",
                    required = required,
                    itemType = type,
                    itemFormat = format,
                    description = description,
                    enumValues = enumValues,
                ),
            )
        "BETWEEN" -> listOf(
            QueryParameter(parameterName, type, format, false, description = description, enumValues = enumValues),
            QueryParameter(
                checkNotNull(endParameterName),
                type,
                format,
                false,
                description = description,
                enumValues = enumValues,
            ),
        )
        else -> listOf(
            QueryParameter(
                parameterName,
                type,
                format,
                required,
                description = description,
                enumValues = enumValues,
            ),
        )
    }

    private fun LsiOpenApiOperation.toOpenApiOperation(contract: LsiOpenApiContract): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("summary", summary)
            putArray("tags").add(contract.displayName)
            if (contract.authenticated) {
                putArray("security").addObject().putArray("jwt")
            }
            body?.let { value -> set("requestBody", value.requestBody(contract, bodyDescription)) }
            if (parameters.isNotEmpty()) {
                val parameterNodes = putArray("parameters")
                parameters.forEach { parameter -> parameterNodes.add(parameter.toOpenApiParameter()) }
            }
            putObject("responses").set("200", response.response(contract, responseContentType))
        }

    private fun BodyKind.requestBody(
        contract: LsiOpenApiContract,
        description: String?,
    ): ObjectNode = objectMapper.createObjectNode().apply {
        put("required", this@requestBody != BodyKind.OPTIONAL_ENTITY)
        description?.let { value -> put("description", value) }
        if (this@requestBody == BodyKind.EXCEL_FILE) {
            putObject("content").putObject("multipart/form-data").putObject("schema").apply {
                put("type", "object")
                putArray("required").add("file")
                putObject("properties").putObject("file").put("type", "string").put("format", "binary")
            }
            return@apply
        }
        putObject("content").putObject("application/json").apply {
            val schema = putObject("schema")
            if (this@requestBody == BodyKind.UPSERT_ENTITY) {
                schema.putArray("oneOf").apply {
                    addObject().put("\$ref", "#/components/schemas/${contract.createSchemaName}")
                    addObject().put("\$ref", "#/components/schemas/${contract.updateSchemaName}")
                }
            } else {
                schema.put("\$ref", schemaReference(contract))
            }
            if (this@requestBody == BodyKind.OPTIONAL_ENTITY) {
                set("example", objectMapper.createObjectNode())
            }
        }
    }

    private fun QueryParameter.toOpenApiParameter(): ObjectNode = objectMapper.createObjectNode().apply {
        put("name", name)
        put("in", "query")
        put("required", required)
        description?.let { value -> put("description", value) }
        putObject("schema").apply {
            put("type", type)
            if (type == "array") {
                putObject("items").apply {
                    put("type", itemType ?: "string")
                    itemFormat?.let { value -> put("format", value) }
                    if (enumValues.isNotEmpty()) {
                        val enumNode = putArray("enum")
                        enumValues.forEach(enumNode::add)
                    }
                }
            } else {
                format?.let { value -> put("format", value) }
                if (enumValues.isNotEmpty()) {
                    val enumNode = putArray("enum")
                    enumValues.forEach(enumNode::add)
                }
            }
        }
    }

    private fun ResponseKind.response(
        contract: LsiOpenApiContract,
        contentType: String?,
    ): ObjectNode = objectMapper.createObjectNode().apply {
        if (this@response == ResponseKind.FILE) {
            put("description", "Excel 文件")
            putObject("content").putObject(contentType ?: XLSX_CONTENT_TYPE).putObject("schema").apply {
                put("type", "string")
                put("format", "binary")
            }
            return@apply
        }
        put("description", "成功或业务错误")
        putObject("content").putObject("application/json").putObject("schema")
            .set("allOf", schema(contract))
    }

    private fun LsiLowcodeCustomOperation.toOpenApiOperation(contract: LsiOpenApiContract): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("operationId", "${contract.schemaName}_$operationCode")
            put("summary", name)
            description?.let { value -> put("description", value) }
            putArray("tags").add(contract.displayName)
            put("x-lowcode-contract", true)
            put("x-lowcode-transport", transport.name)
            if (authenticated) {
                putArray("security").addObject().putArray("jwt")
            }
            if (parameters.isNotEmpty()) {
                val parameterNodes = putArray("parameters")
                parameters.forEach { parameter ->
                    parameterNodes.addObject().apply {
                        put("name", parameter.name)
                        put("in", parameter.location.name.lowercase())
                        put("required", parameter.required)
                        parameter.description?.let { value -> put("description", value) }
                        set("schema", parameter.schema.toOpenApiSchema())
                    }
                }
            }
            requestBody?.let { body ->
                putObject("requestBody").apply {
                    put("required", body.required)
                    body.description?.let { value -> put("description", value) }
                    putObject("content").putObject(body.contentType)
                        .set("schema", body.schema.toOpenApiSchema())
                }
            }
            val responses = putObject("responses")
            installCustomOperationResponse(responses)
        }

    private fun LsiLowcodeCustomOperation.installCustomOperationResponse(responses: ObjectNode) {
        when (transport) {
            LowcodeOperationTransport.HTTP -> responses.putObject("200").apply {
                val body = responseBody
                put("description", body?.description ?: "成功或业务错误")
                if (body == null) {
                    putObject("content").putObject("application/json").putObject("schema")
                        .put("\$ref", "#/components/schemas/CommonResult")
                } else {
                    val bodySchema = body.schema.toOpenApiSchema()
                    val schema = if (body.required) bodySchema else bodySchema.asNullable()
                    val responseSchema = if (responseEnvelope) schema.inCommonResult() else schema
                    putObject("content").putObject(body.contentType).set("schema", responseSchema)
                }
            }
            LowcodeOperationTransport.SSE -> responses.putObject("200").apply {
                put("description", responseBody?.description ?: "Server-Sent Events 事件流")
                val schema = responseBody?.schema?.toOpenApiSchema()
                    ?: objectMapper.createObjectNode().put("type", "string")
                putObject("content").putObject("text/event-stream").set("schema", schema)
            }
            LowcodeOperationTransport.WEBSOCKET -> responses.putObject("101").apply {
                put("description", "WebSocket 协议升级")
            }
            LowcodeOperationTransport.INTERNAL -> error("INTERNAL 操作不能编译为 OpenAPI: $operationCode")
        }
    }

    private fun LsiLowcodeCustomOperation.openApiMethod(): String =
        if (transport == LowcodeOperationTransport.HTTP) method.name.lowercase() else "get"

    private fun LsiLowcodeApiSchema.toOpenApiSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        typeRef?.let { ref ->
            put("\$ref", "#/components/schemas/${ref.componentSchemaName()}")
            return@apply
        }
        type?.let { value -> put("type", value) }
        format?.let { value -> put("format", value) }
        description?.let { value -> put("description", value) }
        if (properties.isNotEmpty()) {
            val propertiesNode = putObject("properties")
            properties.toSortedMap().forEach { (name, schema) ->
                propertiesNode.set(name, schema.toOpenApiSchema())
            }
        }
        if (required.isNotEmpty()) {
            val requiredNode = putArray("required")
            required.sorted().forEach(requiredNode::add)
        }
        items?.let { schema -> set("items", schema.toOpenApiSchema()) }
        if (enumValues.isNotEmpty()) {
            val enumNode = putArray("enum")
            enumValues.forEach(enumNode::add)
        }
        if (oneOf.isNotEmpty()) {
            val oneOfNode = putArray("oneOf")
            oneOf.forEach { schema -> oneOfNode.add(schema.toOpenApiSchema()) }
        }
    }

    private fun LsiLowcodeDtoSchema.toOpenApiSchema(): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("x-kotlin-class", className)
        description?.let { value -> put("description", value) }
        val propertiesNode = putObject("properties")
        properties.toSortedMap().forEach { (name, schema) ->
            propertiesNode.set(name, schema.toOpenApiSchema().apply {
                validations[name]
                    ?.singleOrNull { rule -> rule.code == "maxLength" }
                    ?.parameters
                    ?.get("value")
                    ?.toIntOrNull()
                    ?.let { maximum -> put("maxLength", maximum) }
            })
        }
        val runtimeRequired = required + validations
            .filterValues { rules -> rules.any { rule -> rule.code == "notBlank" || rule.code == "notEmpty" } }
            .keys
        if (runtimeRequired.isNotEmpty()) {
            val requiredNode = putArray("required")
            runtimeRequired.sorted().forEach(requiredNode::add)
        }
    }

    private fun ObjectNode.inCommonResult(): ObjectNode = objectMapper.createObjectNode().apply {
        putArray("allOf").apply {
            addObject().put("\$ref", "#/components/schemas/CommonResult")
            addObject().putObject("properties").set("data", this@inCommonResult)
        }
    }

    private fun ObjectNode.asNullable(): ObjectNode = objectMapper.createObjectNode().apply {
        putArray("oneOf").apply {
            add(this@asNullable)
            addObject().put("type", "null")
        }
    }

    private fun ResponseKind.schema(contract: LsiOpenApiContract) = objectMapper.createArrayNode().apply {
        addObject().put("\$ref", "#/components/schemas/CommonResult")
        addObject().putObject("properties").set("data", dataSchema(contract).apply {
            put("description", "业务响应数据。")
        })
    }

    private fun ResponseKind.dataSchema(contract: LsiOpenApiContract): ObjectNode =
        objectMapper.createObjectNode().apply {
            when (this@dataSchema) {
                ResponseKind.ENTITY -> put("\$ref", "#/components/schemas/${contract.schemaName}")
                ResponseKind.INTEGER -> put("type", "integer")
                ResponseKind.BOOLEAN -> put("type", "boolean")
                ResponseKind.LIST -> {
                    put("type", "array")
                    putObject("items").put("\$ref", "#/components/schemas/${contract.schemaName}")
                }
                ResponseKind.PAGE -> {
                    put("type", "object")
                    putObject("properties").apply {
                        putObject("list").apply {
                            put("type", "array")
                            putObject("items").put("\$ref", "#/components/schemas/${contract.schemaName}")
                        }
                        putObject("total").put("type", "integer").put("format", "int64")
                    }
                }
                ResponseKind.BREADCRUMB -> {
                    put("type", "object")
                    putObject("properties").apply {
                        putObject("nodeId").put("type", "integer").put("format", "int64")
                        putObject("nodes").apply {
                            put("type", "array")
                            putObject("items").put("\$ref", "#/components/schemas/${contract.schemaName}")
                        }
                        putObject("breadcrumbText").put("type", "string")
                    }
                }
                ResponseKind.FILE -> error("文件响应不使用 CommonResult 数据模型")
            }
        }

    private fun BodyKind.schemaReference(contract: LsiOpenApiContract): String = when (this) {
        BodyKind.CREATE_ENTITY -> "#/components/schemas/${contract.createSchemaName}"
        BodyKind.UPSERT_ENTITY -> error("保存请求使用新增和修改请求的 oneOf Schema")
        BodyKind.UPDATE_ENTITY -> "#/components/schemas/${contract.updateSchemaName}"
        BodyKind.OPTIONAL_ENTITY -> "#/components/schemas/${contract.conditionSchemaName}"
        BodyKind.TREE_MOVE -> "#/components/schemas/CrudTreeMoveCommand"
        BodyKind.TREE_CHANGE_PARENT -> "#/components/schemas/CrudTreeChangeParentCommand"
        BodyKind.EXCEL_FILE -> error("Excel 文件使用 multipart/form-data")
    }

    private val LsiOpenApiContract.conditionSchemaName: String
        get() = "${schemaName}Condition"

    private val LsiOpenApiContract.createSchemaName: String
        get() = "${schemaName}CreateRequest"

    private val LsiOpenApiContract.updateSchemaName: String
        get() = "${schemaName}UpdateRequest"

    private val LsiOpenApiContract.displayName: String
        get() = description
            ?.substringBefore('。')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: name

    private fun String.excelContentType(): String =
        if (endsWith(".xls", ignoreCase = true)) XLS_CONTENT_TYPE else XLSX_CONTENT_TYPE

    private data class LsiOpenApiOperation(
        val path: String,
        val method: String,
        val summary: String,
        val body: BodyKind? = null,
        val bodyDescription: String? = null,
        val parameters: List<QueryParameter> = emptyList(),
        val response: ResponseKind,
        val responseContentType: String? = null,
    )

    private data class QueryParameter(
        val name: String,
        val type: String,
        val format: String? = null,
        val required: Boolean = true,
        val itemType: String? = null,
        val itemFormat: String? = null,
        val description: String? = null,
        val enumValues: List<String> = emptyList(),
    )

    private enum class BodyKind {
        CREATE_ENTITY,
        UPSERT_ENTITY,
        UPDATE_ENTITY,
        OPTIONAL_ENTITY,
        TREE_MOVE,
        TREE_CHANGE_PARENT,
        EXCEL_FILE,
    }

    private enum class ResponseKind { ENTITY, INTEGER, BOOLEAN, LIST, PAGE, BREADCRUMB, FILE }

    private const val XLS_CONTENT_TYPE = "application/vnd.ms-excel"
    private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private val CONDITION_IGNORED_PROPERTIES = setOf("id", "createTime", "updateTime", "updater", "creator")
    private val SIMPLE_LIST_PATHS = listOf(
        "/simple-list",
        "/list",
        "/list-all",
        "/list-by-condition",
        "/list-simple",
        "/list-all-simple",
    )
}
