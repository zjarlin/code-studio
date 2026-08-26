package site.addzero.studio.server

import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.StudioConfig

/** 浏览器启动 Studio 所需的单宿主配置。 */
data class StudioClientConfig(
    val contributorId: String,
    val displayName: String,
    val apiBaseUrl: String,
    val openApiPath: String,
    val editableContributorId: String,
    val capabilities: Set<String>,
)

/** 浏览器可见的元数据贡献概要。 */
data class MetadataContributorSummary(
    val id: String,
    val requires: List<String>,
    val editable: Boolean,
)

internal fun StudioConfig.toClientConfig(): StudioClientConfig = StudioClientConfig(
    contributorId = contributorId,
    displayName = displayName,
    apiBaseUrl = apiBaseUrl,
    openApiPath = openApiPath,
    editableContributorId = editableContributorId,
    capabilities = capabilities.toSortedSet(),
)

internal fun MetadataContributor.toSummary(editableContributorId: String): MetadataContributorSummary =
    MetadataContributorSummary(
        id = id,
        requires = requires.sorted(),
        editable = id == editableContributorId,
    )
