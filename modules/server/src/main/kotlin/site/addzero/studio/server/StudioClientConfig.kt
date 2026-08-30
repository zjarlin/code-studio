package site.addzero.studio.server

import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.StudioConfig
import site.addzero.studio.contract.MetadataContributorSummary
import site.addzero.studio.contract.StudioClientConfig

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
