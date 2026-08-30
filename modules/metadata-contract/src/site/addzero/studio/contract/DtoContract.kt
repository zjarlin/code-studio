package site.addzero.studio.contract

import kotlinx.serialization.Serializable
import site.addzero.dto.compiler.LsiDtoAnnotation
import site.addzero.dto.compiler.LsiDtoDefaultValue
import site.addzero.dto.compiler.LsiDtoType
import site.addzero.validation.compiler.LsiValidationRule

@Serializable enum class DtoKind { INPUT, OUTPUT, STRUCTURE, VIEW }
@Serializable enum class DtoSelectionMode { EXPLICIT, ALL_SCALAR_FIELDS, ALL_TABLE_FIELDS, ALL_DEEP_FIELDS }
@Serializable enum class DtoNullability { INHERIT, NULLABLE, NON_NULL }

@Serializable
data class DtoFieldCommand(
    val name: String,
    val sourcePath: String = name,
    val description: String? = null,
    val nullability: DtoNullability = DtoNullability.INHERIT,
    val schema: ApiSchema? = null,
    val kotlinType: LsiDtoType? = null,
    val validations: List<LsiValidationRule> = emptyList(),
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val defaultValue: LsiDtoDefaultValue? = null,
)

@Serializable
data class DtoCommand(
    val id: Long? = null,
    val featureId: Long,
    val dtoCode: String,
    val name: String,
    val packageName: String = "",
    val className: String,
    val kind: DtoKind,
    val visibility: site.addzero.dto.compiler.LsiDtoVisibility = site.addzero.dto.compiler.LsiDtoVisibility.PUBLIC,
    val sourceModelCode: String? = null,
    val selectionMode: DtoSelectionMode = DtoSelectionMode.EXPLICIT,
    val excludedPaths: List<String> = emptyList(),
    val fields: List<DtoFieldCommand> = emptyList(),
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val superTypes: List<LsiDtoType> = emptyList(),
    val contributorId: String? = null,
    val status: Int = 1,
    val version: Int = 1,
    val description: String? = null,
)

@Serializable
data class PreviewFile(
    val filePath: String,
    val content: String,
)

@Serializable
data class ModelPreview(
    val modelId: Long,
    val modelCode: String,
    val files: List<PreviewFile>,
)

@Serializable
data class DtoPreview(
    val dtoId: Long,
    val dtoCode: String,
    val files: List<PreviewFile>,
)

@Serializable
data class LibraryPreview(
    val libraryId: Long,
    val featureId: Long? = null,
    val files: List<PreviewFile>,
)
