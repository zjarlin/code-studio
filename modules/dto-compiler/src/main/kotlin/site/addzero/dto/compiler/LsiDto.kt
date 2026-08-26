package site.addzero.dto.compiler

/** 可递归表达泛型与可空性的 DTO 类型。 */
data class LsiDtoType(
    val qualifiedName: String,
    val arguments: List<LsiDtoType> = emptyList(),
    val nullable: Boolean = false,
) {
    fun canonicalName(): String {
        val argumentsSource = arguments
            .takeIf(List<LsiDtoType>::isNotEmpty)
            ?.joinToString(prefix = "<", postfix = ">", transform = LsiDtoType::canonicalName)
            .orEmpty()
        val nullableSuffix = if (nullable) "?" else ""
        return "$qualifiedName$argumentsSource$nullableSuffix"
    }

    companion object {
        val STRING = LsiDtoType("kotlin.String")
        val INT = LsiDtoType("kotlin.Int")
        val LONG = LsiDtoType("kotlin.Long")
        val DOUBLE = LsiDtoType("kotlin.Double")
        val BOOLEAN = LsiDtoType("kotlin.Boolean")

        fun list(elementType: LsiDtoType): LsiDtoType =
            LsiDtoType("kotlin.collections.List", listOf(elementType))

        fun map(keyType: LsiDtoType, valueType: LsiDtoType): LsiDtoType =
            LsiDtoType("kotlin.collections.Map", listOf(keyType, valueType))
    }
}

/** 构造参数默认值类型。 */
enum class LsiDtoDefaultValueKind {
    NULL,
    DECLARED,
    BOOLEAN,
    INTEGER,
    STRING,
    ENUM,
    EMPTY_INSTANCE,
    EMPTY_LIST,
    EMPTY_MAP,
    EMPTY_SET,
}

/** 不携带任意源码表达式的构造参数默认值。 */
data class LsiDtoDefaultValue(
    val kind: LsiDtoDefaultValueKind,
    val value: String? = null,
) {
    fun canonicalName(): String = "$kind:${value.orEmpty()}"

    companion object {
        val NULL = LsiDtoDefaultValue(LsiDtoDefaultValueKind.NULL)
        /** 编译产物只能确定存在默认值，无法恢复表达式。 */
        val DECLARED = LsiDtoDefaultValue(LsiDtoDefaultValueKind.DECLARED)
        val EMPTY_LIST = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_LIST)
        val EMPTY_MAP = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_MAP)
        val EMPTY_SET = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_SET)

        fun boolean(value: Boolean) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.BOOLEAN, value.toString())

        fun integer(value: Long) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.INTEGER, value.toString())

        fun string(value: String) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.STRING, value)

        fun enum(constantName: String) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.ENUM, constantName)

        val EMPTY_INSTANCE = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_INSTANCE)
    }
}

/** Kotlin 注解的使用点目标。 */
enum class LsiDtoAnnotationUseSiteTarget {
    GET,
    PARAM,
    FIELD,
    PROPERTY,
}

/** Kotlin 注解参数的结构化值类型。 */
enum class LsiDtoAnnotationArgumentKind {
    STRING,
    INTEGER,
    BOOLEAN,
    CLASS,
    ENUM,
}

/** Kotlin 注解参数。 */
data class LsiDtoAnnotationArgument(
    val value: String,
    val kind: LsiDtoAnnotationArgumentKind = LsiDtoAnnotationArgumentKind.STRING,
    val name: String? = null,
)

/** 不携带任意源码表达式的 Kotlin 注解。 */
data class LsiDtoAnnotation(
    val qualifiedName: String,
    val useSiteTarget: LsiDtoAnnotationUseSiteTarget? = null,
    val arguments: List<LsiDtoAnnotationArgument> = emptyList(),
)

/** DTO 构造属性。 */
data class LsiDtoProperty(
    val name: String,
    val type: LsiDtoType,
    val description: String,
    val defaultValue: LsiDtoDefaultValue? = null,
    val annotations: List<LsiDtoAnnotation> = emptyList(),
)

/** Kotlin DTO 的源码可见性。 */
enum class LsiDtoVisibility {
    PUBLIC,
    INTERNAL,
}

/** Kotlin data class 的中性编译输入。 */
data class LsiDtoDefinition(
    val packageName: String,
    val className: String,
    val description: String,
    val properties: List<LsiDtoProperty>,
    val visibility: LsiDtoVisibility = LsiDtoVisibility.PUBLIC,
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val superTypes: List<LsiDtoType> = emptyList(),
)

/** 与上层文件布局解耦的 Kotlin DTO 源码。 */
data class KotlinDtoSource(
    val packageName: String,
    val fileName: String,
    val content: String,
)
