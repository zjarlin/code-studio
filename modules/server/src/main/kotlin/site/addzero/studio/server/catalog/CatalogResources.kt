package site.addzero.studio.server.catalog

import site.addzero.studio.contract.CATALOG_CONTRIBUTION_RESOURCE
import site.addzero.studio.contract.CatalogContributions
import site.addzero.studio.contract.LsiCatalogEntry
import java.net.URL
import java.util.Collections

internal object CatalogResources {
    fun load(
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: CatalogResources::class.java.classLoader,
    ): List<LsiCatalogEntry> {
        val resources = Collections.list(classLoader.getResources(CATALOG_CONTRIBUTION_RESOURCE))
        val contributions = resources.map(::read)
        return CatalogContributions.resolve(contributions)
    }

    internal fun read(resource: URL): List<LsiCatalogEntry> = runCatching {
        val content = resource.openStream().bufferedReader().use { reader -> reader.readText() }
        CatalogContributions.decode(content)
    }.getOrElse { cause ->
        throw IllegalArgumentException("目录贡献读取失败: $resource", cause)
    }
}
