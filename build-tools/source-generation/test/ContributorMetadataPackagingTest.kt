package site.addzero.toolchain.lowcode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.runtime.GENERATION_TARGET_PROFILE_RESOURCE
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_RESOURCE
import site.addzero.studio.runtime.metadataSnapshotResource
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshot
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import java.nio.file.Files
import java.nio.file.Path

class ContributorMetadataPackagingTest {
    @Test
    fun `打包完整 contributor 运行时契约`(@TempDir workspace: Path) {
        val resources = workspace.resolve("resources")
        val manifest = resources.resolve(METADATA_CONTRIBUTOR_RESOURCE)
        val manifestContent =
            """
            {
              "formatVersion": 1,
              "id": "example-app",
              "migrationLocation": "classpath:db/studio/metadata/example-app",
              "requires": []
            }
            """.trimIndent() + "\n"
        Files.createDirectories(manifest.parent)
        Files.writeString(manifest, manifestContent)
        val migrations = workspace.resolve("migrations")
        Files.createDirectories(migrations)
        Files.writeString(migrations.resolve("R__example_app.sql"), "SELECT '${'$'}{contributorId}';\n")
        val profile = workspace.resolve("target-profile.json")
        Files.writeString(
            profile,
            """
            {
              "id": "example",
              "symbols": {},
              "capabilities": []
            }
            """.trimIndent() + "\n",
        )
        Files.writeString(resources.resolve("logback.xml"), "<configuration/>\n")
        val snapshot = LowcodeMetadataSnapshot(
            contributorId = "example-app",
            contributorIds = listOf("example-app"),
            metadata = LowcodeMetadata(
                models = emptyList(),
                dtoDefinitions = emptyList(),
                routeBindings = emptyList(),
                contracts = emptyList(),
            ),
        )
        val snapshotFile = workspace.resolve("metadata.json")
        val snapshotContent = LowcodeMetadataSnapshots.encode(snapshot)
        Files.writeString(snapshotFile, snapshotContent)
        val output = workspace.resolve("output")
        Files.createDirectories(output)
        Files.writeString(output.resolve("stale.txt"), "stale\n")

        packageContributorMetadata(
            contributorMetadataMigrationDirectory = migrations,
            generationTargetProfile = profile,
            metadataSnapshot = snapshotFile,
            moduleResourcesDirectory = resources,
            generatedResourcesDirectory = output,
        )

        assertFalse(Files.exists(output.resolve("stale.txt")))
        assertEquals("<configuration/>\n", Files.readString(output.resolve("logback.xml")))
        assertEquals(manifestContent, Files.readString(output.resolve(METADATA_CONTRIBUTOR_RESOURCE)))
        assertEquals(
            snapshotContent,
            Files.readString(output.resolve(metadataSnapshotResource("example-app"))),
        )
        assertEquals(
            "SELECT '${'$'}{contributorId}';\n",
            Files.readString(output.resolve("db/studio/metadata/example-app/R__example_app.sql")),
        )
        assertEquals(
            """
            {
              "id" : "example",
              "symbols" : { },
              "capabilities" : [ ]
            }
            """.trimIndent() + "\n",
            Files.readString(output.resolve(GENERATION_TARGET_PROFILE_RESOURCE)),
        )
    }
}
