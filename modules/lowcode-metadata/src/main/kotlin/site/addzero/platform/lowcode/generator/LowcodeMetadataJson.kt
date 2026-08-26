package site.addzero.platform.lowcode.generator

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import site.addzero.dto.compiler.LsiDtoType
import site.addzero.dto.compiler.LsiDtoAnnotation
import site.addzero.dto.compiler.LsiDtoAnnotationArgument
import site.addzero.dto.compiler.LsiDtoAnnotationArgumentKind
import site.addzero.dto.compiler.LsiDtoAnnotationUseSiteTarget
import site.addzero.dto.compiler.LsiDtoDefaultValue
import site.addzero.dto.compiler.LsiDtoDefaultValueKind
import site.addzero.validation.compiler.LsiValidationRule

/**
 * 把数据库 JSONB 字段解析为低代码 LSI。
 */
internal object LowcodeMetadataJson {
    private val objectMapper = ObjectMapper()

    fun readDtoTypes(value: String?): List<LsiDtoType> =
        value?.let { json -> objectMapper.readTree(json).requiredArray().map(::readDtoType) }.orEmpty()

    fun readEntityConfig(value: String?): LsiLowcodeEntityConfig {
        val node = value?.let { json -> objectMapper.readTree(json) } ?: return LsiLowcodeEntityConfig()
        return LsiLowcodeEntityConfig(
            sourceMode = LowcodeEntitySourceMode.valueOf(
                node.optionalText("sourceMode") ?: LowcodeEntitySourceMode.GENERATED.name,
            ),
            sourceQualifiedName = node.optionalText("sourceQualifiedName"),
            sourceContributorId = node.optionalText("sourceContributorId"),
            baseMode = LowcodeEntityBaseMode.valueOf(
                node.optionalText("baseMode") ?: LowcodeEntityBaseMode.DEFAULT.name,
            ),
            baseModels = node.stringList("baseModels").map(LowcodeBaseModel::valueOf),
            superTypes = node.stringList("superTypes"),
            inheritedProperties = node.arrayOrEmpty("inheritedProperties").map(::readInheritedProperty),
            inheritedRelationCodes = node.stringList("inheritedRelationCodes"),
            fieldAnnotations = node.optionalObject("fieldAnnotations")
                ?.properties()
                ?.asSequence()
                ?.associate { entry -> entry.key to entry.value.requiredArray().map(::readDtoAnnotation) }
                .orEmpty(),
            relationOrderings = node.optionalObject("relationOrderings")
                ?.properties()
                ?.asSequence()
                ?.associate { entry -> entry.key to entry.value.requiredArray().map(JsonNode::asString) }
                .orEmpty(),
            formulaProperties = node.arrayOrEmpty("formulaProperties").map(::readFormulaProperty),
            transientProperties = node.arrayOrEmpty("transientProperties").map(::readTransientProperty),
            microServiceName = if (node.has("microServiceName")) {
                node.optionalText("microServiceName")
            } else {
                LsiLowcodeEntityConfig().microServiceName
            },
            inheritanceRoot = node.optionalObject("inheritanceRoot")?.let(::readInheritanceRoot),
            inheritanceSubtype = node.optionalObject("inheritanceSubtype")?.let(::readInheritanceSubtype),
        )
    }

    private fun readInheritanceRoot(node: JsonNode): LsiLowcodeInheritanceRoot =
        LsiLowcodeInheritanceRoot(
            strategy = LowcodeInheritanceStrategy.valueOf(node.requiredText("strategy")),
            discriminatorField = node.requiredText("discriminatorField"),
            instantiability = LowcodeEntityInstantiability.valueOf(
                node.optionalText("instantiability") ?: LowcodeEntityInstantiability.ABSTRACT.name,
            ),
            discriminatorValue = node.optionalText("discriminatorValue"),
            joinedTableDissociateAction = LowcodeJoinedTableDissociateAction.valueOf(
                node.optionalText("joinedTableDissociateAction")
                    ?: LowcodeJoinedTableDissociateAction.DELETE.name,
            ),
        )

    private fun readInheritanceSubtype(node: JsonNode): LsiLowcodeInheritanceSubtype =
        LsiLowcodeInheritanceSubtype(
            parentModelCode = node.requiredText("parentModelCode"),
            discriminatorValue = node.optionalText("discriminatorValue"),
            instantiability = LowcodeEntityInstantiability.valueOf(
                node.optionalText("instantiability") ?: LowcodeEntityInstantiability.AUTO.name,
            ),
        )

