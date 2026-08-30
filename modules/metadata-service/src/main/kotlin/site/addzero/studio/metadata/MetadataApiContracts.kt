package site.addzero.studio.metadata

import io.ktor.http.HttpStatusCode

typealias CommonResult<T> = site.addzero.studio.contract.CommonResult<T>
typealias MetadataValidationResult = site.addzero.studio.contract.MetadataValidationResult

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
