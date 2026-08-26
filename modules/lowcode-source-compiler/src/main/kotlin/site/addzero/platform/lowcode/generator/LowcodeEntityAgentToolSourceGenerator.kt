package site.addzero.platform.lowcode.generator

/** 为实体路由中可严格推导的操作生成类型化 Tool。 */
object LowcodeEntityAgentToolSourceGenerator {
    fun generate(
        feature: LsiLowcodeFeature,
        models: List<LowcodeModelMeta>,
        routeBindings: List<LowcodeRouteBinding>,
    ): LowcodeGeneratedFile? {
        val routesByModel = models.associateWith { model -> model.resolveAgentRoute(routeBindings) }
        val operations = routesByModel.flatMap { (model, route) ->
            route.agentExposure.operations.toSortedMap().map { (operationCode, exposure) ->
                require(operationCode in route.enabledOperations) {
                    "实体 Agent 操作未启用: ${model.modelCode}#$operationCode"
                }
                require(operationCode in SUPPORTED_OPERATIONS) {
                    "实体 Agent 操作 ${model.modelCode}#$operationCode 无法从严格输入 Schema 安全生成，" +
                        "请定义带命名输入 DTO 的业务 Service 契约"
                }
                ExposedEntityOperation(model, route, operationCode, exposure)
            }
        }.sortedBy(ExposedEntityOperation::toolName)
        if (operations.isEmpty()) return null

        val duplicateNames = operations.groupingBy(ExposedEntityOperation::toolName)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "实体 Agent 工具名称重复: ${duplicateNames.sorted().joinToString()}"
        }
        operations.forEach { operation ->
            require(TOOL_NAME_PATTERN.matches(operation.toolName)) {
                "Agent 工具名称必须为 1-64 位英文、数字、下划线或连字符: ${operation.toolName}"
            }
            operation.validateQueryArguments()
        }

        val layout = feature.packageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.SERVICE)
        val contributorName = feature.featureCode.toPascalCase() + "EntityToolRegistryContributor"
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
            "$agentPackage.withCallContext",
            "$agentPackage.generated.dto.AgentToolSet",
            "${generationTargetSymbol(GenerationTargetSymbols.CORE_RUNTIME_PACKAGE)}.CallContextProvider",
        )
        val constructorParameters = buildList {
            operations
                .distinctBy { operation -> operation.serviceQualifiedName }
                .sortedBy(ExposedEntityOperation::serviceQualifiedName)
                .forEach { operation ->
                    add("    private val ${operation.servicePropertyName}: ${operation.serviceQualifiedName},")
                }
            add("    private val callContextProvider: CallContextProvider,")
        }.joinToString("\n")
        val registrations = operations.joinToString("\n") { operation ->
            "            tool(${operation.toolClassName}(${operation.servicePropertyName}, context, callContextProvider))"
        }
        val policies = operations.joinToString(",\n") { operation ->
            val permissions = operation.permission
                ?.let { permission -> "setOf(\"${permission.escapeKotlin()}\")" }
                ?: "emptySet()"
            "            \"${operation.toolName}\" to AgentToolPolicy(\n" +
                "                requiredPermissions = $permissions,\n" +
                "                confirmation = AgentToolConfirmation.${operation.exposure.confirmation},\n" +
                "            )"
        }
        val queryConditions = operations
            .filter { operation -> operation.operationCode == "PAGE" || operation.operationCode == "SIMPLE_LIST" }
            .distinctBy { operation -> operation.model.modelCode }
            .mapNotNull(ExposedEntityOperation::queryConditionsSource)
            .joinToString("\n\n")
        val arguments = operations.joinToString("\n\n", transform = ExposedEntityOperation::argumentsSource)
        val tools = operations.joinToString("\n\n", transform = ExposedEntityOperation::toolSource)
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
            |$registrations
            |        },
            |        policies = mapOf(
            |$policies
            |        ),
            |    )
            |}
            |
            |$queryConditions
            |${queryConditions.takeIf(String::isNotBlank)?.let { "\n" }.orEmpty()}$arguments
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
}

