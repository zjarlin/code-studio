package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
data class StudioClientConfig(
    val contributorId: String,
    val displayName: String,
    val apiBaseUrl: String,
    val openApiPath: String,
    val editableContributorId: String,
    val capabilities: Set<String>,
)

@Serializable
data class MetadataContributorSummary(
    val id: String,
    val requires: List<String> = emptyList(),
    val editable: Boolean,
)

@Serializable
enum class StudioWorkspace {
    LIBRARY,
    AGENT,
    API,
}
