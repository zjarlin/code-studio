package site.addzero.dto.compiler

/** 从 DTO LSI 确定性生成 Kotlin data class。 */
object KotlinDtoSourceGenerator {
    fun generate(
        definition: LsiDtoDefinition,
        additionalImports: Set<String> = emptySet(),
    ): KotlinDtoSource {
        definition.validate()
        additionalImports.forEach { qualifiedName ->
            require(qualifiedName.isValidQualifiedName()) { "DTO 附加导入不合法: $qualifiedName" }
        }
        val typeNames = (definition.properties
            .flatMap { property -> property.type.flatten() }
            .map(LsiDtoType::qualifiedName)
            .toSet() + definition.superTypes.flatMap { type -> type.flatten() }.map(LsiDtoType::qualifiedName) +
            definition.annotations.flatMap(KotlinAnnotationSourceRenderer::referencedClassifiers) +
            definition.properties.flatMap { property -> property.annotationQualifiedNames() } + additionalImports)
        val conflictingSimpleNames = typeNames
            .groupBy { qualifiedName -> qualifiedName.simpleName() }
            .filterValues { qualifiedNames -> qualifiedNames.distinct().size > 1 }
            .keys + definition.className
        val imports = typeNames
            .filter { qualifiedName -> qualifiedName.shouldImport(definition.packageName, conflictingSimpleNames) }
            .sorted()
        val importsSource = imports.joinToString("\n") { qualifiedName -> "import $qualifiedName" }
        val packageSource = if (importsSource.isEmpty()) {
            "package ${definition.packageName}"
        } else {
            "package ${definition.packageName}\n\n$importsSource"
        }
        val propertiesSource = definition.properties.joinToString("\n") { property ->
            property.render(definition.packageName, conflictingSimpleNames)
        }
        val visibilitySource = when (definition.visibility) {
            LsiDtoVisibility.PUBLIC -> ""
            LsiDtoVisibility.INTERNAL -> "internal "
        }
        val annotationsSource = definition.annotations
            .joinToString("\n", postfix = if (definition.annotations.isEmpty()) "" else "\n") { annotation ->
                KotlinAnnotationSourceRenderer.render(annotation, definition.packageName, conflictingSimpleNames)
            }
        val superTypesSource = definition.superTypes
            .joinToString(prefix = " : ", separator = ", ") { type ->
                type.render(definition.packageName, conflictingSimpleNames)
            }
            .takeIf { definition.superTypes.isNotEmpty() }
            .orEmpty()
        val declarationSource = if (definition.properties.isEmpty()) {
            "$annotationsSource${visibilitySource}class ${definition.className}$superTypesSource"
        } else {
            """
                |$annotationsSource${visibilitySource}data class ${definition.className}(
                |$propertiesSource
                |)$superTypesSource
            """.trimMargin()
        }
        val content = """
            |$packageSource
            |
            |/** ${definition.description.escapeKDoc()} */
            |$declarationSource
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return KotlinDtoSource(
            packageName = definition.packageName,
            fileName = definition.className,
            content = content,
        )
    }

    private fun LsiDtoDefinition.validate() {
        require(packageName.isValidPackageName()) { "DTO 包名不合法: $packageName" }
        require(className.matches(KOTLIN_IDENTIFIER)) { "DTO 类名不合法: $className" }
        require(description.isNotBlank()) { "DTO $className 缺少说明" }
        val duplicateNames = properties.groupingBy(LsiDtoProperty::name)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "DTO $className 字段名重复: ${duplicateNames.sorted().joinToString()}"
        }
        properties.forEach { property ->
            require(property.name.matches(KOTLIN_IDENTIFIER)) {
                "DTO $className 字段名不合法: ${property.name}"
            }
            require(property.description.isNotBlank()) {
                "DTO $className.${property.name} 缺少说明"
            }
            property.type.validate("DTO $className.${property.name}")
            property.annotations.forEach { annotation ->
                KotlinAnnotationSourceRenderer.validate(annotation, "DTO $className.${property.name}")
            }
            property.defaultValue?.validate("DTO $className.${property.name}", property.type)
        }
        annotations.forEach { annotation ->
            KotlinAnnotationSourceRenderer.validate(annotation, "DTO $className")
        }
        superTypes.forEach { superType ->
            superType.validate("DTO $className 父类型")
            require(!superType.nullable) { "DTO $className 父类型不能可空: ${superType.canonicalName()}" }
        }
    }

    private fun LsiDtoType.validate(owner: String) {
        require(qualifiedName.isValidQualifiedName()) { "$owner 类型不合法: $qualifiedName" }
        arguments.forEach { argument -> argument.validate(owner) }
    }

    private fun LsiDtoProperty.render(
        packageName: String,
        conflictingSimpleNames: Set<String>,
    ): String {
        val defaultSource = defaultValue?.let { value ->
            " = ${value.render(type, packageName, conflictingSimpleNames)}"
        }.orEmpty()
        val annotationsSource = annotations.joinToString("\n", postfix = if (annotations.isEmpty()) "" else "\n") { annotation ->
            "    ${KotlinAnnotationSourceRenderer.render(annotation, packageName, conflictingSimpleNames)}"
        }
        return """
            |    /** ${description.escapeKDoc()} */
            |$annotationsSource    val ${name.escapeIdentifier()}: ${type.render(packageName, conflictingSimpleNames)}$defaultSource,
        """.trimMargin()
    }

    private fun LsiDtoDefaultValue.validate(owner: String, type: LsiDtoType) {
        when (kind) {
            LsiDtoDefaultValueKind.NULL -> require(type.nullable) { "$owner 只有可空类型可以默认 null" }
            LsiDtoDefaultValueKind.DECLARED -> error("$owner 缺少可生成的默认值表达式")
            LsiDtoDefaultValueKind.BOOLEAN -> require(value == "true" || value == "false") {
                "$owner 布尔默认值不合法: $value"
            }
            LsiDtoDefaultValueKind.INTEGER -> requireNotNull(value?.toLongOrNull()) {
                "$owner 整数默认值不合法: $value"
            }
            LsiDtoDefaultValueKind.STRING -> requireNotNull(value) { "$owner 字符串默认值不能为空" }
            LsiDtoDefaultValueKind.ENUM -> require(value?.matches(KOTLIN_IDENTIFIER) == true) {
                "$owner 枚举默认值不合法: $value"
            }
            LsiDtoDefaultValueKind.EMPTY_INSTANCE -> {
                require(!type.nullable && type.arguments.isEmpty()) {
                    "$owner 只有非空无泛型类型可以默认构造空实例"
                }
            }
            LsiDtoDefaultValueKind.EMPTY_LIST -> require(type.qualifiedName in LIST_TYPES) {
                "$owner 不是 List 类型，不能默认 emptyList()"
            }
            LsiDtoDefaultValueKind.EMPTY_MAP -> require(type.qualifiedName in MAP_TYPES) {
                "$owner 不是 Map 类型，不能默认 emptyMap()"
            }
            LsiDtoDefaultValueKind.EMPTY_SET -> require(type.qualifiedName in SET_TYPES) {
                "$owner 不是 Set 类型，不能默认 emptySet()"
            }
        }
    }

    private fun LsiDtoDefaultValue.render(
        type: LsiDtoType,
        packageName: String,
        conflictingSimpleNames: Set<String>,
    ): String = when (kind) {
        LsiDtoDefaultValueKind.NULL -> "null"
        LsiDtoDefaultValueKind.DECLARED -> error("DTO 源码生成不能恢复已编译默认值")
        LsiDtoDefaultValueKind.BOOLEAN,
        LsiDtoDefaultValueKind.INTEGER -> requireNotNull(value)
        LsiDtoDefaultValueKind.STRING -> "\"${requireNotNull(value).escapeKotlin()}\""
        LsiDtoDefaultValueKind.ENUM ->
            "${type.copy(nullable = false).render(packageName, conflictingSimpleNames)}.${requireNotNull(value)}"
        LsiDtoDefaultValueKind.EMPTY_INSTANCE ->
            "${type.render(packageName, conflictingSimpleNames)}()"
        LsiDtoDefaultValueKind.EMPTY_LIST -> "emptyList()"
        LsiDtoDefaultValueKind.EMPTY_MAP -> "emptyMap()"
        LsiDtoDefaultValueKind.EMPTY_SET -> "emptySet()"
    }

    private fun LsiDtoProperty.annotationQualifiedNames(): List<String> = annotations.flatMap { annotation ->
        KotlinAnnotationSourceRenderer.referencedClassifiers(annotation)
    }

    private fun LsiDtoType.render(
        packageName: String,
        conflictingSimpleNames: Set<String>,
    ): String {
        val typeName = when {
            qualifiedName.startsWith("kotlin.") -> qualifiedName.simpleName()
            qualifiedName.substringBeforeLast('.', "") == packageName -> qualifiedName.simpleName()
            qualifiedName.simpleName() in conflictingSimpleNames -> qualifiedName
            else -> qualifiedName.simpleName()
        }
        val argumentsSource = arguments
            .takeIf(List<LsiDtoType>::isNotEmpty)
            ?.joinToString(prefix = "<", postfix = ">") { argument ->
                argument.render(packageName, conflictingSimpleNames)
            }
            .orEmpty()
        val nullableSuffix = if (nullable) "?" else ""
        return "$typeName$argumentsSource$nullableSuffix"
    }

    private fun LsiDtoType.flatten(): List<LsiDtoType> =
        listOf(this) + arguments.flatMap { argument -> argument.flatten() }

    private fun String.shouldImport(packageName: String, conflicts: Set<String>): Boolean =
        '.' in this &&
            !startsWith("kotlin.") &&
            substringBeforeLast('.', "") != packageName &&
            simpleName() !in conflicts

    private fun String.simpleName(): String = substringAfterLast('.')

    private fun String.renderClassifier(packageName: String, conflictingSimpleNames: Set<String>): String = when {
        startsWith("kotlin.") -> simpleName()
        substringBeforeLast('.', "") == packageName -> simpleName()
        simpleName() in conflictingSimpleNames -> this
        else -> simpleName()
    }

    private fun String.isValidPackageName(): Boolean =
        split('.').all { segment -> segment.matches(KOTLIN_IDENTIFIER) && segment !in KOTLIN_KEYWORDS }

    private fun String.isValidQualifiedName(): Boolean = split('.').all { segment -> segment.matches(KOTLIN_IDENTIFIER) }

    private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

    private fun String.escapeKDoc(): String = trim().replace("*/", "* /").replace(Regex("\\s+"), " ")

    private fun String.escapeKotlin(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
}

private val KOTLIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

private val LIST_TYPES = setOf("kotlin.collections.List", "kotlin.collections.MutableList")
private val MAP_TYPES = setOf("kotlin.collections.Map", "kotlin.collections.MutableMap")
private val SET_TYPES = setOf("kotlin.collections.Set", "kotlin.collections.MutableSet")

private val KOTLIN_KEYWORDS = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
)
