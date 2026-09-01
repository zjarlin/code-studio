package site.addzero.studio.report.internal

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Workbook
import site.addzero.studio.contract.report.SpreadsheetBindingTarget
import site.addzero.studio.contract.report.SpreadsheetRangeSpec
import site.addzero.studio.contract.report.SpreadsheetTemplateDocument
import site.addzero.studio.contract.report.SpreadsheetTemplateFillCommand
import site.addzero.studio.contract.report.SpreadsheetVariableType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64
import javax.imageio.ImageIO

internal fun fillSpreadsheetTemplate(
    bytes: ByteArray,
    document: SpreadsheetTemplateDocument,
    command: SpreadsheetTemplateFillCommand,
): ByteArray = openWorkbook(bytes).use { workbook ->
    val images = WorkbookImageRegistry(workbook)
    document.edits.forEach { edit ->
        val sheetIndex = document.sheets.indexOfFirst { it.key == edit.sheetKey }
        check(sheetIndex >= 0)
        workbook.getSheetAt(sheetIndex).cell(edit.row, edit.column).write(
            edit.value,
            SpreadsheetVariableType.TEXT,
            "${edit.sheetKey}:${edit.row}:${edit.column}",
        )
    }

    val variables = document.variables.associateBy { it.key }
    val unknownValues = command.values.keys - variables.keys
    if (unknownValues.isNotEmpty()) reportUnprocessable("存在未定义的模板变量: ${unknownValues.sorted()}")
    val resolvedValues = document.variables.associate { variable ->
        variable.key to (command.values[variable.key] ?: variable.defaultValue)
    }
    val missingRequired = document.variables
        .filter { variable -> variable.required && resolvedValues[variable.key].isNullOrBlank() }
        .map { it.key }
    if (missingRequired.isNotEmpty()) reportUnprocessable("必填模板变量不能为空: $missingRequired")

    document.bindings.forEach { binding ->
        val value = resolvedValues[binding.variableKey] ?: return@forEach
        val variable = checkNotNull(variables[binding.variableKey])
        val sheetIndex = document.sheets.indexOfFirst { it.key == binding.sheetKey }
        check(sheetIndex >= 0)
        val sheet = workbook.getSheetAt(sheetIndex)
        if (binding.target == SpreadsheetBindingTarget.IMAGE_ANCHOR) {
            require(variable.type == SpreadsheetVariableType.IMAGE) {
                "图片锚点只能绑定图片变量: ${variable.key}"
            }
            images.add(sheetIndex, binding.range, value)
        } else {
            require(variable.type != SpreadsheetVariableType.IMAGE) {
                "图片变量必须绑定图片锚点: ${variable.key}"
            }
            sheet.cell(binding.range.fromRow, binding.range.fromColumn).write(value, variable.type, variable.key)
        }
    }

    val ledgers = document.ledgers.associateBy { it.key }
    val unknownLedgers = command.ledgers.keys - ledgers.keys
    if (unknownLedgers.isNotEmpty()) reportUnprocessable("存在未定义的台账: ${unknownLedgers.sorted()}")
    command.ledgers.forEach { (ledgerKey, rows) ->
        val ledger = checkNotNull(ledgers[ledgerKey])
        if (rows.size > ledger.maxRows) reportUnprocessable("台账 $ledgerKey 不能超过 ${ledger.maxRows} 行")
        val sheetIndex = document.sheets.indexOfFirst { it.key == ledger.sheetKey }
        check(sheetIndex >= 0)
        val sheet = workbook.getSheetAt(sheetIndex)
        rows.forEachIndexed { rowOffset, values ->
            val unknownFields = values.keys - ledger.fields.map { it.key }.toSet()
            if (unknownFields.isNotEmpty()) reportUnprocessable("台账 $ledgerKey 存在未定义字段: ${unknownFields.sorted()}")
            ledger.fields.forEach { field ->
                val value = values[field.key]
                if (field.required && value.isNullOrBlank()) {
                    reportUnprocessable("台账 $ledgerKey 第 ${rowOffset + 1} 行的 ${field.label} 不能为空")
                }
                if (value == null) return@forEach
                val row = ledger.firstRow + rowOffset
                if (field.type == SpreadsheetVariableType.IMAGE) {
                    val range = SpreadsheetRangeSpec(row, field.column)
                    images.add(sheetIndex, range, value)
                } else {
                    sheet.cell(row, field.column).write(value, field.type, field.key)
                }
            }
        }
    }

    workbook.setForceFormulaRecalculation(true)
    ByteArrayOutputStream().use { output ->
        workbook.write(output)
        output.toByteArray()
    }
}

private fun org.apache.poi.ss.usermodel.Sheet.cell(rowIndex: Int, columnIndex: Int): Cell {
    val row = getRow(rowIndex) ?: createRow(rowIndex)
    return row.getCell(columnIndex) ?: row.createCell(columnIndex)
}

