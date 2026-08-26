package site.addzero.studio.devhost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DevHostArgumentsTest {
    @Test
    fun `解析 workspace 内的库模块`(@TempDir workspace: Path) {
        val module = workspace.resolve("lib/example-library")
        Files.createDirectories(module)

        val arguments = DevHostArguments.parse(
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
            DevHostArguments.parse(
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
