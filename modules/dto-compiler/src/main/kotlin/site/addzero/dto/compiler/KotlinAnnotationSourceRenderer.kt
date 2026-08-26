package site.addzero.dto.compiler

/** 确定性渲染受限、结构化的 Kotlin 注解，避免元数据携带任意源码表达式。 */
object KotlinAnnotationSourceRenderer {
    fun referencedClassifiers(annotation: LsiDtoAnnotation): Set<String> = buildSet {
        add(annotation.qualifiedName)
        annotation.arguments.forEach { argument ->
            when (argument.kind) {
                LsiDtoAnnotationArgumentKind.CLASS -> add(argument.value)
                LsiDtoAnnotationArgumentKind.ENUM -> add(argument.value.substringBeforeLast('.'))
                else -> Unit
            }
        }
    }

    fun validate(annotation: LsiDtoAnnotation, owner: String) {
        require(annotation.qualifiedName.isValidQualifiedName()) {
            "$owner 注解类型不合法: ${annotation.qualifiedName}"
        }
        annotation.arguments.forEach { argument ->
            argument.name?.let { name ->
                require(name.matches(KOTLIN_IDENTIFIER)) { "$owner 注解参数名不合法: $name" }
            }
            when (argument.kind) {
                LsiDtoAnnotationArgumentKind.STRING -> Unit
                LsiDtoAnnotationArgumentKind.INTEGER -> requireNotNull(argument.value.toLongOrNull()) {
                    "$owner 注解整数参数不合法: ${argument.value}"
                }
                LsiDtoAnnotationArgumentKind.BOOLEAN ->
                    require(argument.value == "true" || argument.value == "false") {
                        "$owner 注解布尔参数不合法: ${argument.value}"
                    }
                LsiDtoAnnotationArgumentKind.CLASS -> require(argument.value.isValidQualifiedName()) {
                    "$owner 注解类参数不合法: ${argument.value}"
                }
                LsiDtoAnnotationArgumentKind.ENUM -> {
                    val classifier = argument.value.substringBeforeLast('.', missingDelimiterValue = "")
                    val entry = argument.value.substringAfterLast('.')
                    require(classifier.isValidQualifiedName() && entry.matches(KOTLIN_IDENTIFIER)) {
                        "$owner 注解枚举参数不合法: ${argument.value}"
                    }
                }
            }
        }
    }

    fun render(
        annotation: LsiDtoAnnotation,
        packageName: String,
        conflictingSimpleNames: Set<String> = emptySet(),
    ): String {
        validate(annotation, "Kotlin")
        val targetSource = annotation.useSiteTarget?.name?.lowercase()?.let { target -> "$target:" }.orEmpty()
        val argumentsSource = annotation.arguments.takeIf(List<LsiDtoAnnotationArgument>::isNotEmpty)
            ?.joinToString(prefix = "(", postfix = ")") { argument ->
                val nameSource = argument.name?.let { name -> "$name = " }.orEmpty()
                val valueSource = when (argument.kind) {
                    LsiDtoAnnotationArgumentKind.STRING -> "\"${argument.value.escapeKotlin()}\""
                    LsiDtoAnnotationArgumentKind.INTEGER,
                    LsiDtoAnnotationArgumentKind.BOOLEAN,
                    -> argument.value
                    LsiDtoAnnotationArgumentKind.CLASS ->
                        "${argument.value.renderClassifier(packageName, conflictingSimpleNames)}::class"
                    LsiDtoAnnotationArgumentKind.ENUM -> {
                        val classifier = argument.value.substringBeforeLast('.')
                        val entry = argument.value.substringAfterLast('.')
                        "${classifier.renderClassifier(packageName, conflictingSimpleNames)}.$entry"
                    }
                }
                "$nameSource$valueSource"
            }.orEmpty()
        return "@$targetSource${annotation.qualifiedName.renderClassifier(packageName, conflictingSimpleNames)}" +
            argumentsSource
    }
}

private fun String.renderClassifier(packageName: String, conflictingSimpleNames: Set<String>): String = when {
    startsWith("kotlin.") -> substringAfterLast('.')
    substringBeforeLast('.', "") == packageName -> substringAfterLast('.')
    substringAfterLast('.') in conflictingSimpleNames -> this
    else -> substringAfterLast('.')
}

private fun String.isValidQualifiedName(): Boolean =
    isNotEmpty() && split('.').all { segment -> segment.matches(KOTLIN_IDENTIFIER) }

private fun String.escapeKotlin(): String =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

private val KOTLIN_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
