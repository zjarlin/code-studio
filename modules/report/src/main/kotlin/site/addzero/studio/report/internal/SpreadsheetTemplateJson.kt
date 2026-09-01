package site.addzero.studio.report.internal

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import site.addzero.studio.contract.report.SpreadsheetSheetSpec
import site.addzero.studio.contract.report.SpreadsheetTemplateDocument
import site.addzero.studio.contract.report.SpreadsheetTemplateFillCommand
import site.addzero.studio.contract.report.SpreadsheetTemplateUpdateCommand

internal object SpreadsheetTemplateJson {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun decodeUpdate(content: String): SpreadsheetTemplateUpdateCommand = json.decodeFromString(content)

    fun decodeFill(content: String): SpreadsheetTemplateFillCommand = json.decodeFromString(content)

    fun decodeDocument(content: String): SpreadsheetTemplateDocument = json.decodeFromString(content)

    fun encodeDocument(document: SpreadsheetTemplateDocument): String = json.encodeToString(document.normalized())
}

private fun SpreadsheetTemplateDocument.normalized(): SpreadsheetTemplateDocument = copy(
    styles = styles.sortedBy { it.key },
    sheets = sheets.map(SpreadsheetSheetSpec::normalized),
    bindings = bindings.sortedBy { it.key },
    edits = edits.sortedWith(compareBy({ it.sheetKey }, { it.row }, { it.column })),
)

private fun SpreadsheetSheetSpec.normalized(): SpreadsheetSheetSpec = copy(
    columnWidthsPx = columnWidthsPx.toSortedMap(),
    rowHeightsPx = rowHeightsPx.toSortedMap(),
    hiddenColumns = hiddenColumns.toSortedSet(),
    cells = cells.sortedWith(compareBy({ it.row }, { it.column })),
    mergedRanges = mergedRanges.sortedWith(compareBy({ it.fromRow }, { it.fromColumn })),
    images = images.sortedBy { it.key },
)

