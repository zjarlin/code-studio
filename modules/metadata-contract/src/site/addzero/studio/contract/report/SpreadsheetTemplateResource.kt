package site.addzero.studio.contract.report

import kotlinx.serialization.Serializable

@Serializable
data class SpreadsheetTemplateUpdateCommand(
    val expectedRevision: Long,
    val draft: SpreadsheetTemplateDraftSpec,
) {
    init {
        require(expectedRevision > 0) { "预期修订号必须大于 0" }
    }
}

@Serializable
data class SpreadsheetTemplateFillCommand(
    val expectedRevision: Long,
    val values: Map<String, String> = emptyMap(),
    val ledgers: Map<String, List<Map<String, String>>> = emptyMap(),
) {
    init {
        require(expectedRevision > 0) { "预期修订号必须大于 0" }
    }
}

@Serializable
data class SpreadsheetTemplateDraftSpec(
    val name: String,
    val description: String? = null,
    val variables: List<SpreadsheetVariableSpec> = emptyList(),
    val bindings: List<SpreadsheetBindingSpec> = emptyList(),
    val ledgers: List<SpreadsheetLedgerSpec> = emptyList(),
    val edits: List<SpreadsheetCellEditSpec> = emptyList(),
) {
    fun applyTo(source: SpreadsheetTemplateDocument): SpreadsheetTemplateDocument = source.copy(
        name = name,
        description = description,
        variables = variables,
        bindings = bindings,
        ledgers = ledgers,
        edits = edits,
    )

}

@Serializable
data class SpreadsheetTemplateListItemView(
    val templateKey: String,
    val revision: Long,
    val name: String,
    val fileName: String,
    val macroEnabled: Boolean,
)

@Serializable
data class SpreadsheetTemplateView(
    val templateKey: String,
    val revision: Long,
    val document: SpreadsheetTemplateDocument,
)

@Serializable
data class SpreadsheetTemplateListPage(
    val rows: List<SpreadsheetTemplateListItemView> = emptyList(),
    val totalRowCount: Long = 0,
    val totalPageCount: Long = 0,
)
