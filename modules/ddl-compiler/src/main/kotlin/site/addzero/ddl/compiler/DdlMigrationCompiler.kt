package site.addzero.ddl.compiler

import java.util.Locale
import site.addzero.platform.lowcode.generator.LowcodeFieldMeta
import site.addzero.platform.lowcode.generator.LowcodeGeneratedFile
import site.addzero.platform.lowcode.generator.LowcodeInheritanceStrategy
import site.addzero.platform.lowcode.generator.LowcodeModelKind
import site.addzero.platform.lowcode.generator.LowcodeModelMeta
import site.addzero.platform.lowcode.generator.LowcodeRelationKind
import site.addzero.platform.lowcode.generator.LowcodeRelationMeta
import site.addzero.platform.lowcode.generator.LsiLowcodeInheritedProperty
import site.addzero.platform.lowcode.generator.inheritanceRootModel
import site.addzero.platform.lowcode.generator.isReference
import site.addzero.platform.lowcode.generator.resolvedInheritedProperties
import site.addzero.platform.lowcode.generator.generatedByStudio
import site.addzero.platform.lowcode.generator.validateLowcodeInheritance

/** 将单个模型编译为供 Studio 预览的不可变 Flyway 迁移。 */
object DdlMigrationCompiler {
    fun compile(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(model),
    ): LowcodeGeneratedFile? {
        if (model.kind != LowcodeModelKind.ENTITY || model.status != 1) {
            return null
        }
        val activeCatalog = modelCatalog.filter { candidate -> candidate.status == 1 }
        validateLowcodeInheritance(activeCatalog)
        if (model.isSingleTableSubtype(activeCatalog)) {
            return null
        }
        return compileMigration(model, activeCatalog)
    }

    private fun compileMigration(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): LowcodeGeneratedFile {
        val rootModel = model.inheritanceRootModel(modelCatalog)
        val singleTable = model.entityConfig.inheritanceRoot?.strategy == LowcodeInheritanceStrategy.SINGLE_TABLE
        val physicalModels = if (singleTable) {
            modelCatalog.filter { candidate ->
                candidate.inheritanceRootModel(modelCatalog)?.modelCode == model.modelCode
            }
        } else {
            listOf(model)
        }
        val inheritedProperties = if (model.entityConfig.inheritanceSubtype != null && rootModel != null) {
            listOf(rootModel.entityConfig.resolvedInheritedProperties().single(LsiLowcodeInheritedProperty::id))
        } else {
            model.entityConfig.resolvedInheritedProperties()
        }
        val columns = inheritedProperties.mapTo(mutableListOf()) { property ->
            val required = if (property.required || property.id) " NOT NULL" else ""
            "  \"${property.dbColumn.escapeSqlIdentifier()}\" ${sqlType(property.kotlinType)}$required"
        }
        physicalModels.sortedBy(LowcodeModelMeta::modelCode).forEach { physicalModel ->
            val forceNullable = singleTable && physicalModel.modelCode != model.modelCode
            physicalModel.fields.sortedBy(LowcodeFieldMeta::orderNo).forEach { field ->
                val required = if (field.required && !forceNullable) " NOT NULL" else ""
                columns += "  \"${field.dbColumn.escapeSqlIdentifier()}\" ${sqlType(field.kotlinType)}$required"
            }
            physicalModel.relations.sortedBy(LowcodeRelationMeta::orderNo)
                .filter { relation -> relation.relationKind.isReference() }
                .forEach { relation ->
                    relation.joinColumn?.takeIf(String::isNotBlank)?.let { column ->
                        val required = if (relation.required && !forceNullable) " NOT NULL" else ""
                        columns += "  \"${column.escapeSqlIdentifier()}\" BIGINT$required"
                    }
                }
        }
        val idColumn = inheritedProperties.single(LsiLowcodeInheritedProperty::id).dbColumn
        columns += "  PRIMARY KEY (\"${idColumn.escapeSqlIdentifier()}\")"
        val tableName = model.tableName.escapeSqlIdentifier()
        val joinTables = physicalModels.flatMap(LowcodeModelMeta::relations)
            .filter { relation -> relation.relationKind == LowcodeRelationKind.MANY_TO_MANY }
            .mapNotNull(::compileJoinTable)
        val content = buildList {
            add("CREATE TABLE IF NOT EXISTS \"$tableName\" (\n${columns.joinToString(",\n")}\n);")
            addAll(joinTables)
            add("COMMENT ON TABLE \"$tableName\" IS '${model.name.escapeSqlLiteral()}';")
            physicalModels.forEach { physicalModel ->
                physicalModel.fields.forEach { field ->
                    add(
                        "COMMENT ON COLUMN \"$tableName\".\"${field.dbColumn.escapeSqlIdentifier()}\" " +
                            "IS '${field.label.escapeSqlLiteral()}';",
                    )
                }
            }
        }.joinToString("\n\n", postfix = "\n")
        val fileName = "V${model.version}__${model.modelCode.camelToSnake()}_lowcode_generated"
        return LowcodeGeneratedFile(
            packageName = "",
            fileName = fileName,
            relativePath = "src/main/resources/db/migration/$fileName.sql",
            content = generatedByStudio(content, extensionName = "sql"),
            extensionName = "sql",
        )
    }

    private fun LowcodeModelMeta.isSingleTableSubtype(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): Boolean = entityConfig.inheritanceSubtype != null &&
        inheritanceRootModel(modelCatalog)?.entityConfig?.inheritanceRoot?.strategy ==
        LowcodeInheritanceStrategy.SINGLE_TABLE

    private fun compileJoinTable(relation: LowcodeRelationMeta): String? {
        val table = relation.joinTable?.takeIf(String::isNotBlank) ?: return null
        val joinColumn = relation.joinTableJoinColumn ?: "${relation.relationCode.camelToSnake()}_id"
        val inverseColumn = relation.joinTableInverseColumn ?: "${(relation.targetClassName ?: "target").camelToSnake()}_id"
        return """
            CREATE TABLE IF NOT EXISTS "${table.escapeSqlIdentifier()}" (
              "${joinColumn.escapeSqlIdentifier()}" BIGINT NOT NULL,
              "${inverseColumn.escapeSqlIdentifier()}" BIGINT NOT NULL,
              PRIMARY KEY ("${joinColumn.escapeSqlIdentifier()}", "${inverseColumn.escapeSqlIdentifier()}")
            );
        """.trimIndent()
    }

    private fun sqlType(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "string" -> "VARCHAR(255)"
        "text" -> "TEXT"
        "long" -> "BIGINT"
        "int", "integer" -> "INTEGER"
        "double" -> "DOUBLE PRECISION"
        "boolean", "bool" -> "BOOLEAN"
        "bigdecimal", "decimal" -> "NUMERIC(19, 2)"
        "localdate" -> "DATE"
        "localdatetime" -> "TIMESTAMP"
        else -> "TEXT"
    }

    private fun String.camelToSnake(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "\$1_\$2")
            .replace('-', '_')
            .lowercase(Locale.ROOT)

    private fun String.escapeSqlIdentifier(): String = replace("\"", "\"\"")

    private fun String.escapeSqlLiteral(): String = replace("'", "''")
}
