package site.addzero.studio.report.internal

import site.addzero.studio.contract.report.ReportDocument

internal object ReportCompiler {
    fun compile(document: ReportDocument): ReportDocument {
        try {
            ReportValidator.requirePublishable(document)
        } catch (cause: IllegalArgumentException) {
            reportUnprocessable(cause.message ?: "报表草稿不能发布")
        }
        return document
    }
}
