package site.addzero.studio.report.generated.controller

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import site.addzero.platform.web.Controller
import site.addzero.studio.contract.report.SpreadsheetTemplateListItemView
import site.addzero.studio.contract.report.SpreadsheetTemplateView
import site.addzero.studio.report.internal.MAX_TEMPLATE_FILE_BYTES
import site.addzero.studio.report.internal.SpreadsheetTemplateCodec
import site.addzero.studio.report.internal.SpreadsheetTemplateJson
import site.addzero.studio.report.internal.SpreadsheetTemplateDocumentRecord
import site.addzero.studio.report.internal.SpreadsheetTemplateStore
import site.addzero.studio.report.internal.SpreadsheetTemplateListRecord
import site.addzero.studio.report.internal.reportBadRequest
import site.addzero.studio.report.internal.reportConflict
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

/** 安装原始 Excel 模板、可编辑语义模型和填充下载传输。 */
class SpreadsheetTemplateController(
    dataSource: DataSource,
    schema: String,
) : Controller {
    override val routeKey = "/spreadsheet-templates"

    private val store = SpreadsheetTemplateStore(dataSource, schema)

    override fun install(route: Route) {
        route.route("/spreadsheet-templates") {
            get {
                val response = runReportRequest {
                    val pageNo = call.queryInt("pageNo", 1)
                    val pageSize = call.queryInt("pageSize", 20)
                    store.page(pageNo, pageSize).mapRows(SpreadsheetTemplateListRecord::toListItemView)
                }
                call.sendReportResponse(response)
            }
            post {
                val response = runReportRequest {
                    val upload = call.receiveSpreadsheetTemplateUpload()
                    val document = SpreadsheetTemplateCodec.read(upload.bytes, upload.fileName, upload.name)
                    val content = SpreadsheetTemplateJson.encodeDocument(document)
                    store.create(upload.templateKey, upload.bytes, content).toView()
                }
                call.sendReportResponse(response)
            }
            route("/{templateKey}") {
                get {
                    val response = runReportRequest {
                        store.detail(call.templateKey()).toView()
                    }
                    call.sendReportResponse(response)
                }
                put {
                    val response = runReportRequest {
                        val templateKey = call.templateKey()
                        val content = call.receiveText()
                        val command = SpreadsheetTemplateJson.decodeUpdate(content)
                        val current = store.detail(templateKey)
                        val currentDocument = SpreadsheetTemplateJson.decodeDocument(current.document)
                        val document = SpreadsheetTemplateJson.encodeDocument(command.draft.applyTo(currentDocument))
                        store.update(templateKey, command.expectedRevision, document).toView()
                    }
                    call.sendReportResponse(response)
                }
                delete {
                    val response = runReportRequest {
                        store.delete(call.templateKey())
                    }
                    call.sendReportResponse(response)
                }
                post("/fill") {
                    val response = runReportRequest {
                        val content = call.receiveText()
                        val command = SpreadsheetTemplateJson.decodeFill(content)
                        val record = store.source(call.templateKey())
                        if (record.revision != command.expectedRevision) {
                            reportConflict(
                                "电子表格模板 ${record.templateKey} revision 已变更，" +
                                    "当前为 ${record.revision}，提交为 ${command.expectedRevision}",
                            )
                        }
                        val document = SpreadsheetTemplateJson.decodeDocument(record.document)
                        val bytes = SpreadsheetTemplateCodec.fill(record.sourceFile, document, command)
                        SpreadsheetDownload(document.source.fileName, document.source.mediaType, bytes)
                    }
                    val download = response.body.data as? SpreadsheetDownload
                    if (response.status != HttpStatusCode.OK || download == null) {
                        call.sendReportResponse(response)
                        return@post
                    }
                    val encodedFileName = URLEncoder.encode(download.fileName, StandardCharsets.UTF_8).replace("+", "%20")
                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename*=UTF-8''$encodedFileName")
                    val contentType = ContentType.parse(download.mediaType)
                    call.respondBytes(download.bytes, contentType, HttpStatusCode.OK)
                }
            }
        }
    }
}

private fun SpreadsheetTemplateListRecord.toListItemView(): SpreadsheetTemplateListItemView = SpreadsheetTemplateListItemView(
    templateKey = templateKey,
    revision = revision,
    name = name,
    fileName = fileName,
    macroEnabled = macroEnabled,
)

private fun SpreadsheetTemplateDocumentRecord.toView(): SpreadsheetTemplateView = SpreadsheetTemplateView(
    templateKey = templateKey,
    revision = revision,
    document = SpreadsheetTemplateJson.decodeDocument(document),
)

private data class SpreadsheetTemplateUpload(
    val templateKey: String,
    val name: String,
    val fileName: String,
    val bytes: ByteArray,
)

private data class SpreadsheetDownload(
    val fileName: String,
    val mediaType: String,
    val bytes: ByteArray,
)

private suspend fun ApplicationCall.receiveSpreadsheetTemplateUpload(): SpreadsheetTemplateUpload {
    var templateKey: String? = null
    var name: String? = null
    var fileName: String? = null
    var bytes: ByteArray? = null
    receiveMultipart().forEachPart { part ->
        try {
            when (part) {
                is PartData.FormItem -> when (part.name) {
                    "templateKey" -> templateKey = part.value.trim()
                    "name" -> name = part.value.trim()
                }
                is PartData.FileItem -> if (part.name == "file") {
                    if (bytes != null) reportBadRequest("Excel 文件只能上传一个")
                    val packet = part.provider().readRemaining(MAX_TEMPLATE_FILE_BYTES + 1L)
                    val content = packet.readByteArray()
                    if (content.size > MAX_TEMPLATE_FILE_BYTES) reportBadRequest("Excel 文件不能超过 20 MB")
                    bytes = content
                    fileName = part.originalFileName?.safeFileName()
                }
                else -> Unit
            }
        } finally {
            part.release()
        }
    }
    val resolvedKey = templateKey?.takeIf(String::isNotBlank) ?: reportBadRequest("templateKey 不能为空")
    if (!TEMPLATE_KEY.matches(resolvedKey)) reportBadRequest("templateKey 必须是稳定标识")
    val resolvedFileName = fileName?.takeIf(String::isNotBlank) ?: reportBadRequest("Excel 文件名不能为空")
    val resolvedBytes = bytes?.takeIf(ByteArray::isNotEmpty) ?: reportBadRequest("Excel 文件不能为空")
    val resolvedName = name?.takeIf(String::isNotBlank) ?: resolvedFileName.substringBeforeLast('.')
    return SpreadsheetTemplateUpload(resolvedKey, resolvedName, resolvedFileName, resolvedBytes)
}

private fun String.safeFileName(): String = substringAfterLast('/').substringAfterLast('\\').take(255)

private fun ApplicationCall.templateKey(): String {
    val templateKey = parameters["templateKey"]?.takeIf(String::isNotBlank)
        ?: reportBadRequest("templateKey 不能为空")
    if (!TEMPLATE_KEY.matches(templateKey)) reportBadRequest("templateKey 必须是稳定标识")
    return templateKey
}

private val TEMPLATE_KEY = Regex("[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*")
