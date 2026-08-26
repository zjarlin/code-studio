package site.addzero.ddl.compiler

import java.util.Locale
import site.addzero.ddlgenerator.core.model.AutoDdlColumn
import site.addzero.ddlgenerator.core.model.AutoDdlJunction
import site.addzero.ddlgenerator.core.model.AutoDdlLogicalType
import site.addzero.ddlgenerator.core.model.AutoDdlSchema
import site.addzero.ddlgenerator.core.model.AutoDdlTable
import site.addzero.platform.lowcode.generator.LowcodeEnumStorage
import site.addzero.platform.lowcode.generator.LowcodeFieldMeta
import site.addzero.platform.lowcode.generator.LowcodeInheritanceStrategy
import site.addzero.platform.lowcode.generator.LowcodeModelKind
import site.addzero.platform.lowcode.generator.LowcodeModelMeta
import site.addzero.platform.lowcode.generator.LowcodeRelationKind
import site.addzero.platform.lowcode.generator.LowcodeRelationMeta
import site.addzero.platform.lowcode.generator.LsiLowcodeInheritedProperty
import site.addzero.platform.lowcode.generator.inheritanceRootModel
import site.addzero.platform.lowcode.generator.isReference
import site.addzero.platform.lowcode.generator.resolvedInheritedProperties
import site.addzero.platform.lowcode.generator.validateLowcodeInheritance

/** 将低代码 LSI 编译为数据库结构模型。 */
object DdlCompiler {
    fun compile(models: List<LowcodeModelMeta>): AutoDdlSchema {
        val entities = models.filter { model -> model.kind == LowcodeModelKind.ENTITY && model.status == 1 }
        validateLowcodeInheritance(entities)
        val entityTables = entities
            .filterNot { model -> model.isSingleTableSubtype(entities) }
            .map { model -> toTable(model, entities) }
        val junctionTables = compileJunctionTables(entities)
        return AutoDdlSchema(
            tables = (entityTables + junctionTables).sortedBy(AutoDdlTable::name),
        )
    }

    private fun compileJunctionTables(models: Collection<LowcodeModelMeta>): List<AutoDdlTable> = models
        .flatMap { model ->
            model.relations
                .filter { relation ->
                    relation.relationKind == LowcodeRelationKind.MANY_TO_MANY &&
                        relation.mappedBy.isNullOrBlank() &&
                        !relation.joinTable.isNullOrBlank()
                }
                .map { relation -> relation.toJunctionTable(model, models) }
        }
        .groupBy { table -> table.name.lowercase() }
        .map { (_, tables) ->
            val signatures = tables.map { table ->
                table.columns.map { column -> column.name.lowercase() to column.logicalType }
            }.distinct()
            require(signatures.size == 1) {
                "中间表 ${tables.first().name} 存在不兼容列定义"
            }
            tables.first()
        }

    private fun LowcodeRelationMeta.toJunctionTable(
        sourceModel: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): AutoDdlTable {
        val tableName = requireNotNull(joinTable)
        val leftColumn = joinTableJoinColumn ?: "${relationCode.camelToSnake()}_id"
        val rightColumn = joinTableInverseColumn ?: "${(targetClassName ?: "target").camelToSnake()}_id"
        val targetTable = modelCatalog.firstOrNull { model ->
            model.modelCode == targetModelCode ||
                model.packageName == targetPackageName && model.className == targetClassName
        }?.tableName ?: (targetClassName ?: "target").camelToSnake()
        return AutoDdlTable(
            name = tableName,
            columns = listOf(
                AutoDdlColumn(leftColumn, AutoDdlLogicalType.INT64, nullable = false, primaryKey = true),
                AutoDdlColumn(rightColumn, AutoDdlLogicalType.INT64, nullable = false, primaryKey = true),
            ),
            comment = label,
            junction = AutoDdlJunction(
                leftTableName = sourceModel.tableName,
                rightTableName = targetTable,
                leftColumnName = leftColumn,
                rightColumnName = rightColumn,
            ),
        )
    }

    private fun toTable(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): AutoDdlTable {
        val inheritanceRoot = model.entityConfig.inheritanceRoot
        if (inheritanceRoot?.strategy == LowcodeInheritanceStrategy.SINGLE_TABLE) {
            return toSingleTable(model, modelCatalog)
        }
        val columns = buildList {
            model.physicalTableInheritedProperties(modelCatalog).forEach { property ->
                add(property.toColumn())
            }
            model.fields.forEach { field -> add(field.toColumn()) }
            model.owningRelationColumns().forEach(::add)
        }.mergeCompatibleColumns(model.tableName)
        return AutoDdlTable(name = model.tableName, columns = columns, comment = model.name)
    }

