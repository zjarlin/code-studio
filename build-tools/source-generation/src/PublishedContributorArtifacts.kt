package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Classpath
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshot
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import site.addzero.studio.runtime.GENERATION_TARGET_PROFILE_RESOURCE
import site.addzero.studio.runtime.GenerationTargetProfile
import site.addzero.studio.runtime.GenerationTargetProfiles
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_RESOURCE
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import site.addzero.studio.runtime.metadataSnapshotResource
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively

internal data class PublishedContributorArtifact(
    val archive: Path,
    val contributor: MetadataContributor,
    val snapshot: LowcodeMetadataSnapshot,
    val targetProfile: GenerationTargetProfile,
)

internal fun readPublishedContributorArtifacts(
    classpath: Classpath,
    repositoryRoot: Path,
): Map<String, PublishedContributorArtifact> {
    val normalizedRepositoryRoot = repositoryRoot.toAbsolutePath().normalize()
    val artifacts = linkedMapOf<String, PublishedContributorArtifact>()
    classpath.resolvedFiles
        .asSequence()
        .map { path -> path.toAbsolutePath().normalize() }
        .filter { path -> !path.startsWith(normalizedRepositoryRoot) }
        .filter { path -> path.fileName.toString().endsWith(".jar", ignoreCase = true) }
        .sortedBy(Path::toString)
        .mapNotNull(::readPublishedContributorArtifact)
        .forEach { artifact ->
            val previous = artifacts.putIfAbsent(artifact.contributor.id, artifact)
            require(previous == null) {
                "中央元数据贡献 id 重复: ${artifact.contributor.id} (${previous?.archive}, ${artifact.archive})"
            }
        }
    validatePublishedContributorClosure(artifacts)
    validatePublishedMigrationNames(artifacts.values)
    return artifacts
}

internal fun requirePublishedTargetProfile(
    artifacts: Iterable<PublishedContributorArtifact>,
    expected: GenerationTargetProfile,
) {
    val mismatches = artifacts
        .filter { artifact -> artifact.targetProfile != expected }
        .map { artifact -> artifact.archive }
        .sorted()
    require(mismatches.isEmpty()) {
        "中央 contributor 携带不一致的 GenerationTargetProfile: ${mismatches.joinToString()}"
    }
}

@OptIn(ExperimentalPathApi::class)
internal fun extractPublishedContributorMigrations(
    artifacts: Iterable<PublishedContributorArtifact>,
    outputDirectory: Path,
): Map<String, Path> {
    outputDirectory.deleteRecursively()
    outputDirectory.createDirectories()
    return artifacts
        .sortedBy { artifact -> artifact.contributor.id }
        .associate { artifact ->
            val targetDirectory = outputDirectory.resolve(artifact.contributor.id)
            extractMigrations(artifact, targetDirectory)
            artifact.contributor.id to targetDirectory
        }
}

private fun readPublishedContributorArtifact(archive: Path): PublishedContributorArtifact? =
    JarFile(archive.toFile()).use { jar ->
        val manifestEntry = jar.getJarEntry(METADATA_CONTRIBUTOR_RESOURCE) ?: return null
        require(!manifestEntry.isDirectory) {
            "中央 contributor manifest 不能是目录: $archive"
        }
        val contributorUrl = jarResourceUrl(archive, METADATA_CONTRIBUTOR_RESOURCE)
        val contributor = MetadataContributors.read(contributorUrl)
        val snapshotPath = metadataSnapshotResource(contributor.id)
        val snapshotEntry = requireNotNull(jar.getJarEntry(snapshotPath)) {
            "中央 contributor 缺少 canonical snapshot: $archive!/$snapshotPath"
        }
        val snapshotContent = jar.getInputStream(snapshotEntry).bufferedReader().use { reader -> reader.readText() }
        val snapshot = LowcodeMetadataSnapshots.decode(snapshotContent)
        require(snapshot.contributorId == contributor.id) {
            "中央 snapshot 归属 ${snapshot.contributorId} 与 manifest ${contributor.id} 不一致: $archive"
        }
        require(snapshot.contributorIds.containsAll(contributor.requires)) {
            "中央 snapshot ${contributor.id} 缺少 manifest.requires: " +
                (contributor.requires - snapshot.contributorIds.toSet()).sorted().joinToString()
        }
        require(snapshotContent == LowcodeMetadataSnapshots.encode(snapshot)) {
            "中央 contributor snapshot 不是 canonical 格式: $archive!/$snapshotPath"
        }
        val profileEntry = requireNotNull(jar.getJarEntry(GENERATION_TARGET_PROFILE_RESOURCE)) {
            "中央 contributor 缺少 GenerationTargetProfile: $archive!/$GENERATION_TARGET_PROFILE_RESOURCE"
        }
        val profileContent = jar.getInputStream(profileEntry).bufferedReader().use { reader -> reader.readText() }
        val targetProfile = GenerationTargetProfiles.decode(profileContent)
        require(migrationEntries(jar, contributor).isNotEmpty()) {
            "中央 contributor 缺少元数据迁移: $archive (${contributor.id})"
        }
        PublishedContributorArtifact(archive, contributor, snapshot, targetProfile)
    }

