package site.addzero.studio.devhost

import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_RESOURCE
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import java.net.URLClassLoader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Comparator
import java.util.Locale

private val CONTRIBUTOR_MANIFEST_PATH = Path.of(METADATA_CONTRIBUTOR_RESOURCE)
private val CONTRIBUTOR_SOURCE_MANIFEST_PATH = Path.of("src/main/resources").resolve(CONTRIBUTOR_MANIFEST_PATH)
private val IGNORED_DISCOVERY_DIRECTORIES = setOf(".amper", ".git", ".idea", "build", "node_modules")

private data class ContributorSource(
    val contributor: MetadataContributor,
    val module: Path,
    val resources: Path,
    val manifest: Path,
)

internal class DevHostModule private constructor(
    val contributor: MetadataContributor,
    val classLoader: URLClassLoader,
    internal val temporaryClasspath: Path,
) : AutoCloseable {
    override fun close() {
        try {
            classLoader.close()
        } finally {
            deleteDirectory(temporaryClasspath)
        }
    }

    companion object {
        fun load(
            workspace: Path,
            module: Path,
        ): DevHostModule {
            val resources = module.resolve("src/main/resources")
            require(Files.isDirectory(resources)) {
                "模块缺少 src/main/resources: $module"
            }
            val manifest = resources.resolve(METADATA_CONTRIBUTOR_RESOURCE)
            require(Files.isRegularFile(manifest)) {
                "模块缺少 $METADATA_CONTRIBUTOR_RESOURCE: $module"
            }
            val selectedSource = contributorSource(manifest)
            val sourcesById = discoverContributors(workspace).toMutableMap()
            val existingSource = sourcesById.putIfAbsent(selectedSource.contributor.id, selectedSource)
            require(existingSource == null || existingSource.manifest == selectedSource.manifest) {
                "元数据贡献 id 重复: ${selectedSource.contributor.id}"
            }
            val requiredSources = resolveRequiredSources(selectedSource.contributor.id, sourcesById)
            val temporaryClasspath = Files.createTempDirectory("studio-dev-host-")
            return runCatching {
                val contributorRoots = materializeContributors(requiredSources, temporaryClasspath)
                val parent = DevHostModule::class.java.classLoader
                val resourceUrls = contributorRoots.map { root -> root.toUri().toURL() }.toTypedArray()
                val classLoader = URLClassLoader(resourceUrls, parent)
                DevHostModule(selectedSource.contributor, classLoader, temporaryClasspath)
            }.getOrElse { cause ->
                deleteDirectory(temporaryClasspath)
                throw cause
            }
        }

        private fun discoverContributors(workspace: Path): Map<String, ContributorSource> {
            val sourcesById = linkedMapOf<String, ContributorSource>()
            Files.walkFileTree(
                workspace,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (directory != workspace && directory.fileName.toString() in IGNORED_DISCOVERY_DIRECTORIES) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (!file.endsWith(CONTRIBUTOR_SOURCE_MANIFEST_PATH)) {
                            return FileVisitResult.CONTINUE
                        }
                        val manifest = file
                        val source = contributorSource(manifest)
                        val previous = sourcesById.putIfAbsent(source.contributor.id, source)
                        require(previous == null) {
                            "元数据贡献 id 重复: ${source.contributor.id}"
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
            return sourcesById
        }

        private fun resolveRequiredSources(
            selectedId: String,
            sourcesById: Map<String, ContributorSource>,
        ): List<ContributorSource> {
            val selectedSources = linkedMapOf<String, ContributorSource>()
            val pendingIds = ArrayDeque(listOf(selectedId))
            while (pendingIds.isNotEmpty()) {
                val contributorId = pendingIds.removeFirst()
                if (selectedSources.containsKey(contributorId)) {
                    continue
                }
                val source = requireNotNull(sourcesById[contributorId]) {
                    "元数据贡献缺少依赖: $contributorId"
                }
                selectedSources[contributorId] = source
                source.contributor.requires.forEach(pendingIds::addLast)
            }
            val ordered = MetadataContributors.resolve(selectedSources.values.map(ContributorSource::contributor))
            return ordered.map { contributor -> selectedSources.getValue(contributor.id) }
        }

        private fun contributorSource(manifest: Path): ContributorSource {
            var resources = manifest
            repeat(CONTRIBUTOR_MANIFEST_PATH.nameCount) {
                resources = resources.parent
            }
            val contributor = MetadataContributors.read(manifest.toUri().toURL())
            val module = resources.parent.parent.parent
            return ContributorSource(contributor, module, resources, manifest.toRealPath())
        }

        private fun materializeContributors(
            sources: List<ContributorSource>,
            temporaryClasspath: Path,
        ): List<Path> = sources.mapIndexed { index, source ->
            val contributorRoot = temporaryClasspath.resolve(index.toString())
            val manifestTarget = contributorRoot.resolve(METADATA_CONTRIBUTOR_RESOURCE)
            Files.createDirectories(manifestTarget.parent)
            Files.copy(source.manifest, manifestTarget, StandardCopyOption.REPLACE_EXISTING)

            val migrationTarget = contributorRoot.resolve("db/studio/metadata/${source.contributor.id}")
            Files.createDirectories(migrationTarget)
            val autonomousMigrations = source.module.resolve("src/main/lowcode-metadata/db/studio/migration")
            val legacyMigrations = source.module.resolve("src/main/lowcode-metadata/db/migration")
            val standardMigrations = source.resources.resolve("db/studio/metadata/${source.contributor.id}")
            val autonomousHasSql = containsSql(autonomousMigrations)
            val standardHasSql = containsSql(standardMigrations)
            require(!autonomousHasSql || !standardHasSql) {
                "元数据贡献同时声明源码与资源迁移目录: ${source.contributor.id}"
            }
            val migrationSource = when {
                autonomousHasSql -> autonomousMigrations
                standardHasSql -> standardMigrations
                containsSql(legacyMigrations) -> legacyMigrations
                else -> null
            }
            if (migrationSource != null) {
                copyDirectory(migrationSource, migrationTarget)
            }
            contributorRoot
        }

        private fun containsSql(directory: Path): Boolean {
            if (!Files.isDirectory(directory)) {
                return false
            }
            return Files.walk(directory).use { paths ->
                paths.anyMatch { path ->
                    Files.isRegularFile(path) && path.fileName.toString().endsWith(".sql", ignoreCase = true)
                }
            }
        }

        private fun copyDirectory(
            source: Path,
            target: Path,
        ) {
            Files.walk(source).use { paths ->
                paths.forEach { path ->
                    val destination = target.resolve(source.relativize(path).toString())
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination)
                    } else {
                        Files.createDirectories(destination.parent)
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        private fun deleteDirectory(directory: Path) {
            if (!Files.exists(directory)) {
                return
            }
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}

internal fun devSchema(contributorId: String): String {
    val sanitized = contributorId
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_]"), "_")
    require(sanitized.any(Char::isLetterOrDigit)) {
        "元数据贡献 id 无法生成 PostgreSQL schema: $contributorId"
    }
    val schema = "code_studio_dev_$sanitized"
    require(schema.length <= 63) {
        "元数据贡献 id 生成的 PostgreSQL schema 超过 63 字符: $contributorId"
    }
    return schema
}
