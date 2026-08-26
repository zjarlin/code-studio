package site.addzero.studio.metadata

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import site.addzero.studio.server.StudioApiController
import site.addzero.studio.runtime.GenerationTargetProfile
import tools.jackson.databind.JsonNode
import javax.sql.DataSource

/** 为当前 contributor 安装可见全量、写入隔离的 Studio 元数据 API。 */
class StudioMetadataController(
    dataSource: DataSource,
    schema: String,
    editableContributorId: String,
    targetProfile: GenerationTargetProfile,
) : StudioApiController {
    private val store = MetadataJdbcStore(dataSource, schema, editableContributorId)
    private val previews = MetadataPreviewService(store, targetProfile)

    override fun install(route: Route) {
        route.route("/lowcode") {
            installLibraryEndpoints()
            installLibraryFeatureEndpoints()
            installModelEndpoints()
            installDtoEndpoints()
            installContractEndpoints()
            installConstantEndpoints()
        }
    }

    private fun Route.installLibraryEndpoints() {
        route("/library") {
            get("/page") {
                val pageNumber = call.queryInt("pageNo", 1)
                val pageSize = call.queryInt("pageSize", 20)
                call.respondMetadata { store.read { libraryPage(pageNumber, pageSize) } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { libraryDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateLibrary(command) } }
            }
            post("/add") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveLibrary(command) } }
            }
            put("/update") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveLibrary(command) } }
            }
            delete("/delete") {
                val request = call.receive<JsonNode>()
                val ids = request.requiredIds()
                call.respondMetadata { store.write { deleteLibraries(ids) } }
            }
            get("/preview") {
                val id = call.queryId()
                val featureId = call.optionalQueryId("featureId")
                call.respondMetadata { previews.library(id, featureId) }
            }
        }
    }

    private fun Route.installLibraryFeatureEndpoints() {
        route("/library-feature") {
            get("/page") {
                val libraryId = call.request.queryParameters["libraryId"]?.toLongOrNull()
                val pageNumber = call.queryInt("pageNo", 1)
                val pageSize = call.queryInt("pageSize", 20)
                call.respondMetadata { store.read { libraryFeaturePage(libraryId, pageNumber, pageSize) } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { libraryFeatureDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateLibraryFeature(command) } }
            }
            post("/create") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveLibraryFeature(command) } }
            }
            put("/update") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveLibraryFeature(command) } }
            }
            delete("/delete") {
                val id = call.queryId()
                call.respondMetadata { store.write { deleteLibraryFeature(id) } }
            }
        }
    }

    private fun Route.installModelEndpoints() {
        route("/model") {
            post("/list") {
                val request = call.receive<JsonNode>()
                call.respondMetadata { store.read { modelList(request) } }
            }
            post("/page") {
                val request = call.receive<JsonNode>()
                call.respondMetadata { store.read { modelPage(request) } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { modelDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateModel(command) } }
            }
            post("/add") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveModel(command) } }
            }
            put("/update") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveModel(command) } }
            }
            delete {
                val request = call.receive<JsonNode>()
                val ids = request.requiredIds()
                call.respondMetadata { store.write { deleteModels(ids) } }
            }
            get("/preview") {
                val id = call.queryId()
                call.respondMetadata { previews.model(id) }
            }
            get("/download") {
                val id = call.queryId()
                call.respondMetadataArchive("lowcode-model-$id.zip") { previews.model(id).files }
            }
        }
    }

    private fun Route.installDtoEndpoints() {
        route("/dto") {
            post("/list") {
                call.respondMetadata { store.read { dtoList() } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { dtoDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateDto(command) } }
            }
            post("/add") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveDto(command) } }
            }
            put("/update") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveDto(command) } }
            }
            delete {
                val request = call.receive<JsonNode>()
                val ids = request.requiredIds()
                call.respondMetadata { store.write { deleteDtos(ids) } }
            }
            get("/validation-rules") {
                call.respondMetadata { dtoValidationRules() }
            }
            post("/reuse-analysis") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { previews.analyzeDtoReuse(command) }
            }
            get("/preview") {
                val id = call.queryId()
                call.respondMetadata { previews.dto(id) }
            }
            get("/download") {
                val id = call.queryId()
                call.respondMetadataArchive("lowcode-dto-$id.zip") { previews.dto(id).files }
            }
        }
    }

    private fun Route.installContractEndpoints() {
        route("/contract") {
            post("/list") {
                call.respondMetadata { store.read { contractList() } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { contractDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateContract(command) } }
            }
            post("/add") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveContract(command) } }
            }
            put("/update") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveContract(command) } }
            }
            delete {
                val request = call.receive<JsonNode>()
                val ids = request.requiredIds()
                call.respondMetadata { store.write { deleteContracts(ids) } }
            }
            get("/preview") {
                val id = call.queryId()
                call.respondMetadata { previews.contract(id) }
            }
            get("/download") {
                val id = call.queryId()
                call.respondMetadataArchive("lowcode-contract-$id.zip") { previews.contract(id).files }
            }
        }
    }

    private fun Route.installConstantEndpoints() {
        route("/constant") {
            post("/list") {
                val request = call.receive<JsonNode>()
                call.respondMetadata { store.read { constantList(request) } }
            }
            get("/detail") {
                val id = call.queryId()
                call.respondMetadata { store.read { constantDetail(id) } }
            }
            post("/validate") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.read { validateConstant(command) } }
            }
            post("/save") {
                val command = call.receive<JsonNode>()
                call.respondMetadata { store.write { saveConstant(command) } }
            }
            delete {
                val request = call.receive<JsonNode>()
                val ids = request.requiredIds()
                call.respondMetadata { store.write { deleteConstants(ids) } }
            }
        }
    }
}

