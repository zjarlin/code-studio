package site.addzero.studio.report.internal

import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.JsonPrimitive
import site.addzero.studio.contract.report.ReportBindingKind
import site.addzero.studio.contract.report.ReportDatasetSource
import site.addzero.studio.contract.report.ReportDatasetSpec
import site.addzero.studio.contract.report.ReportDocument
import site.addzero.studio.contract.report.ReportImageBlock
import site.addzero.studio.contract.report.ReportParameterBinding
import site.addzero.studio.contract.report.ReportRowSpec
import site.addzero.studio.contract.report.ReportTableBlock
import site.addzero.studio.contract.report.ReportTableColumn
import site.addzero.studio.contract.report.ReportTextBlock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportCompilerTest {
    @Test
    fun `发布编译保留网格文档`() {
        val document = document()

        val compiled = ReportCompiler.compile(document)

        assertEquals(document, compiled)
    }

    @Test
    fun `发布以 422 拒绝空布局未引用数据集和无替代文本图片`() {
        assertUnprocessable(ReportDocument(name = "空报表"))
        assertUnprocessable(
            ReportDocument(
                name = "未引用数据集",
                datasets = listOf(dataset()),
                rows = listOf(ReportRowSpec("rowOne", listOf(ReportTextBlock("title", "标题")))),
            ),
        )
        assertUnprocessable(
            ReportDocument(
                name = "无替代文本图片",
                datasets = listOf(dataset()),
                rows = listOf(
                    ReportRowSpec(
                        "rowOne",
                        listOf(ReportImageBlock("image", "orders", "/image", "")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `参数绑定按键确定性序列化`() {
        val first = linkedMapOf(
            "status" to literal("enabled"),
            "pageSize" to literal(200),
        )
        val second = linkedMapOf(
            "pageSize" to literal(200),
            "status" to literal("enabled"),
        )
        val left = document().copy(datasets = listOf(dataset().copy(parameterBindings = first)))
        val right = document().copy(datasets = listOf(dataset().copy(parameterBindings = second)))

        assertEquals(ReportJson.encodeDocument(left), ReportJson.encodeDocument(right))
    }

    private fun assertUnprocessable(document: ReportDocument) {
        val failure = assertFailsWith<ReportRequestException> {
            ReportCompiler.compile(document)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, failure.status)
    }

    private fun document(): ReportDocument = ReportDocument(
        name = "订单报表",
        datasets = listOf(dataset()),
        rows = listOf(
            ReportRowSpec(
                key = "rowOne",
                blocks = listOf(
                    ReportTableBlock(
                        key = "ordersTable",
                        datasetKey = "orders",
                        columns = listOf(ReportTableColumn("amount", "金额", "/amount")),
                    ),
                ),
            ),
        ),
    )

    private fun dataset(): ReportDatasetSpec = ReportDatasetSpec(
        key = "orders",
        name = "订单",
        source = ReportDatasetSource.MODEL,
        modelCode = "order",
    )

    private fun literal(value: Any): ReportParameterBinding = ReportParameterBinding(
        kind = ReportBindingKind.LITERAL,
        literal = when (value) {
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        },
    )
}
