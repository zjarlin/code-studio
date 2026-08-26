package site.addzero.studio.runtime

import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URL
import java.util.Collections
import java.util.PriorityQueue

/** 从 classpath 读取并解析元数据贡献依赖图。 */
object MetadataContributors {
    private val objectMapper = jacksonObjectMapper()
    private val manifestFields = setOf("formatVersion", "id", "migrationLocation", "requires")

    fun load(
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: MetadataContributors::class.java.classLoader,
    ): List<MetadataContributor> {
        val resources = Collections.list(classLoader.getResources(METADATA_CONTRIBUTOR_RESOURCE))
        val contributors = resources.map(::read)
        return resolve(contributors)
    }

    fun read(resource: URL): MetadataContributor = runCatching {
        resource.openStream().use { input ->
            val manifest = objectMapper.readTree(input)
            require(manifest.isObject) {
                "元数据贡献清单必须是 JSON 对象"
            }
            val unknownFields = manifest.propertyNames().asSequence().toSet() - manifestFields
            require(unknownFields.isEmpty()) {
                "元数据贡献清单包含未知字段: ${unknownFields.sorted().joinToString()}"
            }
            objectMapper.treeToValue(manifest, MetadataContributor::class.java)
        }
    }.getOrElse { cause ->
        throw IllegalArgumentException("元数据贡献清单读取失败: $resource", cause)
    }

    fun resolve(contributors: Iterable<MetadataContributor>): List<MetadataContributor> {
        val contributorsById = linkedMapOf<String, MetadataContributor>()
        contributors.forEach { contributor ->
            val previous = contributorsById.putIfAbsent(contributor.id, contributor)
            require(previous == null) {
                "元数据贡献 id 重复: ${contributor.id}"
            }
        }

        contributorsById.values.forEach { contributor ->
            val missing = contributor.requires.filterNot(contributorsById::containsKey)
            require(missing.isEmpty()) {
                "元数据贡献 ${contributor.id} 缺少依赖: ${missing.sorted().joinToString()}"
            }
        }

        val dependentIds = contributorsById.keys.associateWith { mutableListOf<String>() }
        val remainingDependencies = contributorsById.values.associate { contributor ->
            contributor.id to contributor.requires.size
        }.toMutableMap()
        contributorsById.values.forEach { contributor ->
            contributor.requires.forEach { requiredId ->
                dependentIds.getValue(requiredId) += contributor.id
            }
        }

        val ready = PriorityQueue<String>()
        remainingDependencies.filterValues { count -> count == 0 }.keys.forEach(ready::add)
        val result = mutableListOf<MetadataContributor>()
        while (ready.isNotEmpty()) {
            val contributorId = ready.remove()
            result += contributorsById.getValue(contributorId)
            dependentIds.getValue(contributorId).sorted().forEach { dependentId ->
                val remaining = remainingDependencies.getValue(dependentId) - 1
                remainingDependencies[dependentId] = remaining
                if (remaining == 0) {
                    ready += dependentId
                }
            }
        }

        val unresolved = contributorsById.keys - result.map(MetadataContributor::id).toSet()
        require(unresolved.isEmpty()) {
            "元数据贡献依赖存在环: ${unresolved.sorted().joinToString()}"
        }
        return result
    }

    fun uniqueRoot(contributors: Iterable<MetadataContributor>): MetadataContributor {
        val orderedContributors = resolve(contributors)
        val requiredIds = orderedContributors.flatMap(MetadataContributor::requires).toSet()
        val roots = orderedContributors.filter { contributor -> contributor.id !in requiredIds }
        require(roots.size == 1) {
            val rootIds = roots.map(MetadataContributor::id).sorted()
            "元数据贡献闭包必须有且仅有一个根，当前为: ${rootIds.ifEmpty { listOf("<none>") }.joinToString()}"
        }
        return roots.single()
    }

}