private suspend fun ApplicationCall.respondMetadata(action: suspend () -> Any?) {
    val response = runMetadataRequest(action)
    response.cause?.let { cause ->
        application.log.error("Studio 元数据请求失败", cause)
    }
    val status = response.status
    val body = response.body
    respond(status, body)
}

private suspend fun ApplicationCall.respondMetadataArchive(
    fileName: String,
    action: suspend () -> List<MetadataPreviewFile>,
) {
    val result = runMetadataRequest { archivePreviewFiles(action()) }
    result.cause?.let { cause ->
        application.log.error("Studio 元数据归档失败", cause)
    }
    val archive = result.body.data as? ByteArray
    if (archive == null) {
        val status = result.status
        val body = result.body
        respond(status, body)
        return
    }
    response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")
    val contentType = ContentType("application", "zip")
    respondBytes(archive, contentType, HttpStatusCode.OK)
}

private suspend fun runMetadataRequest(action: suspend () -> Any?): MetadataHttpResponse = try {
    val data = action()
    MetadataHttpResponse(HttpStatusCode.OK, CommonResult(0, "", data))
} catch (cause: CancellationException) {
    throw cause
} catch (cause: MetadataRequestException) {
    MetadataHttpResponse(cause.status, CommonResult(cause.status.value, cause.message, null))
} catch (cause: IllegalArgumentException) {
    val status = HttpStatusCode.BadRequest
    val message = cause.message ?: "元数据请求不合法"
    MetadataHttpResponse(status, CommonResult(status.value, message, null))
} catch (cause: Throwable) {
    val status = HttpStatusCode.InternalServerError
    val body = CommonResult<Any?>(status.value, "Studio 元数据服务内部错误", null)
    MetadataHttpResponse(status, body, cause)
}

private fun ApplicationCall.queryId(): Long {
    val value = request.queryParameters["id"]
    return value?.toLongOrNull() ?: badRequest("id 必须是整数")
}

private fun ApplicationCall.optionalQueryId(name: String): Long? {
    val value = request.queryParameters[name] ?: return null
    return value.toLongOrNull() ?: badRequest("$name 必须是整数")
}

private fun ApplicationCall.queryInt(name: String, default: Int): Int {
    val value = request.queryParameters[name] ?: return default
    return value.toIntOrNull() ?: badRequest("$name 必须是整数")
}
