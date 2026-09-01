package site.addzero.studio.report.internal

import site.addzero.studio.contract.report.SpreadsheetTemplateDocument
import site.addzero.studio.contract.report.SpreadsheetTemplateFillCommand

internal object SpreadsheetTemplateCodec {
    fun read(bytes: ByteArray, fileName: String, name: String): SpreadsheetTemplateDocument =
        readSpreadsheetTemplate(bytes, fileName, name)

    fun fill(
        bytes: ByteArray,
        document: SpreadsheetTemplateDocument,
        command: SpreadsheetTemplateFillCommand,
    ): ByteArray = fillSpreadsheetTemplate(bytes, document, command)
}

