package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.dto.compiler.LsiDtoType

class LowcodeMetadataJsonTest {
    @Test
    fun `service only operations are restricted to internal transport`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeMetadataJson.readOperations(
                """[{"operationCode":"find","name":"查询","path":"/items","implementation":"SERVICE_ONLY"}]""",
            )
        }
        val operation = LowcodeMetadataJson.readOperations(
            """[{"operationCode":"find","name":"查询","path":"/items","transport":"INTERNAL","implementation":"SERVICE_ONLY"}]""",
        ).single()

        assertEquals("SERVICE_ONLY 操作必须使用 INTERNAL 传输: find", error.message)
        assertEquals(LowcodeOperationTransport.INTERNAL, operation.transport)
    }

    @Test
    fun `rejects call context declared as an operation parameter`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeMetadataJson.readOperations(
                """[{"operationCode":"find","name":"查询","path":"/items","parameters":[{"name":"context","location":"QUERY","schema":{"kotlinType":{"qualifiedName":"example.runtime.CallContext"}}}]}]""",
            )
        }

        assertEquals("调用上下文不能声明为接口参数，请使用 callContext 操作语义", error.message)
    }

    @Test
    fun `reads service only controller generation setting`() {
        val serviceOnly = LowcodeMetadataJson.readRoute(
            """{"packageName":"example","qualifiedName":"example.Record","className":"Record","path":"/records","generateController":false,"enabledOperations":["GET"],"properties":[]}""",
        )
        val defaultRoute = LowcodeMetadataJson.readRoute(
            """{"packageName":"example","qualifiedName":"example.Record","className":"Record","path":"/records","enabledOperations":["GET"],"properties":[]}""",
        )

        assertEquals(false, serviceOnly?.generateController)
        assertEquals(true, defaultRoute?.generateController)
    }

    @Test
    fun `reads ordered default page sorting`() {
        val route = requireNotNull(
            LowcodeMetadataJson.readRoute(
                """{"packageName":"example","qualifiedName":"example.Record","className":"Record","path":"/records","enabledOperations":["PAGE"],"properties":[],"defaultOrders":[{"propertyName":"sortOrder","direction":"ASC"},{"propertyName":"id","direction":"DESC"}]}""",
            ),
        )

        assertEquals(
            listOf(
                LsiLowcodeOrder("sortOrder", LsiLowcodeOrderDirection.ASC),
                LsiLowcodeOrder("id", LsiLowcodeOrderDirection.DESC),
            ),
            route.defaultOrders,
        )
    }

    @Test
    fun `reads structured entity field annotations`() {
        val config = LowcodeMetadataJson.readEntityConfig(
            """
            {
              "baseMode": "INHERITED",
              "fieldAnnotations": {
                "id": [
                  {"qualifiedName":"org.babyfish.jimmer.sql.Id","arguments":[]},
                  {
                    "qualifiedName":"org.babyfish.jimmer.sql.GeneratedValue",
                    "arguments":[{
                      "name":"strategy",
                      "kind":"ENUM",
                      "value":"org.babyfish.jimmer.sql.GenerationType.IDENTITY"
                    }]
                  }
                ]
              },
              "relationOrderings": {
                "items": ["orderNo", "id"]
              }
            }
            """.trimIndent(),
        )

        assertEquals(2, config.fieldAnnotations.getValue("id").size)
        assertEquals(
            "org.babyfish.jimmer.sql.GenerationType.IDENTITY",
            config.fieldAnnotations.getValue("id")[1].arguments.single().value,
        )
        assertEquals(listOf("orderNo", "id"), config.relationOrderings.getValue("items"))
    }

    @Test
    fun `reads structured Kotlin dto field types`() {
        val field = LowcodeMetadataJson.readDtoFields(
            """
            [{
              "name": "policies",
              "nullability": "NON_NULL",
              "kotlinType": {
                "qualifiedName": "kotlin.collections.Map",
                "arguments": [
                  {"qualifiedName": "kotlin.String"},
                  {"qualifiedName": "example.ToolPolicy"}
                ]
              }
            }]
            """.trimIndent(),
        ).single()

        assertEquals(
            LsiDtoType.map(LsiDtoType.STRING, LsiDtoType("example.ToolPolicy")),
            field.kotlinType,
        )
        assertEquals(null, field.schema)
    }

    @Test
    fun `reads dto selection modes exclusions and entity references`() {
        val dto = LowcodeMetadataJson.readDtos(
            """[{"dtoCode":"userView","className":"UserView","kind":"VIEW","selectionMode":"ALL_DEEP_FIELDS","excludedPaths":["password"]}]""",
        ).single()
        val operation = LowcodeMetadataJson.readOperations(
            """[{"operationCode":"save","name":"保存","path":"/users","callContext":true,"requestBody":{"schema":{"typeRef":{"modelCode":"user"}}}}]""",
        ).single()

        assertEquals(LowcodeDtoSelectionMode.ALL_DEEP_FIELDS, dto.selectionMode)
        assertEquals(listOf("password"), dto.excludedPaths)
        assertEquals(true, operation.callContext)
        assertEquals(LsiLowcodeDtoRef("user"), operation.requestBody?.schema?.typeRef)
    }

    @Test
    fun `entity source ownership is explicit and defaults to generated`() {
        val existing = LowcodeMetadataJson.readEntityConfig(
            """{"sourceMode":"EXISTING","sourceQualifiedName":"example.file.StoredFile"}""",
        )
        val generated = LowcodeMetadataJson.readEntityConfig(
            """{"sourceContributorId":"shared.audit"}""",
        )

        assertEquals(LowcodeEntitySourceMode.EXISTING, existing.sourceMode)
        assertEquals("example.file.StoredFile", existing.sourceQualifiedName)
        assertEquals(LowcodeEntitySourceMode.GENERATED, generated.sourceMode)
        assertEquals(null, generated.sourceQualifiedName)
        assertEquals("shared.audit", generated.sourceContributorId)
        assertEquals(listOf(LowcodeBaseModel.BASE_ENTITY), generated.resolvedBaseModels().map { it.model })
    }

    @Test
    fun `reads composable base models while preserving legacy inherited metadata`() {
        val config = LowcodeMetadataJson.readEntityConfig(
            """{"baseModels":["BASE_ENTITY","TENANT","NAMESPACE","VERSION"]}""",
        )
        val legacy = LowcodeMetadataJson.readEntityConfig(
            """{"baseMode":"INHERITED","superTypes":["example.TreeNode<example.Department>"],"inheritedProperties":[],"inheritedRelationCodes":["parent","children"]}""",
        )

        assertEquals(
            listOf(
                LowcodeBaseModel.BASE_ENTITY,
                LowcodeBaseModel.TENANT,
                LowcodeBaseModel.NAMESPACE,
                LowcodeBaseModel.VERSION,
            ),
            config.baseModels,
        )
        assertEquals(
            listOf("id", "createTime", "updateTime", "updater", "creator", "tenantId", "namespace", "version"),
            config.resolvedInheritedProperties().map { property -> property.name },
        )
        assertEquals(emptyList<LsiLowcodeBaseModelDefinition>(), legacy.resolvedBaseModels())
        assertEquals(listOf("example.TreeNode<example.Department>"), legacy.resolvedSuperTypes())
        assertEquals(listOf("parent", "children"), legacy.inheritedRelationCodes)
    }

    @Test
    fun `node base model replaces its component base models`() {
        val config = LowcodeMetadataJson.readEntityConfig(
            """{"baseModels":["NODE","SNOWFLAKE_ID","NAMESPACE","NAMED"]}""",
        )

        assertEquals(listOf(LowcodeBaseModel.NODE), config.resolvedBaseModels().map { model -> model.model })
        assertEquals(
            listOf("id", "name", "namespace", "nodeType"),
            config.resolvedInheritedProperties().map { property -> property.name },
        )
        assertEquals(
            "node_type",
            config.resolvedInheritedProperties().single { property -> property.name == "nodeType" }.dictionaryCode,
        )
    }

    @Test
    fun `transient properties preserve draft compatibility and read resolver metadata`() {
        val config = LowcodeMetadataJson.readEntityConfig(
            """
            {
              "transientProperties": [
                {"propertyCode":"draftValue","label":"Draft value","kotlinType":"String"},
                {
                  "propertyCode":"latestResult",
                  "label":"Latest result",
                  "kotlinType":"example.Result",
                  "kind":"RESOLVER",
                  "resolverValueType":"Long?",
                  "nullable":true
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(LowcodeTransientKind.DRAFT, config.transientProperties[0].kind)
        assertEquals(null, config.transientProperties[0].resolverValueType)
        assertEquals(LowcodeTransientKind.RESOLVER, config.transientProperties[1].kind)
        assertEquals("Long?", config.transientProperties[1].resolverValueType)
    }

    @Test
    fun `reads request body description from operation metadata`() {
        val operations = LowcodeMetadataJson.readOperations(
            """
            [{
              "operationCode": "createExample",
              "name": "新增示例",
              "path": "/examples",
              "requestBody": {
                "description": "填写示例数据。",
                "schema": {"type": "object"}
              }
            }]
            """.trimIndent(),
        )

        assertEquals("填写示例数据。", operations.single().requestBody?.description)
    }

    @Test
    fun `reads named schema descriptions from materialized route metadata`() {
        val route = requireNotNull(
            LowcodeMetadataJson.readRoute(
                """
                {
                  "packageName": "example.news",
                  "qualifiedName": "example.news.News",
                  "className": "News",
                  "description": "新闻路由",
                  "path": "/news",
                  "enabledOperations": [],
                  "properties": [],
                  "dtoSchemas": [{
                    "ref": {"modelCode": "news"},
                    "className": "News",
                    "description": "新闻",
                    "properties": {
                      "title": {"type": "string", "description": "新闻标题"}
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        val schema = route.dtoSchemas.single()

        assertEquals("新闻", schema.description)
        assertEquals("新闻标题", schema.properties.getValue("title").description)
    }

    @Test
    fun `reads standalone dto references without a model code`() {
        val operation = LowcodeMetadataJson.readOperations(
            """[{"operationCode":"count","name":"统计","path":"/count","responseBody":{"schema":{"typeRef":{"dtoCode":"deviceStatusCount"}}}}]""",
        ).single()

        assertEquals(
            LsiLowcodeDtoRef(dtoCode = "deviceStatusCount"),
            operation.responseBody?.schema?.typeRef,
        )
    }

    @Test
    fun `reads dto field validation rules`() {
        val field = LowcodeMetadataJson.readDtoFields(
            """[{"name":"title","description":"文章标题","validations":[{"code":"notBlank","message":"标题不能为空"}]}]""",
        ).single()

        assertEquals("文章标题", field.description)
        assertEquals("notBlank", field.validations.single().code)
        assertEquals("标题不能为空", field.validations.single().message)
    }

    @Test
    fun `reads inheritance roles from entity config json`() {
        val config = LowcodeMetadataJson.readEntityConfig(
            """
            {
              "inheritanceRoot": {
                "strategy": "JOINED",
                "discriminatorField": "workOrderType",
                "instantiability": "ABSTRACT",
                "joinedTableDissociateAction": "LAX"
              }
            }
            """.trimIndent(),
        )

        assertEquals(LowcodeInheritanceStrategy.JOINED, config.inheritanceRoot?.strategy)
        assertEquals(
            LowcodeJoinedTableDissociateAction.LAX,
            config.inheritanceRoot?.joinedTableDissociateAction,
        )
    }
}
