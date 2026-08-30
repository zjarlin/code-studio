package site.addzero.studio.workbench.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single
import site.addzero.studio.contract.AgentConversationCommand
import site.addzero.studio.contract.AgentConversationView
import site.addzero.studio.contract.AgentDefinitionCommand
import site.addzero.studio.contract.AgentDefinitionSummary
import site.addzero.studio.contract.AgentEvent
import site.addzero.studio.contract.AgentMessageView
import site.addzero.studio.contract.AgentProviderModel
import site.addzero.studio.contract.AgentProviderSettingsCommand
import site.addzero.studio.contract.AgentProviderSettingsView
import site.addzero.studio.contract.AgentStructuredOutputCommand
import site.addzero.studio.contract.MetadataPatchCommand
import site.addzero.studio.contract.MetadataValidationResult
import site.addzero.studio.workbench.transport.AgentStreamPort
import site.addzero.studio.workbench.transport.StudioApi

enum class AgentSection { DEFINITIONS, CHAT, SETTINGS }

@Single
class AgentWorkspaceState(
    private val api: StudioApi,
    private val stream: AgentStreamPort,
    private val json: Json,
) {
    var section by mutableStateOf(AgentSection.CHAT)
        private set
    var definitions by mutableStateOf<List<AgentDefinitionSummary>>(emptyList())
        private set
    var definitionDraft by mutableStateOf<AgentDefinitionCommand?>(null)
        private set
    var conversations by mutableStateOf<List<AgentConversationView>>(emptyList())
        private set
    var messages by mutableStateOf<List<AgentMessageView>>(emptyList())
        private set
    var models by mutableStateOf<List<AgentProviderModel>>(emptyList())
        private set
    var settings by mutableStateOf<AgentProviderSettingsView?>(null)
        private set
    var selectedConversationId by mutableStateOf<Long?>(null)
        private set
    var prompt by mutableStateOf("")
        private set
    var streamedText by mutableStateOf("")
        private set
    var events by mutableStateOf<List<AgentEvent>>(emptyList())
        private set
    var responseId by mutableStateOf<String?>(null)
        private set
    var validation by mutableStateOf<MetadataValidationResult?>(null)
        private set
    var appliedPatchKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var streaming by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load() = runAction {
        definitions = api.agents()
        conversations = api.agentConversations()
        models = api.agentModels()
        settings = api.agentSettings()
        selectedConversationId = selectedConversationId ?: conversations.firstOrNull()?.id
        selectedConversationId?.let { messages = api.agentMessages(it) }
    }

    fun selectSection(value: AgentSection) {
        section = value
    }

    suspend fun selectDefinition(id: Long) = runAction {
        definitionDraft = api.agent(id)
        dirty = false
    }

    fun newDefinition() {
        definitionDraft = AgentDefinitionCommand(
            agentCode = "",
            name = "",
            modelCode = models.firstOrNull()?.id.orEmpty(),
            instructions = "",
            structuredOutput = AgentStructuredOutputCommand(
                name = "result",
                schema = JsonObject(emptyMap()),
            ),
        )
        dirty = true
    }

    fun editDefinition(value: AgentDefinitionCommand) {
        definitionDraft = value
        validation = null
        dirty = true
    }

    suspend fun saveDefinition() = runAction {
        val command = definitionDraft ?: return@runAction
        validation = api.validateAgent(command)
        if (validation?.valid == true) {
            api.saveAgent(command)
            dirty = false
            definitions = api.agents()
        }
    }

    suspend fun deleteDefinition() = runAction {
        definitionDraft?.id?.let { api.deleteAgent(it) }
        definitionDraft = null
        dirty = false
        definitions = api.agents()
    }

    suspend fun selectConversation(id: Long) = runAction {
        selectedConversationId = id
        messages = api.agentMessages(id)
        events = emptyList()
        streamedText = ""
    }

    suspend fun createConversation() = runAction {
        val modelId = models.firstOrNull()?.id ?: error("请先配置可用模型")
        val id = api.createAgentConversation(AgentConversationCommand(modelId = modelId))
        conversations = api.agentConversations()
        selectConversation(id)
    }

    suspend fun deleteConversation(id: Long) = runAction {
        api.deleteAgentConversation(id)
        conversations = api.agentConversations()
        selectedConversationId = conversations.firstOrNull()?.id
        messages = selectedConversationId?.let { api.agentMessages(it) }.orEmpty()
    }

    fun updatePrompt(value: String) {
        prompt = value
    }

    suspend fun send() {
        val conversation = conversations.firstOrNull { it.id == selectedConversationId }
            ?: throw IllegalStateException("请先新建会话")
        val text = prompt.trim()
        if (text.isEmpty()) return
        val input = JsonObject(mapOf(
            "model" to json.parseToJsonElement(json.encodeToString(String.serializer(), conversation.modelId ?: models.first().id)),
            "input" to json.parseToJsonElement(json.encodeToString(String.serializer(), text)),
            "conversation" to json.parseToJsonElement(json.encodeToString(String.serializer(), conversation.externalId)),
            "stream" to kotlinx.serialization.json.JsonPrimitive(true),
        ))
        prompt = ""
        events = emptyList()
        streamedText = ""
        streaming = true
        error = null
        try {
            stream.create(input).collect(::mergeEvent)
            messages = api.agentMessages(conversation.id)
        } catch (cause: Throwable) {
            error = cause.message ?: "暂时无法回答你的问题"
        } finally {
            streaming = false
        }
    }

    suspend fun cancel() {
        stream.cancelActive()
        responseId?.let { stream.cancel(it) }
        streaming = false
    }

    suspend fun applyPatch(command: MetadataPatchCommand) = runAction {
        require(command.questions.isEmpty()) { "Patch 仍有待确认问题" }
        api.applyMetadataPatch(command)
        appliedPatchKeys = appliedPatchKeys + command.key
    }

    fun metadataPatch(part: JsonObject): MetadataPatchCommand? {
        return decodeMetadataPatchPart(part, json)
    }

    suspend fun updateSettings(baseUrl: String, apiKey: String?) = runAction {
        settings = api.updateAgentSettings(AgentProviderSettingsCommand(baseUrl, apiKey))
        models = api.agentModels()
    }

    private fun mergeEvent(event: AgentEvent) {
        events = events + event
        val snapshot = reduceAgentEvent(streamedText, responseId, event, json)
        streamedText = snapshot.text
        responseId = snapshot.responseId
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        loading = true
        error = null
        try {
            block()
        } catch (cause: Throwable) {
            error = cause.message ?: "Agent 操作失败"
        } finally {
            loading = false
        }
    }
}

internal val MetadataPatchCommand.key: String
    get() = "$tableId:$revision"

internal fun decodeMetadataPatchPart(part: JsonObject, json: Json): MetadataPatchCommand? {
    if (part["type"]?.jsonPrimitive?.content != "data-metadata-patch") return null
    val data = part["data"] ?: return null
    return runCatching {
        json.decodeFromJsonElement(MetadataPatchCommand.serializer(), data)
    }.getOrNull()
}

internal data class AgentStreamSnapshot(
    val text: String,
    val responseId: String?,
)

internal fun reduceAgentEvent(
    currentText: String,
    currentResponseId: String?,
    event: AgentEvent,
    json: Json,
): AgentStreamSnapshot {
    val value = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrNull()
        ?: return AgentStreamSnapshot(currentText, currentResponseId)
    val responseId = value["response_id"]?.jsonPrimitive?.content
        ?: (value["response"] as? JsonObject)?.get("id")?.jsonPrimitive?.content
        ?: currentResponseId
    val delta = value["delta"]?.jsonPrimitive?.content.orEmpty()
    return AgentStreamSnapshot(currentText + delta, responseId)
}
