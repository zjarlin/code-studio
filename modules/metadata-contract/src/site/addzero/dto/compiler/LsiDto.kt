package site.addzero.dto.compiler

import kotlinx.serialization.Serializable

@Serializable
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

        fun list(elementType: LsiDtoType) = LsiDtoType("kotlin.collections.List", listOf(elementType))

        fun map(keyType: LsiDtoType, valueType: LsiDtoType) =
            LsiDtoType("kotlin.collections.Map", listOf(keyType, valueType))
    }
}

@Serializable
enum class LsiDtoDefaultValueKind {
    NULL, DECLARED, BOOLEAN, INTEGER, STRING, ENUM, EMPTY_INSTANCE, EMPTY_LIST, EMPTY_MAP, EMPTY_SET,
}

@Serializable
data class LsiDtoDefaultValue(
    val kind: LsiDtoDefaultValueKind,
    val value: String? = null,
) {
    fun canonicalName(): String = "$kind:${value.orEmpty()}"

    companion object {
        val NULL = LsiDtoDefaultValue(LsiDtoDefaultValueKind.NULL)
        val DECLARED = LsiDtoDefaultValue(LsiDtoDefaultValueKind.DECLARED)
        val EMPTY_LIST = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_LIST)
        val EMPTY_MAP = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_MAP)
        val EMPTY_SET = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_SET)
        val EMPTY_INSTANCE = LsiDtoDefaultValue(LsiDtoDefaultValueKind.EMPTY_INSTANCE)

        fun boolean(value: Boolean) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.BOOLEAN, value.toString())
        fun integer(value: Long) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.INTEGER, value.toString())
        fun string(value: String) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.STRING, value)
        fun enum(constantName: String) = LsiDtoDefaultValue(LsiDtoDefaultValueKind.ENUM, constantName)
    }
}

@Serializable
enum class LsiDtoAnnotationUseSiteTarget { GET, PARAM, FIELD, PROPERTY }

@Serializable
enum class LsiDtoAnnotationArgumentKind { STRING, INTEGER, BOOLEAN, CLASS, ENUM }

@Serializable
data class LsiDtoAnnotationArgument(
    val value: String,
    val kind: LsiDtoAnnotationArgumentKind = LsiDtoAnnotationArgumentKind.STRING,
    val name: String? = null,
)

@Serializable
data class LsiDtoAnnotation(
    val qualifiedName: String,
    val useSiteTarget: LsiDtoAnnotationUseSiteTarget? = null,
    val arguments: List<LsiDtoAnnotationArgument> = emptyList(),
)

@Serializable
data class LsiDtoProperty(
    val name: String,
    val type: LsiDtoType,
    val description: String,
    val defaultValue: LsiDtoDefaultValue? = null,
    val annotations: List<LsiDtoAnnotation> = emptyList(),
)

@Serializable
enum class LsiDtoVisibility { PUBLIC, INTERNAL }

@Serializable
data class LsiDtoDefinition(
    val packageName: String,
    val className: String,
    val description: String,
    val properties: List<LsiDtoProperty>,
    val visibility: LsiDtoVisibility = LsiDtoVisibility.PUBLIC,
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val superTypes: List<LsiDtoType> = emptyList(),
)
