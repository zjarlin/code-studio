package site.addzero.validation.compiler

/** 待生成校验扩展的字段。 */
data class LsiValidatedProperty(
    val name: String,
    val valueKind: LsiValidationValueKind,
    val nullable: Boolean,
    val rules: List<LsiValidationRule>,
)

/** 待生成校验扩展的类型。 */
data class LsiValidatedType(
    val packageName: String,
    val className: String,
    val properties: List<LsiValidatedProperty>,
)

/** 与上层源码文件模型解耦的 Kotlin 生成结果。 */
data class KotlinValidationSource(
    val packageName: String,
    val fileName: String,
    val content: String,
)

/** 从校验 LSI 生成无反射的 Kotlin 扩展函数。 */
object KotlinValidationSourceGenerator {
    fun generate(
        type: LsiValidatedType,
        catalog: ValidationRuleMetadataCatalog = ValidationRuleMetadataCatalog.load(),
        exceptionQualifiedName: String? = null,
    ): KotlinValidationSource? {
        val rules = type.properties.flatMap { property ->
            property.rules.map { rule -> Triple(property, rule, catalog.requireValid(rule)) }
        }
        if (rules.isEmpty()) {
            return null
        }
        rules.forEach { (property, _, metadata) ->
            require(property.valueKind in metadata.supportedValueKinds) {
                "字段 ${type.className}.${property.name} 的 ${property.valueKind} 类型不支持规则 ${metadata.code}"
            }
        }
        val importsSource = exceptionQualifiedName
            ?.let { qualifiedName -> "\nimport $qualifiedName\n" }
            .orEmpty()
        val checksSource = rules.joinToString("\n") { (property, rule, metadata) ->
            val expression = metadata.predicate.render(property, rule)
            val message = metadata.resolveMessage(rule).escapeKotlinString()
            "        if ($expression) add(\"$message\")"
        }
        val exceptionName = exceptionQualifiedName?.substringAfterLast('.') ?: "IllegalArgumentException"
        val content = """
            |package ${type.packageName}
            |$importsSource
            |/** 校验 ${type.className} 的字段约束并返回当前对象。 */
            |fun ${type.className}.validate(): ${type.className} {
            |    val errors = buildList {
            |$checksSource
            |    }
            |    if (errors.isNotEmpty()) {
            |        throw $exceptionName(errors.joinToString("；"))
            |    }
            |    return this
            |}
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return KotlinValidationSource(
            packageName = type.packageName,
            fileName = "${type.className}Validation",
            content = content,
        )
    }

    private fun LsiValidationPredicate.render(
        property: LsiValidatedProperty,
        rule: LsiValidationRule,
    ): String {
        val accessor = "this@validate.${property.name.escapeIdentifier()}"
        return when (this) {
            LsiValidationPredicate.BLANK -> if (property.nullable) "$accessor.isNullOrBlank()" else "$accessor.isBlank()"
            LsiValidationPredicate.EMPTY -> if (property.nullable) "$accessor.isNullOrEmpty()" else "$accessor.isEmpty()"
            LsiValidationPredicate.BLANK_ELEMENTS -> {
                val predicate = "$accessor.any(String::isBlank)"
                if (property.nullable) "$accessor?.any(String::isBlank) == true" else predicate
            }
            LsiValidationPredicate.MAX_LENGTH -> {
                val maximum = requireNotNull(rule.parameters["value"]?.toIntOrNull()) {
                    "校验规则 ${rule.code} 参数 value 必须是整数"
                }
                if (property.nullable) "$accessor?.length?.let { it > $maximum } == true" else "$accessor.length > $maximum"
            }
        }
    }

    private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

    private fun String.escapeKotlinString(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
        "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",
    )
}
