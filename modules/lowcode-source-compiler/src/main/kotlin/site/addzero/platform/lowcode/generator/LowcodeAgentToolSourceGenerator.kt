package site.addzero.platform.lowcode.generator

/** 根据显式 Agent 白名单生成类型化 Tool 和 Koin Contributor。 */
object LowcodeAgentToolSourceGenerator {
    fun generate(
        feature: LsiLowcodeFeature,
        contracts: List<LsiLowcodeContract>,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): LowcodeGeneratedFile? {
        val exposed = contracts.flatMap { contract ->
            contract.agentExposure.operations.toSortedMap().map { (operationCode, exposure) ->
                val operation = contract.operations.singleOrNull { candidate ->
                    candidate.operationCode == operationCode &&
                        candidate.generatesService &&
                        candidate.transport == LowcodeOperationTransport.HTTP
                } ?: error("Agent 暴露项无法解析到生成的 HTTP Service 操作: ${contract.contractCode}#$operationCode")
                ExposedOperation(contract, operation, exposure)
            }
        }.sortedBy(ExposedOperation::toolName)
        if (exposed.isEmpty()) return null

        val duplicateNames = exposed.groupingBy(ExposedOperation::toolName)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Agent 工具名称重复: ${duplicateNames.sorted().joinToString()}"
        }
        exposed.forEach { item ->
            require(TOOL_NAME_PATTERN.matches(item.toolName)) {
                "Agent 工具名称必须为 1-64 位英文、数字、下划线或连字符: ${item.toolName}"
            }
        }

