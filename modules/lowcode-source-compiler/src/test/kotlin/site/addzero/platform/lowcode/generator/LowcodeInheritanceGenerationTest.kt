package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.ddl.compiler.DdlCompiler

class LowcodeInheritanceGenerationTest {
    @Test
    fun `subtype route resolves inherited properties from the model catalog`() {
        val root = rootModel(LowcodeInheritanceStrategy.JOINED)
        val subtype = subtypeModel()

        val route = LowcodeSourceCompiler.run { subtype.toLsiRoute(listOf(root, subtype)) }

        assertTrue(route.properties.any { property -> property.name == "workOrderType" })
        assertTrue(route.properties.any { property -> property.name == "plannedTime" })
    }

    @Test
    fun `subtype entity schema exposes inherited association object`() {
        val root = rootModel(LowcodeInheritanceStrategy.JOINED).copy(relations = listOf(deviceRelation()))
        val subtype = subtypeModel()
        val device = deviceModel()

        val schema = subtype.toLsiEntitySchema(listOf(root, subtype, device))
        val deviceSchema = schema.properties.getValue("device")

        assertEquals("object", deviceSchema.type)
        assertEquals("integer", deviceSchema.properties.getValue("id").type)
        assertEquals("string", deviceSchema.properties.getValue("name").type)
        assertTrue(schema.properties.containsKey("deviceId"))
    }

    @Test
    fun `root route exposes discriminator enum and polymorphic subtype schemas`() {
        val root = rootModel(LowcodeInheritanceStrategy.JOINED).copy(
            routeConfig = LsiLowcodeRoute(
                packageName = "example.workorder",
                qualifiedName = "example.workorder.WorkOrder",
                className = "WorkOrder",
                displayName = "Work order",
                description = "Work order",
                path = "/work-order",
                enabledOperations = setOf("PAGE"),
                properties = emptyList(),
                fetchPaths = listOf("plannedTime"),
            ),
        )
        val subtype = subtypeModel()

        val route = LowcodeSourceCompiler.run { root.toLsiRoute(listOf(root, subtype)) }
        val discriminator = requireNotNull(route.discriminator)
        val typeProperty = route.properties.single { property -> property.name == "workOrderType" }
        val subtypeSchema = route.dtoSchemas.single { schema -> schema.ref.modelCode == subtype.modelCode }
        val routeSource = LowcodeRouteSourceGenerator.toContractSource(route)

        assertEquals("string", typeProperty.type)
        assertEquals(listOf("MAINTENANCE"), typeProperty.enumValues)
        assertEquals("workOrderType", discriminator.propertyName)
        assertEquals(LsiLowcodeDtoRef("maintenanceWorkOrder"), discriminator.mapping["MAINTENANCE"])
        assertEquals(listOf("plannedTime"), route.fetchPaths)
        assertEquals(
            listOf("MAINTENANCE"),
            subtypeSchema.properties.getValue("workOrderType").enumValues,
        )
        assertTrue(routeSource.contains("enumValues = listOf("))
        assertTrue(routeSource.contains("discriminator = LsiLowcodeDiscriminator("))
        assertTrue(routeSource.contains("\"MAINTENANCE\" to LsiLowcodeDtoRef("))
    }

    @Test
    fun `generates joined root and subtype annotations from metadata`() {
        val root = rootModel(LowcodeInheritanceStrategy.JOINED)
        val subtype = subtypeModel()

        val files = LowcodeSourceCompiler.generateEntities(listOf(root, subtype))
            .associateBy(LowcodeGeneratedFile::fileName)

        val rootSource = files.getValue("WorkOrder").content
        assertTrue(rootSource.contains("@Entity(instantiability = EntityInstantiability.ABSTRACT)"))
        assertTrue(rootSource.contains("@Inheritance(strategy = InheritanceType.JOINED"))
        assertTrue(rootSource.contains("@Discriminator\n    @Column(name = \"work_order_type\")"))

        val subtypeSource = files.getValue("MaintenanceWorkOrder").content
        assertTrue(subtypeSource.contains("@Table(name = \"maintenance_work_order\")"))
        assertTrue(subtypeSource.contains("@DiscriminatorValue(\"MAINTENANCE\")"))
        assertTrue(subtypeSource.contains("interface MaintenanceWorkOrder : WorkOrder"))
        assertFalse(subtypeSource.contains("BaseEntity"))
    }

    @Test
    fun `subtype transient resolver inherits root id type`() {
        val root = rootModel(LowcodeInheritanceStrategy.JOINED)
        val subtype = subtypeModel().copy(
            entityConfig = subtypeModel().entityConfig.copy(
                transientProperties = listOf(
                    LsiLowcodeTransientProperty(
                        propertyCode = "legacyStatus",
                        label = "旧状态",
                        kotlinType = "String",
                        kind = LowcodeTransientKind.RESOLVER,
                        nullable = true,
                    ),
                ),
            ),
        )

        val resolver = LowcodeSourceCompiler.generateTransientResolverContracts(listOf(root, subtype))
            .single { file -> file.fileName == "MaintenanceWorkOrderLegacyStatusResolver" }

        assertTrue(resolver.content.contains("KTransientResolver<Long, String?>"))
    }

