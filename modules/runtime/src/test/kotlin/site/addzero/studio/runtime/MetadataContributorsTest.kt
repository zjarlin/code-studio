package site.addzero.studio.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class MetadataContributorsTest {
    @Test
    fun `依赖拓扑顺序稳定且同层按 id 排序`() {
        val contributors = listOf(
            contributor("feature", requires = listOf("core")),
            contributor("addon", requires = listOf("core")),
            contributor("core"),
        )

        val resolved = MetadataContributors.resolve(contributors)

        assertEquals(listOf("core", "addon", "feature"), resolved.map(MetadataContributor::id))
    }

    @Test
    fun `重复 id 缺失依赖和环依赖都被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.resolve(listOf(contributor("core"), contributor("core")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.resolve(listOf(contributor("feature", requires = listOf("core"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.resolve(
                listOf(
                    contributor("first", requires = listOf("second")),
                    contributor("second", requires = listOf("first")),
                ),
            )
        }
    }

    @Test
    fun `classpath 清单被发现并排序`(@TempDir directory: Path) {
        val root = directory.resolve("dependency")
        val manifest = root.resolve(METADATA_CONTRIBUTOR_RESOURCE)
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """
            {
              "formatVersion": 1,
              "id": "example-library",
              "migrationLocation": "classpath:db/studio/metadata/example-library",
              "requires": []
            }
            """.trimIndent(),
        )

        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { classLoader ->
            val contributors = MetadataContributors.load(classLoader)

            assertEquals(listOf("example-library"), contributors.map(MetadataContributor::id))
        }
    }

    @Test
    fun `迁移位置必须由 contributor id 唯一推导`() {
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributor(
                formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
                id = "identity.users",
                migrationLocation = "classpath:db/studio/metadata/identity/users",
            )
        }
    }

    @Test
    fun `清单只接受公开契约字段`(@TempDir directory: Path) {
        val manifest = directory.resolve("contributor.json")
        Files.writeString(
            manifest,
            """
            {
              "formatVersion": 1,
              "id": "example-library",
              "migrationLocation": "classpath:db/studio/metadata/example-library",
              "requires": [],
              "unknownField": "unexpected"
            }
            """.trimIndent(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.read(manifest.toUri().toURL())
        }
    }

    @Test
    fun `contributor id 是稳定的小写标识`() {
        listOf("Example", "example_library", "example/library", "-example").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                contributor(id)
            }
        }
        contributor("identity.users")
        contributor("example-service")
    }

    @Test
    fun `依赖闭包自动确定唯一应用根`() {
        val root = MetadataContributors.uniqueRoot(
            listOf(
                contributor("feature", requires = listOf("core")),
                contributor("application", requires = listOf("feature")),
                contributor("core"),
            ),
        )

        assertEquals("application", root.id)
    }

    @Test
    fun `空闭包或多个根都被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.uniqueRoot(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetadataContributors.uniqueRoot(listOf(contributor("first"), contributor("second")))
        }
    }

    private fun contributor(
        id: String,
        requires: List<String> = emptyList(),
    ): MetadataContributor = MetadataContributor(
        formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
        id = id,
        migrationLocation = "classpath:db/studio/metadata/$id",
        requires = requires,
    )
}
