package site.addzero.platform.lowcode.generator

import site.addzero.dto.compiler.LsiDtoType

/**
 * 把独立业务契约生成为领域 Service、传输适配器和编程智能体实施说明。
 */
object LowcodeDomainServiceSourceGenerator {
    fun generate(
        contract: LsiLowcodeContract,
        dtoCatalog: LowcodeDtoTypeCatalog = LowcodeDtoTypeCatalog.EMPTY,
    ): List<LowcodeGeneratedFile> = buildList {
        contract.operations
            .filter { operation -> operation.implementation != LowcodeOperationImplementation.SERVICE_ONLY }
            .forEach { operation ->
                require(!operation.containsKotlinType()) {
                    "结构化 kotlinType 只能用于 SERVICE_ONLY 操作: ${contract.contractCode}#${operation.operationCode}"
                }
            }
        val serviceContract = contract.copy(
            operations = contract.operations.filter(LsiLowcodeCustomOperation::generatesService),
        )
        val controllerContract = contract.copy(
            operations = contract.operations.filter(LsiLowcodeCustomOperation::isGenerated),
        )
        if (serviceContract.operations.isEmpty()) {
            if (controllerContract.operations.isNotEmpty()) {
                add(generateController(contract, controllerContract, dtoCatalog))
            }
            return@buildList
        }
        add(generateService(serviceContract, dtoCatalog))
        if (controllerContract.operations.isNotEmpty()) {
            add(generateController(contract, controllerContract, dtoCatalog))
        }
        add(generateImplementationSkill(serviceContract))
        add(generateTestRedLines(serviceContract))
    }

    private fun generateService(
        contract: LsiLowcodeContract,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): LowcodeGeneratedFile {
        val packageName = contract.generatedPackage()
        val imports = sortedSetOf<String>()
        contract.operations.forEach { operation ->
            operation.collectImports(imports, dtoCatalog)
        }
        val dataClasses = contract.operations.flatMap { operation -> dataClasses(operation, dtoCatalog) }
        val content = buildString {
            appendLine("package $packageName")
            if (imports.isNotEmpty()) {
                appendLine()
                imports.forEach { importName -> appendLine("import $importName") }
            }
            dataClasses.forEach { dataClass ->
                appendLine()
                appendLine(dataClass)
            }
            appendLine()
            appendLine("/**")
            appendLine(" * ${contract.name.escapeKDoc()}")
            contract.description?.lineSequence()?.forEach { line -> appendLine(" * ${line.escapeKDoc()}") }
            appendLine(" */")
            appendLine("interface ${contract.className} {")
            contract.operations.sortedBy(LsiLowcodeCustomOperation::operationCode).forEachIndexed { index, operation ->
                if (index > 0) {
                    appendLine()
                }
                appendLine(operation.serviceMethod(dtoCatalog).prependIndent("    "))
            }
            appendLine("}")
        }
        return contract.kotlinFile(contract.className, content)
    }

