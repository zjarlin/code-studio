package site.addzero.studio.workbench.transport

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import org.koin.core.annotation.Single
import site.addzero.studio.contract.AgentConversationCommand
import site.addzero.studio.contract.AgentConversationModelCommand
import site.addzero.studio.contract.AgentConversationView
import site.addzero.studio.contract.AgentDefinitionCommand
import site.addzero.studio.contract.AgentDefinitionSummary
import site.addzero.studio.contract.AgentMessageView
import site.addzero.studio.contract.AgentProviderModel
import site.addzero.studio.contract.AgentProviderSettingsCommand
import site.addzero.studio.contract.AgentProviderSettingsView
import site.addzero.studio.contract.ApiRequestCommand
import site.addzero.studio.contract.ApiResponseView
import site.addzero.studio.contract.CommonResult
import site.addzero.studio.contract.ConstantCommand
import site.addzero.studio.contract.ConstantListCommand
import site.addzero.studio.contract.ConventionFileCommand
import site.addzero.studio.contract.ConventionFileView
import site.addzero.studio.contract.DtoCommand
import site.addzero.studio.contract.DtoPreview
import site.addzero.studio.contract.LibraryCommand
import site.addzero.studio.contract.LibraryFeatureCommand
import site.addzero.studio.contract.LibraryFeatureView
import site.addzero.studio.contract.LibraryPreview
import site.addzero.studio.contract.LibraryView
import site.addzero.studio.contract.MetadataContributorSummary
import site.addzero.studio.contract.MetadataPatchCommand
import site.addzero.studio.contract.MetadataValidationResult
import site.addzero.studio.contract.ModelCommand
import site.addzero.studio.contract.ModelPageCommand
import site.addzero.studio.contract.ModelPreview
import site.addzero.studio.contract.PageResult
import site.addzero.studio.contract.StudioApiFailure
import site.addzero.studio.contract.StudioClientConfig
import kotlin.time.TimeSource