    @Test
    fun `joined schema keeps common columns in root and shared id in subtype`() {
        val schema = DdlCompiler.compile(
            listOf(rootModel(LowcodeInheritanceStrategy.JOINED), subtypeModel()),
        )

        val rootTable = schema.table("work_order")!!
        assertNotNull(rootTable)
        assertNotNull(rootTable.column("create_time"))
        assertNotNull(rootTable.column("work_order_type"))

        val subtypeTable = schema.table("maintenance_work_order")!!
        assertNotNull(subtypeTable)
        assertTrue(subtypeTable.column("id")!!.primaryKey)
        assertNotNull(subtypeTable.column("planned_time"))
        assertFalse(subtypeTable.columns.any { column -> column.name == "create_time" })
    }

    @Test
    fun `single table folds nullable subtype columns into the root table`() {
        val root = rootModel(LowcodeInheritanceStrategy.SINGLE_TABLE)
        val subtype = subtypeModel()

        val files = LowcodeSourceCompiler.generateEntities(listOf(root, subtype))
            .associateBy(LowcodeGeneratedFile::fileName)
        assertFalse(files.getValue("MaintenanceWorkOrder").content.contains("@Table"))

        val schema = DdlCompiler.compile(listOf(root, subtype))
        val table = schema.table("work_order")!!
        assertNotNull(table)
        assertTrue(table.column("planned_time")!!.nullable)
        assertTrue(schema.table("maintenance_work_order") == null)
    }

    private fun rootModel(strategy: LowcodeInheritanceStrategy) = LowcodeModelMeta(
        id = 1,
        modelCode = "workOrder",
        name = "Work order",
        packageName = "example.workorder",
        className = "WorkOrder",
        tableName = "work_order",
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        contributorId = "example.library",
        entityConfig = LsiLowcodeEntityConfig(
            inheritanceRoot = LsiLowcodeInheritanceRoot(
                strategy = strategy,
                discriminatorField = "workOrderType",
                joinedTableDissociateAction = if (strategy == LowcodeInheritanceStrategy.JOINED) {
                    LowcodeJoinedTableDissociateAction.LAX
                } else {
                    LowcodeJoinedTableDissociateAction.DELETE
                },
            ),
        ),
        fields = listOf(
            field(
                2,
                1,
                "workOrderType",
                "example.workorder.WorkOrderType",
                "work_order_type",
                required = true,
            ).copy(enumStorage = LowcodeEnumStorage.NAME),
        ),
        queries = emptyList(),
        relations = emptyList(),
    )

    private fun subtypeModel() = LowcodeModelMeta(
        id = 2,
        modelCode = "maintenanceWorkOrder",
        name = "Maintenance work order",
        packageName = "example.workorder.maintenance",
        className = "MaintenanceWorkOrder",
        tableName = "maintenance_work_order",
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        contributorId = "example.library",
        entityConfig = LsiLowcodeEntityConfig(
            baseMode = LowcodeEntityBaseMode.INHERITED,
            inheritanceSubtype = LsiLowcodeInheritanceSubtype(
                parentModelCode = "workOrder",
                discriminatorValue = "MAINTENANCE",
            ),
        ),
        fields = listOf(field(3, 2, "plannedTime", "java.time.LocalDateTime", "planned_time", required = true)),
        queries = emptyList(),
        relations = emptyList(),
    )

    private fun deviceModel() = LowcodeModelMeta(
        id = 3,
        modelCode = "device",
        name = "Device",
        packageName = "example.device",
        className = "Device",
        tableName = "device",
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        contributorId = "example.library",
        fields = listOf(field(4, 3, "name", "String", "name", required = true)),
        queries = emptyList(),
        relations = emptyList(),
    )

    private fun deviceRelation() = LowcodeRelationMeta(
        id = 5,
        modelId = 1,
        orderNo = 2,
        relationCode = "device",
        label = "Device",
        relationKind = LowcodeRelationKind.MANY_TO_ONE,
        targetModelId = 3,
        targetModelCode = "device",
        targetPackageName = "example.device",
        targetClassName = "Device",
        joinColumn = "device_id",
        mappedBy = null,
        joinTable = null,
        joinTableJoinColumn = null,
        joinTableInverseColumn = null,
        required = false,
        listVisible = true,
        formVisible = true,
    )

    private fun field(
        id: Long,
        modelId: Long,
        code: String,
        type: String,
        column: String,
        required: Boolean,
    ) = LowcodeFieldMeta(
        id = id,
        modelId = modelId,
        orderNo = id.toInt(),
        fieldCode = code,
        label = code,
        kotlinType = type,
        dbColumn = column,
        required = required,
        listVisible = true,
        formVisible = true,
        formControl = "input",
        dictCode = null,
        defaultValue = null,
        remark = null,
    )
}