    private fun readFormulaProperty(node: JsonNode): LsiLowcodeFormulaProperty =
        LsiLowcodeFormulaProperty(
            propertyCode = node.requiredText("propertyCode"),
            label = node.requiredText("label"),
            kotlinType = node.requiredText("kotlinType"),
            kind = LowcodeFormulaKind.valueOf(node.requiredText("kind")),
            expression = node.requiredText("expression"),
            dependencies = node.stringList("dependencies"),
            nullable = node.booleanOrDefault("nullable", false),
            description = node.optionalText("description"),
        )

    private fun readTransientProperty(node: JsonNode): LsiLowcodeTransientProperty =
        LsiLowcodeTransientProperty(
            propertyCode = node.requiredText("propertyCode"),
            label = node.requiredText("label"),
            kotlinType = node.requiredText("kotlinType"),
            kind = LowcodeTransientKind.valueOf(
                node.optionalText("kind") ?: LowcodeTransientKind.DRAFT.name,
            ),
            resolverValueType = node.optionalText("resolverValueType"),
            nullable = node.booleanOrDefault("nullable", false),
            description = node.optionalText("description"),
            dictionaryCode = node.optionalText("dictionaryCode"),
        )

    fun readDtos(value: String?): List<LsiLowcodeDto> = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.requiredArray()
        ?.map(::readDto)
        .orEmpty()

    fun readDtoFields(value: String?): List<LsiLowcodeDtoField> = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.requiredArray()
        ?.map(::readDtoField)
        .orEmpty()

    fun readDtoAnnotations(value: String?): List<LsiDtoAnnotation> = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.requiredArray()
        ?.map(::readDtoAnnotation)
        .orEmpty()

    fun readRoute(value: String?): LsiLowcodeRoute? = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.takeUnless(JsonNode::isNull)
        ?.let(::readRoute)

    fun readOperations(value: String?): List<LsiLowcodeCustomOperation> = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.requiredArray()
        ?.map(::readCustomOperation)
        .orEmpty()

    fun readAgentExposure(value: String?): LsiAgentExposure = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.takeUnless(JsonNode::isNull)
        ?.let(::readAgentExposure)
        ?: LsiAgentExposure()

    fun readStringList(value: String?): List<String> = value
        ?.let { json -> objectMapper.readTree(json) }
        ?.requiredArray()
        ?.map(JsonNode::asString)
        .orEmpty()

    private fun readInheritedProperty(node: JsonNode): LsiLowcodeInheritedProperty =
        LsiLowcodeInheritedProperty(
            name = node.requiredText("name"),
            kotlinType = node.requiredText("kotlinType"),
            dbColumn = node.requiredText("dbColumn"),
            required = node.requiredBoolean("required"),
            storageKotlinType = node.optionalText("storageKotlinType"),
            id = node.booleanOrDefault("id", false),
            createWritable = node.booleanOrDefault("createWritable", true),
            updateWritable = node.booleanOrDefault("updateWritable", true),
            description = node.optionalText("description"),
            dictionaryCode = node.optionalText("dictionaryCode"),
            maxLength = node.get("maxLength")?.takeUnless(JsonNode::isNull)?.asInt(),
            defaultValue = node.optionalText("defaultValue"),
        )

    private fun readDto(node: JsonNode): LsiLowcodeDto = LsiLowcodeDto(
        dtoCode = node.requiredText("dtoCode"),
        className = node.requiredText("className"),
        kind = LowcodeDtoKind.valueOf(node.requiredText("kind")),
        selectionMode = LowcodeDtoSelectionMode.valueOf(
            node.optionalText("selectionMode") ?: LowcodeDtoSelectionMode.EXPLICIT.name,
        ),
        excludedPaths = node.stringList("excludedPaths"),
        fields = node.arrayOrEmpty("fields").map(::readDtoField),
    )

