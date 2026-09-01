package site.addzero.studio.report.internal

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import site.addzero.studio.contract.report.SpreadsheetBindingSpec
import site.addzero.studio.contract.report.SpreadsheetBindingTarget
import site.addzero.studio.contract.report.SpreadsheetLedgerFieldSpec
import site.addzero.studio.contract.report.SpreadsheetLedgerSpec
import site.addzero.studio.contract.report.SpreadsheetRangeSpec
import site.addzero.studio.contract.report.SpreadsheetTemplateFillCommand
import site.addzero.studio.contract.report.SpreadsheetVariableSpec
import site.addzero.studio.contract.report.SpreadsheetVariableType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpreadsheetTemplateCodecTest {
    @Test
    fun `读取布局并填充文字图片与自定义台账`() {
        val source = sampleWorkbook()
        val imported = SpreadsheetTemplateCodec.read(source, "sample.xlsx", "质检模板")
        val sheet = imported.sheets.single()
        assertTrue(sheet.cells.any { it.row == 4 && it.column == 3 && it.displayValue.isEmpty() })
        val document = imported.copy(
            variables = listOf(
                SpreadsheetVariableSpec("projectName", "工程名称", required = true),
                SpreadsheetVariableSpec("formulaValue", "公式覆盖"),
                SpreadsheetVariableSpec("signature", "签名", SpreadsheetVariableType.IMAGE),
            ),
            bindings = listOf(
                SpreadsheetBindingSpec(
                    "formula-binding",
                    "formulaValue",
                    sheet.key,
                    SpreadsheetBindingTarget.CELL,
                    SpreadsheetRangeSpec(1, 0),
                ),
                SpreadsheetBindingSpec(
                    "project-binding",
                    "projectName",
                    sheet.key,
                    SpreadsheetBindingTarget.MERGED_RANGE,
                    SpreadsheetRangeSpec(0, 0, 0, 1),
                ),
                SpreadsheetBindingSpec(
                    "signature-binding",
                    "signature",
                    sheet.key,
                    SpreadsheetBindingTarget.IMAGE_ANCHOR,
                    SpreadsheetRangeSpec(2, 0, 3, 1),
                ),
                SpreadsheetBindingSpec(
                    "signature-binding-second",
                    "signature",
                    sheet.key,
                    SpreadsheetBindingTarget.IMAGE_ANCHOR,
                    SpreadsheetRangeSpec(2, 2, 3, 3),
                ),
            ),
            ledgers = listOf(
                SpreadsheetLedgerSpec(
                    key = "records",
                    name = "检测台账",
                    sheetKey = sheet.key,
                    firstRow = 5,
                    fields = listOf(
                        SpreadsheetLedgerFieldSpec("code", "编号", column = 0, required = true),
                        SpreadsheetLedgerFieldSpec("value", "值", SpreadsheetVariableType.NUMBER, column = 1),
                    ),
                ),
            ),
        )
        val command = SpreadsheetTemplateFillCommand(
            expectedRevision = 1,
            values = mapOf(
                "projectName" to "Example Project",
                "formulaValue" to "Replaced formula",
                "signature" to "data:image/png;base64,$TINY_PNG",
            ),
            ledgers = mapOf("records" to listOf(mapOf("code" to "A-1", "value" to "12.5"))),
        )

        val output = SpreadsheetTemplateCodec.fill(source, document, command)

        WorkbookFactory.create(ByteArrayInputStream(output)).use { workbook ->
            val result = workbook.getSheetAt(0)
            assertEquals("Example Project", result.getRow(0).getCell(0).stringCellValue)
            assertEquals("A-1", result.getRow(5).getCell(0).stringCellValue)
            assertEquals(12.5, result.getRow(5).getCell(1).numericCellValue)
            assertEquals("Replaced formula", result.getRow(1).getCell(0).stringCellValue)
            assertEquals("1+1", result.getRow(1).getCell(1).cellFormula)
            assertEquals(1, result.numMergedRegions)
            assertEquals(2, workbook.allPictures.size)
        }
    }

    @Test
    fun `拒绝非法日期和伪造图片内容`() {
        val source = sampleWorkbook()
        val imported = SpreadsheetTemplateCodec.read(source, "sample.xlsx", "质检模板")
        val sheet = imported.sheets.single()
        val dateDocument = imported.copy(
            variables = listOf(SpreadsheetVariableSpec("formedOn", "成型日期", SpreadsheetVariableType.DATE)),
            bindings = listOf(
                SpreadsheetBindingSpec(
                    "formed-on-binding",
                    "formedOn",
                    sheet.key,
                    SpreadsheetBindingTarget.CELL,
                    SpreadsheetRangeSpec(0, 0),
                ),
            ),
        )
        assertFailsWith<ReportRequestException> {
            SpreadsheetTemplateCodec.fill(
                source,
                dateDocument,
                SpreadsheetTemplateFillCommand(1, values = mapOf("formedOn" to "not-a-date")),
            )
        }

        val imageDocument = imported.copy(
            variables = listOf(SpreadsheetVariableSpec("signature", "签名", SpreadsheetVariableType.IMAGE)),
            bindings = listOf(
                SpreadsheetBindingSpec(
                    "signature-binding",
                    "signature",
                    sheet.key,
                    SpreadsheetBindingTarget.IMAGE_ANCHOR,
                    SpreadsheetRangeSpec(0, 0),
                ),
            ),
        )
        assertFailsWith<ReportRequestException> {
            SpreadsheetTemplateCodec.fill(
                source,
                imageDocument,
                SpreadsheetTemplateFillCommand(
                    1,
                    values = mapOf("signature" to "data:image/png;base64,SGVsbG8="),
                ),
            )
        }
    }

    @Test
    fun `限制单次填充生成的图片锚点数`() {
        val source = sampleWorkbook()
        val imported = SpreadsheetTemplateCodec.read(source, "sample.xlsx", "质检模板")
        val sheet = imported.sheets.single()
        val document = imported.copy(
            variables = listOf(SpreadsheetVariableSpec("signature", "签名", SpreadsheetVariableType.IMAGE)),
            bindings = List(257) { index ->
                SpreadsheetBindingSpec(
                    "signature-binding-$index",
                    "signature",
                    sheet.key,
                    SpreadsheetBindingTarget.IMAGE_ANCHOR,
                    SpreadsheetRangeSpec(0, 0),
                )
            },
        )

        assertFailsWith<ReportRequestException> {
            SpreadsheetTemplateCodec.fill(
                source,
                document,
                SpreadsheetTemplateFillCommand(
                    1,
                    values = mapOf("signature" to "data:image/png;base64,$TINY_PNG"),
                ),
            )
        }
    }

    @Test
    fun `保持已配置 xlsm 样本的宏和外链包部件`() {
        val fixture = System.getenv("SPREADSHEET_TEMPLATE_FIXTURE")?.let(Path::of)
        assumeTrue(fixture != null && Files.isRegularFile(fixture), "未配置 xlsm 样本")
        val fixturePath = checkNotNull(fixture)
        val source = Files.readAllBytes(fixturePath)
        val imported = SpreadsheetTemplateCodec.read(source, fixturePath.fileName.toString(), "宏模板")

        val output = SpreadsheetTemplateCodec.fill(source, imported, SpreadsheetTemplateFillCommand(expectedRevision = 1))
        val sourceEntries = source.zipEntries()
        val outputEntries = output.zipEntries()

        assertTrue("xl/vbaProject.bin" in outputEntries)
        assertTrue(source.zipFile("xl/vbaProject.bin").contentEquals(output.zipFile("xl/vbaProject.bin")))
        assertEquals(sourceEntries.filter { it.startsWith("xl/externalLinks/") && !it.endsWith('/') }.toSet(), outputEntries.filter {
            it.startsWith("xl/externalLinks/") && !it.endsWith('/')
        }.toSet())
    }

    private fun sampleWorkbook(): ByteArray = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Template")
        sheet.createRow(0).createCell(0).setCellValue("Project")
        sheet.getRow(0).createCell(1).setCellValue("Name")
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 1))
        sheet.createRow(1).createCell(0).cellFormula = "A1+B1"
        sheet.getRow(1).createCell(1).cellFormula = "1+1"
        sheet.createRow(4).createCell(3)
        val picture = workbook.addPicture(Base64.getDecoder().decode(TINY_PNG), XSSFWorkbook.PICTURE_TYPE_PNG)
        val anchor = workbook.creationHelper.createClientAnchor().apply {
            setCol1(2)
            row1 = 2
            setCol2(3)
            row2 = 3
        }
        sheet.createDrawingPatriarch().createPicture(anchor, picture)
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }
    }
}

private fun ByteArray.zipEntries(): List<String> = buildList {
    ZipInputStream(ByteArrayInputStream(this@zipEntries)).use { archive ->
        while (true) add((archive.nextEntry ?: break).name)
    }
}

private fun ByteArray.zipFile(name: String): ByteArray {
    ZipInputStream(ByteArrayInputStream(this)).use { archive ->
        while (true) {
            val entry = archive.nextEntry ?: break
            if (entry.name == name) return archive.readBytes()
        }
    }
    error("ZIP entry 不存在: $name")
}

private const val TINY_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
