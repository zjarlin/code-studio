package site.addzero.studio.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioContractsTest {
    @Test
    fun `Studio 默认不启用`() {
        val config = StudioConfig(
            contributorId = "example-app",
            apiBaseUrl = "/example-api",
        )

        assertFalse(config.enabled)
    }

    @Test
    fun `Studio 不能编辑依赖库贡献`() {
        assertThrows(IllegalArgumentException::class.java) {
            StudioConfig(
                contributorId = "example-app",
                apiBaseUrl = "/example-api",
                editableContributorId = "dependency-library",
            )
        }
    }

    @Test
    fun `生成目标使用语义键而不是宿主限定名`() {
        val profile = GenerationTargetProfile(
            id = "example",
            symbols = mapOf("runtime.lowcode-package" to "example.runtime.lowcode"),
        )

        assertTrue(profile.capabilities.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            GenerationTargetProfile(
                id = "legacy",
                symbols = mapOf("site.addzero.platform.lowcode" to "example.runtime.lowcode"),
            )
        }
    }
}