private fun Cell.write(value: String, type: SpreadsheetVariableType, key: String) {
    try {
        if (cellType == CellType.FORMULA) setCellFormula(null)
        when (type) {
            SpreadsheetVariableType.TEXT -> setCellValue(value)
            SpreadsheetVariableType.NUMBER -> setCellValue(value.toDouble())
            SpreadsheetVariableType.BOOLEAN -> setCellValue(value.toBooleanStrict())
            SpreadsheetVariableType.DATE -> setCellValue(LocalDate.parse(value))
            SpreadsheetVariableType.DATETIME -> setCellValue(LocalDateTime.parse(value))
            SpreadsheetVariableType.IMAGE -> error("图片变量必须写入图片锚点")
        }
    } catch (cause: IllegalArgumentException) {
        reportUnprocessable("变量 $key 的值不符合 $type 类型")
    } catch (cause: DateTimeException) {
        reportUnprocessable("变量 $key 的值不符合 $type 类型")
    }
}

private class WorkbookImageRegistry(
    private val workbook: Workbook,
) {
    private val pictureIndices = mutableMapOf<String, Int>()
    private var anchorCount = 0
    private var totalImageBytes = 0L

    fun add(sheetIndex: Int, range: SpreadsheetRangeSpec, dataUri: String) {
        anchorCount += 1
        if (anchorCount > MAX_IMAGE_ANCHORS) {
            reportUnprocessable("单次填充最多可以生成 $MAX_IMAGE_ANCHORS 个图片锚点")
        }
        val pictureIndex = pictureIndices.getOrPut(dataUri) {
            val image = decodeImage(dataUri)
            totalImageBytes += image.bytes.size
            if (totalImageBytes > MAX_TOTAL_IMAGE_BYTES) {
                reportUnprocessable("单次填充的图片总量不能超过 20 MB")
            }
            workbook.addPicture(image.bytes, image.pictureType)
        }
        val anchor = workbook.creationHelper.createClientAnchor().apply {
            setCol1(range.fromColumn)
            row1 = range.fromRow
            setCol2(range.toColumn + 1)
            row2 = range.toRow + 1
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }
        workbook.getSheetAt(sheetIndex).createDrawingPatriarch().createPicture(anchor, pictureIndex)
    }
}

private data class DecodedImage(
    val bytes: ByteArray,
    val pictureType: Int,
)

private fun decodeImage(dataUri: String): DecodedImage {
    val match = DATA_IMAGE.matchEntire(dataUri) ?: reportUnprocessable(
        "图片变量必须是 PNG 或 JPEG 的 base64 data URI",
    )
    val mediaType = match.groupValues[1].lowercase()
    val bytes = runCatching { Base64.getDecoder().decode(match.groupValues[2]) }
        .getOrElse { reportUnprocessable("图片变量 base64 内容不合法") }
    if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) reportUnprocessable("单张模板图片必须在 1 字节到 5 MB 之间")
    validateImage(bytes, mediaType)
    val pictureType = when (mediaType) {
        "image/png" -> Workbook.PICTURE_TYPE_PNG
        "image/jpeg" -> Workbook.PICTURE_TYPE_JPEG
        else -> error("已由正则限制图片类型")
    }
    return DecodedImage(bytes, pictureType)
}

private fun validateImage(bytes: ByteArray, mediaType: String) {
    val hasExpectedSignature = when (mediaType) {
        "image/png" -> bytes.size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { index -> bytes[index] == PNG_SIGNATURE[index] }
        "image/jpeg" -> bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        else -> false
    }
    if (!hasExpectedSignature) reportUnprocessable("图片内容与声明的 PNG/JPEG 格式不一致")
    try {
        ByteArrayInputStream(bytes).use { input ->
            ImageIO.createImageInputStream(input).use { imageInput ->
                val readers = ImageIO.getImageReaders(imageInput)
                if (!readers.hasNext()) reportUnprocessable("图片内容无法识别")
                val reader = readers.next()
                try {
                    reader.input = imageInput
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0 || width.toLong() * height > MAX_IMAGE_PIXELS) {
                        reportUnprocessable("图片像素尺寸不合法或超过限制")
                    }
                    reader.read(0).flush()
                } finally {
                    reader.dispose()
                }
            }
        }
    } catch (cause: ReportRequestException) {
        throw cause
    } catch (cause: Exception) {
        reportUnprocessable("图片内容损坏或无法解码")
    }
}

private val DATA_IMAGE = Regex("data:(image/(?:png|jpeg));base64,([A-Za-z0-9+/=\\r\\n]+)", RegexOption.IGNORE_CASE)
private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
private const val MAX_TOTAL_IMAGE_BYTES = 20L * 1024 * 1024
private const val MAX_IMAGE_ANCHORS = 256
private const val MAX_IMAGE_PIXELS = 16_777_216L
