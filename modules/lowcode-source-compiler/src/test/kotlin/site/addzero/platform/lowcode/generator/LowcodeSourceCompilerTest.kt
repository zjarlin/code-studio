package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.ddl.compiler.DdlMigrationCompiler
import site.addzero.dto.compiler.LsiDtoAnnotation
import site.addzero.dto.compiler.LsiDtoAnnotationArgument
import site.addzero.dto.compiler.LsiDtoAnnotationArgumentKind

class LowcodeSourceCompilerTest {
    @Test
    fun `generates ordered collection relations from entity metadata`() {
        val child = LowcodeModelMeta(
            id = 2,
            modelCode = "lineItem",
            name = "Line item",
            packageName = "example.order",
            className = "LineItem",
            tableName = "line_item",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(field(2, "orderNo", "Order", "Int", "order_no", true)),
            queries = emptyList(),
            relations = emptyList(),
        )
        val order = LowcodeModelMeta(
            id = 1,
            modelCode = "purchaseOrder",
            name = "Purchase order",
            packageName = "example.order",
            className = "PurchaseOrder",
            tableName = "purchase_order",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                relationOrderings = mapOf("items" to listOf("orderNo", "id")),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(
                LowcodeRelationMeta(
                    id = 1,
                    modelId = 1,
                    orderNo = 1,
                    relationCode = "items",
                    label = "Items",
                    relationKind = LowcodeRelationKind.ONE_TO_MANY,
                    targetModelId = 2,
                    targetModelCode = child.modelCode,
                    targetPackageName = child.packageName,
                    targetClassName = child.className,
                    joinColumn = null,
                    mappedBy = "order",
                    joinTable = null,
                    joinTableJoinColumn = null,
                    joinTableInverseColumn = null,
                    required = false,
                    listVisible = true,
                    formVisible = true,
                ),
            ),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(order, child))
            .single { file -> file.fileName == "PurchaseOrder" }
            .content

        assertTrue(entity.contains("import org.babyfish.jimmer.sql.OrderedProp"))
        assertTrue(
            entity.contains(
                "@OneToMany(mappedBy = \"order\", orderedProps = [OrderedProp(\"orderNo\"), OrderedProp(\"id\")])",
            ),
        )
    }

