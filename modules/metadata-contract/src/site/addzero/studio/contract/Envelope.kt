package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
data class CommonResult<T>(
    val code: Int,
    val msg: String,
    val data: T? = null,
)

@Serializable
data class PageResult<T>(
    val rows: List<T> = emptyList(),
    val totalRowCount: Long = 0,
    val totalPageCount: Long = 0,
)

@Serializable
data class MetadataValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class StudioApiFailure(
    val httpStatus: Int?,
    val businessCode: Int?,
    override val message: String,
) : RuntimeException(message)
