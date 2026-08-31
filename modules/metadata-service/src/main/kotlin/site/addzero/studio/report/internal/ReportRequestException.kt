package site.addzero.studio.report.internal

import io.ktor.http.HttpStatusCode

internal class ReportRequestException(
    val status: HttpStatusCode,
    override val message: String,
) : IllegalArgumentException(message)

internal fun reportBadRequest(message: String): Nothing =
    throw ReportRequestException(HttpStatusCode.BadRequest, message)

internal fun reportNotFound(reportKey: String): Nothing =
    throw ReportRequestException(HttpStatusCode.NotFound, "报表不存在: $reportKey")

internal fun reportConflict(message: String): Nothing =
    throw ReportRequestException(HttpStatusCode.Conflict, message)

