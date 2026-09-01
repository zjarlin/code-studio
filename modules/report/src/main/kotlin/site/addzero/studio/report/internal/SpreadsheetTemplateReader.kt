package site.addzero.studio.report.internal

import org.apache.poi.hssf.usermodel.HSSFPicture
import org.apache.poi.hssf.usermodel.HSSFSheet
import org.apache.poi.ss.SpreadsheetVersion
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.PictureData
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.AreaReference
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import site.addzero.studio.contract.report.SpreadsheetCellSpec
import site.addzero.studio.contract.report.SpreadsheetCellType
import site.addzero.studio.contract.report.SpreadsheetImageSpec
import site.addzero.studio.contract.report.SpreadsheetPrintSpec
import site.addzero.studio.contract.report.SpreadsheetRangeSpec
import site.addzero.studio.contract.report.SpreadsheetSheetSpec
import site.addzero.studio.contract.report.SpreadsheetSourceSpec
import site.addzero.studio.contract.report.SpreadsheetStyleSpec
import site.addzero.studio.contract.report.SpreadsheetTemplateDocument
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

internal fun readSpreadsheetTemplate(
    bytes: ByteArray,
    fileName: String,
    name: String,
): SpreadsheetTemplateDocument {
    require(bytes.isNotEmpty()) { "Excel 文件不能为空" }
    require(bytes.size <= MAX_TEMPLATE_FILE_BYTES) { "Excel 文件不能超过 20 MB" }
    val mediaType = spreadsheetMediaType(fileName)
    val packageFeatures = readPackageFeatures(bytes)
    return openWorkbook(bytes).use { workbook ->
        require(workbook.numberOfSheets in 1..MAX_SHEET_COUNT) { "Excel 工作表数量必须在 1..$MAX_SHEET_COUNT" }
        val styles = (0 until workbook.numCellStyles).map { index ->
            workbook.getCellStyleAt(index).toStyleSpec(workbook)
        }
        val sheets = (0 until workbook.numberOfSheets).map { index ->
            workbook.getSheetAt(index).toSheetSpec(workbook, index)
        }
        SpreadsheetTemplateDocument(
            name = name.trim().ifEmpty { fileName.substringBeforeLast('.') },
            source = SpreadsheetSourceSpec(
                fileName = fileName,
                mediaType = mediaType,
                sha256 = bytes.sha256(),
                macroEnabled = packageFeatures.macroEnabled,
                containsExternalLinks = packageFeatures.containsExternalLinks,
            ),
            styles = styles,
            sheets = sheets,
        )
    }
}

internal fun openWorkbook(bytes: ByteArray): Workbook = try {
    ByteArrayInputStream(bytes).use(WorkbookFactory::create)
} catch (cause: Exception) {
    throw IllegalArgumentException("Excel 文件解析失败，请确认文件是有效的 .xls、.xlsx 或 .xlsm 文件", cause)
}

internal fun spreadsheetMediaType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "xlsm" -> "application/vnd.ms-excel.sheet.macroEnabled.12"
    else -> reportBadRequest("仅支持 .xls、.xlsx 或 .xlsm 文件")
}