    private fun readDtoField(node: JsonNode): LsiLowcodeDtoField = LsiLowcodeDtoField(
        name = node.requiredText("name"),
        sourcePath = node.optionalText("sourcePath") ?: node.requiredText("name"),
        nullability = LowcodeDtoNullability.valueOf(
            node.optionalText("nullability") ?: LowcodeDtoNullability.INHERIT.name,
        ),
        schema = node.optionalObject("schema")?.let(::readApiSchema),
        kotlinType = node.optionalObject("kotlinType")?.let(::readDtoType),
        validations = node.arrayOrEmpty("validations").map(::readValidationRule),
        annotations = node.arrayOrEmpty("annotations").map(::readDtoAnnotation),
        defaultValue = node.optionalObject("defaultValue")?.let { defaultValue ->
            LsiDtoDefaultValue(
                kind = LsiDtoDefaultValueKind.valueOf(defaultValue.requiredText("kind")),
                value = defaultValue.optionalText("value"),
            )
        },
        description = node.optionalText("description"),
    )

    private fun readDtoAnnotation(node: JsonNode): LsiDtoAnnotation = LsiDtoAnnotation(
        qualifiedName = node.requiredText("qualifiedName"),
        useSiteTarget = node.optionalText("useSiteTarget")?.let(LsiDtoAnnotationUseSiteTarget::valueOf),
        arguments = node.arrayOrEmpty("arguments").map { argument ->
            LsiDtoAnnotationArgument(
                value = argument.requiredText("value"),
                kind = LsiDtoAnnotationArgumentKind.valueOf(
                    argument.optionalText("kind") ?: LsiDtoAnnotationArgumentKind.STRING.name,
                ),
                name = argument.optionalText("name"),
            )
        },
    )

    private fun readDtoType(node: JsonNode): LsiDtoType = LsiDtoType(
        qualifiedName = node.requiredText("qualifiedName"),
        arguments = node.arrayOrEmpty("arguments").map(::readDtoType),
        nullable = node.booleanOrDefault("nullable", false),
    )

    private fun readValidationRule(node: JsonNode): LsiValidationRule = LsiValidationRule(
        code = node.requiredText("code"),
        message = node.optionalText("message"),
        parameters = node.optionalObject("parameters")
            ?.properties()
            ?.asSequence()
            ?.associate { entry -> entry.key to entry.value.stringValue() }
            .orEmpty(),
    )

    private fun readRoute(node: JsonNode): LsiLowcodeRoute = LsiLowcodeRoute(
        packageName = node.requiredText("packageName"),
        qualifiedName = node.requiredText("qualifiedName"),
        className = node.requiredText("className"),
        modelCode = node.optionalText("modelCode"),
        description = node.optionalText("description"),
        path = node.requiredText("path"),
        generateController = node.booleanOrDefault("generateController", true),
        aliasPaths = node.stringList("aliasPaths"),
        authenticated = node.booleanOrDefault("authenticated", true),
        fetchPaths = node.stringList("fetchPaths"),
        excludePaths = node.stringList("excludePaths"),
        enabledOperations = node.stringList("enabledOperations").toSet(),
        tree = node.optionalObject("tree")?.let(::readTree),
        excel = node.optionalObject("excel")?.let(::readExcel),
        properties = node.requiredArray("properties").map(::readProperty),
        queryFields = node.arrayOrEmpty("queryFields").map(::readQueryField),
        defaultOrders = node.arrayOrEmpty("defaultOrders").map(::readOrder),
        customOperations = node.arrayOrEmpty("customOperations").map(::readCustomOperation),
        dtoSchemas = node.arrayOrEmpty("dtoSchemas").map(::readDtoSchema),
        discriminator = node.optionalObject("discriminator")?.let(::readDiscriminator),
        featurePackageName = node.optionalText("featurePackageName") ?: node.requiredText("packageName"),
        agentExposure = node.optionalObject("agentExposure")?.let(::readAgentExposure) ?: LsiAgentExposure(),
    )

    private fun readOrder(node: JsonNode): LsiLowcodeOrder = LsiLowcodeOrder(
        propertyName = node.requiredText("propertyName"),
        direction = LsiLowcodeOrderDirection.valueOf(
            node.optionalText("direction") ?: LsiLowcodeOrderDirection.ASC.name,
        ),
    )

    private fun readAgentExposure(node: JsonNode): LsiAgentExposure {
        val operations = node.optionalObject("operations")
            ?.properties()
            ?.associate { entry ->
                entry.key to LsiAgentOperationExposure(
                    confirmation = LsiAgentConfirmation.valueOf(
                        entry.value.optionalText("confirmation") ?: LsiAgentConfirmation.REQUIRED.name,
                    ),
                )
            }
            .orEmpty()
        return LsiAgentExposure(operations = operations)
    }

