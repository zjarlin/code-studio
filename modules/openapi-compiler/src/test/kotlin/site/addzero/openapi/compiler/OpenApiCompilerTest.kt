package site.addzero.openapi.compiler

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.platform.lowcode.generator.LsiLowcodeApiBody
import site.addzero.platform.lowcode.generator.LsiLowcodeApiParameter
import site.addzero.platform.lowcode.generator.LsiLowcodeApiSchema
import site.addzero.platform.lowcode.generator.LsiLowcodeContract
import site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation
import site.addzero.platform.lowcode.generator.LsiLowcodeProperty
import site.addzero.platform.lowcode.generator.LsiLowcodeQueryField
import site.addzero.platform.lowcode.generator.LsiLowcodeRoute
import site.addzero.platform.lowcode.generator.LowcodeApiParameterLocation
import site.addzero.platform.lowcode.generator.LowcodeHttpMethod
import site.addzero.platform.lowcode.generator.LowcodeDtoKind
import site.addzero.platform.lowcode.generator.LowcodeModelKind
import site.addzero.platform.lowcode.generator.LowcodeModelMeta
import site.addzero.platform.lowcode.generator.LowcodeRelationKind
import site.addzero.platform.lowcode.generator.LowcodeRelationMeta
import site.addzero.platform.lowcode.generator.LowcodeSourceCompiler
import site.addzero.platform.lowcode.generator.LsiLowcodeDto
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoField
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoRef
import site.addzero.platform.lowcode.generator.LsiLowcodeDiscriminator
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoSchema
import site.addzero.platform.lowcode.generator.LowcodeFieldMeta
import site.addzero.platform.lowcode.generator.toLsiDtoSchemas
import site.addzero.platform.lowcode.generator.toLsiEntitySchema
import site.addzero.validation.compiler.LsiValidationRule

class OpenApiCompilerTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `normalizes int64 schemas and examples without mutating business example objects`() {
        val baseDocument = baseDocument().apply {
            val schemas = (get("components") as ObjectNode).putObject("schemas")
            schemas.putObject("LongValue").apply {
                put("type", "integer")
                put("format", "int64")
                put("example", 7340032L)
                put("default", 7340033L)
                put("const", 7340034L)
                putArray("enum").add(7340032L).add(7340033L)
                putArray("examples").add(7340034L)
            }
            schemas.putObject("BusinessPayload").apply {
                put("type", "object")
                putObject("example").putObject("shape").apply {
                    put("type", "integer")
                    put("format", "int64")
                    put("example", 7L)
                }
                putObject("x-business-metadata").apply {
                    put("type", "integer")
                    put("format", "int64")
                }
            }
            (get("paths") as ObjectNode).putObject("/example/{id}").putObject("get").apply {
                putArray("parameters").addObject().apply {
                    put("name", "id")
                    put("in", "path")
                    put("required", true)
                    put("example", 7340032L)
                    putObject("schema").put("type", "integer").put("format", "int64")
                }
                putObject("responses").putObject("200").apply {
                    put("description", "成功")
                    putObject("headers").putObject("Content-Length").apply {
                        put("example", 7340032L)
                        putObject("schema").put("type", "integer").put("format", "int64")
                    }
                }
            }
        }

        val document = OpenApiCompiler.compile(baseDocument, emptyList())
        val longSchema = document["components"]["schemas"]["LongValue"]

