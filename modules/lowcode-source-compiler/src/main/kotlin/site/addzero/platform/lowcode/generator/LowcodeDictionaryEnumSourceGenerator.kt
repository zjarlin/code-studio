package site.addzero.platform.lowcode.generator

/** 从版本化字典快照生成业务逻辑需要的强类型枚举。 */
object LowcodeDictionaryEnumSourceGenerator {
    fun generate(
        dictionaries: List<LowcodeDictionaryMeta>,
        models: List<LowcodeModelMeta>,
        contributorId: String,
    ): List<LowcodeGeneratedFile> {
        val modelsByCode = models.associateBy(LowcodeModelMeta::modelCode)
        return dictionaries
            .filter(LowcodeDictionaryMeta::generateEnum)
            .map { dictionary -> dictionary to modelsByCode.getValue(requireNotNull(dictionary.ownerModelCode)) }
            .filter { (_, owner) -> owner.contributorId == contributorId }
            .sortedBy { (dictionary, _) -> dictionary.dictionaryCode }
            .map { (dictionary, owner) -> generate(dictionary, owner) }
    }

    private fun generate(
        dictionary: LowcodeDictionaryMeta,
        owner: LowcodeModelMeta,
    ): LowcodeGeneratedFile {
        val layout = owner.featurePackageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.ENUMS)
        val className = requireNotNull(dictionary.enumClassName)
        val strategy = dictionary.enumStorage.name
        val items = dictionary.items
            .sortedWith(compareBy(LowcodeDictionaryItemMeta::orderNo, LowcodeDictionaryItemMeta::value))
            .joinToString("\n\n") { item ->
                val enumItem = when (dictionary.enumStorage) {
                    LowcodeEnumStorage.NAME -> "@EnumItem(name = \"${item.value.escapeKotlinString()}\")"
                    LowcodeEnumStorage.ORDINAL -> "@EnumItem(ordinal = ${item.value.toInt()})"
                }
                """
                    $enumItem
                    ${requireNotNull(item.enumName)}("${item.value.escapeKotlinString()}", "${item.label.escapeKotlinString()}"),
                """.trimIndent()
            }
            .prependIndent("    ")
        val content = """
            |package $packageName
            |
            |import com.fasterxml.jackson.annotation.JsonCreator
            |import com.fasterxml.jackson.annotation.JsonValue
            |import org.babyfish.jimmer.sql.EnumItem
            |import org.babyfish.jimmer.sql.EnumType
            |
            |/** ${dictionary.name.escapeKDoc()}。 */
            |@EnumType(EnumType.Strategy.$strategy)
            |enum class $className(
            |    @get:JsonValue
            |    val code: String,
            |    val description: String,
            |) {
            |$items
            |    ;
            |
            |    companion object {
            |        @JvmStatic
            |        @JsonCreator
            |        fun fromCode(code: String): $className? = entries.firstOrNull { value -> value.code == code }
            |    }
            |}
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = className,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.ENUMS,
                className,
            ),
            content = generatedByStudio(content),
        )
    }

    private fun String.escapeKotlinString(): String = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private fun String.escapeKDoc(): String = replace("*/", "* /")
}