        val layout = feature.packageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.SERVICE)
        val contributorName = feature.featureCode.toPascalCase() + "ToolRegistryContributor"
        val agentPackage = generationTargetSymbol(GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE)
        val imports = sortedSetOf(
            "ai.koog.agents.core.tools.Tool",
            "ai.koog.agents.core.tools.ToolRegistry",
            "ai.koog.serialization.typeToken",
            "org.koin.core.annotation.Single",
            "$agentPackage.AgentExecutionContext",
            "$agentPackage.AgentToolConfirmation",
            "$agentPackage.AgentToolPolicy",
            "$agentPackage.ToolRegistryContributor",
            "$agentPackage.generated.dto.AgentToolSet",
        )
        if (exposed.any { item -> item.operation.callContext }) {
            imports += "$agentPackage.withCallContext"
            imports += "${generationTargetSymbol(GenerationTargetSymbols.CORE_RUNTIME_PACKAGE)}.CallContextProvider"
        }
        val constructorParameters = buildList {
            exposed
                .map(ExposedOperation::contract)
                .distinctBy(LsiLowcodeContract::serviceQualifiedName)
                .sortedBy(LsiLowcodeContract::serviceQualifiedName)
                .forEach { contract ->
                    add("    private val ${contract.servicePropertyName()}: ${contract.serviceQualifiedName()},")
                }
            if (exposed.any { item -> item.operation.callContext }) {
                add("    private val callContextProvider: CallContextProvider,")
            }
        }.joinToString("\n")
        val toolRegistrations = exposed.joinToString("\n") { item ->
            val contextProviderArgument = if (item.operation.callContext) ", callContextProvider" else ""
            "            tool(${item.toolClassName}(${item.contract.servicePropertyName()}, context$contextProviderArgument))"
        }
        val policyEntries = exposed.joinToString(",\n") { item ->
            val permissions = item.operation.permission
                ?.let { permission -> "setOf(\"${permission.escapeKotlin()}\")" }
                ?: "emptySet()"
            "            \"${item.toolName}\" to AgentToolPolicy(\n" +
                "                requiredPermissions = $permissions,\n" +
                "                confirmation = AgentToolConfirmation.${item.exposure.confirmation},\n" +
                "            )"
        }
        val arguments = exposed.joinToString("\n\n") { item -> item.argumentsSource(dtoCatalog) }
        val tools = exposed.joinToString("\n\n") { item -> item.toolSource(dtoCatalog) }
        val content = """
            |package $packageName
            |
            |${imports.joinToString("\n") { importName -> "import $importName" }}
            |
            |@Single
            |class $contributorName(
            |$constructorParameters
            |) : ToolRegistryContributor {
            |    override fun contribute(context: AgentExecutionContext): AgentToolSet = AgentToolSet(
            |        registry = ToolRegistry {
            |$toolRegistrations
            |        },
            |        policies = mapOf(
            |$policyEntries
            |        ),
            |    )
            |}
            |
            |$arguments
            |
            |$tools
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = contributorName,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.SERVICE,
                contributorName,
            ),
            content = generatedByStudio(content),
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun ExposedOperation.argumentsSource(dtoCatalog: LowcodeDtoTypeCatalog): String {
        val properties = buildList {
            operation.parameters.forEach { parameter ->
                add(
                    "    val ${parameter.name.escapeIdentifier()}: " +
                        parameter.schema.agentKotlinType(dtoCatalog) +
                        if (parameter.required) "," else "? = null,",
                )
            }
            operation.requestBody?.let { body ->
                add(
                    "    val request: ${body.schema.agentRootType(operation.requestTypeName(), dtoCatalog)}" +
                        if (body.required) "," else "? = null,",
                )
            }
        }
        return if (properties.isEmpty()) {
            "class $argumentsClassName"
        } else {
            "data class $argumentsClassName(\n${properties.joinToString("\n")}\n)"
        }
    }

    private fun ExposedOperation.toolSource(dtoCatalog: LowcodeDtoTypeCatalog): String {
        val returnType = operation.responseBody?.schema
            ?.agentRootType(operation.responseTypeName(), dtoCatalog)
            ?: "Unit"
        val callArguments = buildList {
            operation.parameters.forEach { parameter ->
                add("${parameter.name.escapeIdentifier()} = args.${parameter.name.escapeIdentifier()}")
            }
            operation.requestBody?.let { add("request = args.request") }
        }
        val serviceCall = if (callArguments.isEmpty()) {
            "service.${operation.operationCode.escapeIdentifier()}()"
        } else {
            "service.${operation.operationCode.escapeIdentifier()}(\n" +
                callArguments.joinToString(",\n") { argument -> "            $argument" } +
                ",\n        )"
        }
        val execution = if (operation.callContext) {
            "context.withCallContext(callContextProvider) {\n${serviceCall.prependIndent("            ")}\n        }"
        } else {
            serviceCall
        }
        val callContextProviderParameter = if (operation.callContext) {
            "    private val callContextProvider: CallContextProvider,\n"
        } else {
            ""
        }
        return """
            |private class $toolClassName(
            |    private val service: ${contract.serviceQualifiedName()},
            |    private val context: AgentExecutionContext,
            |$callContextProviderParameter
            |) : Tool<$argumentsClassName, $returnType>(
            |    argsType = typeToken<$argumentsClassName>(),
            |    resultType = typeToken<$returnType>(),
            |    name = "$toolName",
            |    description = "${description.escapeKotlin()}",
            |) {
            |    override suspend fun execute(args: $argumentsClassName): $returnType =
            |        $execution
            |}
        """.trimMargin()
    }
}

private data class ExposedOperation(
    val contract: LsiLowcodeContract,
    val operation: LsiLowcodeCustomOperation,
    val exposure: LsiAgentOperationExposure,
) {
    val toolName: String = "${contract.contractCode}_${operation.operationCode}"
    val toolClassName: String = contract.contractCode.toPascalCase() + operation.operationCode.toPascalCase() + "Tool"
    val argumentsClassName: String = toolClassName + "Args"
    val description: String = listOfNotNull(operation.name, operation.description)
        .filter(String::isNotBlank)
        .joinToString("。")
}

private fun LsiLowcodeContract.servicePropertyName(): String =
    className.replaceFirstChar(Char::lowercaseChar)

private fun LsiLowcodeContract.serviceQualifiedName(): String = featurePackageName.generatedLayout()
    .qualifiedName(LowcodeGeneratedResourceKind.SERVICE, className)

private fun LsiLowcodeApiSchema.agentRootType(
    objectName: String,
    dtoCatalog: LowcodeDtoTypeCatalog,
): String = if (type == "object" && typeRef == null) objectName else agentKotlinType(dtoCatalog)

private fun LsiLowcodeApiSchema.agentKotlinType(dtoCatalog: LowcodeDtoTypeCatalog): String {
    typeRef?.let { ref -> return dtoCatalog.resolve(ref).qualifiedName }
    return when (type) {
        "string" -> when (format) {
            "date" -> "java.time.LocalDate"
            "date-time" -> "java.time.LocalDateTime"
            else -> "String"
        }

        "integer" -> if (format == "int64") "Long" else "Int"
        "number" -> "Double"
        "boolean" -> "Boolean"
        "array" -> "List<${requireNotNull(items) { "Agent 数组 Schema 缺少 items" }.agentKotlinType(dtoCatalog)}>"
        "object" -> error("Agent Tool 不支持匿名嵌套对象，请使用命名 DTO")
        else -> error("Agent Tool 不支持的 Schema 类型: $type")
    }
}

private fun LsiLowcodeCustomOperation.requestTypeName(): String = operationCode.toPascalCase() + "Request"

private fun LsiLowcodeCustomOperation.responseTypeName(): String = operationCode.toPascalCase() + "Response"

private fun String.toPascalCase(): String = split(Regex("[^A-Za-z0-9]+"))
    .filter(String::isNotEmpty)
    .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

private fun String.escapeKotlin(): String = replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("$", "\\$")
    .replace("\r", "\\r")
    .replace("\n", "\\n")
    .replace("\t", "\\t")

private val TOOL_NAME_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
)
