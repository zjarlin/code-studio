package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Dependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshot
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import site.addzero.studio.runtime.GENERATION_TARGET_PROFILE_RESOURCE
import site.addzero.studio.runtime.GenerationTargetProfile
import site.addzero.studio.runtime.GenerationTargetProfiles
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_RESOURCE
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.metadataSnapshotResource
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PublishedContributorArtifactsTest {
    @Test
    fun `编译闭包从中央 JAR 读取 contributor`(@TempDir workspace: Path) {
        val repository = workspace.resolve("repository")
        val module = repository.resolve("apps/example")
        val resources = module.resolve("src/main/resources")
        val manifest = resources.resolve(METADATA_CONTRIBUTOR_RESOURCE)
        val migrations = module.resolve("src/main/lowcode-metadata/db/studio/migration")
        Files.createDirectories(manifest.parent)
        Files.createDirectories(migrations)
        val contributor = MetadataContributor(
            formatVersion = 1,
            id = "example",
            migrationLocation = "classpath:db/studio/metadata/example",
            requires = listOf("foundation"),
        )
        Files.writeString(
            manifest,
            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(contributor) + "\n",
        )
        val snapshotFile = module.resolve("src/main/lowcode-metadata/metadata.json")
        Files.writeString(snapshotFile, LowcodeMetadataSnapshots.encode(emptySnapshot("example", listOf("foundation"))))
        val profile = repository.resolve(".code-studio/target-profile.json")
        Files.createDirectories(profile.parent)
        Files.writeString(profile, GenerationTargetProfiles.encode(defaultProfile()))
        val templates = writeSourceTemplates(repository.resolve(".code-studio/templates"))
        val index = repository.resolve(".code-studio/contributors.json")
        Files.writeString(
            index,
            """
            {
              "formatVersion": 1,
              "contributors": {
                "example": "apps/example"
              }
            }
            """.trimIndent() + "\n",
        )
        val foundation = contributorArchive(workspace.resolve("artifacts/foundation.jar"), "foundation")

        compileLowcodeSources(
            contributorManifest = manifest,
            generationTargetProfile = profile,
            sourceTemplateDirectory = templates,
            metadataSnapshot = snapshotFile,
            sourceMetadataSnapshots = emptyList(),
            contributorIndex = index,
            contributorClasspath = classpath(foundation),
            compiledSourceDirectory = workspace.resolve("compiled"),
            scaffoldSourceDirectory = workspace.resolve("scaffolds"),
        )

        assertThrows(IllegalStateException::class.java) {
            compileLowcodeSources(
                contributorManifest = manifest,
                generationTargetProfile = profile,
                sourceTemplateDirectory = templates,
                metadataSnapshot = snapshotFile,
                sourceMetadataSnapshots = emptyList(),
                contributorIndex = index,
                contributorClasspath = classpath(),
                compiledSourceDirectory = workspace.resolve("missing-compiled"),
                scaffoldSourceDirectory = workspace.resolve("missing-scaffolds"),
            )
        }
    }

    @Test
    fun `读取并确定性解压中央 contributor`(@TempDir workspace: Path) {
        val archive = contributorArchive(workspace.resolve("foundation.jar"), "foundation")
        val artifacts = readPublishedContributorArtifacts(classpath(archive), workspace.resolve("repository"))

        assertEquals(listOf("foundation"), artifacts.keys.toList())
        val output = workspace.resolve("output")
        val migrations = extractPublishedContributorMigrations(artifacts.values, output)
        assertEquals(
            "SELECT 'foundation';\n",
            Files.readString(migrations.getValue("foundation").resolve("R__foundation.sql")),
        )
    }

    @Test
    fun `拒绝重复 contributor id`(@TempDir workspace: Path) {
        val first = contributorArchive(workspace.resolve("first.jar"), "foundation")
        val second = contributorArchive(workspace.resolve("second.jar"), "foundation")

        assertThrows(IllegalArgumentException::class.java) {
            readPublishedContributorArtifacts(classpath(first, second), workspace.resolve("repository"))
        }

        val firstVersion = contributorArchive(workspace.resolve("version-first.jar"), "version-first", migrationName = "V1__first.sql")
        val secondVersion = contributorArchive(workspace.resolve("version-second.jar"), "version-second", migrationName = "V1__second.sql")
        assertThrows(IllegalArgumentException::class.java) {
            readPublishedContributorArtifacts(
                classpath(firstVersion, secondVersion),
                workspace.resolve("repository"),
            )
        }
    }

    @Test
    fun `拒绝缺失 snapshot 和迁移路径穿越`(@TempDir workspace: Path) {
        val missingSnapshot = contributorArchive(
            workspace.resolve("missing-snapshot.jar"),
            "missing-snapshot",
            includeSnapshot = false,
        )
        assertThrows(IllegalArgumentException::class.java) {
            readPublishedContributorArtifacts(classpath(missingSnapshot), workspace.resolve("repository"))
        }

        val traversal = contributorArchive(
            workspace.resolve("traversal.jar"),
            "traversal",
            migrationName = "../outside.sql",
        )
        assertThrows(IllegalArgumentException::class.java) {
            readPublishedContributorArtifacts(classpath(traversal), workspace.resolve("repository"))
        }

        val missingDependency = contributorArchive(
            workspace.resolve("missing-dependency.jar"),
            "missing-dependency",
            requires = listOf("foundation"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            readPublishedContributorArtifacts(classpath(missingDependency), workspace.resolve("repository"))
        }
    }

    private fun contributorArchive(
        archive: Path,
        id: String,
        includeSnapshot: Boolean = true,
        migrationName: String = "R__${id.replace('-', '_')}.sql",
        requires: List<String> = emptyList(),
    ): Path {
        val contributor = MetadataContributor(
            formatVersion = 1,
            id = id,
            migrationLocation = "classpath:db/studio/metadata/$id",
            requires = requires,
        )
        val snapshot = emptySnapshot(id, requires)
        val entries = linkedMapOf(
            METADATA_CONTRIBUTOR_RESOURCE to jacksonObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(contributor) + "\n",
            GENERATION_TARGET_PROFILE_RESOURCE to GenerationTargetProfiles.encode(
                defaultProfile(),
            ),
            "db/studio/metadata/$id/$migrationName" to "SELECT '$id';\n",
        )
        if (includeSnapshot) {
            entries[metadataSnapshotResource(id)] = LowcodeMetadataSnapshots.encode(snapshot)
        }
        Files.createDirectories(archive.parent)
        JarOutputStream(Files.newOutputStream(archive)).use { output ->
            entries.toSortedMap().forEach { (name, content) ->
                output.putNextEntry(JarEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return archive
    }

    private fun classpath(vararg archives: Path): Classpath = object : Classpath {
        override val dependencies: List<Dependency> = emptyList()
        override val resolvedFiles: List<Path> = archives.toList()
    }

    private fun emptySnapshot(
        id: String,
        requires: List<String> = emptyList(),
    ): LowcodeMetadataSnapshot = LowcodeMetadataSnapshot(
        contributorId = id,
        contributorIds = listOf(id) + requires,
        metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
        ),
    )

    private fun defaultProfile(): GenerationTargetProfile = GenerationTargetProfile(
        id = "default",
        symbols = emptyMap(),
        capabilities = emptySet(),
    )
}