    private fun generateController(
        contract: LsiLowcodeContract,
        generatedContract: LsiLowcodeContract,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): LowcodeGeneratedFile {
        val packageName = contract.controllerGeneratedPackage()
        val controllerName = contract.controllerClassName()
        val operations = generatedContract.operations.sortedBy(LsiLowcodeCustomOperation::operationCode)
        val validatesRequestDtos = operations.any { operation ->
            operation.requestBody?.schema?.validationSchemaName(generatedContract) != null
        }
        val imports = sortedSetOf(
            "io.ktor.server.routing.Route",
            "org.koin.core.annotation.Single",
            "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.LowcodeContractProvider",
            "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.LowcodeContractResolver",
            "${generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)}.Controller",
        )
        if (operations.any { operation -> operation.requiresRouteWrapper(generatedContract.normalizedRouteKey()) }) {
            imports += "io.ktor.server.routing.route"
        }
        if (operations.isNotEmpty()) {
            imports += "${generatedContract.generatedPackage()}.${generatedContract.className}"
        }
        val httpOperations = operations.filter { operation -> operation.transport == LowcodeOperationTransport.HTTP }
        httpOperations
            .filter { operation -> operation.usesResponseMapping() }
            .mapTo(imports) { operation ->
                "${generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)}.${operation.method.mappingFunction()}"
            }
        if (httpOperations.any { operation -> operation.usesApplicationCall() }) {
            imports += "io.ktor.server.application.call"
        }
        if (httpOperations.any { operation -> !operation.usesResponseMapping() }) {
            imports += "io.ktor.http.HttpMethod"
            imports += "io.ktor.server.routing.method"
        }
        if (httpOperations.any { operation ->
                !operation.responseEnvelope && operation.responseBody?.schema?.isBinary() != true
            }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.respondOperationPayload"
        }
        if (operations.any { operation -> operation.responseBody?.schema?.isBinary() == true }) {
            imports += "io.ktor.http.ContentType"
            imports += "io.ktor.server.response.respondBytes"
        }
        if (operations.any { operation -> operation.requestBody?.required == true }) {
            imports += "io.ktor.server.request.receive"
        }
        if (operations.any { operation -> operation.requestBody?.required == false }) {
            imports += "io.ktor.server.request.receiveNullable"
        }
        if (validatesRequestDtos) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.LowcodeRuntimeValidator"
        }
        if (operations.any { operation -> operation.parameters.any { parameter -> !parameter.usesKtorDelegate() } }) {
            imports += "site.addzero.platform.lowcode.generator.LowcodeApiParameterLocation"
        }
        if (operations.any { operation -> operation.parameters.any { parameter ->
                !parameter.usesKtorDelegate() && parameter.schema.type != "array" && !parameter.required
            } }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.lowcodeParameter"
        }
        if (operations.any { operation -> operation.parameters.any { parameter -> parameter.schema.type == "array" && !parameter.required } }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.lowcodeParameters"
        }
        if (operations.any { operation -> operation.parameters.any { parameter ->
                !parameter.usesKtorDelegate() && parameter.schema.type != "array" && parameter.required
            } }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.requiredLowcodeParameter"
        }
        if (operations.any { operation -> operation.parameters.any { parameter -> parameter.schema.type == "array" && parameter.required } }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.requiredLowcodeParameters"
        }
        if (operations.any { operation -> operation.parameters.any { parameter -> parameter.usesKtorDelegate() } }) {
            imports += "io.ktor.server.util.getValue"
        }
        if (operations.any { operation -> !operation.authenticated }) {
            imports += "${generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)}.allowAnonymous"
        }
        if (operations.any { operation -> operation.transport == LowcodeOperationTransport.SSE }) {
            imports += "io.ktor.server.sse.sse"
        }
        if (operations.any { operation -> operation.transport == LowcodeOperationTransport.WEBSOCKET }) {
            imports += "io.ktor.server.websocket.webSocket"
        }
        operations.forEach { operation ->
            val operationImports = sortedSetOf<String>()
            operation.collectControllerImports(operationImports, dtoCatalog)
            imports += operationImports
            operation.requestBody?.schema?.takeIf { schema -> schema.isObject() && schema.typeRef == null }
                ?.let {
                    imports += "${contract.generatedPackage()}.${operation.operationCode.toPascalCase()}Request"
                }
        }
        val importsSource = imports.joinToString("\n") { importName -> "import $importName" }
        val registrationsSource = operations.joinToString("\n") { operation ->
            "    register${operation.operationCode.toPascalCase()}()"
        }
        val methodsSource = operations.joinToString("\n\n") { operation ->
            operation.controllerRouteMethod(generatedContract, dtoCatalog)
        }
        val constructorSource = if (operations.isEmpty()) {
            """
                |class $controllerName(
                |    private val contractResolver: LowcodeContractResolver,
                |) : Controller, LowcodeContractProvider {
            """.trimMargin()
        } else {
            val validatorParameter = if (validatesRequestDtos) {
                "    private val validator: LowcodeRuntimeValidator,\n"
            } else {
                ""
            }
            """
                |class $controllerName(
                |    private val service: ${generatedContract.className},
                |    private val contractResolver: LowcodeContractResolver,
                |$validatorParameter) : Controller, LowcodeContractProvider {
            """.trimMargin()
        }
        val installSource = if (operations.isEmpty()) {
            ""
        } else {
            """
                |override fun Route.installEndpoints() {
                |$registrationsSource
                |}
            """.trimMargin()
        }
        val content = """
            |package $packageName
            |
            |$importsSource
            |
            |/** ${contract.name.escapeKDoc()}契约控制器。 */
            |@Single
            |$constructorSource
            |    override val routeKey = "${contract.normalizedRouteKey().escapeKotlin()}"
            |
            |    override val contract = contractResolver.requireContract("${contract.contractCode.escapeKotlin()}")
            |
            |${installSource.prependIndent("    ")}
            |${methodsSource.takeIf(String::isNotBlank)?.prependIndent("    ").orEmpty()}
            |}
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return contract.controllerKotlinFile(controllerName, content)
    }

    private fun LsiLowcodeCustomOperation.controllerRouteMethod(
        contract: LsiLowcodeContract,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): String {
        val callReference = "call"
        val parameterBindings = parameterBindings(contract, callReference, dtoCatalog)
        val serviceCall = controllerServiceCall(contract)
        val relativePath = relativePath(contract.normalizedRouteKey())
        val mappingPath = relativePath.takeIf {
            transport == LowcodeOperationTransport.HTTP && usesResponseMapping()
        }
        val operationBody = listOf(parameterBindings, serviceCall)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .prependIndent("    ")
        val transportSource = when (transport) {
            LowcodeOperationTransport.HTTP -> httpControllerRoute(mappingPath, parameterBindings, serviceCall)
            LowcodeOperationTransport.SSE -> """
                |sse {
                |$operationBody
                |}
            """.trimMargin()
            LowcodeOperationTransport.WEBSOCKET -> """
                |webSocket {
                |$operationBody
                |}
            """.trimMargin()
            LowcodeOperationTransport.INTERNAL -> error("INTERNAL 操作不能生成 Controller: $operationCode")
        }
        val authenticatedSource = if (!authenticated) {
            """
                |allowAnonymous {
                |${transportSource.prependIndent("    ")}
                |}
            """.trimMargin()
        } else {
            transportSource
        }
        val routeSource = if (relativePath == null || mappingPath != null) {
            authenticatedSource
        } else {
            """
                |route("${relativePath.escapeKotlin()}") {
                |${authenticatedSource.prependIndent("    ")}
                |}
            """.trimMargin()
        }
        return """
            |private fun Route.register${operationCode.toPascalCase()}() {
            |${routeSource.prependIndent("    ")}
            |}
        """.trimMargin()
    }

    private fun LsiLowcodeCustomOperation.httpControllerRoute(
        relativePath: String?,
        parameterBindings: String,
        serviceCall: String,
    ): String {
        if (usesResponseMapping()) {
            val pathArgument = relativePath
                ?.let { path -> "(\"${path.escapeKotlin()}\")" }
                .orEmpty()
            val responseSource = if (responseBody == null) {
                "$serviceCall\nnull"
            } else {
                serviceCall
            }
            val handlerSource = listOf(parameterBindings, responseSource)
                .filter(String::isNotBlank)
                .joinToString("\n")
                .prependIndent("    ")
            return """
                |${method.mappingFunction()}$pathArgument {
                |$handlerSource
                |}
            """.trimMargin()
        }
        val body = responseBody
        val resultSource = if (body == null) {
            "$serviceCall\nval response: Any? = null\ncall.respondOperationPayload(response, $responseEnvelope)"
        } else if (body.schema.isBinary()) {
            val contentType = body.contentType.escapeKotlin()
            "val response = $serviceCall\n" +
                "val responseContentType = ContentType.parse(\"$contentType\")\n" +
                "call.respondBytes(response, responseContentType)"
        } else {
            "val response = $serviceCall\ncall.respondOperationPayload(response, $responseEnvelope)"
        }
        val handlerSource = listOf(parameterBindings, resultSource)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .prependIndent("        ")
        return """
            |method(HttpMethod.${method.name.lowercase().replaceFirstChar(Char::uppercaseChar)}) {
            |    handle {
            |$handlerSource
            |    }
            |}
        """.trimMargin()
    }

    private fun LsiLowcodeCustomOperation.usesResponseMapping(): Boolean =
        responseEnvelope && responseBody?.schema?.isBinary() != true

    private fun LsiLowcodeCustomOperation.usesApplicationCall(): Boolean =
        !usesResponseMapping() || parameters.isNotEmpty() || requestBody != null

    private fun LsiLowcodeCustomOperation.requiresRouteWrapper(routeKey: String): Boolean =
        relativePath(routeKey) != null &&
            (transport != LowcodeOperationTransport.HTTP || !usesResponseMapping())

    private fun LowcodeHttpMethod.mappingFunction(): String = when (this) {
        LowcodeHttpMethod.GET -> "getMapping"
        LowcodeHttpMethod.POST -> "postMapping"
        LowcodeHttpMethod.PUT -> "putMapping"
        LowcodeHttpMethod.PATCH -> "patchMapping"
        LowcodeHttpMethod.DELETE -> "deleteMapping"
    }

    private fun LsiLowcodeCustomOperation.controllerServiceCall(contract: LsiLowcodeContract): String {
        val arguments = adapterArguments(contract)
        if (arguments.isEmpty()) {
            return "service.${operationCode.escapeIdentifier()}()"
        }
        val argumentsSource = arguments.joinToString("\n") { argument -> "    $argument," }
        return """
            |service.${operationCode.escapeIdentifier()}(
            |$argumentsSource
            |)
        """.trimMargin()
    }

    private fun LsiLowcodeCustomOperation.relativePath(routeKey: String): String? {
        val operationPath = path.trimEnd('/').ifEmpty { "/" }
        if (operationPath == routeKey) {
            return null
        }
        if (routeKey == "/" && operationPath.startsWith('/')) {
            return operationPath
        }
        require(operationPath.startsWith("$routeKey/")) {
            "契约操作 $operationCode 的路径 $path 必须以 routeKey $routeKey 为前缀"
        }
        return operationPath.removePrefix(routeKey)
    }

    private fun dataClasses(
        operation: LsiLowcodeCustomOperation,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): List<String> = buildList {
        operation.requestBody?.schema?.takeIf { schema -> schema.isObject() }?.let { schema ->
            add(schema.dataClass("${operation.operationCode.toPascalCase()}Request", dtoCatalog))
        }
        operation.responseBody?.schema?.takeIf { schema -> schema.isObject() }?.let { schema ->
            add(schema.dataClass("${operation.operationCode.toPascalCase()}Response", dtoCatalog))
        }
    }

    private fun LsiLowcodeCustomOperation.serviceMethod(dtoCatalog: LowcodeDtoTypeCatalog): String = buildString {
        appendLine("/**")
        appendLine(" * ${name.escapeKDoc()}")
        description?.lineSequence()?.forEach { line -> appendLine(" * ${line.escapeKDoc()}") }
        appendLine(" */")
        val parameters = serviceParameters(dtoCatalog)
        val suspendPrefix = if (suspending) "suspend " else ""
        if (parameters.isEmpty()) {
            append("${suspendPrefix}fun ${operationCode.escapeIdentifier()}(): ${returnType(dtoCatalog)}")
            return@buildString
        }
        appendLine("${suspendPrefix}fun ${operationCode.escapeIdentifier()}(")
        parameters.forEach { parameter -> appendLine("    $parameter,") }
        append("): ${returnType(dtoCatalog)}")
    }

    private fun LsiLowcodeCustomOperation.serviceParameters(dtoCatalog: LowcodeDtoTypeCatalog): List<String> = buildList {
        when (transport) {
            LowcodeOperationTransport.HTTP -> Unit
            LowcodeOperationTransport.SSE -> add("session: ServerSSESession")
            LowcodeOperationTransport.WEBSOCKET -> add("session: DefaultWebSocketServerSession")
            LowcodeOperationTransport.INTERNAL -> Unit
        }
        parameters.forEach { parameter ->
            add(
                "${parameter.name.escapeIdentifier()}: " +
                    parameter.schema.kotlinType(dtoCatalog).withNullability(!parameter.required),
            )
        }
        requestBody?.let { body ->
            val type = body.schema.rootType("${operationCode.toPascalCase()}Request", dtoCatalog)
            add("request: ${type.withNullability(!body.required)}")
        }
    }

    private fun LsiLowcodeCustomOperation.adapterArguments(contract: LsiLowcodeContract): List<String> = buildList {
        if (transport in setOf(LowcodeOperationTransport.SSE, LowcodeOperationTransport.WEBSOCKET)) {
            add("this")
        }
        parameters.forEach { parameter ->
            add(parameter.name.escapeIdentifier())
        }
        requestBody?.let { body ->
            val requestName = if (body.schema.validationSchemaName(contract) == null) {
                "requestBody"
            } else {
                "validatedRequestBody"
            }
            add(requestName)
        }
    }

    private fun LsiLowcodeCustomOperation.parameterBindings(
        contract: LsiLowcodeContract,
        callReference: String,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): String = buildList {
        parameters.forEach { parameter ->
            val binding = if (parameter.usesKtorDelegate()) {
                val source = when (parameter.location) {
                    LowcodeApiParameterLocation.PATH -> "pathParameters"
                    LowcodeApiParameterLocation.QUERY -> "queryParameters"
                    LowcodeApiParameterLocation.HEADER,
                    LowcodeApiParameterLocation.COOKIE -> error("该参数来源不支持 Ktor 委托: ${parameter.location}")
                }
                val type = parameter.schema.kotlinType(dtoCatalog).withNullability(!parameter.required)
                "val ${parameter.name.escapeIdentifier()}: $type by $callReference.$source"
            } else {
                "val ${parameter.name.escapeIdentifier()} = ${parameter.adapterExpression(callReference)}"
            }
            add(binding)
        }
        requestBody?.let { body ->
            val type = body.schema.rootType("${operationCode.toPascalCase()}Request", dtoCatalog)
            val receiveMethod = if (body.required) "receive" else "receiveNullable"
            add("val requestBody = $callReference.$receiveMethod<$type>()")
            val schemaName = body.schema.validationSchemaName(contract) ?: return@let
            val contractCode = contract.contractCode.escapeKotlin()
            val escapedSchemaName = schemaName.escapeKotlin()
            val validation = if (body.required) {
                "validator.validateContractInput(\"$contractCode\", \"$escapedSchemaName\", requestBody)"
            } else {
                "requestBody?.let { value -> " +
                    "validator.validateContractInput(\"$contractCode\", \"$escapedSchemaName\", value) }"
            }
            add("val validatedRequestBody = $validation")
        }
    }.joinToString("\n")

    private fun LsiLowcodeApiParameter.usesKtorDelegate(): Boolean =
        location in setOf(LowcodeApiParameterLocation.PATH, LowcodeApiParameterLocation.QUERY) &&
            schema.type in setOf("string", "integer", "number", "boolean") &&
            schema.format !in setOf("date", "date-time") &&
            schema.typeRef == null &&
            schema.kotlinType == null

    private fun LsiLowcodeApiParameter.adapterExpression(callReference: String): String {
        val location = "LowcodeApiParameterLocation.${location.name}"
        val method = when {
            schema.type == "array" && required -> "requiredLowcodeParameters"
            schema.type == "array" -> "lowcodeParameters"
            required -> "requiredLowcodeParameter"
            else -> "lowcodeParameter"
        }
        val source = "$callReference.$method($location, \"${name.escapeKotlin()}\")"
        val scalar = schema.items ?: schema
        val conversion = scalar.scalarConversion()
        return when {
            schema.type == "array" && required -> "$source.map { value -> value$conversion }"
            schema.type == "array" -> "$source.map { value -> value$conversion }.takeIf { values -> values.isNotEmpty() }"
            required -> "$source$conversion"
            else -> "$source?.let { value -> value$conversion }"
        }
    }

    private fun LsiLowcodeApiSchema.scalarConversion(): String = when {
        type == "integer" && format == "int64" -> ".toLong()"
        type == "integer" -> ".toInt()"
        type == "number" -> ".toDouble()"
        type == "boolean" -> ".toBooleanStrict()"
        type == "string" && format == "date" -> ".let(LocalDate::parse)"
        type == "string" && format == "date-time" -> ".let(LocalDateTime::parse)"
        else -> ""
    }

    private fun LsiLowcodeCustomOperation.returnType(dtoCatalog: LowcodeDtoTypeCatalog): String {
        if (transport in setOf(LowcodeOperationTransport.SSE, LowcodeOperationTransport.WEBSOCKET)) {
            return "Unit"
        }
        val body = responseBody ?: return "Unit"
        return body.schema.rootType("${operationCode.toPascalCase()}Response", dtoCatalog)
            .withNullability(!body.required)
    }

    private fun LsiLowcodeApiSchema.rootType(
        objectName: String,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): String = if (isObject()) objectName else kotlinType(dtoCatalog)

    private fun LsiLowcodeApiSchema.validationSchemaName(contract: LsiLowcodeContract): String? {
        val schemaName = typeRef
            ?.takeIf { ref -> ref.dtoCode.isNotBlank() }
            ?.componentSchemaName()
            ?: return null
        return schemaName.takeIf { candidate ->
            contract.dtoSchemas.any { schema -> schema.schemaName == candidate }
        }
    }

    private fun LsiLowcodeApiSchema.kotlinType(dtoCatalog: LowcodeDtoTypeCatalog): String {
        kotlinType?.let { type -> return type.renderServiceType() }
        typeRef?.let { ref -> return dtoCatalog.resolve(ref).className }
        return when (type) {
        "string" -> when (format) {
            "date" -> "LocalDate"
            "date-time" -> "LocalDateTime"
            "binary" -> "ByteArray"
            else -> "String"
        }
        "integer" -> if (format == "int64") "Long" else "Int"
        "number" -> "Double"
        "boolean" -> "Boolean"
        "array" -> "List<${items?.kotlinType(dtoCatalog) ?: "Any"}>"
        "object" -> "Map<String, Any?>"
        else -> "Any"
        }
    }

    private fun LsiLowcodeApiSchema.isBinary(): Boolean = type == "string" && format == "binary"

    private fun LsiLowcodeApiSchema.dataClass(
        className: String,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): String = buildString {
        appendLine("data class $className(")
        properties.toSortedMap().forEach { (name, schema) ->
            val nullable = if (name in required) "" else "?"
            appendLine("    val ${name.escapeIdentifier()}: ${schema.kotlinType(dtoCatalog)}$nullable,")
        }
        append(")")
    }

    private fun LsiLowcodeCustomOperation.collectImports(
        imports: MutableSet<String>,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ) {
        val schemas = parameters.map(LsiLowcodeApiParameter::schema) +
            listOfNotNull(requestBody?.schema, responseBody?.schema)
        if (schemas.any { schema -> schema.containsDate() }) {
            imports += "java.time.LocalDate"
        }
        if (schemas.any { schema -> schema.containsDateTime() }) {
            imports += "java.time.LocalDateTime"
        }
        schemas.forEach { schema -> schema.collectDtoImports(imports, dtoCatalog) }
        when (transport) {
            LowcodeOperationTransport.HTTP -> Unit
            LowcodeOperationTransport.SSE -> imports += "io.ktor.server.sse.ServerSSESession"
            LowcodeOperationTransport.WEBSOCKET -> imports += "io.ktor.server.websocket.DefaultWebSocketServerSession"
            LowcodeOperationTransport.INTERNAL -> Unit
        }
    }

    private fun LsiLowcodeCustomOperation.collectControllerImports(
        imports: MutableSet<String>,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ) {
        val requestSchemas = parameters.map(LsiLowcodeApiParameter::schema) + listOfNotNull(requestBody?.schema)
        if (requestSchemas.any { schema -> schema.containsDate() }) {
            imports += "java.time.LocalDate"
        }
        if (requestSchemas.any { schema -> schema.containsDateTime() }) {
            imports += "java.time.LocalDateTime"
        }
        requestSchemas.forEach { schema -> schema.collectDtoImports(imports, dtoCatalog) }
    }

    private fun LsiLowcodeApiSchema.collectDtoImports(
        imports: MutableSet<String>,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ) {
        kotlinType?.collectServiceImports(imports)
        typeRef?.let { ref -> imports += dtoCatalog.resolve(ref).qualifiedName }
        properties.values.forEach { schema -> schema.collectDtoImports(imports, dtoCatalog) }
        items?.collectDtoImports(imports, dtoCatalog)
        oneOf.forEach { schema -> schema.collectDtoImports(imports, dtoCatalog) }
    }

    private fun LsiDtoType.collectServiceImports(imports: MutableSet<String>) {
        if (!qualifiedName.startsWith("kotlin.")) {
            imports += qualifiedName
        }
        arguments.forEach { argument -> argument.collectServiceImports(imports) }
    }

    private fun LsiDtoType.renderServiceType(): String {
        val argumentsSource = arguments.takeIf(List<LsiDtoType>::isNotEmpty)
            ?.joinToString(prefix = "<", postfix = ">") { argument -> argument.renderServiceType() }
            .orEmpty()
        val nullableSuffix = if (nullable) "?" else ""
        return "${qualifiedName.substringAfterLast('.')}" + argumentsSource + nullableSuffix
    }

    private fun LsiLowcodeCustomOperation.containsKotlinType(): Boolean =
        parameters.any { parameter -> parameter.schema.containsKotlinType() } ||
            requestBody?.schema?.containsKotlinType() == true ||
            responseBody?.schema?.containsKotlinType() == true

    private fun LsiLowcodeApiSchema.containsKotlinType(): Boolean =
        kotlinType != null || properties.values.any { schema -> schema.containsKotlinType() } ||
            items?.containsKotlinType() == true || oneOf.any { schema -> schema.containsKotlinType() }

    private fun LsiLowcodeApiSchema.containsDate(): Boolean =
        format == "date" || properties.values.any { schema -> schema.containsDate() } || items?.containsDate() == true

    private fun LsiLowcodeApiSchema.containsDateTime(): Boolean =
        format == "date-time" ||
            properties.values.any { schema -> schema.containsDateTime() } ||
            items?.containsDateTime() == true

    private fun LsiLowcodeApiSchema.isObject(): Boolean = type == "object" || properties.isNotEmpty()

    private fun String.withNullability(nullable: Boolean): String =
        if (nullable && !endsWith('?')) "$this?" else this

    private fun generateImplementationSkill(contract: LsiLowcodeContract): LowcodeGeneratedFile {
        val implementationName = "${contract.className}Impl"
        val implementationPath = contract.implementationSourcePath(implementationName)
        val content = buildString {
            appendLine("# ${contract.name}领域服务实现")
            appendLine()
            appendLine("实现生成接口 `${contract.generatedPackage()}.${contract.className}`。")
            appendLine()
            appendLine("实现文件：`$implementationPath`")
            appendLine()
            appendLine("实现文件位于功能的 `generated/service` 目录；首次创建后允许维护业务逻辑，重新生成不得覆盖。")
            appendLine()
            appendLine("实现类使用 `@Single` 注册并直接实现生成接口，优先命名为 `$implementationName`。")
            appendLine()
            appendLine("必须逐个实现以下领域方法：")
            contract.operations.sortedBy(LsiLowcodeCustomOperation::operationCode).forEach { operation ->
                appendLine("- `${operation.operationCode}`：${operation.description ?: operation.name}")
            }
            appendLine()
            val generatedOperations = contract.operations.filter(LsiLowcodeCustomOperation::isGenerated)
            val serviceOnlyOperations = contract.operations.filter { operation ->
                operation.implementation == LowcodeOperationImplementation.SERVICE_ONLY
            }
            if (generatedOperations.isNotEmpty()) {
                appendLine("GENERATED 操作的路由、参数读取和响应包装由生成 Controller 负责。")
            }
            if (serviceOnlyOperations.isNotEmpty()) {
                appendLine("SERVICE_ONLY 操作是无传输层的内部 SPI；生成 Service 实现只编排领域行为。")
            }
        }
        return contract.markdownFile("SKILL", content)
    }

    private fun generateTestRedLines(contract: LsiLowcodeContract): LowcodeGeneratedFile {
        val content = buildString {
            appendLine("# 单元测试红线")
            appendLine()
            appendLine("- 每个领域方法至少覆盖成功路径、参数边界和下游失败传播。")
            appendLine("- 测试直接调用 `${contract.className}`，不要只验证 Ktor 路由状态码。")
            appendLine("- 不允许在生成接口或生成适配器中补业务条件分支。")
            appendLine("- 时间范围、分页边界和授权决策必须使用确定性输入断言。")
            contract.operations.sortedBy(LsiLowcodeCustomOperation::operationCode).forEach { operation ->
                appendLine("- `${operation.operationCode}` 必须断言返回结构符合契约 Schema。")
            }
        }
        return contract.markdownFile("UNIT_TEST_RED_LINES", content)
    }

    private fun LsiLowcodeContract.generatedPackage(): String =
        featurePackageName.generatedLayout().packageName(LowcodeGeneratedResourceKind.SERVICE)

    private fun LsiLowcodeContract.controllerGeneratedPackage(): String =
        featurePackageName.generatedLayout().packageName(LowcodeGeneratedResourceKind.CONTROLLER)

    private fun LsiLowcodeContract.controllerClassName(): String {
        val ownerName = className.removeSuffix("ServiceContract").removeSuffix("Service")
        return "${ownerName.ifBlank { className }}Controller"
    }

    private fun LsiLowcodeContract.normalizedRouteKey(): String = path.trimEnd('/').ifEmpty { "/" }

    private fun LsiLowcodeContract.kotlinFile(fileName: String, content: String): LowcodeGeneratedFile {
        val layout = featurePackageName.generatedLayout()
        val packageName = generatedPackage()
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = fileName,
            relativePath = layout.relativeSourcePath(LowcodeGeneratedResourceKind.SERVICE, fileName),
            content = generatedByStudio(content),
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun LsiLowcodeContract.controllerKotlinFile(
        fileName: String,
        content: String,
        deliveryKind: LowcodeGeneratedFileKind = LowcodeGeneratedFileKind.CONTRACT_CONTROLLER,
    ): LowcodeGeneratedFile {
        val layout = featurePackageName.generatedLayout()
        val packageName = controllerGeneratedPackage()
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = fileName,
            relativePath = layout.relativeSourcePath(LowcodeGeneratedResourceKind.CONTROLLER, fileName),
            content = generatedByStudio(content),
            kind = deliveryKind,
        )
    }


    private fun LsiLowcodeContract.markdownFile(fileName: String, content: String): LowcodeGeneratedFile =
        LowcodeGeneratedFile(
            packageName = "",
            fileName = fileName,
            relativePath = "generated/contracts/$contractCode/$fileName.md",
            content = generatedByStudio(content, extensionName = "md"),
            extensionName = "md",
            kind = LowcodeGeneratedFileKind.DOCUMENTATION,
        )

    private fun LsiLowcodeContract.implementationSourcePath(implementationName: String): String {
        val layout = featurePackageName.generatedLayout()
        return layout.relativeSourcePath(
            LowcodeGeneratedResourceKind.SERVICE,
            implementationName,
        )
    }

    private fun String.toPascalCase(): String = split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
        .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

    private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

    private fun String.escapeKotlin(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\$", "\\\$")

    private fun String.escapeKDoc(): String = replace("*/", "* /")

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
        "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",
    )
}
