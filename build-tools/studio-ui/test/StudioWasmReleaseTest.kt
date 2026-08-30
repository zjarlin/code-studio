package site.addzero.toolchain.lowcode

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StudioWasmReleaseTest {
    @Test
    fun `release 目录必须包含可内嵌的 Wasm 应用`(@TempDir directory: Path) {
        Files.writeString(
            directory.resolve("index.html"),
            "<script src=\"import-map-loader.js\"></script><script type=\"module\" src=\"web.mjs\"></script>",
        )
        Files.createDirectories(directory.resolve("vendors"))
        listOf("web.wasm", "web.mjs", "skiko.wasm", "skiko.mjs", "import-map-loader.js").forEach { name ->
            Files.write(directory.resolve(name), byteArrayOf(1))
        }

        validateReleaseDirectory(directory)
    }

    @Test
    fun `release 目录缺少 Skiko 运行时时拒绝打包`(@TempDir directory: Path) {
        Files.writeString(
            directory.resolve("index.html"),
            "<script src=\"import-map-loader.js\"></script><script type=\"module\" src=\"web.mjs\"></script>",
        )
        Files.createDirectories(directory.resolve("vendors"))
        listOf("web.wasm", "web.mjs", "skiko.mjs", "import-map-loader.js").forEach { name ->
            Files.write(directory.resolve(name), byteArrayOf(1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            validateReleaseDirectory(directory)
        }
    }
}
