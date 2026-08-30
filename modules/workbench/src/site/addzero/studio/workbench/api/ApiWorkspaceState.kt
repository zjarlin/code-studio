package site.addzero.studio.workbench.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodedPath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import site.addzero.studio.contract.ApiAuthSession
import site.addzero.studio.contract.ApiHistoryEntry
import site.addzero.studio.contract.ApiMultipartPart
import site.addzero.studio.contract.ApiRequestCommand
import site.addzero.studio.contract.ApiResponseView
import site.addzero.studio.workbench.browser.BrowserPort
import site.addzero.studio.workbench.browser.BrowserFile
import site.addzero.studio.workbench.transport.StudioApi
import site.addzero.studio.workbench.transport.StudioSessionState

@Single
class ApiWorkspaceState(
    private val api: StudioApi,
    private val session: StudioSessionState,
    private val browser: BrowserPort,
    private val json: Json,
) {
    internal var groups by mutableStateOf<List<ApiGroup>>(emptyList())
        private set
    internal var selectedOperation by mutableStateOf<ApiOperation?>(null)
        private set
    var filter by mutableStateOf("")
        private set
    var baseUrl by mutableStateOf("")
        private set
    var pathValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var queryValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var headerValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var multipartValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var multipartFiles by mutableStateOf<Map<String, BrowserFile>>(emptyMap())
        private set
    var bodyText by mutableStateOf("")
        private set
    var response by mutableStateOf<ApiResponseView?>(null)
        private set
    var history by mutableStateOf<List<ApiHistoryEntry>>(emptyList())
        private set
    var authSessions by mutableStateOf<List<ApiAuthSession>>(emptyList())
        private set
    var activeAuthSessionId by mutableStateOf<String?>(null)
        private set
    internal var codeClient by mutableStateOf(TypeScriptClient.AXIOS)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val documentationOnly: Boolean
        get() = browser.query.substringAfter('?').split('&').any { it == "mode=api-docs" }

    internal val filteredGroups: List<ApiGroup>
        get() {
            val keyword = filter.trim().lowercase()
            if (keyword.isEmpty()) return groups
            return groups.mapNotNull { group ->
                val operations = group.operations.filter { operation ->
                    listOf(group.name, operation.summary, operation.path, operation.method, operation.description.orEmpty())
                        .any { keyword in it.lowercase() }
                }
                operations.takeIf(List<ApiOperation>::isNotEmpty)?.let { ApiGroup(group.name, it) }
            }
        }

    val typeScriptSample: String
        get() = selectedOperation?.let { generateTypeScriptRequest(it, codeClient) }.orEmpty()

    suspend fun load() = runAction {
        val config = api.config()
        session.updateConfig(config)
        baseUrl = resolveBaseUrl(config.apiBaseUrl, browser.origin)
        lastDocument = api.openApi(config.openApiPath)
        groups = collectApiGroups(lastDocument)
        authSessions = readList(AUTH_SESSIONS_KEY, ApiAuthSession.serializer())
        history = readList(HISTORY_KEY, ApiHistoryEntry.serializer())
        activeAuthSessionId = browser.read(ACTIVE_AUTH_SESSION_KEY)
        activeAuthSessionId?.let(::activateAuthSession)
        groups.firstOrNull()?.operations?.firstOrNull()?.let(::selectOperation)
    }

    fun updateFilter(value: String) {
        filter = value
    }

    fun updateBaseUrl(value: String) {
        baseUrl = value
    }

    internal fun selectOperation(operation: ApiOperation) {
        selectedOperation = operation
        pathValues = operation.parameters.filter { it.location == "path" }.associate { it.name to "" }
        queryValues = operation.parameters.filter { it.location == "query" }.associate { it.name to "" }
        headerValues = operation.parameters.filter { it.location == "header" }.associate { it.name to "" }
        multipartValues = multipartFields(operation).associate { it.name to "" }
        multipartFiles = emptyMap()
        bodyText = requestBodySample(operation, groupsDocument())
        response = null
        error = null
    }

    fun updatePath(name: String, value: String) {
        pathValues = pathValues + (name to value)
    }

    fun updateQuery(name: String, value: String) {
        queryValues = queryValues + (name to value)
    }

    fun updateHeader(name: String, value: String) {
        headerValues = headerValues + (name to value)
    }

    fun updateMultipart(name: String, value: String) {
        multipartValues = multipartValues + (name to value)
    }

    suspend fun chooseMultipartFile(name: String) {
        browser.chooseFile()?.let { file -> multipartFiles = multipartFiles + (name to file) }
    }

    fun updateBody(value: String) {
        bodyText = value
    }

    internal fun selectCodeClient(value: TypeScriptClient) {
        codeClient = value
    }

    suspend fun execute() = runAction {
        val operation = selectedOperation ?: return@runAction
        validateRequiredValues(operation)
        val url = buildRequestUrl(baseUrl, operation.path, pathValues, queryValues)
        val token = authSessions.firstOrNull { it.id == activeAuthSessionId }?.token.orEmpty()
        val headers = buildMap {
            putAll(headerValues.filterValues(String::isNotBlank))
            if (token.isNotBlank() && keys.none { it.equals("Authorization", ignoreCase = true) }) {
                put("Authorization", "Bearer $token")
            }
            if (requestContentType(operation) != null && requestContentType(operation) != "multipart/form-data") {
                if (keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    put("Content-Type", requestContentType(operation).orEmpty())
                }
            }
        }
        val command = ApiRequestCommand(
            method = operation.method,
            url = url,
            headers = headers,
            body = bodyText.takeIf { it.isNotBlank() && operation.method !in setOf("get", "head") },
            multipart = multipartFields(operation).mapNotNull { field ->
                multipartFiles[field.name]?.let { file ->
                    ApiMultipartPart(
                        name = field.name,
                        bytes = file.bytes,
                        fileName = file.name,
                        contentType = file.contentType,
                    )
                } ?: multipartValues[field.name]?.takeIf(String::isNotBlank)?.let { value ->
                    ApiMultipartPart(name = field.name, value = value)
                }
            },
        )
        response = api.execute(command)
        response?.let { value ->
            history = (listOf(
                ApiHistoryEntry(
                    id = nextId("history"),
                    method = operation.method,
                    url = value.requestUrl,
                    status = value.status,
                    durationMillis = value.durationMillis,
                    createdAt = historyCounter,
                ),
            ) + history).take(100)
            persist(HISTORY_KEY, history, ApiHistoryEntry.serializer())
        }
    }

    fun downloadResponse() {
        val value = response ?: return
        val bytes = value.bytes ?: return
        browser.download(
            bytes = bytes,
            fileName = value.fileName ?: "download",
            contentType = value.contentType ?: "application/octet-stream",
        )
    }

    fun saveAuthSession(name: String, token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        val value = ApiAuthSession(nextId("auth"), name.trim().ifBlank { "未命名会话" }, trimmed)
        authSessions = authSessions + value
        persist(AUTH_SESSIONS_KEY, authSessions, ApiAuthSession.serializer())
        activateAuthSession(value.id)
    }

    fun deleteAuthSession(id: String) {
        authSessions = authSessions.filterNot { it.id == id }
        persist(AUTH_SESSIONS_KEY, authSessions, ApiAuthSession.serializer())
        if (activeAuthSessionId == id) {
            activeAuthSessionId = null
            browser.remove(ACTIVE_AUTH_SESSION_KEY)
            session.updateAccessToken("")
        }
    }

    fun activateAuthSession(id: String) {
        val auth = authSessions.firstOrNull { it.id == id } ?: return
        activeAuthSessionId = id
        browser.write(ACTIVE_AUTH_SESSION_KEY, id)
        session.updateAccessToken(auth.token)
    }

    private fun groupsDocument() = lastDocument

    private fun validateRequiredValues(operation: ApiOperation) {
        val missingParameters = operation.parameters
            .filter(ApiParameter::required)
            .filter { parameter ->
                when (parameter.location) {
                    "path" -> pathValues[parameter.name].isNullOrBlank()
                    "query" -> queryValues[parameter.name].isNullOrBlank()
                    "header" -> headerValues[parameter.name].isNullOrBlank()
                    else -> false
                }
            }
            .map(ApiParameter::name)
        val missingParts = multipartFields(operation)
            .filter(ApiParameter::required)
            .filter { field -> multipartValues[field.name].isNullOrBlank() && multipartFiles[field.name] == null }
            .map(ApiParameter::name)
        val missing = missingParameters + missingParts
        require(missing.isEmpty()) { "请填写必填字段：${missing.joinToString()}" }
    }

    private var lastDocument = kotlinx.serialization.json.JsonObject(emptyMap())
    private var idCounter = 0L
    private var historyCounter = 0L

    private fun nextId(prefix: String): String {
        idCounter += 1
        historyCounter += 1
        return "$prefix-$idCounter"
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        loading = true
        error = null
        try {
            block()
        } catch (cause: Throwable) {
            error = cause.message ?: "API Studio 操作失败"
        } finally {
            loading = false
        }
    }

    private fun <T> readList(key: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        browser.read(key)?.let { value ->
            runCatching { json.decodeFromString(ListSerializer(serializer), value) }.getOrDefault(emptyList())
        }.orEmpty()

    private fun <T> persist(key: String, values: List<T>, serializer: kotlinx.serialization.KSerializer<T>) {
        browser.write(key, json.encodeToString(ListSerializer(serializer), values))
    }
}

internal fun resolveBaseUrl(configured: String, origin: String): String {
    val value = configured.trim()
    if (value.isEmpty()) return origin.trimEnd('/')
    if (value.startsWith('/')) return origin.trimEnd('/') + value
    return value
}

internal fun buildRequestUrl(
    baseUrl: String,
    path: String,
    pathValues: Map<String, String>,
    queryValues: Map<String, String>,
): String {
    val renderedPath = Regex("\\{([^}]+)}").replace(path) { match ->
        pathValues[match.groupValues[1]].orEmpty().encodeURLParameter()
    }
    val builder = URLBuilder(baseUrl)
    val baseSegments = builder.encodedPath.split('/').filter(String::isNotBlank)
    val pathSegments = renderedPath.split('/').filter(String::isNotBlank)
    var overlap = minOf(baseSegments.size, pathSegments.size)
    while (overlap > 0 && baseSegments.takeLast(overlap) != pathSegments.take(overlap)) overlap -= 1
    builder.encodedPath = "/" + (baseSegments + pathSegments.drop(overlap)).joinToString("/")
    builder.parameters.clear()
    queryValues.filterValues(String::isNotBlank).forEach { (name, value) -> builder.parameters.append(name, value) }
    return builder.buildString()
}

private const val AUTH_SESSIONS_KEY = "studio.api.auth-sessions"
private const val ACTIVE_AUTH_SESSION_KEY = "studio.api.active-auth-session"
private const val HISTORY_KEY = "studio.api.history"