    @Test
    fun `generates structured field annotations from entity metadata`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "identityRecord",
            name = "Identity record",
            packageName = "example.record",
            className = "IdentityRecord",
            tableName = "identity_record",
            kind = LowcodeModelKind.MAPPED_SUPERCLASS,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                fieldAnnotations = mapOf(
                    "id" to listOf(
                        LsiDtoAnnotation("org.babyfish.jimmer.sql.Id"),
                        LsiDtoAnnotation(
                            qualifiedName = "org.babyfish.jimmer.sql.GeneratedValue",
                            arguments = listOf(
                                LsiDtoAnnotationArgument(
                                    name = "strategy",
                                    kind = LsiDtoAnnotationArgumentKind.ENUM,
                                    value = "org.babyfish.jimmer.sql.GenerationType.IDENTITY",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            fields = listOf(field(1, "id", "主键", "Long", "id", true)),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single().content

        assertTrue(entity.contains("import org.babyfish.jimmer.sql.GeneratedValue"))
        assertTrue(entity.contains("import org.babyfish.jimmer.sql.GenerationType"))
        assertTrue(entity.contains("import org.babyfish.jimmer.sql.Id"))
        assertTrue(entity.contains("@Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)"))
    }

    @Test
    fun `generates Jimmer defaults from field metadata`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "workItem",
            name = "Work item",
            packageName = "example.work",
            className = "WorkItem",
            tableName = "work_item",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(field(1, "deleted", "逻辑删除", "Int", "deleted", true, defaultValue = "0")),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generate(model)
            .single { file -> file.fileName == "WorkItem" }
            .content

        assertTrue(entity.contains("import org.babyfish.jimmer.sql.Default"))
        assertTrue(entity.contains("@Default(\"0\")\n    @Column(name = \"deleted\")"))
    }

    @Test
    fun `entity ownership resolves the runtime class independently from stale route snapshots`() {
        assertEquals(
            "example.work.generated.entity.WorkItem",
            LsiLowcodeEntityConfig(sourceMode = LowcodeEntitySourceMode.GENERATED)
                .resolveEntityQualifiedName("workItem", "example.work", "WorkItem"),
        )
        assertEquals(
            "host.work.WorkItem",
            LsiLowcodeEntityConfig(
                sourceMode = LowcodeEntitySourceMode.EXISTING,
                sourceQualifiedName = "host.work.WorkItem",
            ).resolveEntityQualifiedName("workItem", "example.work", "WorkItem"),
        )
    }

    @Test
    fun `generates draft-assignable transient properties without database columns`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "workItem",
            name = "Work item",
            packageName = "example.work",
            className = "WorkItem",
            tableName = "work_item",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                transientProperties = listOf(
                    LsiLowcodeTransientProperty(
                        propertyCode = "latestResult",
                        label = "Latest result",
                        kotlinType = "example.work.Result",
                        nullable = true,
                        dictionaryCode = "result_status",
                    ),
                ),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val generated = LowcodeSourceCompiler.generate(model)
        val entity = generated.single { file -> file.fileName == "WorkItem" }.content
        val contract = LowcodeSourceCompiler.run {
            LowcodeRouteSourceGenerator.toContractSource(model.toLsiRoute())
        }

        assertTrue(entity.contains("import org.babyfish.jimmer.sql.Transient"))
        assertTrue(entity.contains("import example.work.Result"))
        assertTrue(entity.contains("@get:Dict(\"result_status\")\n    @Transient\n    val latestResult: Result?"))
        assertFalse(entity.contains("latest_result"))
        assertTrue(contract.contains("name = \"latestResult\""))
        assertTrue(contract.contains("description = \"Latest result\""))
        assertTrue(contract.contains("dictionaryCode = \"result_status\""))
        assertFalse(generated.any { file -> file.fileName.endsWith("Resolver") })
    }

    @Test
    fun `generates complex transient resolver convention and jimmer adapter`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "workItem",
            name = "Work item",
            packageName = "example.work",
            className = "WorkItem",
            tableName = "work_item",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                transientProperties = listOf(
                    LsiLowcodeTransientProperty(
                        propertyCode = "latest_result",
                        label = "Latest result",
                        kotlinType = "example.work.Result",
                        kind = LowcodeTransientKind.RESOLVER,
                        resolverValueType = "Long?",
                        nullable = true,
                    ),
                ),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val generated = LowcodeSourceCompiler.generate(model)
        val entity = generated.single { file -> file.fileName == "WorkItem" }.content
        val convention = generated.single { file -> file.fileName == "WorkItemLatestResultResolver" }

        assertTrue(entity.contains("import example.work.generated.service.WorkItemLatestResultResolverAdapter"))
        assertTrue(entity.contains("@Transient(WorkItemLatestResultResolverAdapter::class)"))
        assertTrue(entity.contains("val latest_result: Result?"))
        assertEquals("example.work.generated.service", convention.packageName)
        assertTrue(convention.content.contains("interface WorkItemLatestResultResolver : KTransientResolver<Long, Long?>"))
        assertTrue(convention.content.contains("@Single\nclass WorkItemLatestResultResolverAdapter("))
        assertTrue(convention.content.contains("KTransientResolver<Long, Long?> by delegate"))
    }

    @Test
    fun `model preview resolves dto paths from the complete model catalog`() {
        val storedFile = LowcodeModelMeta(
            id = 2,
            modelCode = "storedFile",
            name = "文件",
            packageName = "example.file",
            className = "StoredFile",
            tableName = "stored_file",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(field(2, "url", "地址", "String", "url", true)),
            queries = emptyList(),
            relations = emptyList(),
        )
        val relation = LowcodeRelationMeta(
            id = 1,
            modelId = 1,
            orderNo = 1,
            relationCode = "coverFile",
            label = "封面文件",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = storedFile.id,
            targetModelCode = storedFile.modelCode,
            targetPackageName = storedFile.packageName,
            targetClassName = storedFile.className,
            joinColumn = "cover_file_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            dissociateAction = LowcodeDissociateAction.SET_NULL,
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val article = storedFile.copy(
            id = 1,
            modelCode = "article",
            name = "文章",
            packageName = "example.article",
            className = "Article",
            tableName = "article",
            dtoDefinitions = listOf(
                LsiLowcodeDto(
                    dtoCode = "articleView",
                    className = "ArticleView",
                    kind = LowcodeDtoKind.VIEW,
                    fields = listOf(LsiLowcodeDtoField("coverUrl", "coverFile.url")),
                ),
            ),
            relations = listOf(relation),
        )

        val preview = LowcodeSourceCompiler.generate(
            article,
            modelCatalog = listOf(article, storedFile),
        )

        assertTrue(preview.single { file -> file.fileName == "ArticleView" }.content.contains("coverFile?.url"))
    }

    @Test
    fun `generated entity relation reuses target bound to original source`() {
        val storedFileRoute = LsiLowcodeRoute(
            packageName = "example.file",
            qualifiedName = "example.file.StoredFile",
            className = "StoredFile",
            description = "文件",
            path = "/file",
            enabledOperations = setOf("GET"),
            properties = emptyList(),
        )
        val storedFile = LowcodeModelMeta(
            id = -1,
            modelCode = "storedFile",
            name = "文件",
            packageName = "example.file",
            className = "StoredFile",
            tableName = "stored_file",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                sourceMode = LowcodeEntitySourceMode.EXISTING,
                sourceQualifiedName = "example.file.StoredFile",
            ),
            routeConfig = storedFileRoute,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val relation = LowcodeRelationMeta(
            id = 1,
            modelId = 2,
            orderNo = 1,
            relationCode = "coverFile",
            label = "封面文件",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = storedFile.id,
            targetModelCode = storedFile.modelCode,
            targetPackageName = storedFile.packageName,
            targetClassName = storedFile.className,
            joinColumn = "cover_file_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val article = storedFile.copy(
            id = -2,
            modelCode = "article",
            name = "文章",
            packageName = "example.article",
            className = "Article",
            tableName = "article",
            entityConfig = LsiLowcodeEntityConfig(),
            routeConfig = null,
            relations = listOf(relation),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(article), listOf(article, storedFile)).single()

        assertTrue(entity.content.contains("import example.file.StoredFile"))
        assertFalse(entity.content.contains("example.file.generated.entity.StoredFile"))
    }

    @Test
    fun `explicit existing source mode reuses entity independently from route binding`() {
        val route = LsiLowcodeRoute(
            packageName = "example.alarm",
            qualifiedName = "example.alarm.FaultAlarm",
            className = "FaultAlarm",
            description = "故障告警",
            path = "/fault-alarm",
            enabledOperations = setOf("GET"),
            properties = emptyList(),
        )
        val snapshot = LowcodeModelMeta(
            id = -1,
            modelCode = "faultAlarm",
            name = "故障告警",
            packageName = "example.alarm",
            className = "FaultAlarm",
            tableName = "fault_alarm",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.alarm",
            entityConfig = LsiLowcodeEntityConfig(
                sourceMode = LowcodeEntitySourceMode.EXISTING,
                sourceQualifiedName = "example.alarm.FaultAlarm",
            ),
            routeConfig = route,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val binding = LowcodeRouteBinding(route.qualifiedName, checkNotNull(snapshot.contributorId), route)

        val entities = LowcodeSourceCompiler.generateEntities(listOf(snapshot))
        val resolved = LowcodeSourceCompiler.resolveRouteBindings(listOf(binding), listOf(snapshot)).single()

        assertTrue(entities.isEmpty())
        assertEquals("example.alarm.FaultAlarm", resolved.route.qualifiedName)
        assertFalse(resolved.route.qualifiedName.contains(".generated.entity."))
        assertTrue(LowcodeSourceCompiler.generateRouteBindings(listOf(binding), listOf(snapshot)).isEmpty())
    }

    @Test
    fun `snapshot without original entity binding still generates its entity`() {
        val model = LowcodeModelMeta(
            id = -202608140001,
            modelCode = "analysisResult",
            name = "分析结果",
            packageName = "example.analysis",
            className = "AnalysisResult",
            tableName = "analysis_result",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.analysis",
            routeConfig = null,
            fields = listOf(field(1, "name", "名称", "String", "name", true)),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("interface AnalysisResult"))
        assertTrue(entity.packageName == "example.analysis.generated.entity")
    }

    @Test
    fun `inherited query property keeps route metadata without generating a local annotation`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "department",
            name = "部门",
            packageName = "example.department",
            className = "Department",
            tableName = "example_department",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                superTypes = listOf("example.persistence.TreeRecord"),
                inheritedProperties = listOf(
                    LsiLowcodeInheritedProperty("id", "Long", "id", required = true, id = true),
                    LsiLowcodeInheritedProperty("parentId", "Long", "parent_id", required = false),
                ),
            ),
            fields = listOf(field(1, "name", "名称", "String", "name", true)),
            queries = listOf(
                LowcodeQueryMeta(
                    id = 1,
                    modelId = 1,
                    orderNo = 1,
                    queryCode = "parent",
                    label = "父部门",
                    logic = LowcodeQueryLogic.AND,
                    items = listOf(
                        condition(
                            id = 11,
                            orderNo = 1,
                            fieldCode = "parentId",
                            operator = LowcodeQueryOperator.EQ,
                            valueType = LowcodeQueryValueType.SINGLE,
                            paramName = "parentId",
                        ),
                    ),
                ),
            ),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()
        val route = LowcodeSourceCompiler.run { model.toLsiRoute() }

        assertFalse(entity.content.contains("val parentId"))
        assertFalse(entity.content.contains("@Eq(name = \"parentId\")"))
        assertTrue(route.queryFields.single().propertyName == "parentId")
        assertTrue(route.queryFields.single().type == "integer")
    }

    @Test
    fun `generic tree base owns relation source while metadata keeps route properties`() {
        val parent = LowcodeRelationMeta(
            id = 1,
            modelId = 1,
            orderNo = 1,
            relationCode = "parent",
            label = "父部门",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = 1,
            targetModelCode = "department",
            targetPackageName = "example.department",
            targetClassName = "Department",
            joinColumn = "parent_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val children = parent.copy(
            id = 2,
            orderNo = 2,
            relationCode = "children",
            label = "子部门",
            relationKind = LowcodeRelationKind.ONE_TO_MANY,
            joinColumn = null,
            mappedBy = "parent",
        )
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "department",
            name = "部门",
            packageName = "example.department",
            className = "Department",
            tableName = "example_department",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                baseModels = listOf(LowcodeBaseModel.BASE_ENTITY),
                superTypes = listOf(
                    "example.persistence.BaseTreeNode<example.department.generated.entity.Department>",
                ),
                inheritedRelationCodes = listOf("parent", "children"),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(parent, children),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()
        val route = LowcodeSourceCompiler.run { model.toLsiRoute() }

        assertTrue(entity.content.contains("import example.persistence.BaseTreeNode"))
        assertTrue(entity.content.contains("interface Department : BaseEntity, BaseAudit, BaseTreeNode<Department>"))
        assertFalse(entity.content.contains("val parent:"))
        assertFalse(entity.content.contains("val children:"))
        assertTrue(route.properties.any { property -> property.name == "parentId" })
        assertTrue(route.properties.any { property -> property.name == "childrenIds" })
    }

    @Test
    fun `formula properties generate Jimmer annotations without database columns`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "person",
            name = "人员",
            packageName = "example.person",
            className = "Person",
            tableName = "example_person",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                formulaProperties = listOf(
                    LsiLowcodeFormulaProperty(
                        propertyCode = "displayName",
                        label = "显示名称",
                        kotlinType = "String",
                        kind = LowcodeFormulaKind.KOTLIN,
                        expression = "\"${'$'}lastName${'$'}firstName\"",
                        dependencies = listOf("firstName", "lastName"),
                    ),
                    LsiLowcodeFormulaProperty(
                        propertyCode = "orderCount",
                        label = "订单数量",
                        kotlinType = "Long",
                        kind = LowcodeFormulaKind.SQL,
                        expression = "select count(*) from example_order where person_id = %alias.id",
                    ),
                ),
            ),
            fields = listOf(
                field(1, "firstName", "名", "String", "first_name", true),
                field(2, "lastName", "姓", "String", "last_name", true),
            ),
            queries = emptyList(),
            relations = emptyList(),
        )

        val generated = LowcodeSourceCompiler.generate(model) +
            listOfNotNull(DdlMigrationCompiler.compile(model))
        val entity = generated.single { file -> file.fileName == "Person" }.content
        val migration = generated.single { file -> file.extensionName == "sql" }.content

        assertTrue(entity.contains("@Formula(dependencies = [\"firstName\", \"lastName\"])"))
        assertTrue(entity.contains("val displayName: String\n        get() = \"${'$'}lastName${'$'}firstName\""))
        assertTrue(entity.contains("@Formula(sql = \"select count(*) from example_order where person_id = %alias.id\")"))
        assertTrue(entity.contains("val orderCount: Long"))
        assertFalse(migration.contains("display_name"))
        assertFalse(migration.contains("order_count"))
    }

    @Test
    fun `serialized field generates Jimmer annotation`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "settings",
            name = "Settings",
            packageName = "example.settings",
            className = "Settings",
            tableName = "example_settings",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(field(1, "options", "Options", "List<example.settings.Option>", "options", true).copy(serialized = true)),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("import org.babyfish.jimmer.sql.Serialized"))
        assertTrue(entity.content.contains("import example.settings.Option"))
        assertTrue(entity.content.contains("@Serialized\n    @Column(name = \"options\")"))
        assertTrue(entity.content.contains("val options: List<Option>"))
    }

    @Test
    fun `natural key field generates Jimmer annotation`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "authorizationCode",
            name = "Authorization code",
            packageName = "example.oauth2",
            className = "AuthorizationCode",
            tableName = "authorization_code",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(field(1, "code", "Code", "String", "code", true).copy(key = true)),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("import org.babyfish.jimmer.sql.Key"))
        assertTrue(entity.content.contains("@Key\n    @Column(name = \"code\")"))
    }

    @Test
    fun `database route binding uses model properties and keeps route queries`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "product",
            name = "产品",
            packageName = "example.catalog",
            className = "Product",
            tableName = "example_product",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.catalog",
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                superTypes = listOf("example.catalog.RichContent"),
                inheritedProperties = listOf(
                    LsiLowcodeInheritedProperty("coverFileId", "Long", "cover_file_id", false),
                ),
            ),
            fields = listOf(field(1, "name", "Name", "String", "name", true)),
            queries = emptyList(),
            relations = emptyList(),
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "${model.packageName}.${model.className}",
            className = model.className,
            description = model.name,
            path = "/example/product",
            enabledOperations = setOf("GET", "PAGE"),
            fetchPaths = listOf("coverFile.url"),
            properties = emptyList(),
            queryFields = listOf(
                LsiLowcodeQueryField("name", "keyword", "LIKE", "string", null, null),
            ),
            defaultOrders = listOf(
                LsiLowcodeOrder("name", LsiLowcodeOrderDirection.ASC),
                LsiLowcodeOrder("coverFileId", LsiLowcodeOrderDirection.DESC),
            ),
        )
        val binding = LowcodeRouteBinding(route.qualifiedName, checkNotNull(model.contributorId), route)

        val resolved = LowcodeSourceCompiler.resolveRouteBindings(listOf(binding), listOf(model)).single()
        val contract = LowcodeRouteSourceGenerator.toContractSource(resolved.route)

        assertEquals("example.catalog.generated.entity.Product", resolved.route.qualifiedName)
        assertTrue(contract.contains("modelName = \"产品\""))
        assertTrue(contract.contains("name = \"name\""))
        assertTrue(contract.contains("parameterName = \"keyword\""))
        assertTrue(contract.contains("\"coverFile.url\""))
        assertTrue(contract.contains("LowcodeOrderContract(\"name\", LowcodeOrderDirection.ASC)"))
        assertTrue(contract.contains("LowcodeOrderContract(\"coverFileId\", LowcodeOrderDirection.DESC)"))
    }

    @Test
    fun `reference relation keeps object association and route id compatibility property`() {
        val relation = LowcodeRelationMeta(
            id = 1,
            modelId = 4,
            orderNo = 1,
            relationCode = "coverFile",
            label = "封面文件",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = 5,
            targetModelCode = "storedFile",
            targetPackageName = "example.storage.file",
            targetClassName = "StoredFile",
            joinColumn = "cover_file_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            dissociateAction = LowcodeDissociateAction.SET_NULL,
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "News",
            tableName = "cms_news",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(relation),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()
        val route = LowcodeSourceCompiler.run { model.toLsiRoute() }

        assertTrue(entity.content.contains("val coverFile: StoredFile?"))
        assertFalse(entity.content.contains("@IdView"))
        assertFalse(entity.content.contains("val coverFileId: Long?"))
        assertTrue(entity.content.contains("@OnDissociate(DissociateAction.SET_NULL)"))
        val coverFileId = route.properties.single { property -> property.name == "coverFileId" }
        assertEquals("storedFile", coverFileId.referenceTargetModelCode)
        assertEquals("coverFile", coverFileId.referencePropertyName)
    }

    @Test
    fun `required fake foreign key relation keeps nullable query type and non null input`() {
        val relation = LowcodeRelationMeta(
            id = 1,
            modelId = 4,
            orderNo = 1,
            relationCode = "thingModel",
            label = "物模型",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = 5,
            targetModelCode = "thingModel",
            targetPackageName = "example.thing",
            targetClassName = "ThingModel",
            joinColumn = "thing_model_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            required = true,
            listVisible = true,
            formVisible = true,
        )
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "binding",
            name = "映射",
            packageName = "example.binding",
            className = "Binding",
            tableName = "binding",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(relation),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("@ManyToOne(inputNotNull = true)"))
        assertTrue(entity.content.contains("val thingModel: ThingModel?"))
        assertFalse(entity.content.contains("val thingModel: ThingModel\n"))
    }

    @Test
    fun `collection relation generates id view route property and keeps fetch path`() {
        val relation = LowcodeRelationMeta(
            id = 2,
            modelId = 4,
            orderNo = 1,
            relationCode = "menus",
            label = "菜单",
            relationKind = LowcodeRelationKind.MANY_TO_MANY,
            targetModelId = 5,
            targetModelCode = "systemMenu",
            targetPackageName = "example.menu",
            targetClassName = "SystemMenu",
            joinColumn = null,
            mappedBy = null,
            joinTable = "role_menu_mapping",
            joinTableJoinColumn = "role_id",
            joinTableInverseColumn = "menu_id",
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "systemRole",
            name = "角色",
            packageName = "example.role",
            className = "SystemRole",
            tableName = "system_role",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            routeConfig = LsiLowcodeRoute(
                packageName = "example.role",
                qualifiedName = "example.role.SystemRole",
                className = "SystemRole",
                description = "角色",
                path = "/system/role",
                fetchPaths = listOf("menuIds"),
                enabledOperations = setOf("GET"),
                properties = emptyList(),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(relation),
        )

        val route = LowcodeSourceCompiler.run { model.toLsiRoute() }

        assertEquals(listOf("menuIds"), route.fetchPaths)
        val menuIds = route.properties.single { property -> property.name == "menuIds" }
        assertEquals("integer", menuIds.arrayItemType)
        assertEquals("systemMenu", menuIds.referenceTargetModelCode)
    }

    @Test
    fun `reference id query stays in route metadata without entity annotation`() {
        val relation = LowcodeRelationMeta(
            id = 2,
            modelId = 4,
            orderNo = 1,
            relationCode = "channel",
            label = "Channel",
            relationKind = LowcodeRelationKind.MANY_TO_ONE,
            targetModelId = 5,
            targetModelCode = "channel",
            targetPackageName = "example.channel",
            targetClassName = "Channel",
            joinColumn = "channel_id",
            mappedBy = null,
            joinTable = null,
            joinTableJoinColumn = null,
            joinTableInverseColumn = null,
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val query = LowcodeQueryMeta(
            id = 1,
            modelId = 4,
            orderNo = 1,
            queryCode = "channelId",
            label = "Channel ID",
            logic = LowcodeQueryLogic.AND,
            items = listOf(condition(1, 1, "channelId", LowcodeQueryOperator.EQ, LowcodeQueryValueType.SINGLE, "channelId")),
        )
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "message",
            name = "Message",
            packageName = "example.message",
            className = "Message",
            tableName = "example_message",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = emptyList(),
            queries = listOf(query),
            relations = listOf(relation),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()
        val route = LowcodeSourceCompiler.run { model.toLsiRoute() }

        assertFalse(entity.content.contains("site.addzero.jimmer.lowquery.annotation"))
        assertFalse(entity.content.contains("@IdView"))
        assertEquals("channelId", route.queryFields.single().propertyName)
        assertEquals("EQ", route.queryFields.single().operator)
    }

    @Test
    fun `collection relation keeps association without entity id view`() {
        val relation = LowcodeRelationMeta(
            id = 3,
            modelId = 4,
            orderNo = 1,
            relationCode = "categories",
            label = "Categories",
            relationKind = LowcodeRelationKind.MANY_TO_MANY,
            targetModelId = 5,
            targetModelCode = "category",
            targetPackageName = "example.category",
            targetClassName = "Category",
            joinColumn = null,
            mappedBy = null,
            joinTable = "message_category",
            joinTableJoinColumn = "message_id",
            joinTableInverseColumn = "category_id",
            joinTableFilterColumn = "category_type",
            joinTableFilterValues = listOf("primary"),
            required = false,
            listVisible = true,
            formVisible = true,
        )
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "message",
            name = "Message",
            packageName = "example.message",
            className = "Message",
            tableName = "example_message",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(relation),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("val categories: List<Category>"))
        assertFalse(entity.content.contains("@IdView"))
        assertFalse(entity.content.contains("val categoryIds: List<Long>"))
        assertTrue(entity.content.contains("filter = JoinTable.JoinTableFilter(columnName = \"category_type\", values = [\"primary\"])"))
    }

    @Test
    fun `inherited entity does not regenerate id or audit properties`() {
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "news",
            name = "新闻",
            packageName = "example.news",
            className = "News",
            tableName = "cms_news",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example-news",
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                superTypes = listOf("example.persistence.SoftDeletableRecord"),
                inheritedProperties = listOf(
                    LsiLowcodeInheritedProperty("id", "Long", "id", required = true, id = true),
                    LsiLowcodeInheritedProperty(
                        "createTime",
                        "java.time.LocalDateTime",
                        "create_time",
                        required = true,
                    ),
                ),
                microServiceName = null,
            ),
            fields = listOf(field(1, "title", "标题", "String", "title", true)),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertTrue(entity.content.contains("@Entity\n"))
        assertTrue(entity.content.contains("interface News : SoftDeletableRecord"))
        assertTrue(entity.content.contains("import example.persistence.SoftDeletableRecord"))
        assertFalse(entity.content.contains("val id:"))
        assertFalse(entity.content.contains("val createTime:"))
        assertFalse(entity.content.contains("SnowflakeIdGenerator"))
    }

    @Test
    fun `default and optional base models generate composable inheritance`() {
        val model = LowcodeModelMeta(
            id = 4,
            modelCode = "tenantSetting",
            name = "租户配置",
            packageName = "example.setting",
            className = "TenantSetting",
            tableName = "tenant_setting",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseModels = listOf(LowcodeBaseModel.BASE_ENTITY, LowcodeBaseModel.TENANT),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val files = LowcodeSourceCompiler.generate(model) +
            listOfNotNull(DdlMigrationCompiler.compile(model))
        val entity = files.single { file -> file.fileName == "TenantSetting" }
        val migration = files.single { file -> file.extensionName == "sql" }

        val persistencePackage = generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)
        assertTrue(entity.content.contains("import $persistencePackage.BaseEntity"))
        assertTrue(
            entity.content.contains("import $persistencePackage.generated.entity.BaseAudit"),
        )
        assertTrue(entity.content.contains("import $persistencePackage.BaseTenant"))
        assertTrue(entity.content.contains("interface TenantSetting : BaseEntity, BaseAudit, BaseTenant"))
        assertFalse(entity.content.contains("val id:"))
        assertFalse(entity.content.contains("val tenantId:"))
        assertTrue(migration.content.contains("\"tenant_id\" BIGINT"))
    }

    @Test
    fun `generates Ktor lowcode entity query metadata and migration`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "repairTask",
            name = "维修任务",
            packageName = "example.maintenance",
            className = "RepairTask",
            tableName = "repair_task",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(
                field(1, "taskNo", "任务编号", "String", "task_no", true),
                field(2, "title", "任务标题", "String", "title", true),
                field(3, "plannedStart", "计划开始时间", "LocalDateTime", "planned_start", false),
            ),
            queries = listOf(
                LowcodeQueryMeta(
                    id = 1,
                    modelId = 1,
                    orderNo = 1,
                    queryCode = "keyword",
                    label = "关键词",
                    logic = LowcodeQueryLogic.OR,
                    items = listOf(
                        condition(11, 1, "taskNo", LowcodeQueryOperator.LIKE, LowcodeQueryValueType.SINGLE, "keyword"),
                        condition(12, 2, "title", LowcodeQueryOperator.LIKE, LowcodeQueryValueType.SINGLE, "keyword"),
                    ),
                ),
                LowcodeQueryMeta(
                    id = 2,
                    modelId = 1,
                    orderNo = 2,
                    queryCode = "plannedTime",
                    label = "计划时间",
                    logic = LowcodeQueryLogic.AND,
                    items = listOf(
                        condition(
                            21,
                            1,
                            "plannedStart",
                            LowcodeQueryOperator.TIME_RANGE,
                            LowcodeQueryValueType.DATETIME_RANGE,
                            "plannedTime",
                        ),
                    ),
                ),
            ),
            relations = emptyList(),
        )

        val modelWithRoute = LowcodeSourceCompiler.run {
            model.copy(routeConfig = model.toLsiRoute())
        }
        val files = LowcodeSourceCompiler.generate(modelWithRoute) +
            listOfNotNull(DdlMigrationCompiler.compile(modelWithRoute))
        val entity = files.single { file -> file.relativePath.endsWith("/maintenance/generated/entity/RepairTask.kt") }
        val route = LowcodeRouteSourceGenerator.toContractSource(
            LowcodeSourceCompiler.run { modelWithRoute.toLsiRoute() },
        )
        val migration = files.single { file -> file.extensionName == "sql" }

        assertTrue(entity.relativePath.endsWith("/maintenance/generated/entity/RepairTask.kt"))
        assertFalse(entity.content.contains("permissionPrefix"))
        assertFalse(entity.content.contains("site.addzero.jimmer.lowquery.annotation"))
        assertTrue(entity.content.contains(" * 任务标题"))
        assertFalse(entity.content.contains("org.springframework"))
        assertFalse(entity.content.contains("Controller"))
        assertTrue(route.contains("path = \"/repair-task\""))
        assertTrue(route.contains("operator = \"KEYWORD\""))
        assertTrue(route.contains("operator = \"TIME_RANGE\""))
        assertFalse(files.any { file -> file.packageName.contains(".contributor") })
        assertTrue(migration.content.contains("CREATE TABLE IF NOT EXISTS \"repair_task\""))
    }

    @Test
    fun `metadata owned model generates an entity for its contributor`() {
        val route = LsiLowcodeRoute(
            packageName = "example.identity",
            qualifiedName = "example.identity.generated.Member",
            className = "Member",
            description = "Member.",
            path = "/members",
            enabledOperations = setOf("GET", "PAGE"),
            properties = listOf(
                LsiLowcodeProperty("id", "integer", "int64", false, null, "主键。"),
            ),
        )
        val model = LowcodeModelMeta(
            id = 2,
            modelCode = "member",
            name = "Member",
            packageName = "example.identity",
            className = "Member",
            tableName = "members",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "identity.members",
            routeConfig = route,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val files = LowcodeSourceCompiler.generate(model)
        val migration = DdlMigrationCompiler.compile(model)

        val entity = files.single { file -> file.fileName == "Member" }

        assertTrue(entity.relativePath.startsWith("src/main/kotlin/"))
        assertTrue(entity.content.contains("@Entity\n"))
        assertFalse(entity.content.contains("microServiceName"))
        val resolved = LowcodeSourceCompiler.resolveRouteBindings(
            listOf(LowcodeRouteBinding(route.qualifiedName, checkNotNull(model.contributorId), route)),
            listOf(model),
        ).single()
        assertEquals("example.identity.generated.entity.Member", resolved.route.qualifiedName)
        assertFalse(files.any { file -> file.packageName.contains(".contributor") })
        assertTrue(migration!!.content.contains("CREATE TABLE"))
    }

    @Test
    fun `custom contract file follows metadata lifecycle in generated output`() {
        val operation = LsiLowcodeCustomOperation(
            operationCode = "approve",
            name = "审批",
            path = "/repair-task/approve",
            method = LowcodeHttpMethod.POST,
        )
        val route = LsiLowcodeRoute(
            packageName = "example.maintenance",
            qualifiedName = "example.maintenance.RepairTask",
            className = "RepairTask",
            description = "维修任务",
            path = "/repair-task",
            enabledOperations = emptySet(),
            properties = emptyList(),
            customOperations = listOf(operation),
        )
        val model = LowcodeModelMeta(
            id = 3,
            modelCode = "repairTask",
            name = "维修任务",
            packageName = "example.maintenance",
            className = "RepairTask",
            tableName = "repair_task",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            routeConfig = route,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val generated = LowcodeSourceCompiler.generate(model)
        val afterDelete = LowcodeSourceCompiler.generate(
            model.copy(routeConfig = route.copy(customOperations = emptyList())),
        )

        assertTrue(generated.any { file -> file.fileName == "RepairTaskApproveLowcodeContract" })
        assertFalse(afterDelete.any { file -> file.fileName == "RepairTaskApproveLowcodeContract" })
        assertTrue(generated.all { file -> file.relativePath.startsWith("src/main/kotlin/") })
    }

    @Test
    fun `database route binding does not generate a contributor`() {
        val route = LsiLowcodeRoute(
            packageName = "example.catalog",
            qualifiedName = "example.catalog.Product",
            className = "Product",
            description = "示例产品",
            path = "/example/product",
            enabledOperations = setOf("GET", "PAGE"),
            properties = emptyList(),
        )
        val binding = LowcodeRouteBinding(
            routeCode = route.qualifiedName,
            contributorId = "example.catalog",
            route = route,
        )

        assertTrue(LowcodeSourceCompiler.generateRouteBindings(listOf(binding)).isEmpty())
    }

    @Test
    fun `feature entity without an explicit binding receives a deterministic default route`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "repairTaskRecord",
            name = "维修任务记录",
            packageName = "example.repair",
            className = "RepairTaskRecord",
            tableName = "repair_task_record",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.repair",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val resolved = LowcodeSourceCompiler.resolveRouteBindings(
            bindings = emptyList(),
            models = listOf(model),
            modelsRequiringRoutes = listOf(model),
        ).single()

        assertEquals("/repair-task-record", resolved.route.path)
        assertEquals("example.repair.generated.entity.RepairTaskRecord", resolved.route.qualifiedName)
        assertEquals(model.contributorId, resolved.contributorId)
    }

    private fun field(
        id: Long,
        code: String,
        label: String,
        type: String,
        column: String,
        required: Boolean,
        defaultValue: String? = null,
    ): LowcodeFieldMeta = LowcodeFieldMeta(
        id = id,
        modelId = 1,
        orderNo = id.toInt(),
        fieldCode = code,
        label = label,
        kotlinType = type,
        dbColumn = column,
        required = required,
        listVisible = true,
        formVisible = true,
        formControl = "input",
        dictCode = null,
        defaultValue = defaultValue,
        remark = null,
    )

    private fun condition(
        id: Long,
        orderNo: Int,
        fieldCode: String,
        operator: LowcodeQueryOperator,
        valueType: LowcodeQueryValueType,
        paramName: String,
    ): LowcodeQueryConditionMeta = LowcodeQueryConditionMeta(
        id = id,
        queryId = if (id < 20) 1 else 2,
        orderNo = orderNo,
        fieldCode = fieldCode,
        operator = operator,
        valueType = valueType,
        paramName = paramName,
    )
}
