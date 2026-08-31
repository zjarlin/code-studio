package site.addzero.studio.server.catalog

import site.addzero.studio.contract.LsiCatalogEntry
import site.addzero.studio.runtime.StudioAccessRequest

/** 返回当前请求可见的后台目录。 */
fun interface StudioCatalogProvider {
    suspend fun entries(request: StudioAccessRequest): List<LsiCatalogEntry>

    companion object {
        val EMPTY: StudioCatalogProvider = StudioCatalogProvider { emptyList() }
    }
}
