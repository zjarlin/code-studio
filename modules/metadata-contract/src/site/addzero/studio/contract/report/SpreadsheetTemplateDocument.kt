package site.addzero.studio.contract.report

import kotlinx.serialization.Serializable

const val SPREADSHEET_TEMPLATE_VERSION: Int = 1

@Serializable
data class SpreadsheetTemplateDocument(
    val version: Int = SPREADSHEET_TEMPLATE_VERSION,
    val name: String,
    val description: String? = null,
    val source: SpreadsheetSourceSpec,
    val styles: List<SpreadsheetStyleSpec> = emptyList(),
    val sheets: List<SpreadsheetSheetSpec>,
    val variables: List<SpreadsheetVariableSpec> = emptyList(),
    val bindings: List<SpreadsheetBindingSpec> = emptyList(),
    val ledgers: List<SpreadsheetLedgerSpec> = emptyList(),
    val edits: List<SpreadsheetCellEditSpec> = emptyList(),
) {
    init {
        require(version == SPREADSHEET_TEMPLATE_VERSION) { "不支持的电子表格模板版本: $version" }
        require(name.isNotBlank()) { "电子表格模板名称不能为空" }
        require(description == null || description.isNotBlank()) { "电子表格模板说明不能是空白字符串" }
        require(sheets.isNotEmpty()) { "电子表格模板至少需要一个工作表" }
        require(variables.size <= MAX_TEMPLATE_VARIABLES) { "模板变量不能超过 $MAX_TEMPLATE_VARIABLES 个" }
        require(bindings.size <= MAX_TEMPLATE_BINDINGS) { "模板绑定不能超过 $MAX_TEMPLATE_BINDINGS 个" }
        require(ledgers.size <= MAX_TEMPLATE_LEDGERS) { "模板台账不能超过 $MAX_TEMPLATE_LEDGERS 个" }
        require(edits.size <= MAX_TEMPLATE_EDITS) { "单元格修改不能超过 $MAX_TEMPLATE_EDITS 个" }
        requireUniqueKeys(styles.map { it.key.toString() }, "单元格样式")
        requireUniqueKeys(sheets.map(SpreadsheetSheetSpec::key), "工作表")
        requireUniqueKeys(variables.map(SpreadsheetVariableSpec::key), "模板变量")
        requireUniqueKeys(bindings.map(SpreadsheetBindingSpec::key), "模板绑定")
        requireUniqueKeys(ledgers.map(SpreadsheetLedgerSpec::key), "台账")
        requireReferences()
    }

    private fun requireReferences() {
        val styleKeys = styles.map(SpreadsheetStyleSpec::key).toSet()
        val sheetKeys = sheets.map(SpreadsheetSheetSpec::key).toSet()
        val sheetsByKey = sheets.associateBy(SpreadsheetSheetSpec::key)
        val variableKeys = variables.map(SpreadsheetVariableSpec::key).toSet()
        require(sheets.flatMap(SpreadsheetSheetSpec::cells).map(SpreadsheetCellSpec::styleKey).all(styleKeys::contains)) {
            "单元格引用了不存在的样式"
        }
        require(bindings.map(SpreadsheetBindingSpec::sheetKey).all(sheetKeys::contains)) {
            "模板绑定引用了不存在的工作表"
        }
        require(bindings.all { binding ->
            val sheet = sheetsByKey[binding.sheetKey]
            sheet != null && binding.range.toRow < sheet.rowCount && binding.range.toColumn < sheet.columnCount
        }) {
            "模板绑定超出工作表范围"
        }
        require(bindings.map(SpreadsheetBindingSpec::variableKey).all(variableKeys::contains)) {
            "模板绑定引用了不存在的变量"
        }
        val variableTypes = variables.associate { variable -> variable.key to variable.type }
        require(bindings.all { binding ->
            val imageVariable = variableTypes[binding.variableKey] == SpreadsheetVariableType.IMAGE
            imageVariable == (binding.target == SpreadsheetBindingTarget.IMAGE_ANCHOR)
        }) {
            "图片变量必须绑定 IMAGE_ANCHOR，其他变量不能绑定图片锚点"
        }
        require(ledgers.map(SpreadsheetLedgerSpec::sheetKey).all(sheetKeys::contains)) {
            "台账引用了不存在的工作表"
        }
        require(ledgers.all { ledger ->
            val sheet = sheetsByKey[ledger.sheetKey]
            sheet != null &&
                ledger.firstRow.toLong() + ledger.maxRows <= MAX_WORKBOOK_ROWS &&
                ledger.fields.all { field -> field.column < sheet.columnCount }
        }) {
            "台账区域超出工作表范围"
        }
        require(edits.map(SpreadsheetCellEditSpec::sheetKey).all(sheetKeys::contains)) {
            "单元格修改引用了不存在的工作表"
        }
        require(edits.all { edit ->
            val sheet = sheetsByKey[edit.sheetKey]
            sheet != null && edit.row < sheet.rowCount && edit.column < sheet.columnCount
        }) {
            "单元格修改超出工作表范围"
        }
        val editTargets = edits.map { edit -> Triple(edit.sheetKey, edit.row, edit.column) }
        require(editTargets.distinct().size == editTargets.size) { "单元格修改目标不能重复" }
    }
}

