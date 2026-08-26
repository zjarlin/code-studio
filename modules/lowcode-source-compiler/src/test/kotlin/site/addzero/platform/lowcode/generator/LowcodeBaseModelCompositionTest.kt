package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.ddl.compiler.DdlMigrationCompiler

class LowcodeBaseModelCompositionTest {
    @Test
    fun `BaseEntity components remain available without duplicate inheritance`() {
        val config = LsiLowcodeEntityConfig(
            baseMode = LowcodeEntityBaseMode.INHERITED,
            baseModels = listOf(
                LowcodeBaseModel.BASE_ENTITY,
                LowcodeBaseModel.SNOWFLAKE_ID,
                LowcodeBaseModel.CREATE_TIME,
                LowcodeBaseModel.UPDATE_TIME,
                LowcodeBaseModel.AUDIT,
            ),
        )

        assertEquals(listOf(LowcodeBaseModel.BASE_ENTITY), config.resolvedBaseModels().map { it.model })
        assertEquals(
            listOf(
                "${generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)}.BaseEntity",
                "${generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)}.generated.entity.BaseAudit",
            ),
            config.resolvedSuperTypes(),
        )
        assertEquals(
            listOf("id", "createTime", "updateTime", "updater", "creator"),
            config.resolvedInheritedProperties().map { property -> property.name },
        )
        val updater = config.resolvedInheritedProperties().single { property -> property.name == "updater" }
        assertEquals(generationTargetSymbol(GenerationTargetSymbols.AUDIT_PRINCIPAL), updater.kotlinType)
        assertEquals("Long", updater.storageKotlinType)
    }

    @Test
    fun `append-only entity composes snowflake id and create time`() {
        val entityConfig = LsiLowcodeEntityConfig(
            baseModels = listOf(LowcodeBaseModel.SNOWFLAKE_ID, LowcodeBaseModel.CREATE_TIME),
        )
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "deviceData",
            name = "设备数据",
            packageName = "example.telemetry",
            className = "DeviceData",
            tableName = "device_data",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = entityConfig,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val files = LowcodeSourceCompiler.generate(model) +
            listOfNotNull(DdlMigrationCompiler.compile(model))
        val entity = files.single { file -> file.fileName == "DeviceData" }
        val migration = files.single { file -> file.extensionName == "sql" }

        val persistencePackage = generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)
        assertTrue(entity.content.contains("import $persistencePackage.BaseCreateTime"))
        assertTrue(entity.content.contains("import $persistencePackage.BaseSnowflakeId"))
        assertTrue(entity.content.contains("interface DeviceData : BaseSnowflakeId, BaseCreateTime"))
        assertFalse(entity.content.contains("BaseUpdateTime"))
        assertTrue(migration.content.contains("\"create_time\" TIMESTAMP NOT NULL"))
        assertFalse(migration.content.contains("\"update_time\""))
    }

    @Test
    fun `relation node composes namespace into entity and ddl`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "relationNode",
            name = "关系节点",
            packageName = "example.relation",
            className = "RelationNode",
            tableName = "relation_node",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            entityConfig = LsiLowcodeEntityConfig(
                baseModels = listOf(
                    LowcodeBaseModel.NODE,
                    LowcodeBaseModel.SNOWFLAKE_ID,
                    LowcodeBaseModel.NAMESPACE,
                    LowcodeBaseModel.NAMED,
                ),
                inheritanceRoot = LsiLowcodeInheritanceRoot(
                    strategy = LowcodeInheritanceStrategy.JOINED,
                    discriminatorField = "nodeType",
                ),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val files = LowcodeSourceCompiler.generate(model) +
            listOfNotNull(DdlMigrationCompiler.compile(model))
        val entity = files.single { file -> file.fileName == "RelationNode" }
        val migration = files.single { file -> file.extensionName == "sql" }

        assertTrue(
            entity.content.contains(
                "import ${generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)}.BaseNode",
            ),
        )
        assertTrue(entity.content.contains("interface RelationNode : BaseNode"))
        assertFalse(entity.content.contains("import org.babyfish.jimmer.sql.Discriminator"))
        assertTrue(migration.content.contains("\"namespace\" VARCHAR(255)"))
        assertTrue(migration.content.contains("\"node_type\" VARCHAR(255) NOT NULL"))
    }

    @Test
    fun `entity source ownership does not leak into generated paths`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "member",
            name = "Member",
            packageName = "example.identity",
            className = "Member",
            tableName = "members",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "identity.members",
            entityConfig = LsiLowcodeEntityConfig(
                sourceContributorId = "shared.audit",
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )

        val entity = LowcodeSourceCompiler.generateEntities(listOf(model)).single()

        assertEquals("shared.audit", model.entitySourceContributorId())
        assertTrue(entity.relativePath.startsWith("src/main/kotlin/"))
        assertTrue(entity.content.contains("interface Member : BaseEntity, BaseAudit"))
    }

    @Test
    fun `legacy explicit BaseEntity parent also receives audit associations`() {
        val config = LsiLowcodeEntityConfig(
            baseMode = LowcodeEntityBaseMode.INHERITED,
            superTypes = listOf("example.persistence.BaseEntity"),
        )

        assertEquals(
            listOf(
                "example.persistence.BaseEntity",
                "${generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)}.generated.entity.BaseAudit",
            ),
            config.resolvedSuperTypes(),
        )
    }
}
