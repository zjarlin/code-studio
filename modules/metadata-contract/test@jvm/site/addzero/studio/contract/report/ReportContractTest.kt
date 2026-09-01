package site.addzero.studio.contract.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportContractTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        classDiscriminator = "classType"
    }

    @Test
    fun `报表文档使用唯一 kind 判别五类报表块`() {
        val document = completeDocument()

        val encoded = json.encodeToString(document)
        val decoded = json.decodeFromString<ReportDocument>(encoded)

        assertEquals(document, decoded)
        assertFalse("\"classType\"" in encoded)
        val blockKinds = ReportBlockKind.entries.joinToString("|") { kind -> kind.name }
        assertEquals(5, Regex("\"kind\":\"(?:$blockKinds)\"").findAll(encoded).count())
        assertTrue("\"source\":\"MODEL\"" in encoded)
        assertTrue("\"source\":\"OPENAPI\"" in encoded)
        ReportBlockKind.entries.forEach { kind ->
            assertTrue("\"kind\":\"${kind.name}\"" in encoded)
        }
    }

    @Test
    fun `绑定只允许报表参数或 JSON 字面量`() {
        val parameter = ReportParameterBinding(
            kind = ReportBindingKind.PARAMETER,
            parameterKey = "startDate",
        )
        val literal = ReportParameterBinding(
            kind = ReportBindingKind.LITERAL,
            literal = JsonPrimitive(200),
        )
        val nullLiteral = ReportParameterBinding(
            kind = ReportBindingKind.LITERAL,
            literal = null,
        )

        assertEquals("startDate", parameter.parameterKey)
        assertEquals("200", literal.literal.toString())
        val encodedNull = json.encodeToString(nullLiteral)
        assertEquals(nullLiteral, json.decodeFromString<ReportParameterBinding>(encodedNull))
        assertTrue("\"literal\":null" in encodedNull)
        assertFailsWith<IllegalArgumentException> {
            ReportParameterBinding(ReportBindingKind.PARAMETER, parameterKey = "startDate", literal = JsonPrimitive(1))
        }
        assertFailsWith<IllegalArgumentException> {
            ReportParameterBinding(
                ReportBindingKind.LITERAL,
                literal = buildJsonObject { put("unsafe", true) },
            )
        }
    }

    @Test
    fun `报表文档校验重复键断裂引用和 RFC 6901 指针`() {
        assertFailsWith<IllegalArgumentException> {
            ReportDocument(
                name = "重复参数",
                parameters = listOf(
                    ReportParameter("keyword", "关键字", ReportParameterType.TEXT),
                    ReportParameter("keyword", "重复关键字", ReportParameterType.TEXT),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReportDocument(
                name = "无效参数引用",
                datasets = listOf(
                    modelDataset(
                        bindings = mapOf(
                            "keyword" to ReportParameterBinding(
                                ReportBindingKind.PARAMETER,
                                parameterKey = "missingParameter",
                            ),
                        ),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReportDocument(
                name = "无效数据集引用",
                rows = listOf(
                    ReportRowSpec(
                        key = "rowOne",
                        blocks = listOf(
                            ReportMetricBlock("total", "missingDataset", "总数", "/total"),
                        ),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReportDatasetField("badPointer", "无效指针", "/value~2name")
        }
    }

    @Test
    fun `A4 配置网格与表格行数保持有界`() {
        assertFailsWith<IllegalArgumentException> {
            ReportPageSpec(marginMm = 9)
        }
        assertFailsWith<IllegalArgumentException> {
            ReportRowSpec(
                key = "rowOne",
                blocks = listOf(
                    ReportTextBlock("left", "左", columnSpan = 7),
                    ReportTextBlock("right", "右", columnSpan = 6),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ReportTableBlock("table", "orders", columnSpan = 11)
        }
        assertFailsWith<IllegalArgumentException> {
            ReportTableBlock("table", "orders", rowLimit = MAX_REPORT_ROW_COUNT + 1)
        }
    }

    @Test
    fun `枚举参数必须声明选项并约束默认值`() {
        val parameter = ReportParameter(
            key = "status",
            label = "状态",
            type = ReportParameterType.ENUM,
            defaultValue = "enabled",
            options = listOf(ReportParameterOption("enabled", "启用")),
        )

        assertEquals("enabled", parameter.defaultValue)
        assertFailsWith<IllegalArgumentException> {
            ReportParameter("status", "状态", ReportParameterType.ENUM)
        }
    }

    @Test
    fun `参数默认值必须符合声明类型`() {
        ReportParameter("amount", "金额", ReportParameterType.NUMBER, defaultValue = "12.5")
        ReportParameter("enabled", "启用", ReportParameterType.BOOLEAN, defaultValue = "false")
        ReportParameter("date", "日期", ReportParameterType.DATE, defaultValue = "2024-02-29")
        ReportParameter("time", "时间", ReportParameterType.DATETIME, defaultValue = "2024-02-29T23:59:59+08:00")

        listOf(
            Triple("amount", ReportParameterType.NUMBER, "abc"),
            Triple("enabled", ReportParameterType.BOOLEAN, "yes"),
            Triple("date", ReportParameterType.DATE, "2025-02-29"),
            Triple("time", ReportParameterType.DATETIME, "2024-01-01T25:00"),
        ).forEach { (key, type, defaultValue) ->
            assertFailsWith<IllegalArgumentException> {
                ReportParameter(key, key, type, defaultValue = defaultValue)
            }
        }
    }

    @Test
    fun `草稿和发布资源只使用键与修订号`() {
        val publication = ReportPublicationView(
            reportKey = "sales-summary",
            publishedRevision = 4,
            document = completeDocument(),
        )
        val view = ReportView(
            reportKey = "sales-summary",
            revision = 5,
            document = completeDocument(),
            publishedRevision = publication.publishedRevision,
        )

        assertEquals(5, view.revision)
        assertEquals(4, view.publishedRevision)
        assertEquals(
            "{\"expectedRevision\":5}",
            json.encodeToString(ReportPublishCommand(expectedRevision = 5)),
        )
        assertFailsWith<IllegalArgumentException> {
            ReportUpdateCommand(0, document = completeDocument())
        }
    }

    private fun completeDocument(): ReportDocument = ReportDocument(
        name = "销售汇总",
        page = ReportPageSpec(ReportPageOrientation.LANDSCAPE, marginMm = 8),
        parameters = listOf(
            ReportParameter("startDate", "开始日期", ReportParameterType.DATE, required = true),
        ),
        datasets = listOf(
            modelDataset(
                bindings = mapOf(
                    "startDate" to ReportParameterBinding(
                        ReportBindingKind.PARAMETER,
                        parameterKey = "startDate",
                    ),
                ),
            ),
            ReportDatasetSpec(
                key = "summary",
                name = "汇总",
                source = ReportDatasetSource.OPENAPI,
                operationId = "getSalesSummary",
                fields = listOf(ReportDatasetField("total", "销售额", "/total")),
            ),
        ),
        rows = listOf(
            ReportRowSpec(
                key = "heading",
                blocks = listOf(
                    ReportTextBlock("title", "销售报表", columnSpan = 6),
                    ReportMetricBlock(
                        key = "total",
                        datasetKey = "summary",
                        label = "销售额",
                        valuePointer = "/total",
                        aggregate = ReportMetricAggregate.SUM,
                        columnSpan = 6,
                    ),
                ),
            ),
            ReportRowSpec(
                key = "details",
                blocks = listOf(
                    ReportTableBlock(
                        key = "orderTable",
                        datasetKey = "orders",
                        columns = listOf(ReportTableColumn("amount", "金额", "/amount")),
                    ),
                ),
            ),
            ReportRowSpec(
                key = "visuals",
                blocks = listOf(
                    ReportChartBlock(
                        key = "salesChart",
                        datasetKey = "orders",
                        chartKind = ReportChartKind.BAR,
                        categoryPointer = "/date",
                        valuePointer = "/amount",
                        columnSpan = 8,
                    ),
                    ReportImageBlock(
                        key = "productImage",
                        datasetKey = "orders",
                        sourcePointer = "/imageUrl",
                        alt = "商品",
                        columnSpan = 4,
                    ),
                ),
            ),
        ),
    )

    private fun modelDataset(
        bindings: Map<String, ReportParameterBinding> = emptyMap(),
    ): ReportDatasetSpec = ReportDatasetSpec(
        key = "orders",
        name = "订单",
        source = ReportDatasetSource.MODEL,
        modelCode = "order",
        parameterBindings = bindings,
        fields = listOf(
            ReportDatasetField("date", "日期", "/date"),
            ReportDatasetField("amount", "金额", "/amount"),
        ),
    )
}