private data class ExposedEntityOperation(
    val model: LowcodeModelMeta,
    val route: LsiLowcodeRoute,
    val operationCode: String,
    val exposure: LsiAgentOperationExposure,
) {
    val toolName: String = "${model.modelCode}_${operationCode.lowercase()}"
    val toolClassName: String = model.modelCode.toPascalCase() + operationCode.toPascalCase() + "Tool"
    val argumentsClassName: String = toolClassName + "Args"
    val serviceQualifiedName: String = model.featurePackageName.generatedLayout()
        .qualifiedName(LowcodeGeneratedResourceKind.SERVICE, model.entityClassName() + "Service")
    val servicePropertyName: String = (model.entityClassName() + "Service").replaceFirstChar(Char::lowercaseChar)
    val entityQualifiedName: String = model.entityQualifiedName()
    val permission: String? = if (route.authenticated) {
        "${operationCode.httpMethod()}:${route.path.normalizedResourcePath().removeSuffix("/")}/**"
    } else {
        null
    }
    private val queryFields: List<LsiLowcodeQueryField>
        get() = route.queryFields.sortedBy(LsiLowcodeQueryField::parameterName)
    private val usesConditions: Boolean
        get() = operationCode == "PAGE" || operationCode == "SIMPLE_LIST"

    fun validateQueryArguments() {
        if (!usesConditions) return
        val names = queryFields.flatMap { field -> listOfNotNull(field.parameterName, field.endParameterName) }
        val duplicates = names.groupingBy(String::lowercase).eachCount().filterValues { count -> count > 1 }.keys
        require(duplicates.isEmpty()) {
            "实体 Agent 查询参数名称重复: ${model.modelCode}#${duplicates.sorted().joinToString()}"
        }
    }

    fun queryConditionsSource(): String? {
        if (!usesConditions || queryFields.isEmpty()) return null
        val className = queryConditionsClassName
        val properties = queryFields.flatMap { field ->
            buildList {
                add(field.queryPropertySource(field.parameterName, field.required))
                field.endParameterName?.let { name -> add(field.queryPropertySource(name, false)) }
            }
        }.joinToString("\n")
        val entries = queryFields.flatMap { field ->
            buildList {
                add(field.queryMapEntry(field.parameterName, field.required))
                field.endParameterName?.let { name -> add(field.queryMapEntry(name, false)) }
            }
        }.joinToString("\n")
        return """
            |private data class $className(
            |$properties
            |) {
            |    fun toQueryMap(): Map<String, List<String>> = buildMap {
            |$entries
            |    }
            |}
        """.trimMargin()
    }

    fun argumentsSource(): String = when (operationCode) {
        "GET", "DELETE" -> "data class $argumentsClassName(\n    val id: Long,\n)"
        "DELETE_LIST" -> "data class $argumentsClassName(\n    val ids: List<Long>,\n)"
        "TREE" -> "data class $argumentsClassName(\n    val keyword: String? = null,\n)"
        "PAGE" -> if (queryFields.isEmpty()) {
            "data class $argumentsClassName(\n    val pageNo: Int = 1,\n    val pageSize: Int = 10,\n)"
        } else {
            val defaultValue = if (queryFields.none(LsiLowcodeQueryField::required)) " = $queryConditionsClassName()" else ""
            "data class $argumentsClassName(\n" +
                "    val pageNo: Int = 1,\n" +
                "    val pageSize: Int = 10,\n" +
                "    val conditions: $queryConditionsClassName$defaultValue,\n" +
                ")"
        }

        "SIMPLE_LIST" -> if (queryFields.isEmpty()) {
            "class $argumentsClassName"
        } else {
            val defaultValue = if (queryFields.none(LsiLowcodeQueryField::required)) " = $queryConditionsClassName()" else ""
            "data class $argumentsClassName(\n    val conditions: $queryConditionsClassName$defaultValue,\n)"
        }

        else -> error("不支持的实体 Agent 操作: $operationCode")
    }

    fun toolSource(): String {
        val returnType = when (operationCode) {
            "GET" -> "$entityQualifiedName?"
            "PAGE" ->
                "${generationTargetSymbol(GenerationTargetSymbols.CORE_RUNTIME_PACKAGE)}.PageResult<$entityQualifiedName>"
            "SIMPLE_LIST", "TREE" -> "List<$entityQualifiedName>"
            "DELETE", "DELETE_LIST" -> "Boolean"
            else -> error("不支持的实体 Agent 操作: $operationCode")
        }
        val conditions = if (queryFields.isEmpty()) "emptyMap()" else "args.conditions.toQueryMap()"
        val invocation = when (operationCode) {
            "GET" -> "service.findById(args.id)"
            "PAGE" -> "service.page(args.pageNo, args.pageSize, $conditions)"
            "SIMPLE_LIST" -> "service.simpleList($conditions)"
            "TREE" -> "service.tree(args.keyword)"
            "DELETE" -> "service.delete(args.id)"
            "DELETE_LIST" -> "service.deleteList(args.ids)"
            else -> error("不支持的实体 Agent 操作: $operationCode")
        }
        val description = "${model.name}${operationCode.displayName()}"
        return """
            |private class $toolClassName(
            |    private val service: $serviceQualifiedName,
            |    private val context: AgentExecutionContext,
            |    private val callContextProvider: CallContextProvider,
            |) : Tool<$argumentsClassName, $returnType>(
            |    argsType = typeToken<$argumentsClassName>(),
            |    resultType = typeToken<$returnType>(),
            |    name = "$toolName",
            |    description = "${description.escapeKotlin()}",
            |) {
            |    override suspend fun execute(args: $argumentsClassName): $returnType =
            |        context.withCallContext(callContextProvider) {
            |            $invocation
            |        }
            |}
        """.trimMargin()
    }

    private val queryConditionsClassName: String
        get() = model.modelCode.toPascalCase() + "AgentQueryConditions"
}