@Serializable
data class SpreadsheetSourceSpec(
    val fileName: String,
    val mediaType: String,
    val sha256: String,
    val macroEnabled: Boolean = false,
    val containsExternalLinks: Boolean = false,
) {
    init {
        require(fileName.isNotBlank()) { "源文件名不能为空" }
        require(mediaType.isNotBlank()) { "源文件媒体类型不能为空" }
        require(SHA256.matches(sha256)) { "源文件 sha256 不合法" }
    }
}

@Serializable
data class SpreadsheetSheetSpec(
    val key: String,
    val name: String,
    val rowCount: Int,
    val columnCount: Int,
    val columnWidthsPx: Map<Int, Int> = emptyMap(),
    val rowHeightsPx: Map<Int, Int> = emptyMap(),
    val hiddenColumns: Set<Int> = emptySet(),
    val cells: List<SpreadsheetCellSpec> = emptyList(),
    val mergedRanges: List<SpreadsheetRangeSpec> = emptyList(),
    val images: List<SpreadsheetImageSpec> = emptyList(),
    val print: SpreadsheetPrintSpec = SpreadsheetPrintSpec(),
) {
    init {
        requireReportKey(key, "工作表")
        require(name.isNotBlank()) { "工作表名称不能为空: $key" }
        require(rowCount > 0 && columnCount > 0) { "工作表范围不合法: $key" }
        require(columnWidthsPx.all { (column, width) -> column >= 0 && width > 0 }) { "工作表列宽不合法: $key" }
        require(rowHeightsPx.all { (row, height) -> row >= 0 && height > 0 }) { "工作表行高不合法: $key" }
        require(hiddenColumns.all { it >= 0 }) { "工作表隐藏列不合法: $key" }
        require(cells.all { it.row < rowCount && it.column < columnCount }) { "单元格超出工作表范围: $key" }
        require(mergedRanges.all { it.toRow < rowCount && it.toColumn < columnCount }) { "合并区域超出工作表范围: $key" }
        require(images.all { it.range.toRow < rowCount && it.range.toColumn < columnCount }) { "图片超出工作表范围: $key" }
    }
}

@Serializable
enum class SpreadsheetCellType {
    EMPTY,
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    ERROR,
    FORMULA,
}

@Serializable
data class SpreadsheetCellSpec(
    val row: Int,
    val column: Int,
    val type: SpreadsheetCellType,
    val displayValue: String = "",
    val formula: String? = null,
    val styleKey: Int,
) {
    init {
        require(row >= 0 && column >= 0) { "单元格坐标不能为负数" }
        require((type == SpreadsheetCellType.FORMULA) == (formula != null)) { "只有公式单元格必须声明 formula" }
    }
}

@Serializable
data class SpreadsheetRangeSpec(
    val fromRow: Int,
    val fromColumn: Int,
    val toRow: Int = fromRow,
    val toColumn: Int = fromColumn,
) {
    init {
        require(fromRow >= 0 && fromColumn >= 0) { "区域起点不能为负数" }
        require(toRow >= fromRow && toColumn >= fromColumn) { "区域终点不能早于起点" }
    }
}

@Serializable
data class SpreadsheetImageSpec(
    val key: String,
    val mediaType: String,
    val dataBase64: String,
    val range: SpreadsheetRangeSpec,
) {
    init {
        requireReportKey(key, "图片")
        require(mediaType.startsWith("image/")) { "图片媒体类型不合法: $mediaType" }
        require(dataBase64.isNotBlank()) { "图片内容不能为空: $key" }
    }
}

