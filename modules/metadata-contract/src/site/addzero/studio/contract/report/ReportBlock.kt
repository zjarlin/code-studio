@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package site.addzero.studio.contract.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class ReportBlockKind {
    TEXT,
    METRIC,
    TABLE,
    CHART,
    IMAGE,
}

@Serializable
enum class ReportMetricAggregate {
    FIRST,
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
}

@Serializable
enum class ReportChartKind {
    BAR,
    LINE,
    PIE,
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface ReportBlockSpec {
    val key: String
    val kind: ReportBlockKind
    val columnSpan: Int
}

@Serializable
@SerialName("TEXT")
data class ReportTextBlock(
    override val key: String,
    val text: String,
    override val columnSpan: Int = REPORT_GRID_COLUMNS,
    @Transient override val kind: ReportBlockKind = ReportBlockKind.TEXT,
) : ReportBlockSpec {
    init {
        require(kind == ReportBlockKind.TEXT)
        requireReportBlock()
    }
}

@Serializable
@SerialName("METRIC")
data class ReportMetricBlock(
    override val key: String,
    val datasetKey: String,
    val label: String,
    val valuePointer: String,
    val aggregate: ReportMetricAggregate = ReportMetricAggregate.FIRST,
    override val columnSpan: Int = REPORT_GRID_COLUMNS,
    @Transient override val kind: ReportBlockKind = ReportBlockKind.METRIC,
) : ReportBlockSpec {
    init {
        require(kind == ReportBlockKind.METRIC)
        requireReportBlock()
        requireReportDataBlock(datasetKey)
        require(label.isNotBlank()) {
            "指标标签不能为空: $key"
        }
        requireJsonPointer(valuePointer, "指标值")
    }
}

@Serializable
@SerialName("TABLE")
data class ReportTableBlock(
    override val key: String,
    val datasetKey: String,
    val columns: List<ReportTableColumn> = emptyList(),
    val rowLimit: Int = 20,
    override val columnSpan: Int = REPORT_GRID_COLUMNS,
    @Transient override val kind: ReportBlockKind = ReportBlockKind.TABLE,
) : ReportBlockSpec {
    init {
        require(kind == ReportBlockKind.TABLE)
        requireReportBlock()
        requireReportDataBlock(datasetKey)
        require(columnSpan == REPORT_GRID_COLUMNS) {
            "表格块必须独占 $REPORT_GRID_COLUMNS 列: $key"
        }
        require(rowLimit in 1..MAX_REPORT_ROW_COUNT) {
            "表格行数必须在 1..$MAX_REPORT_ROW_COUNT 之间: $rowLimit"
        }
        requireUniqueKeys(columns.map(ReportTableColumn::key), "表格列")
    }
}

@Serializable
data class ReportTableColumn(
    val key: String,
    val label: String,
    val valuePointer: String,
) {
    init {
        requireReportKey(key, "表格列")
        require(label.isNotBlank()) {
            "表格列标签不能为空: $key"
        }
        requireJsonPointer(valuePointer, "表格列")
    }
}

@Serializable
@SerialName("CHART")
data class ReportChartBlock(
    override val key: String,
    val datasetKey: String,
    val chartKind: ReportChartKind,
    val categoryPointer: String,
    val valuePointer: String,
    override val columnSpan: Int = REPORT_GRID_COLUMNS,
    @Transient override val kind: ReportBlockKind = ReportBlockKind.CHART,
) : ReportBlockSpec {
    init {
        require(kind == ReportBlockKind.CHART)
        requireReportBlock()
        requireReportDataBlock(datasetKey)
        requireJsonPointer(categoryPointer, "图表分类")
        requireJsonPointer(valuePointer, "图表值")
    }
}

@Serializable
@SerialName("IMAGE")
data class ReportImageBlock(
    override val key: String,
    val datasetKey: String,
    val sourcePointer: String,
    val alt: String,
    override val columnSpan: Int = REPORT_GRID_COLUMNS,
    @Transient override val kind: ReportBlockKind = ReportBlockKind.IMAGE,
) : ReportBlockSpec {
    init {
        require(kind == ReportBlockKind.IMAGE)
        requireReportBlock()
        requireReportDataBlock(datasetKey)
        requireJsonPointer(sourcePointer, "图片地址")
    }
}

private fun ReportBlockSpec.requireReportBlock() {
    requireReportKey(key, "报表块")
    require(columnSpan in 1..REPORT_GRID_COLUMNS) {
        "报表块列跨度必须在 1..$REPORT_GRID_COLUMNS 之间: $columnSpan"
    }
}

private fun requireReportDataBlock(datasetKey: String) {
    requireReportKey(datasetKey, "数据集")
}

internal fun ReportBlockSpec.datasetReference(): String? = when (this) {
    is ReportTextBlock -> null
    is ReportMetricBlock -> datasetKey
    is ReportTableBlock -> datasetKey
    is ReportChartBlock -> datasetKey
    is ReportImageBlock -> datasetKey
}