    private fun toSingleTable(
        root: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): AutoDdlTable {
        val hierarchy = modelCatalog
            .filter { model -> model.inheritanceRootModel(modelCatalog)?.modelCode == root.modelCode }
            .sortedBy(LowcodeModelMeta::modelCode)
        val columns = buildList {
            root.entityConfig.resolvedInheritedProperties().forEach { property -> add(property.toColumn()) }
            root.fields.forEach { field -> add(field.toColumn()) }
            root.owningRelationColumns().forEach(::add)
            hierarchy.filterNot { model -> model.modelCode == root.modelCode }.forEach { subtype ->
                subtype.fields.forEach { field -> add(field.toColumn(forceNullable = true)) }
                subtype.owningRelationColumns(forceNullable = true).forEach(::add)
            }
        }.mergeCompatibleColumns(root.tableName)
        return AutoDdlTable(name = root.tableName, columns = columns, comment = root.name)
    }

    private fun LowcodeModelMeta.physicalTableInheritedProperties(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): List<LsiLowcodeInheritedProperty> {
        val root = inheritanceRootModel(modelCatalog)
        if (entityConfig.inheritanceSubtype == null || root == null) {
            return entityConfig.resolvedInheritedProperties()
        }
        val id = root.entityConfig.resolvedInheritedProperties().single { property -> property.id }
        return listOf(id)
    }

    private fun LowcodeModelMeta.isSingleTableSubtype(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): Boolean = entityConfig.inheritanceSubtype != null &&
        inheritanceRootModel(modelCatalog)?.entityConfig?.inheritanceRoot?.strategy ==
        LowcodeInheritanceStrategy.SINGLE_TABLE

    private fun LowcodeModelMeta.owningRelationColumns(
        forceNullable: Boolean = false,
    ): List<AutoDdlColumn> = relations
        .filter { relation -> relation.relationKind.isReference() && !relation.joinColumn.isNullOrBlank() }
        .map { relation ->
            AutoDdlColumn(
                name = checkNotNull(relation.joinColumn),
                logicalType = AutoDdlLogicalType.INT64,
                nullable = forceNullable || !relation.required,
                comment = relation.label,
            )
        }

    private fun List<AutoDdlColumn>.mergeCompatibleColumns(tableName: String): List<AutoDdlColumn> {
        val columnsByName = linkedMapOf<String, AutoDdlColumn>()
        forEach { column ->
            val key = column.name.lowercase()
            val existing = columnsByName[key]
            check(existing == null || existing.logicalType == column.logicalType) {
                "SINGLE_TABLE 表 $tableName 的共享列 ${column.name} 存在不兼容类型"
            }
            columnsByName[key] = if (existing == null) {
                column
            } else {
                existing.copy(nullable = existing.nullable || column.nullable)
            }
        }
        return columnsByName.values.toList()
    }

    private fun LsiLowcodeInheritedProperty.toColumn() = AutoDdlColumn(
        name = dbColumn,
        logicalType = (storageKotlinType ?: kotlinType).toLogicalType(serialized = false, maxLength = maxLength),
        nullable = !required,
        length = maxLength,
        defaultValue = defaultValue,
        comment = description,
        primaryKey = id,
    )

    private fun LowcodeFieldMeta.toColumn(
        forceNullable: Boolean = false,
    ) = AutoDdlColumn(
        name = dbColumn,
        logicalType = kotlinType.toLogicalType(
            serialized = serialized,
            maxLength = maxLength,
            enumStorage = enumStorage,
        ),
        nullable = forceNullable || !required,
        length = maxLength,
        defaultValue = defaultValue,
        comment = label,
        nativeTypeHint = if (serialized) "JSONB" else null,
    )

    private fun String.toLogicalType(
        serialized: Boolean,
        maxLength: Int?,
        enumStorage: LowcodeEnumStorage? = null,
    ): AutoDdlLogicalType {
        if (serialized) return AutoDdlLogicalType.JSON
        if (enumStorage == LowcodeEnumStorage.ORDINAL) return AutoDdlLogicalType.INT32
        if (enumStorage == LowcodeEnumStorage.NAME) {
            return if (maxLength == null) AutoDdlLogicalType.TEXT else AutoDdlLogicalType.STRING
        }
        return when (substringAfterLast('.').substringBefore('<').lowercase()) {
            "string" -> if (maxLength == null) AutoDdlLogicalType.TEXT else AutoDdlLogicalType.STRING
            "boolean" -> AutoDdlLogicalType.BOOLEAN
            "byte" -> AutoDdlLogicalType.INT8
            "short" -> AutoDdlLogicalType.INT16
            "int" -> AutoDdlLogicalType.INT32
            "long" -> AutoDdlLogicalType.INT64
            "float" -> AutoDdlLogicalType.FLOAT32
            "double" -> AutoDdlLogicalType.FLOAT64
            "bigdecimal", "decimal" -> AutoDdlLogicalType.DECIMAL
            "localdate" -> AutoDdlLogicalType.DATE
            "localtime" -> AutoDdlLogicalType.TIME
            "localdatetime" -> AutoDdlLogicalType.DATETIME
            "instant", "offsetdatetime", "zoneddatetime" -> AutoDdlLogicalType.DATETIME_TZ
            "uuid" -> AutoDdlLogicalType.UUID
            "bytearray" -> AutoDdlLogicalType.BINARY
            else -> AutoDdlLogicalType.UNKNOWN
        }
    }

    private fun String.camelToSnake(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "\$1_\$2")
            .replace('-', '_')
            .lowercase(Locale.ROOT)
}
