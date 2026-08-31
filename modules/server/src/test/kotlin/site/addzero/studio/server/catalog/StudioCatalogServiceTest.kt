package site.addzero.studio.server.catalog

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import site.addzero.studio.contract.LsiCatalogEntry
import site.addzero.studio.contract.LsiCatalogEntryKind
import site.addzero.studio.runtime.StudioAccessRequest
import site.addzero.studio.runtime.StudioPermissionPolicy

class StudioCatalogServiceTest {
    @Test
    fun `合并覆盖后按宿主权限过滤并修正场景默认路径`() = runBlocking {
        val checkedPermissions = mutableListOf<String>()
        val service = StudioCatalogService(
            baseEntries = entries(),
            overrideReader = CatalogOverrideReader {
                CatalogOverrides(
                    routes = mapOf(
                        "studio.library" to CatalogEntryOverride(
                            name = "资源库",
                            icon = "database",
                            order = 5,
                        ),
                    ),
                    elements = mapOf(
                        "studio.library.create" to CatalogEntryOverride(
                            permissions = emptyList(),
                        ),
                    ),
                )
            },
            permissionPolicy = StudioPermissionPolicy { _, permission ->
                checkedPermissions += permission
                permission == "library:read"
            },
        )

        val result = service.entries(request())

        assertEquals(
            listOf("studio", "studio.library", "studio.library.create"),
            result.map(LsiCatalogEntry::key),
        )
        assertEquals("/console/studio/library", result.first().path)
        assertEquals("资源库", result[1].name)
        assertEquals("database", result[1].icon)
        assertEquals("/console/studio/library", result[1].path)
        assertEquals(emptyList<String>(), result.last().permissions)
        assertEquals(listOf("library:read", "library:write"), checkedPermissions)
    }

    private fun entries(): List<LsiCatalogEntry> = listOf(
        LsiCatalogEntry(
            routeKey = "studio",
            path = "/console/studio/api-docs",
            kind = LsiCatalogEntryKind.SCENE,
            name = "Studio",
        ),
        LsiCatalogEntry(
            routeKey = "studio.library",
            path = "/console/studio/library",
            parentKey = "studio",
            kind = LsiCatalogEntryKind.ROUTE,
            name = "库",
            permissions = listOf("library:read"),
        ),
        LsiCatalogEntry(
            routeKey = "studio.api-docs",
            path = "/console/studio/api-docs",
            parentKey = "studio",
            kind = LsiCatalogEntryKind.ROUTE,
            name = "API 文档",
            permissions = listOf("library:write"),
        ),
        LsiCatalogEntry(
            routeKey = "studio.library",
            elementKey = "studio.library.create",
            parentKey = "studio.library",
            kind = LsiCatalogEntryKind.ELEMENT,
            name = "新建库",
            permissions = listOf("library:write"),
        ),
    )

    private fun request(): StudioAccessRequest = StudioAccessRequest(
        method = "GET",
        path = "/console/api/catalog",
        headers = emptyMap(),
    )
}
