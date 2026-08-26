package site.addzero.constant.compiler

/** 与上层构建系统解耦的 Kotlin 常量源文件。 */
data class KotlinConstantSource(
    val packageName: String,
    val fileName: String,
    val content: String,
)

/** 从常量 LSI 确定性生成 Kotlin 常量对象。 */
object KotlinConstantSourceGenerator {
    fun validate(group: LsiConstantGroup) {
        group.validateGroup()
    }

    fun generate(group: LsiConstantGroup): KotlinConstantSource {
        validate(group)
        val constantsSource = group.constants
            .sortedBy(LsiConstant::name)
            .joinToString("\n\n") { constant ->
                """
                    /** ${constant.description.escapeKDoc()} */
                    const val ${constant.name}: ${constant.type.kotlinType} = ${constant.renderValue()}
                """.trimIndent()
            }
            .prependIndent("    ")
        val content = """
            |package ${group.packageName}
            |
            |/** ${group.description.escapeKDoc()} */
            |object ${group.objectName} {
            |$constantsSource
            |}
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return KotlinConstantSource(
            packageName = group.packageName,
            fileName = group.objectName,
            content = content,
        )
    }

    private fun LsiConstantGroup.validateGroup() {
        require(packageName.split('.').all { part -> part.matches(KOTLIN_IDENTIFIER) }) {
            "常量包名不合法: $packageName"
        }
        require(objectName.matches(KOTLIN_IDENTIFIER)) { "常量对象名不合法: $objectName" }
        require(description.isNotBlank()) { "常量对象 $objectName 缺少说明" }
        require(constants.isNotEmpty()) { "常量对象 $objectName 至少需要一条常量" }
        val duplicates = constants.groupingBy(LsiConstant::name).eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicates.isEmpty()) { "常量名称重复: ${duplicates.sorted().joinToString()}" }
        constants.forEach { constant ->
            require(constant.name.matches(CONSTANT_IDENTIFIER)) { "常量名不合法: ${constant.name}" }
            require(constant.description.isNotBlank()) { "常量 ${constant.name} 缺少说明" }
            constant.renderValue()
        }
    }

    private fun LsiConstant.renderValue(): String = when (type) {
        LsiConstantType.BOOLEAN -> requireNotNull(value.toBooleanStrictOrNull()) {
            "常量 $name 不是合法 Boolean: $value"
        }.toString()
        LsiConstantType.INT -> requireNotNull(value.toIntOrNull()) {
            "常量 $name 不是合法 Int: $value"
        }.toString()
        LsiConstantType.LONG -> {
            val parsed = requireNotNull(value.toLongOrNull()) {
                "常量 $name 不是合法 Long: $value"
            }
            "${parsed}L"
        }
        LsiConstantType.STRING -> "\"${value.escapeKotlinString()}\""
    }

    private val LsiConstantType.kotlinType: String
        get() = when (this) {
            LsiConstantType.BOOLEAN -> "Boolean"
            LsiConstantType.INT -> "Int"
            LsiConstantType.LONG -> "Long"
            LsiConstantType.STRING -> "String"
        }

    private fun String.escapeKotlinString(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private fun String.escapeKDoc(): String = replace("*/", "* /")
}

private val KOTLIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val CONSTANT_IDENTIFIER = Regex("[A-Z][A-Z0-9_]*")
