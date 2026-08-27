package site.addzero.studio.development

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DevelopmentHostArgumentsTest {
    @Test
    fun `解析 workspace 内的库模块`(@TempDir workspace: Path) {
        val module = workspace.resolve("lib/example-library")
        Files.createDirectories(module)

        val arguments = DevelopmentHostArguments.parse(
            arrayOf(
                "--workspace",
                workspace.toString(),
                "--module",
                "lib/example-library",
            ),
        )

        assertEquals(workspace.toRealPath(), arguments.workspace)
        assertEquals(module.toRealPath(), arguments.module)
    }

    @Test
    fun `拒绝 workspace 之外的模块`(
        @TempDir workspace: Path,
        @TempDir outside: Path,
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            DevelopmentHostArguments.parse(
                arrayOf(
                    "--workspace",
                    workspace.toString(),
                    "--module",
                    outside.toString(),
                ),
            )
        }
    }
}
