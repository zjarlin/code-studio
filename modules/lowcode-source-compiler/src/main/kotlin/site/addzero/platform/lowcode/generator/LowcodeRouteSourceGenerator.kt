package site.addzero.platform.lowcode.generator

import site.addzero.dto.compiler.LsiDtoType

object LowcodeRouteSourceGenerator {

    /** 把独立业务契约渲染为 LSI 源码。 */
    fun toContractSource(contract: LsiLowcodeContract): String = contract.toSource()

    /** 返回独立业务契约源码需要的确定性导入。 */
    fun contractImports(contract: LsiLowcodeContract): Set<String> = buildSet {
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiBody")
        add("site.addzero.platform.lowcode.generator.LsiAgentConfirmation")
        add("site.addzero.platform.lowcode.generator.LsiAgentExposure")
        add("site.addzero.platform.lowcode.generator.LsiAgentOperationExposure")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiParameter")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiSchema")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeContract")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeDtoRef")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeDtoSchema")
        add("site.addzero.platform.lowcode.generator.LowcodeApiParameterLocation")
        add("site.addzero.platform.lowcode.generator.LowcodeHttpMethod")
        add("site.addzero.platform.lowcode.generator.LowcodeOperationTransport")
        if (contract.operations.any { operation -> operation.containsKotlinType() }) {
            add("site.addzero.dto.compiler.LsiDtoType")
        }
        if (contract.dtoSchemas.any { schema -> schema.validations.isNotEmpty() }) {
            add("site.addzero.validation.compiler.LsiValidationRule")
        }
        if (contract.operations.any { operation -> !operation.isGenerated }) {
            add("site.addzero.platform.lowcode.generator.LowcodeOperationImplementation")
        }
    }

    /** 把实体路由 LSI 渲染为运行时契约源码。 */
    fun toContractSource(route: LsiLowcodeRoute): String = buildString {
        appendLine("LowcodeRestContract(")
        appendLine("    modelName = \"${(route.displayName ?: route.className).escapeKotlin()}\",")
        route.modelCode?.let { modelCode ->
            appendLine("    modelCode = \"${modelCode.escapeKotlin()}\",")
        }
        appendLine("    modelDescription = ${route.description.toNullableStringSource()},")
        appendLine("    schemaName = \"${route.qualifiedName.replace('.', '_').escapeKotlin()}\",")
        appendLine("    path = \"${route.path.escapeKotlin()}\",")
        appendLine("    aliasPaths = ${route.aliasPaths.toStringListSource(4)},")
        appendLine("    enabledOperations = ${route.enabledOperations.toStringSetSource(4)},")
        appendLine("    authenticated = ${route.authenticated},")
        appendLine("    tree = ${route.tree.toContractSource()},")
        appendLine("    properties = ${route.properties.toPropertiesSource()},")
        appendLine("    queryFields = ${route.queryFields.toContractQueryFieldsSource()},")
        appendLine("    defaultOrders = ${route.defaultOrders.toContractOrdersSource()},")
        appendLine("    fetchPaths = ${route.fetchPaths.toStringListSource(4)},")
        appendLine("    excludePaths = ${route.excludePaths.toStringListSource(4)},")
        appendLine("    excel = ${route.excel.toContractSource()},")
        appendLine("    customOperations = ${route.customOperations.toCustomOperationsSource()},")
        appendLine("    dtoSchemas = ${route.dtoSchemas.toDtoSchemasSource()},")
        appendLine("    discriminator = ${route.discriminator.toSource()},")
        appendLine("    agentExposure = ${route.agentExposure.toSource()},")
        append(")")
    }

    /** 返回实体运行时契约源码需要的确定性导入。 */
    fun contractImports(route: LsiLowcodeRoute): Set<String> = buildSet {
        val runtimePackage = generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)
        add("$runtimePackage.LowcodeExcelContract")
        add("$runtimePackage.LowcodeQueryField")
        add("$runtimePackage.LowcodeQueryStateCase")
        add("$runtimePackage.LowcodeOrderContract")
        add("$runtimePackage.LowcodeOrderDirection")
        add("$runtimePackage.LowcodeRestContract")
        add("$runtimePackage.LowcodeSchemaProperty")
        add("$runtimePackage.LowcodeTreeContract")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiBody")
        add("site.addzero.platform.lowcode.generator.LsiAgentConfirmation")
        add("site.addzero.platform.lowcode.generator.LsiAgentExposure")
        add("site.addzero.platform.lowcode.generator.LsiAgentOperationExposure")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiParameter")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeApiSchema")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeDiscriminator")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeDtoRef")
        add("site.addzero.platform.lowcode.generator.LsiLowcodeDtoSchema")
        add("site.addzero.platform.lowcode.generator.LowcodeApiParameterLocation")
        add("site.addzero.platform.lowcode.generator.LowcodeHttpMethod")
        add("site.addzero.platform.lowcode.generator.LowcodeOperationTransport")
        if (route.dtoSchemas.any { schema -> schema.validations.isNotEmpty() }) {
            add("site.addzero.validation.compiler.LsiValidationRule")
        }
        if (route.customOperations.any { operation -> !operation.isGenerated }) {
            add("site.addzero.platform.lowcode.generator.LowcodeOperationImplementation")
        }
    }

    private fun List<LsiLowcodeOrder>.toContractOrdersSource(): String = joinToString(
        prefix = "listOf(",
        postfix = ")",
    ) { order ->
        "LowcodeOrderContract(\"${order.propertyName.escapeKotlin()}\", LowcodeOrderDirection.${order.direction.name})"
    }

    fun generateContracts(route: LsiLowcodeRoute): List<LowcodeGeneratedSource> = generateContracts(
        featurePackageName = route.featurePackageName,
        packageName = route.packageName,
        qualifiedName = route.qualifiedName,
        className = route.className,
        operations = route.customOperations,
    )

    private fun generateContracts(
        featurePackageName: String,
        packageName: String,
        qualifiedName: String,
        className: String,
        operations: List<LsiLowcodeCustomOperation>,
    ): List<LowcodeGeneratedSource> =
        operations
            .filter(LsiLowcodeCustomOperation::isGenerated)
            .sortedBy(LsiLowcodeCustomOperation::operationCode)
            .map { operation ->
                generateContract(
                    featurePackageName = featurePackageName,
                    packageName = packageName,
                    qualifiedName = qualifiedName,
                    className = className,
                    operation = operation,
                )
            }

    private fun generateContract(
        featurePackageName: String,
        packageName: String,
        qualifiedName: String,
        className: String,
        operation: LsiLowcodeCustomOperation,
    ): LowcodeGeneratedSource {
        val generatedPackage = featurePackageName.generatedLayout()
            .packageName(LowcodeGeneratedResourceKind.CONTROLLER)
        val contractName = "${className}${operation.operationCode.toPascalCase()}LowcodeContract"
        val handlerType = when (operation.transport) {
            LowcodeOperationTransport.HTTP -> "ApplicationCall"
            LowcodeOperationTransport.SSE -> "ServerSSESession"
            LowcodeOperationTransport.WEBSOCKET -> "DefaultWebSocketServerSession"
            LowcodeOperationTransport.INTERNAL -> error("INTERNAL 操作不能生成路由契约: ${operation.operationCode}")
        }
        val handlerMethod = when (operation.transport) {
            LowcodeOperationTransport.HTTP -> "handleHttp"
            LowcodeOperationTransport.SSE -> "handleSse"
            LowcodeOperationTransport.WEBSOCKET -> "handleWebSocket"
            LowcodeOperationTransport.INTERNAL -> error("INTERNAL 操作不能生成路由契约: ${operation.operationCode}")
        }
        val handlerArgument = when (operation.transport) {
            LowcodeOperationTransport.HTTP -> "call"
            LowcodeOperationTransport.SSE,
            LowcodeOperationTransport.WEBSOCKET,
            -> "session"
            LowcodeOperationTransport.INTERNAL -> error("INTERNAL 操作不能生成路由契约: ${operation.operationCode}")
        }
        val content = buildString {
            appendLine("package $generatedPackage")
            appendLine()
            appendLine("import io.ktor.server.application.ApplicationCall")
            appendLine("import io.ktor.server.sse.ServerSSESession")
            appendLine("import io.ktor.server.websocket.DefaultWebSocketServerSession")
            appendLine(
                "import ${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}." +
                    "LowcodeCustomOperationHandler",
            )
            appendLine()
            appendLine("/**")
            appendLine(" * ${operation.name.escapeKDoc()}")
            operation.description?.lineSequence()?.forEach { line ->
                appendLine(" * ${line.escapeKDoc()}")
            }
            appendLine(" */")
            appendLine("abstract class $contractName : LowcodeCustomOperationHandler {")
            appendLine("    final override val operationKey: String = \"${qualifiedName.replace('.', '_').escapeKotlin()}#${operation.operationCode.escapeKotlin()}\"")
            appendLine()
            appendLine("    final override suspend fun $handlerMethod($handlerArgument: $handlerType) {")
            appendLine("        execute($handlerArgument)")
            appendLine("    }")
            appendLine()
            appendLine("    abstract suspend fun execute($handlerArgument: $handlerType)")
            appendLine("}")
        }
        return LowcodeGeneratedSource(
            packageName = generatedPackage,
            fileName = contractName,
            content = content,
        )
    }

    private fun String.escapeKotlin(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\\$")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")

    private fun String.escapeKDoc(): String = replace("*/", "* /")

    private fun String.toPascalCase(): String =
        split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotEmpty)
            .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

    private fun String?.toNullableStringSource(): String =
        this?.let { value -> "\"${value.escapeKotlin()}\"" } ?: "null"

    private fun LsiLowcodeTree?.toContractSource(): String {
        if (this == null) {
            return "null"
        }
        return """
            LowcodeTreeContract(
                parentIdProperty = "${parentIdProperty.escapeKotlin()}",
                childrenProperty = "${childrenProperty.escapeKotlin()}",
                keywordProperty = "${keywordProperty.escapeKotlin()}",
                sortProperty = ${sortProperty.toNullableStringSource()},
            )
        """.trimIndent().prependIndent("        ").trimStart()
    }

    private fun LsiLowcodeExcel?.toContractSource(): String {
        if (this == null) {
            return "null"
        }
        return """
            LowcodeExcelContract(
                importEnabled = $importEnabled,
                exportEnabled = $exportEnabled,
                customImport = $customImport,
                customExport = $customExport,
                fileName = "${fileName.escapeKotlin()}",
                templateFileName = "${templateFileName.escapeKotlin()}",
                sheetName = "${sheetName.escapeKotlin()}",
                templateSheetName = "${templateSheetName.escapeKotlin()}",
                importColumns = ${importColumns.toPropertiesSource()},
                exportColumns = ${exportColumns.toPropertiesSource()},
            )
        """.trimIndent().prependIndent("        ").trimStart()
    }

    private fun List<LsiLowcodeCustomOperation>.toCustomOperationsSource(): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        return sortedBy(LsiLowcodeCustomOperation::operationCode).joinToString(
            prefix = "listOf(\n",
            postfix = "\n        )",
            separator = ",\n",
        ) { operation -> operation.toSource().prependIndent("            ") }
    }

    private fun LsiLowcodeCustomOperation.toSource(): String {
        val arguments = buildList {
            add("operationCode = \"${operationCode.escapeKotlin()}\",")
            add("name = \"${name.escapeKotlin()}\",")
            add("description = ${description.toNullableStringSource()},")
            add("path = \"${path.escapeKotlin()}\",")
            add("method = LowcodeHttpMethod.$method,")
            add("transport = LowcodeOperationTransport.$transport,")
            if (!isGenerated) {
                add(
                    "implementation = LowcodeOperationImplementation.$implementation,",
                )
            }
            add("authenticated = $authenticated,")
            add("suspending = $suspending,")
            permission?.let { value -> add("permission = \"${value.escapeKotlin()}\",") }
            add("callContext = $callContext,")
            add("parameters = ${parameters.toSource()},")
            add("requestBody = ${requestBody.toSource()},")
            add("responseBody = ${responseBody.toSource()},")
            add("responseEnvelope = $responseEnvelope,")
        }
        val argumentsSource = arguments.joinToString("\n") { argument -> argument.prependIndent("    ") }
        return "LsiLowcodeCustomOperation(\n$argumentsSource\n)"
    }

    private fun List<LsiLowcodeApiParameter>.toSource(): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        return joinToString(prefix = "listOf(\n", postfix = "\n)", separator = ",\n") { parameter ->
            """
                LsiLowcodeApiParameter(
                    name = "${parameter.name.escapeKotlin()}",
                    location = LowcodeApiParameterLocation.${parameter.location},
                    required = ${parameter.required},
                    description = ${parameter.description.toNullableStringSource()},
                    schema = ${parameter.schema.toSource()},
                )
            """.trimIndent().prependIndent("    ")
        }
    }

    private fun LsiLowcodeApiBody?.toSource(): String {
        if (this == null) {
            return "null"
        }
        return """
            LsiLowcodeApiBody(
                contentType = "${contentType.escapeKotlin()}",
                required = $required,
                description = ${description.toNullableStringSource()},
                schema = ${schema.toSource()},
            )
        """.trimIndent()
    }

    private fun LsiLowcodeApiSchema.toSource(): String {
        val propertiesSource = if (properties.isEmpty()) {
            "emptyMap()"
        } else {
            properties.toSortedMap().entries.joinToString(
                prefix = "mapOf(",
                postfix = ")",
            ) { (name, schema) -> "\"${name.escapeKotlin()}\" to ${schema.toSource()}" }
        }
        return "LsiLowcodeApiSchema(" +
            "type = ${type.toNullableStringSource()}, " +
            "typeRef = ${typeRef.toSource()}, " +
            "kotlinType = ${kotlinType.toSource()}, " +
            "format = ${format.toNullableStringSource()}, " +
            "description = ${description.toNullableStringSource()}, " +
            "properties = $propertiesSource, " +
            "required = ${required.toStringSetSource(0)}, " +
            "items = ${items?.toSource() ?: "null"}, " +
            "enumValues = ${enumValues.toStringListSource(0)}, " +
            "oneOf = ${oneOf.toSchemaListSource()}" +
            ")"
    }

    private fun LsiDtoType?.toSource(): String = this?.let { type ->
        "LsiDtoType(qualifiedName = \"${type.qualifiedName.escapeKotlin()}\", " +
            "arguments = ${type.arguments.joinToString(prefix = "listOf(", postfix = ")") { argument -> argument.toSource() }}, " +
            "nullable = ${type.nullable})"
    } ?: "null"

    private fun LsiLowcodeCustomOperation.containsKotlinType(): Boolean =
        parameters.any { parameter -> parameter.schema.containsKotlinType() } ||
            requestBody?.schema?.containsKotlinType() == true ||
            responseBody?.schema?.containsKotlinType() == true

    private fun LsiLowcodeApiSchema.containsKotlinType(): Boolean =
        kotlinType != null || properties.values.any { schema -> schema.containsKotlinType() } ||
            items?.containsKotlinType() == true || oneOf.any { schema -> schema.containsKotlinType() }

    private fun LsiLowcodeDtoRef?.toSource(): String = this?.let { ref ->
        "LsiLowcodeDtoRef(modelCode = ${ref.modelCode.toNullableStringSource()}, " +
            "dtoCode = \"${ref.dtoCode.escapeKotlin()}\")"
    } ?: "null"

    private fun LsiLowcodeDiscriminator?.toSource(): String {
        if (this == null) return "null"
        val mappingSource = mapping.toSortedMap().entries.joinToString(
            prefix = "mapOf(",
            postfix = ")",
        ) { (value, ref) -> "\"${value.escapeKotlin()}\" to ${ref.toSource()}" }
        return "LsiLowcodeDiscriminator(" +
            "propertyName = \"${propertyName.escapeKotlin()}\", " +
            "mapping = $mappingSource)"
    }

    private fun LsiLowcodeContract.toSource(): String = """
        LsiLowcodeContract(
            contractCode = "${contractCode.escapeKotlin()}",
            name = "${name.escapeKotlin()}",
            description = ${description.toNullableStringSource()},
            packageName = "${packageName.escapeKotlin()}",
            className = "${className.escapeKotlin()}",
            path = "${path.escapeKotlin()}",
            contributorId = ${contributorId.toNullableStringSource()},
            operations = ${operations.toCustomOperationsSource()},
            dtoSchemas = ${dtoSchemas.toDtoSchemasSource()},
            agentExposure = ${agentExposure.toSource()},
        )
    """.trimIndent()

    private fun LsiAgentExposure.toSource(): String {
        if (operations.isEmpty()) return "LsiAgentExposure()"
        val operationsSource = operations.toSortedMap().entries.joinToString(
            prefix = "mapOf(\n",
            postfix = "\n    )",
            separator = ",\n",
        ) { (operationCode, exposure) ->
            "    \"${operationCode.escapeKotlin()}\" to " +
                "LsiAgentOperationExposure(confirmation = LsiAgentConfirmation.${exposure.confirmation})"
        }
        return "LsiAgentExposure(\n    operations = $operationsSource,\n)"
    }

    private fun List<LsiLowcodeDtoSchema>.toDtoSchemasSource(): String {
        if (isEmpty()) return "emptyList()"
        return sortedBy(LsiLowcodeDtoSchema::schemaName).joinToString(
            prefix = "listOf(\n",
            postfix = "\n        )",
            separator = ",\n",
        ) { schema ->
            """
            LsiLowcodeDtoSchema(
                ref = ${schema.ref.toSource()},
                className = "${schema.className.escapeKotlin()}",
                properties = ${schema.properties.toSortedMap().entries.joinToString(
                    prefix = "mapOf(",
                    postfix = ")",
                ) { (name, property) -> "\"${name.escapeKotlin()}\" to ${property.toSource()}" }},
                required = ${schema.required.toStringSetSource(0)},
                validations = ${schema.validations.toValidationRulesSource()},
                description = ${schema.description.toNullableStringSource()},
            )
            """.trimIndent().prependIndent("            ")
        }
    }

    private fun List<LsiLowcodeApiSchema>.toSchemaListSource(): String =
        if (isEmpty()) "emptyList()" else joinToString(prefix = "listOf(", postfix = ")") { schema -> schema.toSource() }

    private fun Map<String, List<site.addzero.validation.compiler.LsiValidationRule>>.toValidationRulesSource(): String {
        if (isEmpty()) return "emptyMap()"
        return toSortedMap().entries.joinToString(prefix = "mapOf(", postfix = ")") { (property, rules) ->
            val rulesSource = rules.sortedBy(site.addzero.validation.compiler.LsiValidationRule::code)
                .joinToString(prefix = "listOf(", postfix = ")") { rule ->
                    val parameters = rule.parameters.toSortedMap().entries.joinToString(
                        prefix = "mapOf(",
                        postfix = ")",
                    ) { (name, value) -> "\"${name.escapeKotlin()}\" to \"${value.escapeKotlin()}\"" }
                    "LsiValidationRule(" +
                        "code = \"${rule.code.escapeKotlin()}\", " +
                        "message = ${rule.message.toNullableStringSource()}, " +
                        "parameters = $parameters)"
                }
            "\"${property.escapeKotlin()}\" to $rulesSource"
        }
    }

    private fun Set<String>.toStringSetSource(indent: Int): String {
        if (isEmpty()) {
            return "emptySet()"
        }
        val spaces = " ".repeat(indent)
        return sorted().joinToString(
            prefix = "setOf(\n",
            postfix = "\n$spaces)",
            separator = ",\n",
        ) { value -> "$spaces    \"${value.escapeKotlin()}\"" }
    }

    private fun List<String>.toStringListSource(indent: Int): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        val spaces = " ".repeat(indent)
        return sorted().joinToString(
            prefix = "listOf(\n",
            postfix = "\n$spaces)",
            separator = ",\n",
        ) { value -> "$spaces    \"${value.escapeKotlin()}\"" }
    }

    private fun List<LsiLowcodeProperty>.toPropertiesSource(): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        return joinToString(
            prefix = "listOf(\n",
            postfix = "\n        )",
            separator = ",\n",
        ) { property ->
            val inputMetadataSource = buildList {
                if (property.identifier) {
                    add("identifier = true,")
                }
                if (!property.createWritable) {
                    add("createWritable = false,")
                }
                if (!property.updateWritable) {
                    add("updateWritable = false,")
                }
            }.joinToString(
                prefix = if (property.identifier || !property.createWritable || !property.updateWritable) "\n" else "",
                separator = "\n",
            ) { line -> "                $line" }
            """
            LowcodeSchemaProperty(
                name = "${property.name.escapeKotlin()}",
                type = "${property.type.escapeKotlin()}",
                format = ${property.format?.let { format -> "\"${format.escapeKotlin()}\"" } ?: "null"},
                required = ${property.required},$inputMetadataSource
                arrayItemType = ${property.arrayItemType?.let { type -> "\"${type.escapeKotlin()}\"" } ?: "null"},
                description = ${property.description.toNullableStringSource()},
                dictionaryCode = ${property.dictionaryCode.toNullableStringSource()},
                referenceTargetModelCode = ${property.referenceTargetModelCode.toNullableStringSource()},
                referencePropertyName = ${property.referencePropertyName.toNullableStringSource()},
                enumValues = ${property.enumValues.toStringListSource(16)},
                maxLength = ${property.maxLength ?: "null"},
            )
            """.trimIndent().prependIndent("            ")
        }
    }

    private fun List<LsiLowcodeQueryField>.toContractQueryFieldsSource(): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        return joinToString(
            prefix = "listOf(\n",
            postfix = "\n        )",
            separator = ",\n",
        ) { field ->
            """
            LowcodeQueryField(
                propertyName = "${field.propertyName.escapeKotlin()}",
                parameterName = "${field.parameterName.escapeKotlin()}",
                operator = "${field.operator.escapeKotlin()}",
                type = "${field.type.escapeKotlin()}",
                format = ${field.format?.let { format -> "\"${format.escapeKotlin()}\"" } ?: "null"},
                endParameterName = ${field.endParameterName?.let { name -> "\"${name.escapeKotlin()}\"" } ?: "null"},
                required = ${field.required},
                stateCases = ${field.stateCases.toContractStateCasesSource()},
                description = ${field.description.toNullableStringSource()},
                enumValues = ${field.enumValues.toStringListSource(16)},
            )
            """.trimIndent().prependIndent("            ")
        }
    }

    private fun List<LsiLowcodeStateCase>.toContractStateCasesSource(): String {
        if (isEmpty()) {
            return "emptyList()"
        }
        return joinToString(
            prefix = "listOf(\n",
            postfix = "\n                )",
            separator = ",\n",
        ) { stateCase ->
            """
            LowcodeQueryStateCase(
                parameterValue = "${stateCase.parameterValue.escapeKotlin()}",
                operator = "${stateCase.operator.escapeKotlin()}",
                expression = "${stateCase.expression.escapeKotlin()}",
            )
            """.trimIndent().prependIndent("                ")
        }
    }

}
