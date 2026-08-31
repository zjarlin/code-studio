package site.addzero.studio.report.internal

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import site.addzero.studio.contract.report.ReportCreateCommand
import site.addzero.studio.contract.report.ReportDocument
import site.addzero.studio.contract.report.ReportPublishCommand
import site.addzero.studio.contract.report.ReportUpdateCommand

internal object ReportJson {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun decodeCreate(content: String): ReportCreateCommand = json.decodeFromString(content)

    fun decodeUpdate(content: String): ReportUpdateCommand = json.decodeFromString(content)

    fun decodePublish(content: String): ReportPublishCommand = json.decodeFromString(content)

    fun decodeDocument(content: String): ReportDocument = json.decodeFromString(content)

    fun encodeDocument(document: ReportDocument): String = json.encodeToString(document.normalized())
}

private fun ReportDocument.normalized(): ReportDocument = copy(
    datasets = datasets.map { dataset ->
        dataset.copy(parameterBindings = dataset.parameterBindings.toSortedMap())
    },
)