private fun Sheet.toSheetSpec(workbook: Workbook, sheetIndex: Int): SpreadsheetSheetSpec {
    val images = readImages(sheetIndex)
    val mergedRanges = mergedRegions.map { region -> region.toRangeSpec() }
    val print = workbook.readPrintSpec(sheetIndex)
    val rowCount = maxOf(
        lastRowNum + 1,
        mergedRanges.maxOfOrNull { it.toRow + 1 } ?: 1,
        images.maxOfOrNull { it.range.toRow + 1 } ?: 1,
        print.area?.toRow?.plus(1) ?: 1,
    )
    val columnCount = maxOf(
        (0..lastRowNum).maxOfOrNull { rowIndex -> getRow(rowIndex)?.lastCellNum?.toInt()?.coerceAtLeast(0) ?: 0 } ?: 0,
        mergedRanges.maxOfOrNull { it.toColumn + 1 } ?: 1,
        images.maxOfOrNull { it.range.toColumn + 1 } ?: 1,
        print.area?.toColumn?.plus(1) ?: 1,
    )
    require(rowCount <= MAX_TEMPLATE_ROWS && columnCount <= MAX_TEMPLATE_COLUMNS) {
        "工作表 $sheetName 超过可编辑范围 ${MAX_TEMPLATE_ROWS}x$MAX_TEMPLATE_COLUMNS"
    }
    require(rowCount.toLong() * columnCount <= MAX_RENDERED_CELLS) {
        "工作表 $sheetName 的可编辑单元格范围不能超过 $MAX_RENDERED_CELLS"
    }
    val formatter = DataFormatter(Locale.ROOT)
    val cells = buildList {
        for (rowIndex in 0 until rowCount) {
            val row = getRow(rowIndex) ?: continue
            for (columnIndex in 0 until columnCount) {
                val cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK) ?: continue
                add(cell.toCellSpec(formatter))
            }
        }
    }
    return SpreadsheetSheetSpec(
        key = "sheet${sheetIndex + 1}",
        name = sheetName,
        rowCount = rowCount,
        columnCount = columnCount,
        columnWidthsPx = (0 until columnCount).associateWith { column ->
            getColumnWidthInPixels(column).roundToInt().coerceAtLeast(MIN_COLUMN_WIDTH_PX)
        },
        rowHeightsPx = (0 until rowCount).associateWith { row ->
            ((getRow(row)?.heightInPoints ?: defaultRowHeightInPoints) * POINT_TO_PIXEL).roundToInt().coerceAtLeast(1)
        },
        hiddenColumns = (0 until columnCount).filterTo(sortedSetOf(), ::isColumnHidden),
        cells = cells,
        mergedRanges = mergedRanges,
        images = images,
        print = print,
    )
}

private fun Cell.toCellSpec(formatter: DataFormatter): SpreadsheetCellSpec {
    val formula = cellType.takeIf { it == CellType.FORMULA }?.let { cellFormula }
    return SpreadsheetCellSpec(
        row = rowIndex,
        column = columnIndex,
        type = semanticType(),
        displayValue = if (formula == null) formatter.formatCellValue(this) else cachedFormulaValue(formatter),
        formula = formula,
        styleKey = cellStyle.index.toInt(),
    )
}

private fun Cell.semanticType(): SpreadsheetCellType = when (cellType) {
    CellType.BLANK, CellType._NONE -> SpreadsheetCellType.EMPTY
    CellType.STRING -> SpreadsheetCellType.TEXT
    CellType.NUMERIC -> if (DateUtil.isCellDateFormatted(this)) SpreadsheetCellType.DATE else SpreadsheetCellType.NUMBER
    CellType.BOOLEAN -> SpreadsheetCellType.BOOLEAN
    CellType.ERROR -> SpreadsheetCellType.ERROR
    CellType.FORMULA -> SpreadsheetCellType.FORMULA
}

private fun Cell.cachedFormulaValue(formatter: DataFormatter): String = when (cachedFormulaResultType) {
    CellType.STRING -> stringCellValue
    CellType.NUMERIC -> formatter.formatRawCellContents(numericCellValue, cellStyle.dataFormat.toInt(), cellStyle.dataFormatString)
    CellType.BOOLEAN -> booleanCellValue.toString()
    CellType.ERROR -> FormulaError.forInt(errorCellValue).string
    CellType.BLANK, CellType._NONE -> ""
    CellType.FORMULA -> ""
}

private fun CellStyle.toStyleSpec(workbook: Workbook): SpreadsheetStyleSpec {
    val font = workbook.getFontAt(fontIndex)
    return SpreadsheetStyleSpec(
        key = index.toInt(),
        fontFamily = font.fontName.takeIf(String::isNotBlank),
        fontSizePt = font.fontHeightInPoints.toDouble().takeIf { it > 0 },
        bold = font.bold,
        italic = font.italic,
        textColor = (font as? XSSFFont)?.xssfColor.toCssColor(),
        fillColor = (this as? XSSFCellStyle)?.fillForegroundXSSFColor.toCssColor(),
        horizontalAlignment = alignment.name,
        verticalAlignment = verticalAlignment.name,
        wrapText = wrapText,
        borderTop = borderTop.name.takeUnless { it == "NONE" },
        borderRight = borderRight.name.takeUnless { it == "NONE" },
        borderBottom = borderBottom.name.takeUnless { it == "NONE" },
        borderLeft = borderLeft.name.takeUnless { it == "NONE" },
        numberFormat = dataFormatString?.takeIf(String::isNotBlank),
    )
}