@Serializable
data class SpreadsheetPrintSpec(
    val area: SpreadsheetRangeSpec? = null,
    val landscape: Boolean = false,
    val paperSize: Int = 0,
    val fitWidth: Int = 0,
    val fitHeight: Int = 0,
    val marginLeft: Double = 0.0,
    val marginRight: Double = 0.0,
    val marginTop: Double = 0.0,
    val marginBottom: Double = 0.0,
)

@Serializable
data class SpreadsheetStyleSpec(
    val key: Int,
    val fontFamily: String? = null,
    val fontSizePt: Double? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textColor: String? = null,
    val fillColor: String? = null,
    val horizontalAlignment: String? = null,
    val verticalAlignment: String? = null,
    val wrapText: Boolean = false,
    val borderTop: String? = null,
    val borderRight: String? = null,
    val borderBottom: String? = null,
    val borderLeft: String? = null,
    val numberFormat: String? = null,
) {
    init {
        require(key >= 0) { "样式 key 不能为负数" }
        require(fontSizePt == null || fontSizePt > 0) { "字体大小必须大于 0" }
        require(textColor == null || COLOR.matches(textColor)) { "文字颜色不合法: $textColor" }
        require(fillColor == null || COLOR.matches(fillColor)) { "填充颜色不合法: $fillColor" }
    }
}

@Serializable
enum class SpreadsheetVariableType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    IMAGE,
}

@Serializable
data class SpreadsheetVariableSpec(
    val key: String,
    val label: String,
    val type: SpreadsheetVariableType = SpreadsheetVariableType.TEXT,
    val required: Boolean = false,
    val defaultValue: String? = null,
) {
    init {
        requireReportKey(key, "模板变量")
        require(label.isNotBlank()) { "模板变量名称不能为空: $key" }
        require(type != SpreadsheetVariableType.IMAGE || defaultValue == null) { "图片变量不能声明默认值: $key" }
    }
}

@Serializable
enum class SpreadsheetBindingTarget {
    CELL,
    MERGED_RANGE,
    IMAGE_ANCHOR,
}

@Serializable
data class SpreadsheetBindingSpec(
    val key: String,
    val variableKey: String,
    val sheetKey: String,
    val target: SpreadsheetBindingTarget,
    val range: SpreadsheetRangeSpec,
) {
    init {
        requireReportKey(key, "模板绑定")
        requireReportKey(variableKey, "模板变量")
        requireReportKey(sheetKey, "工作表")
    }
}

@Serializable
data class SpreadsheetLedgerSpec(
    val key: String,
    val name: String,
    val sheetKey: String,
    val firstRow: Int,
    val maxRows: Int = 200,
    val fields: List<SpreadsheetLedgerFieldSpec> = emptyList(),
) {
    init {
        requireReportKey(key, "台账")
        require(name.isNotBlank()) { "台账名称不能为空: $key" }
        requireReportKey(sheetKey, "工作表")
        require(firstRow >= 0) { "台账起始行不能为负数: $key" }
        require(maxRows in 1..1000) { "台账最大行数必须在 1..1000: $key" }
        requireUniqueKeys(fields.map(SpreadsheetLedgerFieldSpec::key), "台账字段")
        require(fields.map(SpreadsheetLedgerFieldSpec::column).distinct().size == fields.size) {
            "台账字段列不能重复: $key"
        }
    }
}

@Serializable
data class SpreadsheetLedgerFieldSpec(
    val key: String,
    val label: String,
    val type: SpreadsheetVariableType = SpreadsheetVariableType.TEXT,
    val column: Int,
    val required: Boolean = false,
) {
    init {
        requireReportKey(key, "台账字段")
        require(label.isNotBlank()) { "台账字段名称不能为空: $key" }
        require(column >= 0) { "台账字段列不能为负数: $key" }
    }
}

@Serializable
data class SpreadsheetCellEditSpec(
    val sheetKey: String,
    val row: Int,
    val column: Int,
    val value: String,
) {
    init {
        requireReportKey(sheetKey, "工作表")
        require(row >= 0 && column >= 0) { "单元格修改坐标不能为负数" }
    }
}

private val SHA256 = Regex("[a-f0-9]{64}")
private val COLOR = Regex("#[A-Fa-f0-9]{6}(?:[A-Fa-f0-9]{2})?")
private const val MAX_TEMPLATE_VARIABLES = 200
private const val MAX_TEMPLATE_BINDINGS = 2_000
private const val MAX_TEMPLATE_LEDGERS = 100
private const val MAX_TEMPLATE_EDITS = 20_000
private const val MAX_WORKBOOK_ROWS = 1_048_576L
