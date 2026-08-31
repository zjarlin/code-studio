package site.addzero.studio.contract.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
enum class ReportDatasetSource {
    MODEL,
    OPENAPI,
}

@Serializable
enum class ReportBindingKind {
    PARAMETER,
    LITERAL,
}

@Serializable
data class ReportParameterBinding(
    val kind: ReportBindingKind,
    val parameterKey: String? = null,
    val literal: JsonElement? = null,
) {
    init {
        when (kind) {
            ReportBindingKind.PARAMETER -> {
                requireNotNull(parameterKey) {
                    "PARAMETER 绑定必须声明 parameterKey"
                }
                requireReportKey(parameterKey, "参数")
                require(literal == null) {
                    "PARAMETER 绑定不能声明字面量"
                }
            }

            ReportBindingKind.LITERAL -> {
                require(parameterKey == null) {
                    "LITERAL 绑定不能声明 parameterKey"
                }
                require(literal is JsonPrimitive) {
                    "LITERAL 绑定只允许 JSON 字符串、数字、布尔值或 null"
                }
            }
        }
    }
}

@Serializable
data class ReportDatasetField(
    val key: String,
    val label: String,
    val pointer: String,
) {
    init {
        requireReportKey(key, "数据集字段")
        require(label.isNotBlank()) {
            "数据集字段名称不能为空: $key"
        }
        requireJsonPointer(pointer, "数据集字段")
    }
}

/** 只表达可由宿主解析的同源 GET 数据源，不携带 URL、请求体或请求头。 */
@Serializable
data class ReportDatasetSpec(
    val key: String,
    val name: String,
    val source: ReportDatasetSource,
    val modelCode: String? = null,
    val operationId: String? = null,
    val parameterBindings: Map<String, ReportParameterBinding> = emptyMap(),
    val fields: List<ReportDatasetField> = emptyList(),
) {
    init {
        requireReportKey(key, "数据集")
        require(name.isNotBlank()) {
            "数据集名称不能为空: $key"
        }
        when (source) {
            ReportDatasetSource.MODEL -> {
                require(!modelCode.isNullOrBlank()) {
                    "MODEL 数据集必须声明 modelCode"
                }
                require(operationId == null) {
                    "MODEL 数据集不能声明 operationId"
                }
            }

            ReportDatasetSource.OPENAPI -> {
                require(modelCode == null) {
                    "OPENAPI 数据集不能声明 modelCode"
                }
                require(!operationId.isNullOrBlank() && OPERATION_ID.matches(operationId)) {
                    "OPENAPI 数据集必须声明稳定 operationId"
                }
            }
        }
        require(parameterBindings.keys.none(String::isBlank)) {
            "数据集参数名不能为空: $key"
        }
        requireUniqueKeys(fields.map(ReportDatasetField::key), "数据集字段")
    }
}

private val OPERATION_ID = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
