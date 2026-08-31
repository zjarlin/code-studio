package site.addzero.studio.contract.report

import kotlinx.serialization.Serializable

const val REPORT_DOCUMENT_VERSION: Int = 1
const val REPORT_GRID_COLUMNS: Int = 12
const val MAX_REPORT_ROW_COUNT: Int = 200

@Serializable
enum class ReportPageOrientation {
    PORTRAIT,
    LANDSCAPE,
}

@Serializable
data class ReportPageSpec(
    val orientation: ReportPageOrientation = ReportPageOrientation.PORTRAIT,
    val marginMm: Int = 12,
) {
    init {
        require(marginMm in REPORT_MARGIN_PRESETS) {
            "A4 页边距只能是 8、12 或 20 mm: $marginMm"
        }
    }
}

@Serializable
enum class ReportParameterType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    ENUM,
}

@Serializable
data class ReportParameterOption(
    val value: String,
    val label: String,
) {
    init {
        require(value.isNotBlank()) {
            "枚举参数值不能为空"
        }
        require(label.isNotBlank()) {
            "枚举参数名称不能为空: $value"
        }
    }
}

@Serializable
data class ReportParameter(
    val key: String,
    val label: String,
    val type: ReportParameterType,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<ReportParameterOption> = emptyList(),
) {
    init {
        requireReportKey(key, "参数")
        require(label.isNotBlank()) {
            "报表参数标签不能为空: $key"
        }
        requireUniqueKeys(options.map(ReportParameterOption::value), "枚举参数值")
        require((type == ReportParameterType.ENUM) == options.isNotEmpty()) {
            "只有 ENUM 参数必须且可以声明 options: $key"
        }
        require(defaultValue == null || options.isEmpty() || options.any { option -> option.value == defaultValue }) {
            "枚举参数默认值不在 options 中: $key"
        }
    }
}

@Serializable
data class ReportDocument(
    val version: Int = REPORT_DOCUMENT_VERSION,
    val name: String,
    val description: String? = null,
    val page: ReportPageSpec = ReportPageSpec(),
    val parameters: List<ReportParameter> = emptyList(),
    val datasets: List<ReportDatasetSpec> = emptyList(),
    val rows: List<ReportRowSpec> = emptyList(),
) {
    init {
        require(version == REPORT_DOCUMENT_VERSION) {
            "不支持的报表文档版本: $version"
        }
        require(name.isNotBlank()) {
            "报表名称不能为空"
        }
        require(description == null || description.isNotBlank()) {
            "报表说明不能是空白字符串"
        }
        requireUniqueKeys(parameters.map(ReportParameter::key), "参数")
        requireUniqueKeys(datasets.map(ReportDatasetSpec::key), "数据集")
        requireUniqueKeys(rows.map(ReportRowSpec::key), "网格行")
        requireUniqueKeys(rows.flatMap(ReportRowSpec::blocks).map(ReportBlockSpec::key), "报表块")
        requireParameterReferences()
        requireDatasetReferences()
    }

    private fun requireParameterReferences() {
        val parameterKeys = parameters.map(ReportParameter::key).toSet()
        val referencedKeys = datasets
            .flatMap { dataset -> dataset.parameterBindings.values }
            .filter { binding -> binding.kind == ReportBindingKind.PARAMETER }
            .mapNotNull(ReportParameterBinding::parameterKey)
        require(referencedKeys.all(parameterKeys::contains)) {
            "报表数据集引用了不存在的参数: ${referencedKeys.filterNot(parameterKeys::contains).distinct().sorted()}"
        }
    }

    private fun requireDatasetReferences() {
        val datasetKeys = datasets.map(ReportDatasetSpec::key).toSet()
        val referencedKeys = rows.flatMap(ReportRowSpec::blocks).mapNotNull(ReportBlockSpec::datasetReference)
        require(referencedKeys.all(datasetKeys::contains)) {
            "报表块引用了不存在的数据集: ${referencedKeys.filterNot(datasetKeys::contains).distinct().sorted()}"
        }
    }
}

@Serializable
data class ReportRowSpec(
    val key: String,
    val blocks: List<ReportBlockSpec> = emptyList(),
) {
    init {
        requireReportKey(key, "网格行")
        requireUniqueKeys(blocks.map(ReportBlockSpec::key), "报表块")
        require(blocks.sumOf(ReportBlockSpec::columnSpan) <= REPORT_GRID_COLUMNS) {
            "网格行 $key 的列跨度之和不能超过 $REPORT_GRID_COLUMNS"
        }
    }
}

internal fun requireReportKey(value: String, role: String) {
    require(REPORT_KEY.matches(value)) {
        "$role key 必须是稳定标识: $value"
    }
}

internal fun requireUniqueKeys(values: List<String>, role: String) {
    require(values.distinct().size == values.size) {
        "$role key 不能重复"
    }
}

internal fun requireJsonPointer(value: String, role: String) {
    require(JSON_POINTER.matches(value)) {
        "$role 必须使用 RFC 6901 JSON Pointer: $value"
    }
}

private val REPORT_MARGIN_PRESETS = setOf(8, 12, 20)
private val REPORT_KEY = Regex("[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*")
private val JSON_POINTER = Regex("(?:/(?:[^~/]|~[01])*)*")
