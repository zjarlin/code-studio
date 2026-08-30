package site.addzero.validation.compiler

import java.util.ServiceLoader

fun interface ValidationRuleMetadataProvider {
    fun metadata(): Collection<LsiValidationRuleMetadata>
}

class ValidationRuleMetadataCatalog private constructor(
    private val metadataByCode: Map<String, LsiValidationRuleMetadata>,
) {
    val metadata: List<LsiValidationRuleMetadata>
        get() = metadataByCode.values.sortedBy(LsiValidationRuleMetadata::code)

    fun require(code: String): LsiValidationRuleMetadata = metadataByCode[code]
        ?: error("未注册校验规则: $code")

    fun find(code: String): LsiValidationRuleMetadata? = metadataByCode[code]

    fun requireValid(rule: LsiValidationRule): LsiValidationRuleMetadata = require(rule.code).requireValid(rule)

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
            require(duplicates.isEmpty()) { "校验规则编码重复: ${duplicates.sorted().joinToString()}" }
            metadata.forEach(LsiValidationRuleMetadata::requireValidMetadata)
            return ValidationRuleMetadataCatalog(metadata.associateBy(LsiValidationRuleMetadata::code))
        }
    }
}

private fun LsiValidationRuleMetadata.requireValidMetadata() {
    require(code.matches(RULE_CODE_PATTERN)) { "校验规则编码不合法: $code" }
    require(supportedValueKinds.isNotEmpty()) { "校验规则 $code 未声明支持的值类型" }
    val duplicates = parameters.groupingBy(LsiValidationParameterMetadata::code)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
    require(duplicates.isEmpty()) { "校验规则 $code 参数编码重复: ${duplicates.sorted().joinToString()}" }
    parameters.forEach { parameter ->
        val minimum = parameter.minimum
        val maximum = parameter.maximum
        require(parameter.code.matches(RULE_CODE_PATTERN)) { "校验规则 $code 参数编码不合法: ${parameter.code}" }
        require(minimum == null || maximum == null || minimum <= maximum) {
            "校验规则 $code 参数 ${parameter.code} 的范围不合法"
        }
    }
}

private val RULE_CODE_PATTERN = Regex("[a-z][A-Za-z0-9]*")
