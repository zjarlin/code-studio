package site.addzero.studio.runtime

import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URL
import java.util.Collections

const val GENERATION_TARGET_PROFILE_RESOURCE: String = "META-INF/code-studio/target-profile.json"

/** 读取构建期与运行时共享的生成目标，拒绝依赖 JAR 携带不一致配置。 */
object GenerationTargetProfiles {
    fun load(
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader
            ?: GenerationTargetProfiles::class.java.classLoader,
    ): GenerationTargetProfile {
        val resources = Collections.list(classLoader.getResources(GENERATION_TARGET_PROFILE_RESOURCE))
            .sortedBy(URL::toExternalForm)
        require(resources.isNotEmpty()) {
            "classpath 缺少 $GENERATION_TARGET_PROFILE_RESOURCE"
        }
        val profiles = resources.map(::read)
        val expected = profiles.first()
        val mismatches = resources.zip(profiles)
            .filter { (_, profile) -> profile != expected }
            .map { (resource, _) -> resource.toExternalForm() }
        require(mismatches.isEmpty()) {
            "classpath 包含不一致的 GenerationTargetProfile: ${mismatches.joinToString()}"
        }
        return expected
    }

    fun read(resource: URL): GenerationTargetProfile = runCatching {
        resource.openStream().bufferedReader().use { reader -> decode(reader.readText()) }
    }.getOrElse { cause ->
        throw IllegalArgumentException("GenerationTargetProfile 读取失败: $resource", cause)
    }

    fun decode(content: String): GenerationTargetProfile {
        val tree = objectMapper.readTree(content)
        require(tree.isObject) { "GenerationTargetProfile 必须是 JSON 对象" }
        val unknownFields = tree.propertyNames().asSequence().toSet() - PROFILE_FIELDS
        require(unknownFields.isEmpty()) {
            "GenerationTargetProfile 包含未知字段: ${unknownFields.sorted().joinToString()}"
        }
        return objectMapper.treeToValue(tree, GenerationTargetProfile::class.java).canonical()
    }

    fun encode(profile: GenerationTargetProfile): String =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile.canonical()) + "\n"

    private fun GenerationTargetProfile.canonical(): GenerationTargetProfile = copy(
        symbols = symbols.toSortedMap(),
        capabilities = capabilities.toSortedSet(),
    )

    private val objectMapper = jacksonObjectMapper()
    private val PROFILE_FIELDS = setOf("id", "symbols", "capabilities")
}
