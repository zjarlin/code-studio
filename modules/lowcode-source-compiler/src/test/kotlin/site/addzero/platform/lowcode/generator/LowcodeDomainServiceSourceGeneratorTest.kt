package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.dto.compiler.LsiDtoType

class LowcodeDomainServiceSourceGeneratorTest {
    @Test
    fun `ordinary HTTP operations use value returning mapping DSL`() {
        val operations = LowcodeHttpMethod.entries.map { method ->
            val operationCode = method.name.lowercase()
            LsiLowcodeCustomOperation(
                operationCode = operationCode,
                name = operationCode,
                path = "/mapping/$operationCode",
                method = method,
                responseBody = LsiLowcodeApiBody(
                    schema = LsiLowcodeApiSchema(type = "string"),
                ),
            )
        }
        val contract = LsiLowcodeContract(
            contractCode = "mapping",
            name = "Mapping",
            packageName = "example.mapping",
            className = "MappingService",
            path = "/mapping",
            operations = operations,
        )

        val controller = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.fileName == "MappingController" }

        LowcodeHttpMethod.entries.forEach { method ->
            val mappingFunction = method.name.lowercase() + "Mapping"
            val webPackage = generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)
            assertTrue(controller.content.contains("$webPackage.$mappingFunction"))
            assertTrue(controller.content.contains("$mappingFunction(\"/${method.name.lowercase()}\") {"))
        }
        assertFalse(controller.content.contains("HttpMethod"))
        assertFalse(controller.content.contains("handle {"))
        assertFalse(controller.content.contains("respondOperationPayload"))
        assertFalse(controller.content.contains("io.ktor.server.application.call"))
        assertFalse(controller.content.contains("io.ktor.server.routing.route"))
    }

    @Test
    fun `binary response generates byte array service return`() {
        val contract = LsiLowcodeContract(
            contractCode = "archive",
            name = "Archive",
            packageName = "example.archive",
            className = "ArchiveService",
            path = "/archive",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "download",
                    name = "Download",
                    path = "/archive/download",
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(type = "string", format = "binary"),
                    ),
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)
        val service = files.single { file -> file.fileName == "ArchiveService" }
        val controller = files.single { file -> file.fileName == "ArchiveController" }

        assertTrue(service.content.contains("suspend fun download(): ByteArray"))
        assertTrue(controller.content.contains("import io.ktor.server.routing.route"))
        assertTrue(controller.content.contains("route(\"/download\")"))
        assertTrue(controller.content.contains("val responseContentType = ContentType.parse(\"application/json\")"))
        assertTrue(controller.content.contains("call.respondBytes(response, responseContentType)"))
    }

    @Test
    fun `service operation can generate a synchronous method`() {
        val contract = LsiLowcodeContract(
            contractCode = "progress",
            name = "Progress",
            packageName = "example.progress",
            className = "ProgressService",
            path = "/progress",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "get",
                    name = "Get progress",
                    path = "/progress",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    suspending = false,
                    responseBody = LsiLowcodeApiBody(schema = LsiLowcodeApiSchema(type = "string")),
                ),
            ),
        )

        val service = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.fileName == "ProgressService" }

        assertTrue(service.content.contains("fun get(): String"))
        assertFalse(service.content.contains("suspend fun get"))
    }

    @Test
    fun `service only operation supports structured Kotlin types`() {
        val outputType = LsiDtoType(
            qualifiedName = "kotlinx.coroutines.flow.Flow",
            arguments = listOf(LsiDtoType.STRING),
        )
        val contract = LsiLowcodeContract(
            contractCode = "streamingStorage",
            name = "Streaming storage",
            packageName = "example.storage",
            className = "StreamingStorageService",
            path = "/storage",
            contributorId = "example.example",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "observe",
                    name = "Observe",
                    path = "/storage/observe",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "output",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = true,
                            schema = LsiLowcodeApiSchema(
                                kotlinType = LsiDtoType("java.io.OutputStream"),
                            ),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(kotlinType = outputType),
                    ),
                ),
            ),
        )

        val service = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.fileName == "StreamingStorageService" }

        assertTrue(service.content.contains("import java.io.OutputStream"))
        assertTrue(service.content.contains("import kotlinx.coroutines.flow.Flow"))
        assertTrue(service.content.contains("output: OutputStream"))
        assertTrue(service.content.contains("): Flow<String>"))
    }

    @Test
    fun `internal operation generates only a typed service contract`() {
        val contract = LsiLowcodeContract(
            contractCode = "executor",
            name = "内部执行器",
            packageName = "example.executor",
            className = "ExecutorService",
            path = "/internal/executor",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "execute",
                    name = "执行",
                    path = "/internal/executor/execute",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "input",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = true,
                            schema = LsiLowcodeApiSchema(
                                kotlinType = LsiDtoType("example.protocol.ExecutorInput"),
                            ),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(
                            kotlinType = LsiDtoType("example.protocol.ExecutorOutput"),
                        ),
                    ),
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)
        val service = files.single { file -> file.fileName == "ExecutorService" }

        assertTrue(service.content.contains("input: ExecutorInput"))
        assertTrue(service.content.contains("): ExecutorOutput"))
        assertFalse(files.any { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER })
    }

    @Test
    fun `optional structured Kotlin type receives one nullable suffix`() {
        val contract = LsiLowcodeContract(
            contractCode = "optionalTime",
            name = "Optional time",
            packageName = "example.time",
            className = "OptionalTimeService",
            path = "/time",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "find",
                    name = "Find",
                    path = "/time/find",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "time",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = false,
                            schema = LsiLowcodeApiSchema(
                                kotlinType = LsiDtoType("java.time.LocalDateTime", nullable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val source = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.fileName == "OptionalTimeService" }.content

        assertTrue(source.contains("time: LocalDateTime?"))
        assertFalse(source.contains("LocalDateTime??"))
    }

    @Test
    fun `generated REST operation rejects structured Kotlin types`() {
        val contract = LsiLowcodeContract(
            contractCode = "invalidStorage",
            name = "Invalid storage",
            packageName = "example.storage",
            className = "InvalidStorageService",
            path = "/storage",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "write",
                    name = "Write",
                    path = "/storage/write",
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "output",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = true,
                            schema = LsiLowcodeApiSchema(kotlinType = LsiDtoType("java.io.OutputStream")),
                        ),
                    ),
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeDomainServiceSourceGenerator.generate(contract)
        }

        assertTrue(error.message.orEmpty().contains("SERVICE_ONLY"))
    }

    @Test
    fun `optional response body generates nullable service return`() {
        val contract = LsiLowcodeContract(
            contractCode = "catalog",
            name = "Catalog",
            packageName = "example.catalog",
            className = "CatalogService",
            path = "/catalog",
            contributorId = "example.example",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "findItem",
                    name = "Find item",
                    path = "/catalog/item",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    responseBody = LsiLowcodeApiBody(
                        required = false,
                        schema = LsiLowcodeApiSchema(type = "string"),
                    ),
                ),
            ),
        )

        val service = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.fileName == "CatalogService" }

        assertTrue(service.content.contains("suspend fun findItem(): String?"))
    }

    @Test
    fun `existing REST contract does not generate a metadata controller`() {
        val contract = LsiLowcodeContract(
            contractCode = "existingNews",
            name = "既有新闻接口",
            packageName = "example.news",
            className = "ExistingNewsService",
            path = "/news",
            contributorId = "example.example-news",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "createNews",
                    name = "创建新闻",
                    path = "/news/create",
                    implementation = LowcodeOperationImplementation.EXISTING_REST,
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)

        assertTrue(files.isEmpty())
    }

    @Test
    fun `service only operation generates contract without duplicate route`() {
        val contract = LsiLowcodeContract(
            contractCode = "existingMail",
            name = "既有邮件接口",
            packageName = "example.mail",
            className = "MailApplicationService",
            path = "/mail",
            contributorId = "example.example-mail",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "sendMail",
                    name = "发送邮件",
                    path = "/mail/send",
                    transport = LowcodeOperationTransport.INTERNAL,
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                    responseBody = LsiLowcodeApiBody(schema = LsiLowcodeApiSchema(type = "integer", format = "int64")),
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)

        val service = files.single { file -> file.fileName == "MailApplicationService" }
        assertTrue(service.content.contains("suspend fun sendMail(): Long"))
        assertFalse(files.any { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER })
        assertTrue(
            files.single { file -> file.fileName == "SKILL" }.content
                .contains("SERVICE_ONLY 操作是无传输层的内部 SPI"),
        )
    }

    @Test
    fun `custom operation can bind request and response directly to an entity`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "News",
            tableName = "cms_news",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example-news",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val entityRef = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news"))
        val contract = LsiLowcodeContract(
            contractCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "NewsService",
            path = "/news",
            contributorId = model.contributorId,
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "saveNews",
                    name = "保存新闻",
                    path = "/news",
                    requestBody = LsiLowcodeApiBody(schema = entityRef),
                    responseBody = LsiLowcodeApiBody(schema = entityRef),
                ),
            ),
        )

        val files = LowcodeSourceCompiler.generate(contract, listOf(model))
        val service = files.single { file -> file.fileName == "NewsService" }
        val controller = files.single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }

        assertTrue(service.content.contains("import example.news.generated.entity.News"))
        assertTrue(service.content.contains("request: News"))
        assertTrue(service.content.contains("): News"))
        assertTrue(controller.content.contains("contractResolver.requireContract(\"news\")"))
        assertTrue(controller.content.contains("val requestBody = call.receive<News>()"))
        assertFalse(controller.content.contains("LowcodeRuntimeValidator"))
        assertFalse(controller.content.contains("validateContractInput"))
        assertFalse(controller.content.contains("LsiLowcodeDtoRef"))
        assertFalse(files.any { file -> file.packageName.contains(".contributor") })
    }

    @Test
    fun `resolves named dto references without generated package names in contract metadata`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "News",
            tableName = "cms_news",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example-news",
            dtoDefinitions = listOf(
                LsiLowcodeDto("newsView", "NewsView", LowcodeDtoKind.VIEW),
                LsiLowcodeDto("newsRequest", "NewsRequest", LowcodeDtoKind.INPUT),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val contract = LsiLowcodeContract(
            contractCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "NewsService",
            path = "/news",
            contributorId = model.contributorId,
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "saveNews",
                    name = "保存新闻",
                    path = "/news",
                    callContext = true,
                    requestBody = LsiLowcodeApiBody(
                        description = "填写新闻内容。",
                        schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news", "newsRequest")),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news", "newsView")),
                    ),
                ),
            ),
        )

        val files = LowcodeSourceCompiler.generate(contract, listOf(model))
        val service = files.single { file -> file.fileName == "NewsService" }
        val controller = files.single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }

        assertTrue(service.content.contains("import example.news.generated.dto.NewsRequest"))
        assertTrue(service.content.contains("import example.news.generated.dto.NewsView"))
        assertFalse(service.content.contains("CallContext"))
        assertTrue(service.content.contains("request: NewsRequest"))
        assertTrue(service.content.contains("): NewsView"))
        assertEquals("NewsController", controller.fileName)
        assertEquals("example.news.generated.controller", controller.packageName)
        assertFalse(controller.content.contains("import io.ktor.server.routing.handle"))
        assertTrue(controller.content.contains("private val service: NewsService"))
        assertFalse(controller.content.contains("callContext"))
        assertTrue(controller.content.contains("val requestBody = call.receive<NewsRequest>()"))
        assertTrue(controller.content.contains("private val validator: LowcodeRuntimeValidator"))
        assertTrue(
            controller.content.contains(
                "val validatedRequestBody = validator.validateContractInput(" +
                    "\"news\", \"news_newsRequest\", requestBody)",
            ),
        )
        assertFalse(controller.content.contains("validateContractInput(\"news\", \"news_newsRequest\", call.receive"))
        assertTrue(controller.content.contains("service.saveNews("))
        assertFalse(controller.content.contains("requirePermission"))
        assertTrue(controller.content.lineSequence().any { line -> line == "@Single" })
        assertTrue(controller.content.contains(") : Controller, LowcodeContractProvider"))
        assertTrue(controller.content.contains("contractResolver.requireContract(\"news\")"))
        assertFalse(controller.content.contains("LsiLowcodeContract"))
        assertFalse(controller.content.contains("LowcodeRestContract("))
        assertFalse(files.any { file -> file.packageName.contains(".contributor") })
    }

    @Test
    fun `rejects structure dto from a public operation`() {
        val command = LsiLowcodeDtoDefinition(
            dtoCode = "exampleCommand",
            name = "示例命令",
            packageName = "example.command",
            className = "ExampleCommand",
            kind = LowcodeDtoKind.STRUCTURE,
            contributorId = "example.example-command",
            fields = listOf(
                LsiLowcodeDtoField(
                    name = "value",
                    nullability = LowcodeDtoNullability.NON_NULL,
                    kotlinType = LsiDtoType("kotlin.String"),
                ),
            ),
        )
        val contract = LsiLowcodeContract(
            contractCode = "exampleCommand",
            name = "示例命令",
            packageName = "example.command",
            className = "ExampleCommandService",
            path = "/example-command",
            contributorId = command.contributorId,
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "validate",
                    name = "校验示例命令",
                    path = "/example-command/validate",
                    requestBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(typeRef = command.ref),
                    ),
                    responseBody = LsiLowcodeApiBody(schema = LsiLowcodeApiSchema(type = "boolean")),
                ),
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            LowcodeSourceCompiler.generate(contract, emptyList(), listOf(command))
        }

        assertTrue(error.message.orEmpty().contains("OpenAPI 操作 validate"))
        assertTrue(error.message.orEmpty().contains("exampleCommand"))
    }

    @Test
    fun `aggregates typed operations into one domain service and generated adapter`() {
        val contract = LsiLowcodeContract(
            contractCode = "examplePlan",
            name = "示例方案",
            packageName = "example.planning",
            className = "ExamplePlanService",
            path = "/example-plans",
            contributorId = "example.planning",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "search7days",
                    name = "查询未来七天的方案",
                    description = "查询未来七天的方案。",
                    path = "/example-plans/next-seven-days",
                    method = LowcodeHttpMethod.GET,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "startDate",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = true,
                            schema = LsiLowcodeApiSchema(type = "string", format = "date"),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(
                            type = "object",
                            properties = mapOf(
                                "planId" to LsiLowcodeApiSchema(type = "integer", format = "int64"),
                                "name" to LsiLowcodeApiSchema(type = "string"),
                            ),
                            required = setOf("planId", "name"),
                        ),
                    ),
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)
        val service = files.single { file -> file.fileName == "ExamplePlanService" }
        val controller = files.single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }
        val skill = files.single { file -> file.fileName == "SKILL" }

        assertEquals("example.planning.generated.service", service.packageName)
        assertEquals(LowcodeGeneratedFileKind.COMPILED_SOURCE, service.kind)
        assertTrue(service.content.startsWith("// generated by studio"))
        assertTrue(service.relativePath.startsWith("src/main/kotlin/"))
        assertTrue(service.content.contains("interface ExamplePlanService"))
        assertTrue(service.content.contains("suspend fun search7days("))
        assertTrue(service.content.contains("startDate: LocalDate"))
        assertTrue(service.content.contains("): Search7daysResponse"))
        assertEquals("ExamplePlanController", controller.fileName)
        assertEquals("example.planning.generated.controller", controller.packageName)
        assertTrue(controller.relativePath.startsWith("src/main/kotlin/"))
        assertTrue(controller.content.contains("override val routeKey = \"/example-plans\""))
        assertFalse(controller.content.contains("routeKey: String"))
        assertTrue(controller.content.contains("override fun Route.installEndpoints()"))
        assertFalse(controller.content.contains("route.route(routeKey)"))
        assertTrue(controller.content.contains("private fun Route.registerSearch7days()"))
        assertTrue(controller.content.contains("getMapping(\"/next-seven-days\")"))
        assertFalse(controller.content.contains("route(\"/next-seven-days\")"))
        assertFalse(controller.content.contains("route(\"/example-plans/next-seven-days\")"))
        assertTrue(controller.content.contains("private val service: ExamplePlanService"))
        assertTrue(controller.content.contains("service.search7days("))
        assertTrue(controller.content.contains("requiredLowcodeParameter"))
        assertTrue(controller.content.contains("getMapping"))
        assertTrue(
            controller.content.contains(
                "${generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)}.getMapping",
            ),
        )
        assertFalse(controller.content.contains("respondOperationPayload(response, true)"))
        assertFalse(controller.content.contains("respondLowcodeResult"))
        assertTrue(controller.content.lineSequence().any { line -> line == "@Single" })
        assertTrue(controller.content.contains(") : Controller, LowcodeContractProvider"))
        assertTrue(controller.content.contains("contractResolver.requireContract(\"examplePlan\")"))
        assertFalse(controller.content.contains("LsiLowcodeContract"))
        assertFalse(files.any { file -> file.packageName.contains(".contributor") })
        assertTrue(skill.content.contains("ExamplePlanServiceImpl"))
        assertTrue(
            skill.content.contains(
                "src/main/kotlin/example/planning/generated/service/ExamplePlanServiceImpl.kt",
            ),
        )
        assertTrue(skill.content.contains("重新生成不得覆盖"))
        assertFalse(files.any { file -> file.content.contains("class ExamplePlanServiceImpl") })
        assertFalse(files.any { file -> file.fileName.endsWith("LowcodeHandler") })
    }

    @Test
    fun `generated scalar path and query parameters use typed Ktor delegates`() {
        val contract = LsiLowcodeContract(
            contractCode = "example",
            name = "示例服务",
            packageName = "example.contract",
            className = "ExampleService",
            path = "/examples",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "get",
                    name = "查询示例",
                    path = "/examples/{slug}",
                    method = LowcodeHttpMethod.GET,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "slug",
                            location = LowcodeApiParameterLocation.PATH,
                            required = true,
                            schema = LsiLowcodeApiSchema(type = "string"),
                        ),
                        LsiLowcodeApiParameter(
                            name = "id",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = true,
                            schema = LsiLowcodeApiSchema(type = "integer", format = "int64"),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(schema = LsiLowcodeApiSchema(type = "boolean")),
                ),
            ),
        )

        val controller = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }

        assertTrue(controller.content.contains("import io.ktor.server.util.getValue"))
        assertTrue(controller.content.contains("val slug: String by call.pathParameters"))
        assertTrue(controller.content.contains("val id: Long by call.queryParameters"))
        assertTrue(controller.content.contains("getMapping(\"/{slug}\")"))
        assertFalse(controller.content.contains("io.ktor.server.routing.route"))
        val serviceArguments = controller.content.substringAfter("service.get(").substringBefore(")")
        assertTrue(serviceArguments.contains("slug,"))
        assertTrue(serviceArguments.contains("id,"))
        assertFalse(controller.content.contains("requiredLowcodeParameter"))
        assertFalse(controller.content.contains("LowcodeApiParameterLocation"))
    }

    @Test
    fun `generated controllers delegate authentication to the API interceptor`() {
        val contract = LsiLowcodeContract(
            contractCode = "example",
            name = "示例服务",
            packageName = "example.contract",
            className = "ExampleService",
            path = "/examples",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "protectedGet",
                    name = "受保护查询",
                    path = "/examples/protected",
                    method = LowcodeHttpMethod.GET,
                ),
                LsiLowcodeCustomOperation(
                    operationCode = "publicGet",
                    name = "公开查询",
                    path = "/examples/public",
                    method = LowcodeHttpMethod.GET,
                    authenticated = false,
                ),
            ),
        )

        val controller = LowcodeDomainServiceSourceGenerator.generate(contract)
            .single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }

        assertFalse(controller.content.contains("authenticate("))
        assertFalse(controller.content.contains("JwtTokenService"))
        assertTrue(
            controller.content.contains(
                "import ${generationTargetSymbol(GenerationTargetSymbols.WEB_RUNTIME_PACKAGE)}.allowAnonymous",
            ),
        )
        assertTrue(controller.content.contains("allowAnonymous {"))
        assertTrue(controller.content.contains("getMapping(\"/public\")"))
        assertFalse(controller.content.contains("io.ktor.server.routing.route"))
    }

    @Test
    fun `rejects an operation path outside its controller route key`() {
        val contract = LsiLowcodeContract(
            contractCode = "example",
            name = "示例服务",
            packageName = "example.contract",
            className = "ExampleService",
            path = "/examples",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "listOther",
                    name = "查询其他数据",
                    path = "/other",
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeDomainServiceSourceGenerator.generate(contract)
        }

        assertTrue(error.message.orEmpty().contains("routeKey /examples"))
    }

    @Test
    fun `generates streaming operations inside the contract controller`() {
        val contract = LsiLowcodeContract(
            contractCode = "events",
            name = "事件服务",
            packageName = "example.events",
            className = "EventService",
            path = "/events",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "streamEvents",
                    name = "流式查询事件",
                    path = "/events/stream",
                    transport = LowcodeOperationTransport.SSE,
                ),
                LsiLowcodeCustomOperation(
                    operationCode = "connectEvents",
                    name = "连接事件通道",
                    path = "/events/socket",
                    transport = LowcodeOperationTransport.WEBSOCKET,
                ),
            ),
        )

        val files = LowcodeDomainServiceSourceGenerator.generate(contract)
        val controller = files.single { file -> file.kind == LowcodeGeneratedFileKind.CONTRACT_CONTROLLER }

        assertTrue(controller.content.contains("route(\"/stream\")"))
        assertTrue(controller.content.contains("sse {"))
        assertTrue(Regex("""service\.streamEvents\(\s+this,""").containsMatchIn(controller.content))
        assertTrue(controller.content.contains("route(\"/socket\")"))
        assertTrue(controller.content.contains("webSocket {"))
        assertTrue(Regex("""service\.connectEvents\(\s+this,""").containsMatchIn(controller.content))
        assertFalse(files.any { file -> file.fileName.endsWith("LowcodeHandler") })
    }
}