private fun LsiLowcodeQueryField.queryPropertySource(name: String, isRequired: Boolean): String =
    "    val ${name.escapeIdentifier()}: ${agentKotlinType()}" + if (isRequired) "," else "? = null,"

private fun LsiLowcodeQueryField.queryMapEntry(name: String, isRequired: Boolean): String {
    val value = name.escapeIdentifier()
    val values = if (type == "array") "$value.map { item -> item.toString() }" else "listOf($value.toString())"
    return if (isRequired) {
        "        put(\"${name.escapeKotlin()}\", $values)"
    } else {
        val nestedValues = if (type == "array") {
            "value.map { item -> item.toString() }"
        } else {
            "listOf(value.toString())"
        }
        "        $value?.let { value -> put(\"${name.escapeKotlin()}\", $nestedValues) }"
    }
}

private fun LsiLowcodeQueryField.agentKotlinType(): String = when (type) {
    "string" -> when (format) {
        "date" -> "java.time.LocalDate"
        "date-time" -> "java.time.LocalDateTime"
        else -> "String"
    }

    "integer" -> if (format == "int64") "Long" else "Int"
    "number" -> "Double"
    "boolean" -> "Boolean"
    "array" -> "List<String>"
    else -> error("实体 Agent 查询不支持的 Schema 类型: $type")
}

private fun LowcodeModelMeta.resolveAgentRoute(bindings: List<LowcodeRouteBinding>): LsiLowcodeRoute =
    requireNotNull(
        bindings.firstOrNull { binding ->
            binding.contributorId == contributorId &&
                binding.route.className == className &&
                binding.route.packageName == packageName
        }?.route,
    ) {
        "实体 Agent 工具缺少路由元数据: $modelCode"
    }

private fun String.httpMethod(): String = when (this) {
    "GET", "PAGE", "SIMPLE_LIST", "TREE" -> "GET"
    "DELETE", "DELETE_LIST" -> "DELETE"
    else -> error("不支持的实体 Agent 操作: $this")
}

private fun String.displayName(): String = when (this) {
    "GET" -> "详情查询"
    "PAGE" -> "分页查询"
    "SIMPLE_LIST" -> "列表查询"
    "TREE" -> "树形查询"
    "DELETE" -> "删除"
    "DELETE_LIST" -> "批量删除"
    else -> error("不支持的实体 Agent 操作: $this")
}

private fun String.normalizedResourcePath(): String = trim().trimEnd('/').let { path ->
    when {
        path.isEmpty() -> "/"
        path.startsWith('/') -> path
        else -> "/$path"
    }
}

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

private val SUPPORTED_OPERATIONS = setOf("GET", "PAGE", "SIMPLE_LIST", "TREE", "DELETE", "DELETE_LIST")
private val TOOL_NAME_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
)