@Single
class StudioApi(
    private val client: HttpClient,
    private val json: Json,
    private val session: StudioSessionState,
) {
    fun close() {
        client.close()
    }

    suspend fun config(): StudioClientConfig = client.get("/studio/config").body()

    suspend fun contributors(): List<MetadataContributorSummary> =
        client.get("/studio/api/contributors").body()

    suspend fun libraries(): List<LibraryView> {
        val page = envelope(
            path = "/studio/api/lowcode/library/page?pageNo=1&pageSize=1000",
            deserializer = LibraryPage.serializer(),
        )
        return page.list
    }

    suspend fun libraryFeatures(libraryId: Long): List<LibraryFeatureView> {
        val page = envelope(
            path = "/studio/api/lowcode/library-feature/page?pageNo=1&pageSize=1000&libraryId=$libraryId",
            deserializer = LibraryFeaturePage.serializer(),
        )
        return page.list.sortedBy(LibraryFeatureView::featureCode)
    }

    suspend fun validateLibrary(command: LibraryCommand): MetadataValidationResult =
        post("/studio/api/lowcode/library/validate", command)

    suspend fun saveLibrary(command: LibraryCommand): Long = if (command.id == null) {
        post("/studio/api/lowcode/library/add", command)
    } else {
        put("/studio/api/lowcode/library/update", command)
    }

    suspend fun deleteLibrary(id: Long): Boolean =
        delete("/studio/api/lowcode/library/delete", listOf(id), ListSerializer(Long.serializer()))

    suspend fun previewLibrary(id: Long, featureId: Long? = null): LibraryPreview {
        val suffix = featureId?.let { "&featureId=$it" }.orEmpty()
        return envelope("/studio/api/lowcode/library/preview?id=$id$suffix", LibraryPreview.serializer())
    }

    suspend fun saveLibraryFeature(command: LibraryFeatureCommand): LibraryFeatureView = if (command.id == null) {
        post("/studio/api/lowcode/library-feature/create", command)
    } else {
        put("/studio/api/lowcode/library-feature/update", command)
    }

    suspend fun validateLibraryFeature(command: LibraryFeatureCommand): MetadataValidationResult =
        post("/studio/api/lowcode/library-feature/validate", command)

    suspend fun deleteLibraryFeature(id: Long): Boolean =
        envelope("/studio/api/lowcode/library-feature/delete?id=$id", Boolean.serializer()) { method = HttpMethod.Delete }

    suspend fun modelPage(command: ModelPageCommand): PageResult<ModelCommand> =
        post("/studio/api/lowcode/model/page", command)

    suspend fun model(id: Long): ModelCommand =
        envelope("/studio/api/lowcode/model/detail?id=$id", ModelCommand.serializer())

    suspend fun validateModel(command: ModelCommand): MetadataValidationResult =
        post("/studio/api/lowcode/model/validate", command)

    suspend fun saveModel(command: ModelCommand): Long = if (command.id == null) {
        post("/studio/api/lowcode/model/add", command)
    } else {
        put("/studio/api/lowcode/model/update", command)
    }

    suspend fun deleteModel(id: Long): Boolean =
        delete("/studio/api/lowcode/model", listOf(id), ListSerializer(Long.serializer()))

    suspend fun previewModel(id: Long): ModelPreview =
        envelope("/studio/api/lowcode/model/preview?id=$id", ModelPreview.serializer())

    suspend fun dtos(): List<DtoCommand> = post("/studio/api/lowcode/dto/list", JsonObject(emptyMap()))

    suspend fun dto(id: Long): DtoCommand = envelope("/studio/api/lowcode/dto/detail?id=$id", DtoCommand.serializer())

    suspend fun validateDto(command: DtoCommand): MetadataValidationResult =
        post("/studio/api/lowcode/dto/validate", command)

    suspend fun saveDto(command: DtoCommand): Long = if (command.id == null) {
        post("/studio/api/lowcode/dto/add", command)
    } else {
        put("/studio/api/lowcode/dto/update", command)
    }

    suspend fun deleteDto(id: Long): Boolean =
        delete("/studio/api/lowcode/dto", listOf(id), ListSerializer(Long.serializer()))

    suspend fun previewDto(id: Long): DtoPreview =
        envelope("/studio/api/lowcode/dto/preview?id=$id", DtoPreview.serializer())

    suspend fun conventionFiles(): List<ConventionFileView> =
        post("/studio/api/lowcode/convention-file/list", JsonObject(emptyMap()))

    suspend fun validateConventionFile(command: ConventionFileCommand): MetadataValidationResult =
        post("/studio/api/lowcode/convention-file/validate", command)

    suspend fun saveConventionFile(command: ConventionFileCommand): Long = if (command.id == null) {
        post("/studio/api/lowcode/convention-file/add", command)
    } else {
        put("/studio/api/lowcode/convention-file/update", command)
    }

    suspend fun deleteConventionFile(id: Long): Boolean =
        delete("/studio/api/lowcode/convention-file", listOf(id), ListSerializer(Long.serializer()))

    suspend fun constants(featureId: Long?): List<ConstantCommand> =
        post("/studio/api/lowcode/constant/list", ConstantListCommand(featureId))

    suspend fun validateConstant(command: ConstantCommand): MetadataValidationResult =
        post("/studio/api/lowcode/constant/validate", command)

    suspend fun saveConstant(command: ConstantCommand): ConstantCommand =
        post("/studio/api/lowcode/constant/save", command)

    suspend fun deleteConstant(id: Long): Boolean =
        delete("/studio/api/lowcode/constant", listOf(id), ListSerializer(Long.serializer()))

    suspend fun agents(): List<AgentDefinitionSummary> =
        post("/studio/api/lowcode/agent/list", JsonObject(emptyMap()))

    suspend fun agent(id: Long): AgentDefinitionCommand =
        envelope("/studio/api/lowcode/agent/detail?id=$id", AgentDefinitionCommand.serializer())

    suspend fun validateAgent(command: AgentDefinitionCommand): MetadataValidationResult =
        post("/studio/api/lowcode/agent/validate", command)

    suspend fun saveAgent(command: AgentDefinitionCommand): Long = if (command.id == null) {
        post("/studio/api/lowcode/agent/add", command)
    } else {
        put("/studio/api/lowcode/agent/update", command)
    }

    suspend fun deleteAgent(id: Long): Boolean =
        delete("/studio/api/lowcode/agent", listOf(id), ListSerializer(Long.serializer()))

    suspend fun agentSettings(): AgentProviderSettingsView =
        envelope("/studio/api/agent/settings", AgentProviderSettingsView.serializer())

    suspend fun updateAgentSettings(command: AgentProviderSettingsCommand): AgentProviderSettingsView =
        put("/studio/api/agent/settings", command)

    suspend fun agentModels(): List<AgentProviderModel> =
        envelope("/studio/api/agent/models", ListSerializer(AgentProviderModel.serializer()))

    suspend fun agentConversations(): List<AgentConversationView> =
        envelope("/studio/api/agent/conversations", ListSerializer(AgentConversationView.serializer()))

    suspend fun createAgentConversation(command: AgentConversationCommand): Long =
        post("/studio/api/agent/conversations", command)

    suspend fun updateAgentConversationModel(command: AgentConversationModelCommand): Boolean =
        put("/studio/api/agent/conversations/model", command)

    suspend fun deleteAgentConversation(id: Long): Boolean =
        delete("/studio/api/agent/conversations", listOf(id), ListSerializer(Long.serializer()))

    suspend fun agentMessages(id: Long): List<AgentMessageView> =
        envelope("/studio/api/agent/messages?id=$id", ListSerializer(AgentMessageView.serializer()))

    suspend fun applyMetadataPatch(command: MetadataPatchCommand): Int =
        post("/studio/api/lowcode/agent/display-text/apply", command)

    suspend fun openApi(path: String): JsonObject = client.get(path).body()

    suspend fun execute(command: ApiRequestCommand): ApiResponseView {
        val started = TimeSource.Monotonic.markNow()
        val response = client.request(command.url) {
            method = HttpMethod.parse(command.method.uppercase())
            command.headers.forEach { (name, value) -> header(name, value) }
            if (command.multipart.isNotEmpty()) {
                setBody(MultiPartFormDataContent(formData {
                    command.multipart.forEach { part ->
                        val bytes = part.bytes
                        if (bytes == null) {
                            append(part.name, part.value.orEmpty())
                        } else {
                            val headers = Headers.build {
                                part.fileName?.let { fileName ->
                                    append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter("filename", fileName).toString())
                                }
                                part.contentType?.let { contentType -> append(HttpHeaders.ContentType, contentType) }
                            }
                            append(part.name, bytes, headers)
                        }
                    }
                }))
            } else if (command.body != null) {
                setBody(command.body)
            }
        }
        val bytes = response.body<ByteArray>()
        val contentType = response.headers[HttpHeaders.ContentType]
        val binary = contentType?.let(::isBinaryContentType) == true
        return ApiResponseView(
            status = response.status.value,
            statusText = response.status.description,
            durationMillis = started.elapsedNow().inWholeMilliseconds,
            headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(", ") },
            bodyText = bytes.takeUnless { binary }?.decodeToString(),
            bytes = bytes.takeIf { binary },
            fileName = response.headers[HttpHeaders.ContentDisposition]?.let(::responseFileName),
            contentType = contentType,
            requestUrl = command.url,
            curl = renderCurl(command),
        )
    }

    private suspend inline fun <reified Request : Any, reified Response> post(path: String, command: Request): Response =
        envelope(path, serializer<Response>()) {
            method = HttpMethod.Post
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(serializer<Request>(), command))
        }

    private suspend inline fun <reified Request : Any, reified Response> put(path: String, command: Request): Response =
        envelope(path, serializer<Response>()) {
            method = HttpMethod.Put
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.encodeToString(serializer<Request>(), command))
        }

    private suspend inline fun <reified Request : Any, reified Response> delete(
        path: String,
        command: Request,
        requestSerializer: kotlinx.serialization.SerializationStrategy<Request>,
    ): Response = envelope(path, serializer<Response>()) {
        method = HttpMethod.Delete
        setBody(json.encodeToString(requestSerializer, command))
        header(HttpHeaders.ContentType, ContentType.Application.Json)
    }

    private suspend fun <T> envelope(
        path: String,
        deserializer: KSerializer<T>,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = client.request(path) {
            session.accessToken.takeIf(String::isNotBlank)?.let(::bearerAuth)
            block()
        }
        return response.requireEnvelope(deserializer)
    }

    private suspend fun <T> HttpResponse.requireEnvelope(deserializer: KSerializer<T>): T {
        val payload = bodyAsText()
        val result = runCatching {
            json.decodeFromString(CommonResult.serializer(deserializer), payload)
        }.getOrElse { cause ->
            throw StudioApiFailure(status.value, null, "平台服务返回了无效 JSON: ${cause.message.orEmpty()}")
        }
        if (result.code != 0) {
            throw StudioApiFailure(status.value, result.code, result.msg.ifBlank { "请求失败" })
        }
        if (status.value !in 200..299) {
            throw StudioApiFailure(status.value, result.code, result.msg.ifBlank { "HTTP ${status.value}" })
        }
        return result.data ?: throw StudioApiFailure(status.value, result.code, "平台服务返回空数据")
    }
}