private fun extractMigrations(
    artifact: PublishedContributorArtifact,
    targetDirectory: Path,
) {
    JarFile(artifact.archive.toFile()).use { jar ->
        migrationEntries(jar, artifact.contributor).forEach { entryName ->
            val prefix = migrationPrefix(artifact.contributor)
            val relativePath = safeMigrationRelativePath(artifact.archive, entryName, prefix)
            val target = targetDirectory.resolve(relativePath).normalize()
            require(target.startsWith(targetDirectory.normalize())) {
                "中央 contributor 迁移路径越界: ${artifact.archive}!/$entryName"
            }
            target.createParentDirectories()
            jar.getInputStream(jar.getJarEntry(entryName)).use { input ->
                target.toFile().outputStream().use(input::copyTo)
            }
        }
    }
}

private fun migrationEntries(
    jar: JarFile,
    contributor: MetadataContributor,
): List<String> {
    val prefix = migrationPrefix(contributor)
    val entries = jar.entries().asSequence()
        .filter { entry -> !entry.isDirectory && entry.name.startsWith(prefix) }
        .map { entry -> entry.name }
        .sorted()
        .toList()
    entries.forEach { entryName ->
        require(entryName.endsWith(".sql", ignoreCase = true)) {
            "中央 contributor 迁移目录只能包含 SQL 文件: $entryName"
        }
        safeMigrationRelativePath(Path.of(jar.name), entryName, prefix)
    }
    return entries
}

private fun validatePublishedContributorClosure(artifacts: Map<String, PublishedContributorArtifact>) {
    artifacts.values.forEach { artifact ->
        val closureIds = linkedSetOf<String>()
        val pending = ArrayDeque(listOf(artifact.contributor.id))
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!closureIds.add(id)) {
                continue
            }
            val contributor = requireNotNull(artifacts[id]?.contributor) {
                "中央 contributor ${artifact.contributor.id} 缺少依赖制品: $id"
            }
            contributor.requires.forEach(pending::addLast)
        }
        require(artifact.snapshot.contributorIds.toSet() == closureIds) {
            "中央 contributor snapshot 闭包漂移: ${artifact.contributor.id}，" +
                "期望 ${closureIds.sorted().joinToString()}，实际 ${artifact.snapshot.contributorIds.sorted().joinToString()}"
        }
    }
    MetadataContributors.resolve(artifacts.values.map(PublishedContributorArtifact::contributor))
}

private fun validatePublishedMigrationNames(artifacts: Iterable<PublishedContributorArtifact>) {
    val versions = linkedMapOf<String, String>()
    val repeatables = linkedMapOf<String, String>()
    artifacts.sortedBy { artifact -> artifact.contributor.id }.forEach { artifact ->
        JarFile(artifact.archive.toFile()).use { jar ->
            migrationEntries(jar, artifact.contributor).forEach { entryName ->
                val name = Path.of(entryName).fileName.toString()
                val versioned = VERSIONED_MIGRATION.matchEntire(name)
                val repeatable = REPEATABLE_MIGRATION.matchEntire(name)
                require(versioned != null || repeatable != null) {
                    "中央 contributor 迁移名不符合 Flyway 规范: ${artifact.archive}!/$entryName"
                }
                if (repeatable != null) {
                    val description = repeatable.groupValues[1].lowercase()
                    val location = "${artifact.archive}!/$entryName"
                    val previous = repeatables.putIfAbsent(description, location)
                    require(previous == null) {
                        "中央 contributor Flyway repeatable $description 冲突: $previous, $location"
                    }
                } else {
                    val version = versioned!!.groupValues[1].replace(Regex("[._]"), ".")
                    val location = "${artifact.archive}!/$entryName"
                    val previous = versions.putIfAbsent(version, location)
                    require(previous == null) {
                        "中央 contributor Flyway version $version 冲突: $previous, $location"
                    }
                }
            }
        }
    }
}

private fun safeMigrationRelativePath(
    archive: Path,
    entryName: String,
    prefix: String,
): Path {
    val relativePath = Path.of(entryName.removePrefix(prefix))
    require(!relativePath.isAbsolute && relativePath.none { segment -> segment.toString() == ".." }) {
        "中央 contributor 迁移路径越界: $archive!/$entryName"
    }
    return relativePath
}

private fun migrationPrefix(contributor: MetadataContributor): String =
    contributor.migrationLocation.removePrefix("classpath:").trimEnd('/') + "/"

private fun jarResourceUrl(archive: Path, resource: String) =
    java.net.URI.create("jar:${archive.toUri()}!/$resource").toURL()

private val VERSIONED_MIGRATION = Regex("V(.+)__[^/]+\\.sql", RegexOption.IGNORE_CASE)
private val REPEATABLE_MIGRATION = Regex("R__([^/]+)\\.sql", RegexOption.IGNORE_CASE)
