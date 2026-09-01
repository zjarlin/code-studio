package site.addzero.studio.report.internal

import site.addzero.studio.contract.report.ReportBindingKind
import site.addzero.studio.contract.report.ReportBlockSpec
import site.addzero.studio.contract.report.ReportChartBlock
import site.addzero.studio.contract.report.ReportDocument
import site.addzero.studio.contract.report.ReportImageBlock
import site.addzero.studio.contract.report.ReportMetricBlock
import site.addzero.studio.contract.report.ReportTableBlock
import site.addzero.studio.contract.report.ReportTextBlock

internal object ReportValidator {
    fun requirePublishable(document: ReportDocument) {
        require(document.rows.isNotEmpty()) {
            "发布报表至少需要一行"
        }
        require(document.rows.all { row -> row.blocks.isNotEmpty() }) {
            "发布报表的每一行都必须包含报表块"
        }
        val blocks = document.rows.flatMap { row -> row.blocks }
        requireReferencedDatasets(document, blocks)
        requireReferencedParameters(document)
        blocks.filterIsInstance<ReportTableBlock>().forEach { block ->
            require(block.columns.isNotEmpty()) {
                "表格块必须至少包含一列: ${block.key}"
            }
        }
        blocks.filterIsInstance<ReportTextBlock>().forEach { block ->
            require(block.text.isNotBlank()) {
                "文本块内容不能为空: ${block.key}"
            }
        }
        blocks.filterIsInstance<ReportImageBlock>().forEach { block ->
            require(block.alt.isNotBlank()) {
                "图片块替代文本不能为空: ${block.key}"
            }
        }
    }

    private fun requireReferencedDatasets(document: ReportDocument, blocks: List<ReportBlockSpec>) {
        val referenced = blocks.mapNotNull(ReportBlockSpec::datasetReference).toSet()
        val unreferenced = document.datasets.map { dataset -> dataset.key }.filterNot(referenced::contains)
        require(unreferenced.isEmpty()) {
            "报表包含未引用的数据集: ${unreferenced.sorted()}"
        }
    }

    private fun requireReferencedParameters(document: ReportDocument) {
        val referenced = document.datasets
            .flatMap { dataset -> dataset.parameterBindings.values }
            .filter { binding -> binding.kind == ReportBindingKind.PARAMETER }
            .mapNotNull { binding -> binding.parameterKey }
            .toSet()
        val unreferenced = document.parameters.map { parameter -> parameter.key }.filterNot(referenced::contains)
        require(unreferenced.isEmpty()) {
            "报表包含未引用的参数: ${unreferenced.sorted()}"
        }
    }
}

private val ReportBlockSpec.datasetReference: String?
    get() = when (this) {
        is ReportTextBlock -> null
        is ReportMetricBlock -> datasetKey
        is ReportTableBlock -> datasetKey
        is ReportChartBlock -> datasetKey
        is ReportImageBlock -> datasetKey
    }