@Serializable
private data class LibraryPage(
    val list: List<LibraryView> = emptyList(),
    val total: Long = 0,
)

@Serializable
private data class LibraryFeaturePage(
    val list: List<LibraryFeatureView> = emptyList(),
    val total: Long = 0,
)

private fun isBinaryContentType(value: String): Boolean {
    val contentType = value.substringBefore(';').trim().lowercase()
    return contentType == "application/octet-stream" ||
        contentType == "application/pdf" ||
        contentType == "application/zip" ||
        contentType.startsWith("application/vnd.") ||
        contentType.startsWith("image/") ||
        contentType.startsWith("audio/") ||
        contentType.startsWith("video/")
}

private fun responseFileName(value: String): String? =
    Regex("filename\\*?=(?:UTF-8''|\\\")?([^;\\\"]+)", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.get(1)
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun renderCurl(command: ApiRequestCommand): String = buildString {
    append("curl --request ")
    append(command.method.uppercase())
    append(" '")
    append(command.url.replace("'", "'\\''"))
    append("'")
    command.headers.forEach { (name, value) ->
        append(" \\\n  --header '")
        append(name)
        append(": ")
        append(value.replace("'", "'\\''"))
        append("'")
    }
    command.body?.let { body ->
        append(" \\\n  --data-raw '")
        append(body.replace("'", "'\\''"))
        append("'")
    }
    command.multipart.forEach { part ->
        append(" \\\n  --form '")
        append(part.name.replace("'", "'\\''"))
        append('=')
        if (part.bytes != null) append('@')
        append((part.fileName ?: part.value.orEmpty()).replace("'", "'\\''"))
        append("'")
    }
}
