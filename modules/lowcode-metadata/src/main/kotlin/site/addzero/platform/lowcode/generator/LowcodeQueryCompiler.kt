package site.addzero.platform.lowcode.generator

import java.util.Locale

/**
 * 将查询配置编译为路由、OpenAPI 和运行时共同消费的 LSI 查询字段。
 *
 * 注解、数据库和 Studio 都只是元数据提供端，实体源码不承载查询语义。
 */
object LowcodeQueryCompiler {
    fun compile(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(model),
    ): List<LsiLowcodeQueryField> {
        val propertiesByCode = model.queryPropertiesByCode(modelCatalog)
        return model.queries.sortedBy(LowcodeQueryMeta::orderNo).flatMap { query ->
            query.items.sortedBy(LowcodeQueryConditionMeta::orderNo).mapNotNull { condition ->
                val property = propertiesByCode[condition.fieldCode] ?: return@mapNotNull null
                val parameterName = condition.paramName?.takeIf(String::isNotBlank) ?: query.queryCode
                val operator = condition.compiledOperator(query.logic)
                val schema = property.kotlinType.toOpenApiType(property.enumStorage, property.enumValues)
                LsiLowcodeQueryField(
                    propertyName = condition.fieldCode,
                    parameterName = parameterName,
                    operator = operator,
                    type = schema.type,
                    format = schema.format,
                    endParameterName = if (operator == LowcodeQueryOperator.BETWEEN.name) {
                        "${parameterName}End"
                    } else {
                        null
                    },
                    stateCases = condition.compiledStateCases(),
                    description = property.description ?: property.label,
                    enumValues = schema.enumValues,
                )
            }
        }
    }

    private fun LowcodeQueryConditionMeta.compiledOperator(logic: LowcodeQueryLogic): String = when {
        logic == LowcodeQueryLogic.OR -> "KEYWORD"
        operator == LowcodeQueryOperator.NULL_STATE || operator == LowcodeQueryOperator.ZERO_STATE -> "STATE_SWITCH"
        else -> operator.name
    }

    private fun LowcodeQueryConditionMeta.compiledStateCases(): List<LsiLowcodeStateCase> =
        when (operator) {
            LowcodeQueryOperator.NULL_STATE -> listOf(
                LsiLowcodeStateCase(parameterValue = "0", operator = "IS_NULL", expression = ""),
                LsiLowcodeStateCase(parameterValue = "1", operator = "IS_NOT_NULL", expression = ""),
            )
            LowcodeQueryOperator.ZERO_STATE -> listOf(
                LsiLowcodeStateCase(parameterValue = "true", operator = "EQ", expression = "0"),
                LsiLowcodeStateCase(parameterValue = "false", operator = "GT", expression = "0"),
            )
            else -> emptyList()
        }

    private fun LowcodeModelMeta.queryPropertiesByCode(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): Map<String, QueryProperty> = buildMap {
        inheritanceLineage(modelCatalog).forEach { model ->
            model.fields.forEach { field ->
                put(
                    field.fieldCode,
                    QueryProperty(
                        field.kotlinType,
                        field.label,
                        field.remark,
                        field.enumStorage,
                        model.discriminatorEnumValues(field, modelCatalog),
                    ),
                )
            }
            model.entityConfig.resolvedInheritedProperties(
                includeConventionDefault = model.entityConfig.inheritanceSubtype == null,
            ).forEach { property ->
                put(property.name, QueryProperty(property.kotlinType, property.name, property.description))
            }
            model.entityConfig.formulaProperties.forEach { property ->
                put(property.propertyCode, QueryProperty(property.kotlinType, property.label, property.description))
            }
            model.entityConfig.transientProperties.forEach { property ->
                put(property.propertyCode, QueryProperty(property.kotlinType, property.label, property.description))
            }
            model.relations.filter { relation -> relation.relationKind.isReference() }.forEach { relation ->
                put("${relation.relationCode}Id", QueryProperty("Long", "${relation.label}编号", null))
            }
        }
    }
}

data class OpenApiType(
    val type: String,
    val format: String? = null,
    val arrayItemType: String? = null,
    val enumValues: List<String> = emptyList(),
)

fun String.toOpenApiType(
    enumStorage: LowcodeEnumStorage? = null,
    enumValues: List<String> = emptyList(),
): OpenApiType {
    val normalized = trim()
    when (enumStorage) {
        LowcodeEnumStorage.NAME -> return OpenApiType("string", enumValues = enumValues)
        LowcodeEnumStorage.ORDINAL -> return OpenApiType("integer", "int32")
        null -> Unit
    }
    COLLECTION_TYPE_PATTERN.matchEntire(normalized)?.let { match ->
        val itemType = match.groupValues[1].toOpenApiType()
        return OpenApiType("array", arrayItemType = itemType.type)
    }
    return when (normalized.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "string", "text" -> OpenApiType("string")
        "long" -> OpenApiType("integer", "int64")
        "int", "integer" -> OpenApiType("integer", "int32")
        "double", "bigdecimal", "decimal" -> OpenApiType("number", "double")
        "boolean", "bool" -> OpenApiType("boolean")
        "localdate" -> OpenApiType("string", "date")
        "localdatetime" -> OpenApiType("string", "date-time")
        else -> OpenApiType("object")
    }
}

private val COLLECTION_TYPE_PATTERN =
    Regex("(?:kotlin\\.collections\\.)?(?:List|MutableList|Set|MutableSet|Collection|Array)<(.+)>")

private data class QueryProperty(
    val kotlinType: String,
    val label: String,
    val description: String?,
    val enumStorage: LowcodeEnumStorage? = null,
    val enumValues: List<String> = emptyList(),
)