private fun org.apache.poi.xssf.usermodel.XSSFColor?.toCssColor(): String? {
    val argb = this?.argbHex ?: return null
    return "#${argb.takeLast(6)}"
}

private fun Sheet.readImages(sheetIndex: Int): List<SpreadsheetImageSpec> {
    val pictures: List<Pair<PictureData, SpreadsheetRangeSpec>> = when (this) {
        is XSSFSheet -> drawingPatriarch?.shapes.orEmpty().filterIsInstance<XSSFPicture>().mapNotNull { picture ->
            picture.pictureData?.let { data -> data to picture.clientAnchor.toRangeSpec() }
        }
        is HSSFSheet -> drawingPatriarch?.children.orEmpty().filterIsInstance<HSSFPicture>().mapNotNull { picture ->
            picture.pictureData?.let { data -> data to picture.clientAnchor.toRangeSpec() }
        }
        else -> emptyList()
    }
    return pictures.mapIndexed { index, (data, range) ->
        SpreadsheetImageSpec(
            key = "image${sheetIndex + 1}-${index + 1}",
            mediaType = data.mimeType,
            dataBase64 = Base64.getEncoder().encodeToString(data.data),
            range = range,
        )
    }
}

private fun org.apache.poi.ss.usermodel.ClientAnchor.toRangeSpec(): SpreadsheetRangeSpec = SpreadsheetRangeSpec(
    fromRow = row1.coerceAtLeast(0),
    fromColumn = col1.toInt().coerceAtLeast(0),
    toRow = row2.coerceAtLeast(row1 + 1) - 1,
    toColumn = col2.toInt().coerceAtLeast(col1.toInt() + 1) - 1,
)

private fun org.apache.poi.ss.util.CellRangeAddress.toRangeSpec(): SpreadsheetRangeSpec = SpreadsheetRangeSpec(
    fromRow = firstRow,
    fromColumn = firstColumn,
    toRow = lastRow,
    toColumn = lastColumn,
)

@Suppress("DEPRECATION")
private fun Workbook.readPrintSpec(sheetIndex: Int): SpreadsheetPrintSpec {
    val sheet = getSheetAt(sheetIndex)
    val setup = sheet.printSetup
    return SpreadsheetPrintSpec(
        area = getPrintArea(sheetIndex)?.toPrintRange(),
        landscape = setup.landscape,
        paperSize = setup.paperSize.toInt(),
        fitWidth = setup.fitWidth.toInt(),
        fitHeight = setup.fitHeight.toInt(),
        marginLeft = sheet.getMargin(Sheet.LeftMargin),
        marginRight = sheet.getMargin(Sheet.RightMargin),
        marginTop = sheet.getMargin(Sheet.TopMargin),
        marginBottom = sheet.getMargin(Sheet.BottomMargin),
    )
}

private fun String.toPrintRange(): SpreadsheetRangeSpec? = runCatching {
    val reference = AreaReference(substringBefore(','), SpreadsheetVersion.EXCEL2007).firstCell
    val last = AreaReference(substringBefore(','), SpreadsheetVersion.EXCEL2007).lastCell
    SpreadsheetRangeSpec(reference.row, reference.col.toInt(), last.row, last.col.toInt())
}.getOrNull()

private data class PackageFeatures(
    val macroEnabled: Boolean,
    val containsExternalLinks: Boolean,
)

private fun readPackageFeatures(bytes: ByteArray): PackageFeatures {
    var macroEnabled = false
    var containsExternalLinks = false
    runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                val name = entry.name
                macroEnabled = macroEnabled || name == "xl/vbaProject.bin"
                containsExternalLinks = containsExternalLinks || name.startsWith("xl/externalLinks/")
            }
        }
    }
    return PackageFeatures(macroEnabled, containsExternalLinks)
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte) }

internal const val MAX_TEMPLATE_FILE_BYTES: Int = 20 * 1024 * 1024
private const val MAX_SHEET_COUNT = 64
private const val MAX_TEMPLATE_ROWS = 1000
private const val MAX_TEMPLATE_COLUMNS = 256
private const val MAX_RENDERED_CELLS = 20_000
private const val MIN_COLUMN_WIDTH_PX = 12
private const val POINT_TO_PIXEL = 96.0 / 72.0
