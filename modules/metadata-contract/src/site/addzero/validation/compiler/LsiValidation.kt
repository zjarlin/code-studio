package site.addzero.validation.compiler

import kotlinx.serialization.Serializable

@Serializable
enum class LsiValidationValueKind { TEXT, COLLECTION, TEXT_COLLECTION }

@Serializable
enum class LsiValidationPredicate { BLANK, EMPTY, BLANK_ELEMENTS, MAX_LENGTH }

@Serializable
enum class LsiValidationParameterKind { INTEGER }

@Serializable
data class LsiValidationParameterMetadata(
    val code: String,
    val name: String,
    val description: String,
    val kind: LsiValidationParameterKind,
    val required: Boolean = true,
    val minimum: Long? = null,
    val maximum: Long? = null,
)

@Serializable
data class LsiValidationRuleMetadata(
    val code: String,
    val name: String,
    val description: String,
    val predicate: LsiValidationPredicate,
    val supportedValueKinds: Set<LsiValidationValueKind>,
    val defaultMessage: String,
    val parameters: List<LsiValidationParameterMetadata> = emptyList(),
)

@Serializable
data class LsiValidationRule(
    val code: String,
    val message: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

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
            require(minimum == null || parsed >= minimum) { "校验规则 $ruleCode 参数 $code 不能小于 $minimum" }
            require(maximum == null || parsed <= maximum) { "校验规则 $ruleCode 参数 $code 不能大于 $maximum" }
        }
    }
}
