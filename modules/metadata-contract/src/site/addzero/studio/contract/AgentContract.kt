package site.addzero.studio.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentDefinitionSummary(
    val id: Long,
    val agentCode: String,
    val name: String,
    val modelCode: String,
    val status: Int,
    val version: Int,
)

@Serializable
data class AgentStructuredOutputCommand(
    val name: String,
    val description: String? = null,
    val schema: JsonObject,
    val strict: Boolean = true,
)

@Serializable
data class AgentDefinitionCommand(
    val id: Long? = null,
    val agentCode: String,
    val name: String,
    val modelCode: String,
    val instructions: String,
    val toolCodes: List<String> = emptyList(),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val structuredOutput: AgentStructuredOutputCommand,
    val status: Int = 1,
    val version: Int = 1,
    val description: String? = null,
)

@Serializable
data class AgentProviderSettingsView(
    val baseUrl: String,
    val apiKeyConfigured: Boolean,
    val apiKeyMasked: String? = null,
)

@Serializable
data class AgentProviderSettingsCommand(
    val baseUrl: String,
    val apiKey: String? = null,
)

@Serializable
data class AgentProviderModel(
    val id: String,
    val contextWindow: Int,
    val contextWindowEstimated: Boolean,
)

@Serializable
data class AgentConversationView(
    val id: Long,
    val externalId: String,
    val title: String,
    val modelId: String? = null,
    val createTime: String,
    val updateTime: String? = null,
)

@Serializable
data class AgentConversationCommand(
    val title: String? = null,
    val modelId: String,
)

@Serializable
data class AgentConversationModelCommand(
    val conversationId: Long,
    val modelId: String,
)

@Serializable
enum class AgentReasoningEffort { PROVIDER, MINIMAL, LOW, MEDIUM, HIGH, XHIGH }

@Serializable
enum class AgentChatMode { AUTO, CONFIGURATION, DISPLAY_TEXT }

@Serializable
data class AgentChatCommand(
    val conversationId: Long,
    val text: String,
    val modelId: String,
    val reasoningEffort: AgentReasoningEffort = AgentReasoningEffort.PROVIDER,
    val mode: AgentChatMode = AgentChatMode.AUTO,
    val contextSnapshotId: String? = null,
)

@Serializable
data class AgentContextUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val contextWindow: Int,
    val contextWindowEstimated: Boolean,
    val compactedMessages: Int,
)

@Serializable
data class AgentContextResourceReference(
    val type: String,
    val id: String,
)

@Serializable
data class AgentContextSnapshotCommand(
    val scene: String,
    @SerialName("resource_refs") val resourceRefs: List<AgentContextResourceReference> = emptyList(),
    val draft: JsonObject? = null,
    val state: JsonObject? = null,
)

@Serializable
data class AgentContextSnapshotView(
    val id: String,
    @SerialName("expires_at") val expiresAt: Long,
)

@Serializable
data class AgentEvent(
    val event: String,
    val data: String,
    val id: String? = null,
)

@Serializable
data class AgentMessageView(
    val id: String,
    val role: String,
    val parts: List<JsonObject> = emptyList(),
)

@Serializable
data class MetadataPatchCommand(
    val tableId: String,
    val revision: String,
    val patches: List<JsonObject>,
    val questions: List<String> = emptyList(),
)