        assertEquals("string", longSchema["type"].asString())
        assertFalse(longSchema.has("format"))
        listOf("example", "default", "const").forEach { name ->
            assertTrue(longSchema[name].isString)
        }
        assertTrue(longSchema["enum"].values().asSequence().all(JsonNode::isString))
        assertTrue(longSchema["examples"].values().asSequence().all(JsonNode::isString))
        val parameter = document["paths"]["/example/{id}"]["get"]["parameters"][0]
        assertEquals("string", parameter["schema"]["type"].asString())
        assertEquals("7340032", parameter["example"].asString())
        val contentLength = document["paths"]["/example/{id}"]["get"]["responses"]["200"]
            .get("headers")["Content-Length"]
        assertEquals("string", contentLength["schema"]["type"].asString())
        assertEquals("7340032", contentLength["example"].asString())
        val businessSchema = document["components"]["schemas"]["BusinessPayload"]
        assertEquals("integer", businessSchema["example"]["shape"]["type"].asString())
        assertEquals("int64", businessSchema["example"]["shape"]["format"].asString())
        assertTrue(businessSchema["example"]["shape"]["example"].isIntegralNumber)
        assertEquals("integer", businessSchema["x-business-metadata"]["type"].asString())
        assertEquals("integer", baseDocument["components"]["schemas"]["LongValue"]["type"].asString())
        assertTrue(baseDocument["components"]["schemas"]["LongValue"]["example"].isIntegralNumber)
    }

    @Test
    fun `publishes stable dto components and contract references`() {
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
            contributorId = "example.news",
            dtoDefinitions = listOf(
                LsiLowcodeDto(
                    dtoCode = "newsView",
                    className = "NewsView",
                    kind = LowcodeDtoKind.VIEW,
                    fields = listOf(
                        LsiLowcodeDtoField(
                            name = "title",
                            validations = listOf(
                                LsiValidationRule("maxLength", parameters = mapOf("value" to "180")),
                            ),
                        ),
                    ),
                ),
            ),
            fields = listOf(
                LowcodeFieldMeta(
                    id = 1,
                    modelId = 1,
                    orderNo = 1,
                    fieldCode = "title",
                    label = "标题",
                    kotlinType = "String",
                    dbColumn = "title",
                    required = true,
                    listVisible = true,
                    formVisible = true,
                    formControl = "input",
                    dictCode = null,
                    defaultValue = null,
                    remark = null,
                    maxLength = 180,
                ),
            ),
            queries = emptyList(),
            relations = emptyList(),
        )
        val storedFile = model.copy(
            id = 2,
            modelCode = "storedFile",
            name = "文件",
            packageName = "example.file",
            className = "StoredFile",
            tableName = "stored_file",
            dtoDefinitions = emptyList(),
            fields = listOf(
                model.fields.single().copy(
                    id = 2,
                    modelId = 2,
                    fieldCode = "url",
                    label = "地址",
                    dbColumn = "url",
                ),
            ),
        )
        val entityModel = model.copy(
            relations = listOf(
                LowcodeRelationMeta(
                    id = 3,
                    modelId = 1,
                    orderNo = 1,
                    relationCode = "coverFile",
                    label = "封面文件",
                    relationKind = LowcodeRelationKind.MANY_TO_ONE,
                    targetModelId = 2,
                    targetModelCode = "storedFile",
                    targetPackageName = "example.file",
                    targetClassName = "StoredFile",
                    joinColumn = "cover_file_id",
                    mappedBy = null,
                    joinTable = null,
                    joinTableJoinColumn = null,
                    joinTableInverseColumn = null,
                    required = false,
                    listVisible = true,
                    formVisible = true,
                ),
            ),
        )
        val contract = LsiLowcodeContract(
            contractCode = "news",
            name = "新闻",
            packageName = "example.news.contract",
            className = "NewsService",
            path = "/news",
            contributorId = model.contributorId,
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "getNews",
                    name = "查询新闻",
                    path = "/news/{id}",
                    method = LowcodeHttpMethod.GET,
                    authenticated = true,
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news", "newsView")),
                    ),
                ),
                LsiLowcodeCustomOperation(
                    operationCode = "saveNews",
                    name = "保存新闻",
                    path = "/news",
                    method = LowcodeHttpMethod.POST,
                    requestBody = LsiLowcodeApiBody(
                        description = "填写新闻数据。",
                        schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news")),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef("news")),
                    ),
                ),
            ),
        )
        val resolvedContract = LowcodeSourceCompiler.resolveContract(
            contract,
            listOf(entityModel, storedFile),
        )
        val generatedController = LowcodeSourceCompiler.generate(
            contract,
            listOf(entityModel, storedFile),
        ).single { file -> file.fileName == "NewsController" }
        val generatedEntity = LowcodeSourceCompiler.generate(
            entityModel,
            listOf(entityModel, storedFile),
        ).single { file -> file.relativePath.endsWith("/generated/entity/News.kt") }

        val document = OpenApiCompiler.compile(
            baseDocument(),
            listOf(resolvedContract.toLsiOpenApiContract()),
        )
        val operation = document["paths"]["/news/{id}"]["get"]
        val saveOperation = document["paths"]["/news"]["post"]
        val entitySchema = document["components"]["schemas"]["news_entity"]
        val coverFileSchema = document["components"]["schemas"]["news_entity"]["properties"]["coverFile"]

        assertTrue(generatedEntity.content.contains("@Entity"))
        assertTrue(generatedEntity.content.contains(" * 标题"))
        assertTrue(generatedController.content.contains("import example.news.generated.entity.News"))
        assertTrue(generatedController.content.contains("val requestBody = call.receive<News>()"))
        assertEquals("新闻", entitySchema["description"].asString())
        assertEquals("标题", entitySchema["properties"]["title"]["description"].asString())
        assertEquals("填写新闻数据。", saveOperation["requestBody"]["description"].asString())
        assertEquals(
            "#/components/schemas/news_entity",
            saveOperation["requestBody"]["content"]["application/json"]["schema"]["\$ref"].asString(),
        )
        assertEquals("NewsView", document["components"]["schemas"]["news_newsView"]["x-kotlin-class"].asString())
        assertEquals("News", document["components"]["schemas"]["news_entity"]["x-kotlin-class"].asString())
        assertEquals(
            "标题",
            document["components"]["schemas"]["news_newsView"]["properties"]["title"]["description"].asString(),
        )
        assertEquals(180, document["components"]["schemas"]["news_newsView"]["properties"]["title"]["maxLength"].asInt())
        assertEquals("object", coverFileSchema["type"].asString())
        val logicalCoverFileId = resolvedContract.dtoSchemas
            .single { schema -> schema.ref == LsiLowcodeDtoRef("news") }
            .properties.getValue("coverFile")
            .properties.getValue("id")
        assertEquals("integer", logicalCoverFileId.type)
        assertEquals("int64", logicalCoverFileId.format)
        assertEquals("string", coverFileSchema["properties"]["id"]["type"].asString())
        assertFalse(coverFileSchema["properties"]["id"].has("format"))
        assertEquals("string", coverFileSchema["properties"]["url"]["type"].asString())
        assertEquals(
            "#/components/schemas/news_newsView",
            operation["responses"]["200"]["content"]["application/json"]["schema"]
                ["allOf"][1]["properties"]["data"]["\$ref"].asString(),
        )
        assertFalse(operation.has("x-permission"))
        assertEquals("jwt", operation["security"][0].propertyNames().first())
        assertEquals(
            "#/components/schemas/news_entity",
            document["paths"]["/news"]["post"]["responses"]["200"]["content"]["application/json"]["schema"]
                ["allOf"][1]["properties"]["data"]["\$ref"].asString(),
        )
    }

    @Test
    fun `compiles model metadata into schemas and CRUD operations`() {
        val route = LsiLowcodeRoute(
            packageName = "example.user",
            qualifiedName = "example.user.User",
            className = "User",
            modelCode = "user",
            description = "示例用户。",
            path = "/example/users",
            authenticated = false,
            enabledOperations = setOf("CREATE", "UPSERT", "UPDATE", "PAGE", "SIMPLE_LIST", "LIST_BY_CONDITION"),
            properties = listOf(
                LsiLowcodeProperty(
                    name = "id",
                    type = "integer",
                    format = "int64",
                    required = true,
                    identifier = true,
                    arrayItemType = null,
                    description = "用户编号。",
                    dictionaryCode = null,
                    referenceTargetModelCode = "storedFile",
                    referencePropertyName = "avatarFile",
                ),
                LsiLowcodeProperty(
                    name = "createTime",
                    type = "string",
                    format = "date-time",
                    required = true,
                    createWritable = false,
                    updateWritable = false,
                    arrayItemType = null,
                    description = "创建时间。",
                ),
                LsiLowcodeProperty(
                    name = "state",
                    type = "string",
                    format = null,
                    required = true,
                    arrayItemType = null,
                    description = "State",
                    enumValues = listOf("OPEN", "CLOSED"),
                ),
            ),
            queryFields = listOf(
                LsiLowcodeQueryField(
                    propertyName = "id",
                    parameterName = "excludedIds",
                    operator = "NOT_IN",
                    type = "integer",
                    format = "int64",
                ),
                LsiLowcodeQueryField(
                    propertyName = "state",
                    parameterName = "states",
                    operator = "IN",
                    type = "string",
                    format = null,
                    enumValues = listOf("OPEN", "CLOSED"),
                ),
            ),
        )

        val baseDocument = baseDocument()
        val contract = route.toLsiOpenApiContract()
        val logicalId = contract.properties.single { property -> property.name == "id" }
        assertEquals("integer", logicalId.type)
        assertEquals("int64", logicalId.format)

        val document = OpenApiCompiler.compile(baseDocument, listOf(contract))

        assertTrue(document["paths"].has("/example/users/create"))
        assertTrue(document["paths"].has("/example/users/upsert"))
        assertTrue(document["paths"].has("/example/users/update"))
        assertTrue(document["paths"].has("/example/users/page"))
        assertFalse(document["paths"]["/example/users/create"]["post"].has("security"))
        assertEquals(
            "填写要新增的示例用户数据。",
            document["paths"]["/example/users/create"]["post"]["requestBody"]["description"].asString(),
        )
        val entityId = document["components"]["schemas"]["example_user_User"]["properties"]["id"]
        assertEquals("string", entityId["type"].asString())
        assertFalse(entityId.has("format"))
        assertEquals(
            "#/components/schemas/example_user_User",
            document["components"]["schemas"]["user_entity"]["\$ref"].asString(),
        )
        assertEquals(
            "storedFile",
            document["components"]["schemas"]["example_user_User"]["properties"]["id"]
                ["x-lowcode-reference"]["targetModelCode"].asString(),
        )
        assertEquals(
            "avatarFile",
            document["components"]["schemas"]["example_user_User"]["properties"]["id"]
                ["x-lowcode-reference"]["propertyName"].asString(),
        )
        val createSchema = document["components"]["schemas"]["example_user_UserCreateRequest"]
        assertFalse(createSchema["additionalProperties"].asBoolean())
        assertEquals(
            listOf("state"),
            createSchema["required"].values().asSequence().map { property -> property.asString() }.toList(),
        )
        assertTrue(createSchema["properties"].has("state"))
        assertFalse(createSchema["properties"].has("id"))
        assertFalse(createSchema["properties"].has("createTime"))
        assertEquals(
            "#/components/schemas/example_user_UserCreateRequest",
            document["paths"]["/example/users/create"]["post"]["requestBody"]
                ["content"]["application/json"]["schema"]["\$ref"].asString(),
        )
        val updateSchema = document["components"]["schemas"]["example_user_UserUpdateRequest"]
        assertFalse(updateSchema["additionalProperties"].asBoolean())
        assertEquals(
            listOf("id"),
            updateSchema["required"].values().asSequence().map { property -> property.asString() }.toList(),
        )
        assertTrue(updateSchema["properties"].has("id"))
        assertEquals("string", updateSchema["properties"]["id"]["type"].asString())
        assertFalse(updateSchema["properties"]["id"].has("format"))
        assertTrue(updateSchema["properties"].has("state"))
        assertFalse(updateSchema["properties"].has("createTime"))
        assertEquals(
            "#/components/schemas/example_user_UserUpdateRequest",
            document["paths"]["/example/users/update"]["put"]["requestBody"]
                ["content"]["application/json"]["schema"]["\$ref"].asString(),
        )
        assertEquals(
            listOf(
                "#/components/schemas/example_user_UserCreateRequest",
                "#/components/schemas/example_user_UserUpdateRequest",
            ),
            document["paths"]["/example/users/upsert"]["post"]["requestBody"]
                ["content"]["application/json"]["schema"]["oneOf"]
                .values().asSequence().map { schema -> schema["\$ref"].asString() }.toList(),
        )
        val excludedIds = document["paths"]["/example/users/page"]["get"]["parameters"]
            .first { parameter -> parameter["name"].asString() == "excludedIds" }
        assertEquals("array", excludedIds["schema"]["type"].asString())
        assertEquals("string", excludedIds["schema"]["items"]["type"].asString())
        assertFalse(excludedIds["schema"]["items"].has("format"))
        val pageData = document["paths"]["/example/users/page"]["get"]["responses"]["200"]
            .get("content")["application/json"]["schema"]["allOf"][1]["properties"]["data"]
        assertEquals("string", pageData["properties"]["total"]["type"].asString())
        assertFalse(pageData["properties"]["total"].has("format"))
        val pageNo = document["paths"]["/example/users/page"]["get"]["parameters"]
            .first { parameter -> parameter["name"].asString() == "pageNo" }
        assertEquals("integer", pageNo["schema"]["type"].asString())
        assertEquals("int32", pageNo["schema"]["format"].asString())
        val nullableParentId = document["components"]["schemas"]["CrudTreeChangeParentCommand"]
            .get("properties")["parentId"]
        assertEquals(
            listOf("string", "null"),
            nullableParentId["type"].values().asSequence().map { value -> value.asString() }.toList(),
        )
        assertFalse(nullableParentId.has("format"))
        assertEquals(
            listOf("OPEN", "CLOSED"),
            document["components"]["schemas"]["example_user_User"]["properties"]["state"]["enum"]
                .values().asSequence().map { value -> value.asString() }.toList(),
        )
        val states = document["paths"]["/example/users/page"]["get"]["parameters"]
            .first { parameter -> parameter["name"].asString() == "states" }
        assertEquals(
            listOf("OPEN", "CLOSED"),
            states["schema"]["items"]["enum"].values().asSequence().map { value -> value.asString() }.toList(),
        )
        val simpleListAliases = listOf(
            "/example/users/simple-list",
            "/example/users/list",
            "/example/users/list-all",
            "/example/users/list-by-condition",
            "/example/users/list-simple",
            "/example/users/list-all-simple",
        )
        simpleListAliases.forEach { path ->
            val operation = document["paths"][path]["get"]
            assertTrue(!operation.isMissingNode)
            assertEquals(
                setOf("excludedIds", "states"),
                operation["parameters"].values().asSequence()
                    .map { parameter -> parameter["name"].asString() }
                    .toSet(),
            )
            assertEquals(
                listOf("示例用户"),
                operation["tags"].values().asSequence().map { tag -> tag.asString() }.toList(),
            )
        }
        val documentedPaths = document["paths"].propertyNames().asSequence().toList()
        val firstAliasIndex = documentedPaths.indexOf(simpleListAliases.first())
        val documentedAliasPaths = documentedPaths.subList(
            firstAliasIndex,
            firstAliasIndex + simpleListAliases.size,
        )
        assertEquals(simpleListAliases, documentedAliasPaths)
        assertTrue(!document["paths"]["/example/users/list-by-condition"]["post"].isMissingNode)
        assertFalse(baseDocument["components"].has("schemas"))
    }

    @Test
    fun `upsert 单独启用时仍发布裁剪后的新增和修改请求`() {
        val route = LsiLowcodeRoute(
            packageName = "example.record",
            qualifiedName = "example.record.Record",
            className = "Record",
            description = "示例记录。",
            path = "/example/records",
            enabledOperations = setOf("UPSERT"),
            properties = listOf(
                LsiLowcodeProperty(
                    name = "id",
                    type = "integer",
                    format = "int64",
                    required = true,
                    identifier = true,
                    arrayItemType = null,
                    description = "记录编号。",
                ),
                LsiLowcodeProperty(
                    name = "name",
                    type = "string",
                    format = null,
                    required = true,
                    arrayItemType = null,
                    description = "记录名称。",
                ),
            ),
        )

        val document = OpenApiCompiler.compile(baseDocument(), listOf(route.toLsiOpenApiContract()))
        val schemas = document["components"]["schemas"]

        assertTrue(schemas.has("example_record_RecordCreateRequest"))
        assertTrue(schemas.has("example_record_RecordUpdateRequest"))
        assertFalse(document["paths"].has("/example/records/create"))
        assertFalse(document["paths"].has("/example/records/update"))
        assertEquals(
            listOf(
                "#/components/schemas/example_record_RecordCreateRequest",
                "#/components/schemas/example_record_RecordUpdateRequest",
            ),
            document["paths"]["/example/records/upsert"]["post"]["requestBody"]
                ["content"]["application/json"]["schema"]["oneOf"]
                .values().asSequence().map { schema -> schema["\$ref"].asString() }.toList(),
        )
    }

    @Test
    fun `compiles route discriminator into oneOf subtype references`() {
        val subtypeRef = LsiLowcodeDtoRef("maintenanceWorkOrder")
        val route = LsiLowcodeRoute(
            packageName = "example.workorder",
            qualifiedName = "example.workorder.WorkOrder",
            className = "WorkOrder",
            description = null,
            path = "/work-orders",
            enabledOperations = setOf("PAGE"),
            properties = listOf(
                LsiLowcodeProperty(
                    name = "workOrderType",
                    type = "string",
                    format = null,
                    required = true,
                    arrayItemType = null,
                    description = "Work order type",
                    enumValues = listOf("MAINTENANCE"),
                ),
            ),
            dtoSchemas = listOf(
                LsiLowcodeDtoSchema(
                    ref = subtypeRef,
                    className = "MaintenanceWorkOrder",
                    properties = mapOf(
                        "workOrderType" to LsiLowcodeApiSchema(
                            type = "string",
                            enumValues = listOf("MAINTENANCE"),
                        ),
                    ),
                    required = setOf("workOrderType"),
                ),
            ),
            discriminator = LsiLowcodeDiscriminator(
                propertyName = "workOrderType",
                mapping = mapOf("MAINTENANCE" to subtypeRef),
            ),
        )

        val document = OpenApiCompiler.compile(baseDocument(), listOf(route.toLsiOpenApiContract()))
        val schema = document["components"]["schemas"]["example_workorder_WorkOrder"]

        assertEquals(
            "#/components/schemas/maintenanceWorkOrder_entity",
            schema["oneOf"][0]["\$ref"].asString(),
        )
        assertEquals("workOrderType", schema["discriminator"]["propertyName"].asString())
        assertEquals(
            "#/components/schemas/maintenanceWorkOrder_entity",
            schema["discriminator"]["mapping"]["MAINTENANCE"].asString(),
        )
        assertTrue(document["components"]["schemas"].has("maintenanceWorkOrder_entity"))
    }

    @Test
    fun `compiles independent contract parameters request body and response body`() {
        val contract = LsiLowcodeContract(
            contractCode = "exampleUserAdmin",
            name = "示例用户管理",
            packageName = "example.user",
            className = "ExampleUserAdminService",
            path = "/example/user-admin",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "resetPassword",
                    name = "重置密码",
                    path = "/example/user-admin/{id}/reset-password",
                    method = LowcodeHttpMethod.PUT,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "id",
                            location = LowcodeApiParameterLocation.PATH,
                            required = true,
                            schema = LsiLowcodeApiSchema(type = "integer", format = "int64"),
                        ),
                    ),
                    requestBody = LsiLowcodeApiBody(
                        description = "填写新的登录密码。",
                        schema = LsiLowcodeApiSchema(
                            type = "object",
                            properties = mapOf("password" to LsiLowcodeApiSchema(type = "string")),
                            required = setOf("password"),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        required = false,
                        description = "重置结果；无结果时 data 为 null。",
                        schema = LsiLowcodeApiSchema(type = "boolean"),
                    ),
                ),
            ),
        )

        val document = OpenApiCompiler.compile(baseDocument(), listOf(contract.toLsiOpenApiContract()))
        val operation = document["paths"]["/example/user-admin/{id}/reset-password"]["put"]

        assertEquals("path", operation["parameters"][0]["in"].asString())
        assertEquals("string", operation["parameters"][0]["schema"]["type"].asString())
        assertFalse(operation["parameters"][0]["schema"].has("format"))
        assertEquals("填写新的登录密码。", operation["requestBody"]["description"].asString())
        assertEquals(
            "string",
            operation["requestBody"]["content"]["application/json"]["schema"]
                ["properties"]["password"]["type"].asString(),
        )
        assertEquals(
            "boolean",
            operation["responses"]["200"]["content"]["application/json"]["schema"]
                ["allOf"][1]["properties"]["data"]["oneOf"][0]["type"].asString(),
        )
        assertEquals(
            "null",
            operation["responses"]["200"]["content"]["application/json"]["schema"]
                ["allOf"][1]["properties"]["data"]["oneOf"][1]["type"].asString(),
        )
        assertEquals("重置结果；无结果时 data 为 null。", operation["responses"]["200"]["description"].asString())
    }

    @Test
    fun `metadata overrides route fields while preserving extra response documentation`() {
        val baseDocument = baseDocument().apply {
            (get("paths") as ObjectNode).putObject("/example/files/{id}").putObject("get").apply {
                put("operationId", "routeDownload")
                put("summary", "路由下载说明")
                put("deprecated", true)
                putArray("tags").add("路由标签")
                putArray("parameters").apply {
                    addObject().apply {
                        put("name", "id")
                        put("in", "path")
                        put("required", true)
                        put("description", "路由文件编号")
                        putObject("schema").put("type", "string").put("format", "uuid")
                    }
                    addObject().apply {
                        put("name", "X-Route-Only")
                        put("in", "header")
                        put("required", false)
                        putObject("schema").put("type", "string")
                    }
                }
                putObject("responses").apply {
                    putObject("200").apply {
                        put("description", "路由完整响应")
                        putObject("headers").putObject("Accept-Ranges")
                            .putObject("schema").put("type", "string")
                    }
                    listOf("206", "304", "416").forEach { status ->
                        putObject(status).put("description", "路由额外响应 $status")
                    }
                }
            }
        }
        val contract = LsiLowcodeContract(
            contractCode = "exampleFile",
            name = "示例文件",
            packageName = "example.file",
            className = "ExampleFileService",
            path = "/example/files",
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "getContent",
                    name = "读取文件内容",
                    path = "/example/files/{id}",
                    method = LowcodeHttpMethod.GET,
                    parameters = listOf(
                        LsiLowcodeApiParameter(
                            name = "id",
                            location = LowcodeApiParameterLocation.PATH,
                            required = true,
                            description = "元数据文件编号",
                            schema = LsiLowcodeApiSchema(type = "integer", format = "int64"),
                        ),
                        LsiLowcodeApiParameter(
                            name = "download",
                            location = LowcodeApiParameterLocation.QUERY,
                            required = false,
                            description = "是否下载",
                            schema = LsiLowcodeApiSchema(type = "boolean"),
                        ),
                    ),
                    responseBody = LsiLowcodeApiBody(
                        contentType = "application/octet-stream",
                        description = "元数据完整响应",
                        schema = LsiLowcodeApiSchema(type = "string", format = "binary"),
                    ),
                    responseEnvelope = false,
                ),
            ),
        )

        val document = OpenApiCompiler.compile(baseDocument, listOf(contract.toLsiOpenApiContract()))
        val operation = document["paths"]["/example/files/{id}"]["get"]

        assertEquals("example_file_ExampleFileService_getContent", operation["operationId"].asString())
        assertEquals("读取文件内容", operation["summary"].asString())
        assertEquals(listOf("示例文件"), operation["tags"].values().asSequence().map { it.asString() }.toList())
        assertFalse(operation.has("deprecated"))
        assertEquals(2, operation["parameters"].size())
        assertEquals("id", operation["parameters"][0]["name"].asString())
        assertEquals("path", operation["parameters"][0]["in"].asString())
        assertEquals("元数据文件编号", operation["parameters"][0]["description"].asString())
        assertEquals("string", operation["parameters"][0]["schema"]["type"].asString())
        assertFalse(operation["parameters"][0]["schema"].has("format"))
        assertEquals("download", operation["parameters"][1]["name"].asString())
        assertEquals("query", operation["parameters"][1]["in"].asString())
        assertFalse(operation["parameters"][1]["required"].asBoolean())
        assertEquals("是否下载", operation["parameters"][1]["description"].asString())
        assertEquals("boolean", operation["parameters"][1]["schema"]["type"].asString())
        assertEquals("元数据完整响应", operation["responses"]["200"]["description"].asString())
        assertEquals(
            "binary",
            operation["responses"]["200"]["content"]["application/octet-stream"]["schema"]["format"].asString(),
        )
        assertEquals("string", operation["responses"]["200"]["headers"]["Accept-Ranges"]["schema"]["type"].asString())
        listOf("206", "304", "416").forEach { status ->
            assertEquals("路由额外响应 $status", operation["responses"][status]["description"].asString())
        }
    }

    private fun baseDocument() = objectMapper.createObjectNode().apply {
        put("openapi", "3.1.1")
        putObject("paths")
        putObject("components")
    }
}
