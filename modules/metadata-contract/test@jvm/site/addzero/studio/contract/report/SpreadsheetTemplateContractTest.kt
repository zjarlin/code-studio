package site.addzero.studio.contract.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpreadsheetTemplateContractTest {
    private val json = Json { encodeDefaults = true; explicitNulls = true }

    @Test
    fun `电子表格模板可确定性往返序列化`() {
        val document = document()

        val encoded = json.encodeToString(document)
        val decoded = json.decodeFromString<SpreadsheetTemplateDocument>(encoded)

        assertEquals(document, decoded)
        assertTrue("\"macroEnabled\":true" in encoded)
        assertTrue("\"target\":\"IMAGE_ANCHOR\"" in encoded)
    }

    @Test
    fun `拒绝断裂引用重复编辑与错误图片绑定`() {
        assertFailsWith<IllegalArgumentException> {
            document().copy(bindings = listOf(binding(variableKey = "missing")))
        }
        assertFailsWith<IllegalArgumentException> {
            document().copy(bindings = listOf(binding(target = SpreadsheetBindingTarget.CELL)))
        }
        assertFailsWith<IllegalArgumentException> {
            document().copy(edits = listOf(
                SpreadsheetCellEditSpec("sheet1", 0, 0, "A"),
                SpreadsheetCellEditSpec("sheet1", 0, 0, "B"),
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            document().copy(edits = listOf(SpreadsheetCellEditSpec("sheet1", 4, 0, "A")))
        }
    }

    @Test
    fun `台账字段和范围保持有界`() {
        assertFailsWith<IllegalArgumentException> {
            SpreadsheetLedgerSpec("records", "记录", "sheet1", firstRow = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SpreadsheetRangeSpec(2, 2, 1, 3)
        }
        assertFailsWith<IllegalArgumentException> {
            document().copy(bindings = listOf(binding(range = SpreadsheetRangeSpec(0, 0, 9, 9))))
        }
        assertFailsWith<IllegalArgumentException> {
            document().copy(
                ledgers = listOf(
                    SpreadsheetLedgerSpec(
                        "records",
                        "记录",
                        "sheet1",
                        firstRow = 1,
                        fields = listOf(
                            SpreadsheetLedgerFieldSpec("code", "编号", column = 0),
                            SpreadsheetLedgerFieldSpec("name", "名称", column = 0),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `编辑草稿只替换允许修改的覆盖层`() {
        val source = document()
        val draft = SpreadsheetTemplateDraftSpec(
            name = "更新名称",
            description = source.description,
            variables = source.variables,
            bindings = source.bindings,
            ledgers = source.ledgers,
            edits = source.edits,
        )

        val updated = draft.applyTo(source)

        assertEquals("更新名称", updated.name)
        assertEquals(source.source, updated.source)
        assertEquals(source.styles, updated.styles)
        assertEquals(source.sheets, updated.sheets)
        assertFailsWith<IllegalArgumentException> { SpreadsheetTemplateFillCommand(expectedRevision = 0) }
    }

    private fun document(): SpreadsheetTemplateDocument = SpreadsheetTemplateDocument(
        name = "检验记录",
        source = SpreadsheetSourceSpec(
            fileName = "record.xlsm",
            mediaType = "application/vnd.ms-excel.sheet.macroEnabled.12",
            sha256 = "a".repeat(64),
            macroEnabled = true,
        ),
        styles = listOf(SpreadsheetStyleSpec(0)),
        sheets = listOf(
            SpreadsheetSheetSpec(
                key = "sheet1",
                name = "记录",
                rowCount = 4,
                columnCount = 4,
                cells = listOf(SpreadsheetCellSpec(0, 0, SpreadsheetCellType.TEXT, "名称", styleKey = 0)),
            ),
        ),
        variables = listOf(SpreadsheetVariableSpec("signature", "签名", SpreadsheetVariableType.IMAGE)),
        bindings = listOf(binding()),
        ledgers = listOf(
            SpreadsheetLedgerSpec(
                key = "records",
                name = "台账",
                sheetKey = "sheet1",
                firstRow = 1,
                fields = listOf(SpreadsheetLedgerFieldSpec("code", "编号", column = 0)),
            ),
        ),
    )

    private fun binding(
        variableKey: String = "signature",
        target: SpreadsheetBindingTarget = SpreadsheetBindingTarget.IMAGE_ANCHOR,
        range: SpreadsheetRangeSpec = SpreadsheetRangeSpec(1, 1, 2, 2),
    ): SpreadsheetBindingSpec = SpreadsheetBindingSpec(
        key = "signature-binding",
        variableKey = variableKey,
        sheetKey = "sheet1",
        target = target,
        range = range,
    )
}
