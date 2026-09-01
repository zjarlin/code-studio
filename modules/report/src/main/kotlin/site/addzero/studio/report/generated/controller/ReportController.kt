package site.addzero.studio.report.generated.controller

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import site.addzero.studio.contract.CommonResult
import site.addzero.studio.contract.PageResult
import site.addzero.studio.contract.report.PublishedReportListItemView
import site.addzero.studio.contract.report.PublishedReportView
import site.addzero.studio.contract.report.ReportListItemView
import site.addzero.studio.contract.report.ReportPublicationView
import site.addzero.studio.contract.report.ReportView
import site.addzero.studio.report.internal.ReportCompiler
import site.addzero.studio.report.internal.ReportStore
import site.addzero.studio.report.internal.ReportJson
import site.addzero.studio.report.internal.ReportRecord
import site.addzero.studio.report.internal.ReportRequestException
import site.addzero.studio.report.internal.reportBadRequest
import site.addzero.studio.report.internal.reportConflict
import site.addzero.studio.server.StudioApiController
import javax.sql.DataSource

/** 安装报表草稿与当前发布快照的 HTTP 传输。 */
class ReportController(
    dataSource: DataSource,
    schema: String,
) : StudioApiController {
    private val store = ReportStore(dataSource, schema)

    override fun install(route: Route) {
        route.installDraftEndpoints()
        route.installPublishedEndpoints()
    }

    private fun Route.installDraftEndpoints() {
        route("/reports") {
            get {
                val response = runReportRequest {
                    val pageNo = call.queryInt("pageNo", 1)
                    val pageSize = call.queryInt("pageSize", 20)
                    val page = store.page(pageNo, pageSize)
                    page.mapRows(ReportRecord::toListItemView)
                }
                call.sendReportResponse(response)
            }
            post {
                val response = runReportRequest {
                    val content = call.receiveText()
                    val command = ReportJson.decodeCreate(content)
                    val document = ReportJson.encodeDocument(command.document)
                    val record = store.create(command.reportKey, document)
                    record.toReportView()
                }
                call.sendReportResponse(response)
            }
            route("/{reportKey}") {
                get {
                    val response = runReportRequest {
                        val reportKey = call.reportKey()
                        val record = store.detail(reportKey)
                        record.toReportView()
                    }
                    call.sendReportResponse(response)
                }
                put {
                    val response = runReportRequest {
                        val reportKey = call.reportKey()
                        val content = call.receiveText()
                        val command = ReportJson.decodeUpdate(content)
                        val document = ReportJson.encodeDocument(command.document)
                        val record = store.update(reportKey, command.expectedRevision, document)
                        record.toReportView()
                    }
                    call.sendReportResponse(response)
                }
                delete {
                    val response = runReportRequest {
                        val reportKey = call.reportKey()
                        store.delete(reportKey)
                    }
                    call.sendReportResponse(response)
                }
                route("/publication") {
                    post {
                        val response = runReportRequest {
                            val reportKey = call.reportKey()
                            val content = call.receiveText()
                            val command = ReportJson.decodePublish(content)
                            val draft = store.detail(reportKey)
                            if (draft.revision != command.expectedRevision) {
                                val message = "报表 $reportKey revision 已变更，" +
                                    "当前为 ${draft.revision}，提交为 ${command.expectedRevision}"
                                reportConflict(
                                    message,
                                )
                            }
                            val document = ReportJson.decodeDocument(draft.draftDocument)
                            val compiled = ReportCompiler.compile(document)
                            val publishedDocument = ReportJson.encodeDocument(compiled)
                            val record = store.publish(reportKey, command.expectedRevision, publishedDocument)
                            record.toPublicationView()
                        }
                        call.sendReportResponse(response)
                    }
                    delete {
                        val response = runReportRequest {
                            val reportKey = call.reportKey()
                            store.withdraw(reportKey)
                        }
                        call.sendReportResponse(response)
                    }
                }
            }
        }
    }

    private fun Route.installPublishedEndpoints() {
        route("/published-reports") {
            get {
                val response = runReportRequest {
                    val pageNo = call.queryInt("pageNo", 1)
                    val pageSize = call.queryInt("pageSize", 20)
                    val page = store.publishedPage(pageNo, pageSize)
                    page.mapRows(ReportRecord::toPublishedListItemView)
                }
                call.sendReportResponse(response)
            }
            get("/{reportKey}") {
                val response = runReportRequest {
                    val reportKey = call.reportKey()
                    val record = store.publishedDetail(reportKey)
                    record.toPublishedView()
                }
                call.sendReportResponse(response)
            }
        }
    }
}