    private fun readDtoSchema(node: JsonNode): LsiLowcodeDtoSchema = LsiLowcodeDtoSchema(
        ref = readDtoRef(node.requiredObject("ref")),
        className = node.requiredText("className"),
        properties = node.requiredObject("properties")
            .properties()
            .associate { field -> field.key to readApiSchema(field.value) },
        required = node.stringList("required").toSet(),
        validations = node.optionalObject("validations")
            ?.properties()
            ?.associate { field -> field.key to field.value.toList().map(::readValidationRule) }
            .orEmpty(),
        description = node.optionalText("description"),
    )

    private fun readDiscriminator(node: JsonNode): LsiLowcodeDiscriminator = LsiLowcodeDiscriminator(
        propertyName = node.requiredText("propertyName"),
        mapping = node.requiredObject("mapping")
            .properties()
            .associate { field -> field.key to readDtoRef(field.value) },
    )

    private fun readCustomOperation(node: JsonNode): LsiLowcodeCustomOperation = LsiLowcodeCustomOperation(
        operationCode = node.requiredText("operationCode"),
        name = node.requiredText("name"),
        description = node.optionalText("description"),
        path = node.requiredText("path"),
        method = LowcodeHttpMethod.valueOf(node.optionalText("method") ?: LowcodeHttpMethod.POST.name),
        transport = LowcodeOperationTransport.valueOf(
            node.optionalText("transport") ?: LowcodeOperationTransport.HTTP.name,
        ),
        implementation = LowcodeOperationImplementation.valueOf(
            node.optionalText("implementation") ?: LowcodeOperationImplementation.GENERATED.name,
        ),
        authenticated = node.booleanOrDefault("authenticated", true),
        suspending = node.booleanOrDefault("suspending", true),
        permission = node.optionalText("permission"),
        callContext = node.booleanOrDefault("callContext", false),
        parameters = node.arrayOrEmpty("parameters").map(::readApiParameter),
        requestBody = node.optionalObject("requestBody")?.let(::readApiBody),
        responseBody = node.optionalObject("responseBody")?.let(::readApiBody),
        responseEnvelope = node.booleanOrDefault("responseEnvelope", true),
    )

    private fun readApiParameter(node: JsonNode): LsiLowcodeApiParameter = LsiLowcodeApiParameter(
        name = node.requiredText("name"),
        location = LowcodeApiParameterLocation.valueOf(node.requiredText("location")),
        required = node.booleanOrDefault("required", false),
        description = node.optionalText("description"),
        schema = readApiSchema(node.requiredObject("schema")),
    )

    private fun readApiBody(node: JsonNode): LsiLowcodeApiBody = LsiLowcodeApiBody(
        contentType = node.optionalText("contentType") ?: "application/json",
        required = node.booleanOrDefault("required", true),
        description = node.optionalText("description"),
        schema = readApiSchema(node.requiredObject("schema")),
    )

    private fun readApiSchema(node: JsonNode): LsiLowcodeApiSchema = LsiLowcodeApiSchema(
        type = node.optionalText("type"),
        typeRef = node.optionalObject("typeRef")?.let(::readDtoRef),
        kotlinType = node.optionalObject("kotlinType")?.let(::readDtoType),
        format = node.optionalText("format"),
        description = node.optionalText("description"),
        properties = node.optionalObject("properties")
            ?.properties()
            ?.associate { field -> field.key to readApiSchema(field.value) }
            .orEmpty(),
        required = node.stringList("required").toSet(),
        items = node.optionalObject("items")?.let(::readApiSchema),
        enumValues = node.stringList("enumValues"),
        oneOf = node.arrayOrEmpty("oneOf").map(::readApiSchema),
    )

    private fun readDtoRef(node: JsonNode): LsiLowcodeDtoRef = LsiLowcodeDtoRef(
        modelCode = node.optionalText("modelCode"),
        dtoCode = node.optionalText("dtoCode").orEmpty(),
    )

    private fun readTree(node: JsonNode): LsiLowcodeTree = LsiLowcodeTree(
        parentIdProperty = node.requiredText("parentIdProperty"),
        childrenProperty = node.requiredText("childrenProperty"),
        keywordProperty = node.requiredText("keywordProperty"),
        sortProperty = node.optionalText("sortProperty"),
    )

