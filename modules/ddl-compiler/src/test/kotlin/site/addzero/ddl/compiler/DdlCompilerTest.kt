package site.addzero.ddl.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.ddlgenerator.core.diff.SchemaDiffPlanner
import site.addzero.ddlgenerator.core.model.AutoDdlSchema
import site.addzero.ddlgenerator.core.model.AutoDdlTable
import site.addzero.ddlgenerator.core.model.AutoDdlLogicalType
import site.addzero.platform.lowcode.generator.LowcodeBaseModel
import site.addzero.platform.lowcode.generator.LowcodeEntityBaseMode
import site.addzero.platform.lowcode.generator.LowcodeEnumStorage
import site.addzero.platform.lowcode.generator.LowcodeFieldMeta
import site.addzero.platform.lowcode.generator.LowcodeModelKind
import site.addzero.platform.lowcode.generator.LowcodeModelMeta
import site.addzero.platform.lowcode.generator.LowcodeRelationKind
import site.addzero.platform.lowcode.generator.LowcodeRelationMeta
import site.addzero.platform.lowcode.generator.LsiLowcodeEntityConfig
import site.addzero.platform.lowcode.generator.LsiLowcodeInheritedProperty

class DdlCompilerTest {
    @Test
    fun `maps fields inherited properties and owning relations to database schema`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "article",
            name = "Article",
            packageName = "example.article",
            className = "Article",
            tableName = "article",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.library",
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                inheritedProperties = listOf(
                    LsiLowcodeInheritedProperty("id", "Long", "id", required = true, id = true),
                ),
            ),
            fields = listOf(
                LowcodeFieldMeta(
                    id = 2,
                    modelId = 1,
                    orderNo = 1,
                    fieldCode = "title",
                    label = "Title",
                    kotlinType = "String",
                    dbColumn = "title",
                    required = true,
                    listVisible = true,
                    formVisible = true,
                    formControl = "input",
                    dictCode = null,
                    defaultValue = null,
                    remark = null,
                    maxLength = 128,
                ),
            ),
            queries = emptyList(),
            relations = listOf(
                LowcodeRelationMeta(
                    id = 3,
                    modelId = 1,
                    orderNo = 2,
                    relationCode = "coverFile",
                    label = "Cover file",
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

        val table = DdlCompiler.compile(listOf(model)).table("article")!!

        assertEquals("Article", table.comment)
        assertEquals(AutoDdlLogicalType.INT64, table.column("id")!!.logicalType)
        assertEquals(128, table.column("title")!!.length)
        assertEquals("Title", table.column("title")!!.comment)
        assertEquals(AutoDdlLogicalType.INT64, table.column("cover_file_id")!!.logicalType)
        assertNull(table.column("cover_file_id")!!.length)
        assertEquals("Cover file", table.column("cover_file_id")!!.comment)

        val migration = DdlMigrationCompiler.compile(model)

        assertEquals("V1__article_lowcode_generated", migration!!.fileName)
        assertTrue(migration.content.contains("\"title\" VARCHAR(255) NOT NULL"))
        assertTrue(migration.content.contains("COMMENT ON TABLE \"article\" IS 'Article';"))
    }

    @Test
    fun `default entity base columns participate in schema diff`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "article",
            name = "Article",
            packageName = "example.article",
            className = "Article",
            tableName = "article",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.library",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val desired = DdlCompiler.compile(listOf(model))
        val operations = SchemaDiffPlanner.plan(
            desired,
            AutoDdlSchema(tables = listOf(AutoDdlTable("article", emptyList()))),
        )

        assertTrue(desired.table("article")!!.column("id")!!.primaryKey)
        assertEquals(
            AutoDdlLogicalType.INT64,
            desired.table("article")!!.column("creator")!!.logicalType,
        )
        assertTrue(operations.isNotEmpty())
    }

    @Test
    fun `composed base models contribute their columns and defaults`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "tenantSetting",
            name = "Tenant setting",
            packageName = "example.setting",
            className = "TenantSetting",
            tableName = "tenant_setting",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseModels = listOf(LowcodeBaseModel.BASE_ENTITY, LowcodeBaseModel.TENANT, LowcodeBaseModel.STATUS),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val table = DdlCompiler.compile(listOf(model)).table("tenant_setting")!!

        assertEquals(AutoDdlLogicalType.INT64, table.column("tenant_id")!!.logicalType)
        assertEquals("1", table.column("status")!!.defaultValue)
    }

    @Test
    fun `maps explicit enum storage without guessing unknown custom scalars`() {
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
            fields = listOf(
                field(
                    id = 2,
                    code = "status",
                    kotlinType = "example.work.WorkStatus",
                    column = "status",
                    enumStorage = LowcodeEnumStorage.NAME,
                ),
                field(
                    id = 3,
                    code = "priority",
                    kotlinType = "example.work.WorkPriority",
                    column = "priority",
                    enumStorage = LowcodeEnumStorage.ORDINAL,
                ),
                field(
                    id = 4,
                    code = "externalId",
                    kotlinType = "example.work.ExternalId",
                    column = "external_id",
                ),
            ),
            queries = emptyList(),
            relations = emptyList(),
        )

        val table = DdlCompiler.compile(listOf(model)).table("work_item")!!

        assertEquals(AutoDdlLogicalType.TEXT, table.column("status")!!.logicalType)
        assertEquals(AutoDdlLogicalType.INT32, table.column("priority")!!.logicalType)
        assertEquals(AutoDdlLogicalType.UNKNOWN, table.column("external_id")!!.logicalType)
    }

    @Test
    fun `compiles owning many to many relation as a junction table`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "article",
            name = "Article",
            packageName = "example.article",
            className = "Article",
            tableName = "article",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = emptyList(),
            queries = emptyList(),
            relations = listOf(
                LowcodeRelationMeta(
                    id = 2,
                    modelId = 1,
                    orderNo = 1,
                    relationCode = "tags",
                    label = "Tags",
                    relationKind = LowcodeRelationKind.MANY_TO_MANY,
                    targetModelId = 2,
                    targetModelCode = "tag",
                    targetPackageName = "example.tag",
                    targetClassName = "Tag",
                    joinColumn = null,
                    mappedBy = null,
                    joinTable = "article_tag",
                    joinTableJoinColumn = "article_id",
                    joinTableInverseColumn = "tag_id",
                    required = true,
                    listVisible = false,
                    formVisible = true,
                ),
            ),
        )

        val junction = DdlCompiler.compile(listOf(model)).table("article_tag")!!

        assertEquals(listOf("article_id", "tag_id"), junction.primaryKeyColumnNames)
        assertEquals("article", junction.junction!!.leftTableName)
        assertEquals("tag", junction.junction!!.rightTableName)
    }

    private fun field(
        id: Long,
        code: String,
        kotlinType: String,
        column: String,
        enumStorage: LowcodeEnumStorage? = null,
    ) = LowcodeFieldMeta(
        id = id,
        modelId = 1,
        orderNo = id.toInt(),
        fieldCode = code,
        label = code,
        kotlinType = kotlinType,
        dbColumn = column,
        required = false,
        listVisible = true,
        formVisible = true,
        formControl = "input",
        dictCode = null,
        defaultValue = null,
        remark = null,
        enumStorage = enumStorage,
    )
}
