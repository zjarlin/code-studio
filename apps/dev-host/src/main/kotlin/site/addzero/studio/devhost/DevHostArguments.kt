package site.addzero.studio.devhost

import java.nio.file.Files
import java.nio.file.Path

internal data class DevHostArguments(
    val workspace: Path,
    val module: Path,
) {
    companion object {
        fun parse(arguments: Array<String>): DevHostArguments {
            val values = parsePairs(arguments)
            val workspace = requiredDirectory(values, "--workspace")
            val moduleArgument = values.getValue("--module")
            val moduleCandidate = Path.of(moduleArgument)
            val resolvedModule = if (moduleCandidate.isAbsolute) {
                moduleCandidate
            } else {
                workspace.resolve(moduleCandidate)
            }
            val module = resolvedModule.toRealPath()
            require(module.startsWith(workspace)) {
                "--module 必须位于 --workspace 内"
            }
            require(Files.isDirectory(module)) {
                "--module 不是目录: $module"
            }
            return DevHostArguments(workspace, module)
        }

        private fun parsePairs(arguments: Array<String>): Map<String, String> {
            require(arguments.size == 4) {
                "用法: --workspace <path> --module <path>"
            }
            val values = linkedMapOf<String, String>()
            arguments.toList().chunked(2).forEach { pair ->
                val name = pair[0]
                require(name == "--workspace" || name == "--module") {
                    "未知参数: $name"
                }
                require(values.putIfAbsent(name, pair[1]) == null) {
                    "参数重复: $name"
                }
            }
            require(values.keys == setOf("--workspace", "--module")) {
                "用法: --workspace <path> --module <path>"
            }
            return values
        }

        private fun requiredDirectory(
            values: Map<String, String>,
            name: String,
        ): Path {
            val path = Path.of(values.getValue(name)).toRealPath()
            require(Files.isDirectory(path)) {
                "$name 不是目录: $path"
            }
            return path
        }
    }
}
