package site.addzero.studio.metadata

import io.ktor.http.HttpStatusCode

/** Studio UI 使用的稳定响应包络。 */
data class CommonResult<T>(
    val code: Int,
    val msg: String,
    val data: T?,
)

/** 元数据命令校验结果。 */
data class MetadataValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String> = emptyList(),
)

internal data class MetadataHttpResponse(
    val status: HttpStatusCode,
    val body: CommonResult<Any?>,
    val cause: Throwable? = null,
)

internal class MetadataRequestException(
    val status: HttpStatusCode,
    override val message: String,
) : IllegalArgumentException(message)

internal fun badRequest(message: String): Nothing =
    throw MetadataRequestException(HttpStatusCode.BadRequest, message)

internal fun forbidden(message: String): Nothing =
    throw MetadataRequestException(HttpStatusCode.Forbidden, message)

internal fun notFound(message: String): Nothing =
    throw MetadataRequestException(HttpStatusCode.NotFound, message)

internal fun conflict(message: String): Nothing =
    throw MetadataRequestException(HttpStatusCode.Conflict, message)
