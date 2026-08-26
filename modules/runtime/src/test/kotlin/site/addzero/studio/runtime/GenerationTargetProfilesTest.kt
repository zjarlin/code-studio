package site.addzero.studio.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class GenerationTargetProfilesTest {
    @Test
    fun `classpath profiles must be canonically equal`(@TempDir directory: Path) {
        val first = directory.resolve("first")
        val second = directory.resolve("second")
        writeProfile(
            first,
            """
            {
              "id": "application",
              "symbols": {
                "extension.agent-package": "example.runtime.agent",
                "runtime.web-package": "example.runtime.web",
                "runtime.core-package": "example.runtime.core"
              },
              "capabilities": ["agent"]
            }
            """.trimIndent(),
        )
        writeProfile(
            second,
            """
            {
              "capabilities": ["agent"],
              "symbols": {
                "runtime.core-package": "example.runtime.core",
                "extension.agent-package": "example.runtime.agent",
                "runtime.web-package": "example.runtime.web"
              },
              "id": "application"
            }
            """.trimIndent(),
        )

        URLClassLoader(arrayOf(first.toUri().toURL(), second.toUri().toURL()), null).use { classLoader ->
            val profile = GenerationTargetProfiles.load(classLoader)

            assertEquals(
                listOf("extension.agent-package", "runtime.core-package", "runtime.web-package"),
                profile.symbols.keys.toList(),
            )
        }
    }

    @Test
    fun `classpath rejects profile drift`(@TempDir directory: Path) {
        val first = directory.resolve("first")
        val second = directory.resolve("second")
        writeProfile(first, profile("example.runtime.web"))
        writeProfile(second, profile("other.runtime.web"))

        URLClassLoader(arrayOf(first.toUri().toURL(), second.toUri().toURL()), null).use { classLoader ->
            assertThrows(IllegalArgumentException::class.java) {
                GenerationTargetProfiles.load(classLoader)
            }
        }
    }

    private fun writeProfile(root: Path, content: String) {
        val resource = root.resolve(GENERATION_TARGET_PROFILE_RESOURCE)
        Files.createDirectories(resource.parent)
        Files.writeString(resource, content)
    }

    private fun profile(webPackage: String): String =
        """
        {
          "id": "application",
          "symbols": {"runtime.web-package": "$webPackage"},
          "capabilities": []
        }
        """.trimIndent()
}
