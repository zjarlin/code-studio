package site.addzero.studio.server.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.contract.CATALOG_CONTRIBUTION_RESOURCE
import site.addzero.studio.contract.CatalogContributions
import site.addzero.studio.contract.LsiCatalogEntry
import site.addzero.studio.contract.LsiCatalogEntryKind
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class CatalogResourcesTest {
    @Test
    fun `从多个制品资源合并目录约定`(@TempDir workspace: Path) {
        val first = workspace.resolve("first")
        val second = workspace.resolve("second")
        writeCatalog(first, sceneAndRoute())
        writeCatalog(second, element())

        URLClassLoader(arrayOf(first.toUri().toURL(), second.toUri().toURL()), null).use { classLoader ->
            val entries = CatalogResources.load(classLoader)

            assertEquals(
                listOf("studio", "studio.library", "studio.library.create"),
                entries.map(LsiCatalogEntry::key),
            )
        }
    }

    private fun writeCatalog(root: Path, entries: List<LsiCatalogEntry>) {
        val target = root.resolve(CATALOG_CONTRIBUTION_RESOURCE)
        Files.createDirectories(target.parent)
        Files.writeString(target, CatalogContributions.encode(entries))
    }

    private fun sceneAndRoute(): List<LsiCatalogEntry> = listOf(
        LsiCatalogEntry(
            routeKey = "studio",
            path = "/console/studio/library",
            kind = LsiCatalogEntryKind.SCENE,
            name = "Studio",
        ),
        LsiCatalogEntry(
            routeKey = "studio.library",
            path = "/console/studio/library",
            parentKey = "studio",
            kind = LsiCatalogEntryKind.ROUTE,
            name = "库",
        ),
    )

    private fun element(): List<LsiCatalogEntry> = listOf(
        LsiCatalogEntry(
            routeKey = "studio.library",
            elementKey = "studio.library.create",
            parentKey = "studio.library",
            kind = LsiCatalogEntryKind.ELEMENT,
            name = "新建库",
        ),
    )
}