    private fun readExcel(node: JsonNode): LsiLowcodeExcel = LsiLowcodeExcel(
        importEnabled = node.requiredBoolean("importEnabled"),
        exportEnabled = node.requiredBoolean("exportEnabled"),
        customImport = node.booleanOrDefault("customImport", false),
        customExport = node.requiredBoolean("customExport"),
        fileName = node.requiredText("fileName"),
        templateFileName = node.requiredText("templateFileName"),
        sheetName = node.requiredText("sheetName"),
        templateSheetName = node.optionalText("templateSheetName") ?: node.requiredText("sheetName"),
        importColumns = node.arrayOrEmpty("importColumns").map(::readProperty),
        exportColumns = node.arrayOrEmpty("exportColumns").map(::readProperty),
    )

    private fun readProperty(node: JsonNode): LsiLowcodeProperty = LsiLowcodeProperty(
        name = node.requiredText("name"),
        type = node.requiredText("type"),
        format = node.optionalText("format"),
        required = node.requiredBoolean("required"),
        identifier = node.booleanOrDefault("identifier", false),
        createWritable = node.booleanOrDefault("createWritable", true),
        updateWritable = node.booleanOrDefault("updateWritable", true),
        arrayItemType = node.optionalText("arrayItemType"),
        description = node.optionalText("description"),
        dictionaryCode = node.optionalText("dictionaryCode"),
        referenceTargetModelCode = node.optionalText("referenceTargetModelCode"),
        referencePropertyName = node.optionalText("referencePropertyName"),
        enumValues = node.stringList("enumValues"),
        maxLength = node.get("maxLength")?.takeUnless(JsonNode::isNull)?.asInt(),
    )

    private fun readQueryField(node: JsonNode): LsiLowcodeQueryField = LsiLowcodeQueryField(
        propertyName = node.requiredText("propertyName"),
        parameterName = node.requiredText("parameterName"),
        operator = node.requiredText("operator"),
        type = node.requiredText("type"),
        format = node.optionalText("format"),
        endParameterName = node.optionalText("endParameterName"),
        required = node.booleanOrDefault("required", false),
        stateCases = node.arrayOrEmpty("stateCases").map(::readStateCase),
        description = node.optionalText("description"),
        enumValues = node.stringList("enumValues"),
    )

    private fun readStateCase(node: JsonNode): LsiLowcodeStateCase = LsiLowcodeStateCase(
        parameterValue = node.requiredText("parameterValue"),
        operator = node.requiredText("operator"),
        expression = node.requiredText("expression"),
    )

    private fun JsonNode.requiredText(name: String): String = requiredField(name)
        .takeUnless(JsonNode::isNull)
        ?.asString()
        ?: error("低代码元数据字段 $name 不能为空")

    private fun JsonNode.requiredBoolean(name: String): Boolean = requiredField(name).asBoolean()

    private fun JsonNode.booleanOrDefault(name: String, defaultValue: Boolean): Boolean =
        get(name)?.takeUnless(JsonNode::isNull)?.asBoolean() ?: defaultValue

    private fun JsonNode.optionalText(name: String): String? =
        get(name)?.takeUnless(JsonNode::isNull)?.asString()

    private fun JsonNode.stringList(name: String): List<String> =
        arrayOrEmpty(name).map { value -> value.asString() }

    private fun JsonNode.requiredArray(name: String): List<JsonNode> =
        requiredField(name).requiredArray()

    private fun JsonNode.requiredArray(): List<JsonNode> {
        require(isArray) { "低代码元数据必须是数组" }
        return toList()
    }

    private fun JsonNode.arrayOrEmpty(name: String): List<JsonNode> =
        get(name)?.takeUnless(JsonNode::isNull)?.requiredArray().orEmpty()

    private fun JsonNode.optionalObject(name: String): JsonNode? =
        get(name)?.takeUnless(JsonNode::isNull)?.also { value ->
            require(value.isObject) { "低代码元数据字段 $name 必须是对象" }
        }

    private fun JsonNode.requiredObject(name: String): JsonNode =
        requiredField(name).also { value ->
            require(value.isObject) { "低代码元数据字段 $name 必须是对象" }
        }

    private fun JsonNode.requiredField(name: String): JsonNode =
        get(name) ?: error("低代码元数据缺少字段: $name")
}
