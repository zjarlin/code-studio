package site.addzero.validation.compiler

import java.util.ServiceLoader

/** 可由校验规则处理的值类别。 */
enum class LsiValidationValueKind {
    TEXT,
    COLLECTION,
    TEXT_COLLECTION,
}

/** 可稳定编译为目标语言表达式的校验谓词。 */
enum class LsiValidationPredicate {
    BLANK,
    EMPTY,
    BLANK_ELEMENTS,
    MAX_LENGTH,
}

/** Studio 可编辑的校验参数类型。 */
enum class LsiValidationParameterKind {
    INTEGER,
}

/** 一条校验规则的参数定义。 */
data class LsiValidationParameterMetadata(
    val code: String,
    val name: String,
    val description: String,
    val kind: LsiValidationParameterKind,
    val required: Boolean = true,
    val minimum: Long? = null,
    val maximum: Long? = null,
)

/** Studio 和编译器共享的校验规则目录项。 */
data class LsiValidationRuleMetadata(
    val code: String,
    val name: String,
    val description: String,
    val predicate: LsiValidationPredicate,
    val supportedValueKinds: Set<LsiValidationValueKind>,
    val defaultMessage: String,
    val parameters: List<LsiValidationParameterMetadata> = emptyList(),
)

/** 字段上配置的一条校验规则。 */
data class LsiValidationRule(
    val code: String,
    val message: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

/** 供宿主或插件提供校验规则元数据。 */
fun interface ValidationRuleMetadataProvider {
    fun metadata(): Collection<LsiValidationRuleMetadata>
}

/** 已校验并按编码建立索引的规则目录。 */
class ValidationRuleMetadataCatalog private constructor(
    private val metadataByCode: Map<String, LsiValidationRuleMetadata>,
) {
    val metadata: List<LsiValidationRuleMetadata>
        get() = metadataByCode.values.sortedBy(LsiValidationRuleMetadata::code)

    fun require(code: String): LsiValidationRuleMetadata = metadataByCode[code]
        ?: error("未注册校验规则: $code")

    fun find(code: String): LsiValidationRuleMetadata? = metadataByCode[code]

    fun requireValid(rule: LsiValidationRule): LsiValidationRuleMetadata {
        val metadata = require(rule.code)
        return metadata.requireValid(rule)
    }

    companion object {
        fun load(classLoader: ClassLoader = ValidationRuleMetadataProvider::class.java.classLoader):
            ValidationRuleMetadataCatalog {
            val metadata = ServiceLoader.load(ValidationRuleMetadataProvider::class.java, classLoader)
                .flatMap { provider -> provider.metadata() }
                .toList()
            val duplicates = metadata.groupingBy(LsiValidationRuleMetadata::code)
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
            require(duplicates.isEmpty()) {
                "校验规则编码重复: ${duplicates.sorted().joinToString()}"
            }
            metadata.forEach { rule ->
                require(rule.code.matches(RULE_CODE_PATTERN)) { "校验规则编码不合法: ${rule.code}" }
                require(rule.supportedValueKinds.isNotEmpty()) { "校验规则 ${rule.code} 未声明支持的值类型" }
                val duplicateParameters = rule.parameters.groupingBy(LsiValidationParameterMetadata::code)
                    .eachCount()
                    .filterValues { count -> count > 1 }
                    .keys
                require(duplicateParameters.isEmpty()) {
                    "校验规则 ${rule.code} 参数编码重复: ${duplicateParameters.sorted().joinToString()}"
                }
                rule.parameters.forEach { parameter ->
                    require(parameter.code.matches(RULE_CODE_PATTERN)) {
                        "校验规则 ${rule.code} 参数编码不合法: ${parameter.code}"
                    }
                    require(parameter.minimum == null || parameter.maximum == null || parameter.minimum <= parameter.maximum) {
                        "校验规则 ${rule.code} 参数 ${parameter.code} 的范围不合法"
                    }
                }
            }
            return ValidationRuleMetadataCatalog(metadata.associateBy(LsiValidationRuleMetadata::code))
        }
    }
}

/** 根据目录项校验一条可执行规则。 */
fun LsiValidationRuleMetadata.requireValid(rule: LsiValidationRule): LsiValidationRuleMetadata {
    require(code == rule.code) { "校验规则编码不匹配: $code != ${rule.code}" }
    val declaredParameters = parameters.associateBy(LsiValidationParameterMetadata::code)
    val unknownParameters = rule.parameters.keys - declaredParameters.keys
    require(unknownParameters.isEmpty()) {
        "校验规则 ${rule.code} 包含未声明参数: ${unknownParameters.sorted().joinToString()}"
    }
    parameters.forEach { parameter ->
        val value = rule.parameters[parameter.code]
        require(!parameter.required || !value.isNullOrBlank()) {
            "校验规则 ${rule.code} 缺少参数 ${parameter.code}"
        }
        if (!value.isNullOrBlank()) {
            parameter.requireValid(value, rule.code)
        }
    }
    return this
}

/** 解析规则消息中的参数占位符。 */
fun LsiValidationRuleMetadata.resolveMessage(rule: LsiValidationRule): String =
    (rule.message?.takeIf(String::isNotBlank) ?: defaultMessage).let { template ->
        rule.parameters.entries.sortedBy(Map.Entry<String, String>::key).fold(template) { message, (code, value) ->
            message.replace("{$code}", value)
        }
    }

private fun LsiValidationParameterMetadata.requireValid(value: String, ruleCode: String) {
    when (kind) {
        LsiValidationParameterKind.INTEGER -> {
            val parsed = value.toLongOrNull()
                ?: throw IllegalArgumentException("校验规则 $ruleCode 参数 $code 必须是整数")
            require(minimum == null || parsed >= minimum) {
                "校验规则 $ruleCode 参数 $code 不能小于 $minimum"
            }
            require(maximum == null || parsed <= maximum) {
                "校验规则 $ruleCode 参数 $code 不能大于 $maximum"
            }
        }
    }
}

private val RULE_CODE_PATTERN = Regex("[a-z][A-Za-z0-9]*")
