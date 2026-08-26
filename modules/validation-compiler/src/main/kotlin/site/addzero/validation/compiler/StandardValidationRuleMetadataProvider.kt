package site.addzero.validation.compiler

/** 平台内置的纯值校验规则。 */
class StandardValidationRuleMetadataProvider : ValidationRuleMetadataProvider {
    override fun metadata(): Collection<LsiValidationRuleMetadata> = listOf(
        LsiValidationRuleMetadata(
            code = "notBlank",
            name = "非空白文本",
            description = "文本去除空白后必须包含内容。",
            predicate = LsiValidationPredicate.BLANK,
            supportedValueKinds = setOf(LsiValidationValueKind.TEXT),
            defaultMessage = "字段不能为空",
        ),
        LsiValidationRuleMetadata(
            code = "notEmpty",
            name = "非空集合",
            description = "集合、列表或映射至少包含一个元素。",
            predicate = LsiValidationPredicate.EMPTY,
            supportedValueKinds = setOf(
                LsiValidationValueKind.COLLECTION,
                LsiValidationValueKind.TEXT_COLLECTION,
            ),
            defaultMessage = "字段至少需要一个元素",
        ),
        LsiValidationRuleMetadata(
            code = "noBlankElements",
            name = "元素非空白",
            description = "文本集合中的每个元素去除空白后都必须包含内容。",
            predicate = LsiValidationPredicate.BLANK_ELEMENTS,
            supportedValueKinds = setOf(LsiValidationValueKind.TEXT_COLLECTION),
            defaultMessage = "集合元素不能为空",
        ),
        LsiValidationRuleMetadata(
            code = "maxLength",
            name = "最大长度",
            description = "文本长度不能超过配置值。",
            predicate = LsiValidationPredicate.MAX_LENGTH,
            supportedValueKinds = setOf(LsiValidationValueKind.TEXT),
            defaultMessage = "字段长度不能超过 {value}",
            parameters = listOf(
                LsiValidationParameterMetadata(
                    code = "value",
                    name = "最大长度",
                    description = "允许的最大字符数。",
                    kind = LsiValidationParameterKind.INTEGER,
                    minimum = 1,
                ),
            ),
        ),
    )
}