private fun ReportRecord.toListItemView(): ReportListItemView {
    val document = ReportJson.decodeDocument(draftDocument)
    return ReportListItemView(
        reportKey = reportKey,
        revision = revision,
        name = document.name,
        description = document.description,
        publishedRevision = publishedRevision,
    )
}

private fun ReportRecord.toReportView(): ReportView {
    val document = ReportJson.decodeDocument(draftDocument)
    return ReportView(
        reportKey = reportKey,
        revision = revision,
        document = document,
        publishedRevision = publishedRevision,
    )
}

private fun ReportRecord.toPublicationView(): ReportPublicationView {
    val version = checkNotNull(publishedRevision)
    val content = checkNotNull(publishedDocument)
    val document = ReportJson.decodeDocument(content)
    return ReportPublicationView(reportKey, version, document)
}

private fun ReportRecord.toPublishedListItemView(): PublishedReportListItemView {
    val version = checkNotNull(publishedRevision)
    val content = checkNotNull(publishedDocument)
    val document = ReportJson.decodeDocument(content)
    return PublishedReportListItemView(
        reportKey = reportKey,
        publishedRevision = version,
        name = document.name,
        description = document.description,
    )
}

private fun ReportRecord.toPublishedView(): PublishedReportView {
    val version = checkNotNull(publishedRevision)
    val content = checkNotNull(publishedDocument)
    val document = ReportJson.decodeDocument(content)
    return PublishedReportView(reportKey, version, document)
}

internal fun <T, R> PageResult<T>.mapRows(transform: (T) -> R): PageResult<R> = PageResult(
    rows = rows.map(transform),
    totalRowCount = totalRowCount,
    totalPageCount = totalPageCount,
)

internal data class ReportHttpResponse(
    val status: HttpStatusCode,
    val body: CommonResult<Any?>,
    val cause: Throwable? = null,
)

internal suspend fun runReportRequest(action: suspend () -> Any?): ReportHttpResponse = try {
    val data = action()
    ReportHttpResponse(HttpStatusCode.OK, CommonResult(0, "", data))
} catch (cause: CancellationException) {
    throw cause
} catch (cause: ReportRequestException) {
    ReportHttpResponse(cause.status, CommonResult(cause.status.value, cause.message, null))
} catch (cause: SerializationException) {
    val status = HttpStatusCode.BadRequest
    val message = cause.message ?: "报表请求 JSON 不合法"
    ReportHttpResponse(status, CommonResult(status.value, message, null))
} catch (cause: IllegalArgumentException) {
    val status = HttpStatusCode.BadRequest
    val message = cause.message ?: "报表请求不合法"
    ReportHttpResponse(status, CommonResult(status.value, message, null))
} catch (cause: Throwable) {
    val status = HttpStatusCode.InternalServerError
    val body = CommonResult<Any?>(status.value, "报表服务内部错误", null)
    ReportHttpResponse(status, body, cause)
}

internal suspend fun ApplicationCall.sendReportResponse(response: ReportHttpResponse) {
    response.cause?.let { cause -> application.log.error("报表请求失败", cause) }
    val status = response.status
    val body = response.body
    respond(status, body)
}

private fun ApplicationCall.reportKey(): String {
    val reportKey = parameters["reportKey"]
        ?.takeIf(String::isNotBlank)
        ?: reportBadRequest("reportKey 不能为空")
    if (!REPORT_KEY.matches(reportKey)) {
        reportBadRequest("reportKey 必须是稳定标识")
    }
    return reportKey
}

internal fun ApplicationCall.queryInt(name: String, default: Int): Int {
    val value = request.queryParameters[name] ?: return default
    return value.toIntOrNull() ?: reportBadRequest("$name 必须是整数")
}

private val REPORT_KEY = Regex("[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*")
