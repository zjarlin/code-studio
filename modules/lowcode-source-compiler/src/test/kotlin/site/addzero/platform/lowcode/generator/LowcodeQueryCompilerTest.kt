package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LowcodeQueryCompilerTest {
    @Test
    fun `compiles collection metadata as OpenAPI arrays`() {
        assertEquals(OpenApiType("array", arrayItemType = "integer"), "List<Int>".toOpenApiType())
        assertEquals(
            OpenApiType("array", arrayItemType = "object"),
            "List<example.rule.Trigger>".toOpenApiType(),
        )
        assertEquals(
            OpenApiType("array", arrayItemType = "string"),
            "kotlin.collections.Set<String>".toOpenApiType(),
        )
        assertEquals(OpenApiType("string", format = "date-time"), "java.time.LocalDateTime".toOpenApiType())
        assertEquals(
            OpenApiType("string", enumValues = listOf("OPEN", "CLOSED")),
            "example.State".toOpenApiType(LowcodeEnumStorage.NAME, listOf("OPEN", "CLOSED")),
        )
        assertEquals(
            OpenApiType("integer", format = "int32"),
            "example.State".toOpenApiType(LowcodeEnumStorage.ORDINAL),
        )
    }

    @Test
    fun `compiles low query condition semantics without entity annotations`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "record",
            name = "记录",
            packageName = "example.record",
            className = "Record",
            tableName = "example_record",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            fields = listOf(
                field(1, "name", "String"),
                field(2, "status", "Int"),
                field(3, "publishedTime", "LocalDateTime"),
            ),
            queries = listOf(
                query(1, "namePrefix", condition(11, "name", LowcodeQueryOperator.STARTS_WITH)),
                query(2, "nameSuffix", condition(12, "name", LowcodeQueryOperator.ENDS_WITH)),
                query(3, "statuses", condition(13, "status", LowcodeQueryOperator.NOT_IN)),
                query(4, "publishedBetween", condition(14, "publishedTime", LowcodeQueryOperator.BETWEEN)),
                query(5, "publishedRange", condition(15, "publishedTime", LowcodeQueryOperator.TIME_RANGE)),
                query(6, "publishState", condition(16, "publishedTime", LowcodeQueryOperator.NULL_STATE)),
                query(7, "successState", condition(17, "status", LowcodeQueryOperator.ZERO_STATE)),
            ),
            relations = emptyList(),
        )

        val fields = LowcodeQueryCompiler.compile(model).associateBy(LsiLowcodeQueryField::parameterName)

        assertEquals("STARTS_WITH", fields.getValue("namePrefix").operator)
        assertEquals("ENDS_WITH", fields.getValue("nameSuffix").operator)
        assertEquals("NOT_IN", fields.getValue("statuses").operator)
        assertEquals("publishedBetweenEnd", fields.getValue("publishedBetween").endParameterName)
        assertNull(fields.getValue("publishedRange").endParameterName)
        assertEquals("TIME_RANGE", fields.getValue("publishedRange").operator)
        assertEquals("STATE_SWITCH", fields.getValue("publishState").operator)
        assertEquals(
            listOf("0" to "IS_NULL", "1" to "IS_NOT_NULL"),
            fields.getValue("publishState").stateCases.map { state -> state.parameterValue to state.operator },
        )
        assertEquals("STATE_SWITCH", fields.getValue("successState").operator)
        assertEquals(
            listOf("true" to "EQ", "false" to "GT"),
            fields.getValue("successState").stateCases.map { state -> state.parameterValue to state.operator },
        )

        val staleRoute = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "${model.packageName}.generated.${model.className}",
            className = model.className,
            description = null,
            path = "/records",
            enabledOperations = setOf("PAGE"),
            properties = emptyList(),
            queryFields = listOf(
                LsiLowcodeQueryField("stale", "stale", "EQ", "string", null),
            ),
        )
        val compiledRoute = LowcodeSourceCompiler.run { model.toLsiRoute(staleRoute) }

        assertEquals(fields.values.toList(), compiledRoute.queryFields)
    }

    private fun field(id: Long, code: String, type: String) = LowcodeFieldMeta(
        id = id,
        modelId = 1,
        orderNo = id.toInt(),
        fieldCode = code,
        label = code,
        kotlinType = type,
        dbColumn = code,
        required = false,
        listVisible = true,
        formVisible = true,
        formControl = "input",
        dictCode = null,
        defaultValue = null,
        remark = null,
    )

    private fun query(id: Long, code: String, condition: LowcodeQueryConditionMeta) = LowcodeQueryMeta(
        id = id,
        modelId = 1,
        orderNo = id.toInt(),
        queryCode = code,
        label = code,
        logic = LowcodeQueryLogic.AND,
        items = listOf(condition.copy(queryId = id)),
    )

    private fun condition(id: Long, fieldCode: String, operator: LowcodeQueryOperator) =
        LowcodeQueryConditionMeta(
            id = id,
            queryId = 0,
            orderNo = 1,
            fieldCode = fieldCode,
            operator = operator,
            valueType = when (operator) {
                LowcodeQueryOperator.NOT_IN -> LowcodeQueryValueType.MULTIPLE
                LowcodeQueryOperator.BETWEEN,
                LowcodeQueryOperator.TIME_RANGE,
                -> LowcodeQueryValueType.DATETIME_RANGE
                else -> LowcodeQueryValueType.SINGLE
            },
            paramName = null,
        )
}
