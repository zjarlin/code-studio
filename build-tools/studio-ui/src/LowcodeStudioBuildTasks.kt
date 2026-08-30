package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@TaskAction
@OptIn(ExperimentalPathApi::class)
fun buildLowcodeStudio(
    @Input kotlinWrapper: Path,
    @Input projectFile: Path,
    @Input versionCatalog: Path,
    @Input webModule: Path,
    @Input workbenchModule: Path,
    @Input contractModule: Path,
    @Output generatedResourcesDirectory: Path,
) {
    require(kotlinWrapper.isRegularFile()) { "Studio 缺少 Kotlin wrapper: $kotlinWrapper" }
    require(projectFile.isRegularFile()) { "Studio 缺少 project.yaml: $projectFile" }
    require(versionCatalog.isRegularFile()) { "Studio 缺少 libs.versions.toml: $versionCatalog" }
    require(webModule.isDirectory()) { "Studio 缺少 Web 模块: $webModule" }
    require(workbenchModule.isDirectory()) { "Studio 缺少 Workbench 模块: $workbenchModule" }
    require(contractModule.isDirectory()) { "Studio 缺少共享契约模块: $contractModule" }

    val studioRoot = projectFile.parent
    val taskOutputDirectory = generatedResourcesDirectory.parent
    val buildDirectory = taskOutputDirectory.resolve("web-build")
    val outputDirectory = generatedResourcesDirectory.resolve("studio")

    generatedResourcesDirectory.deleteRecursively()
    buildDirectory.deleteRecursively()
    outputDirectory.createDirectories()

    runCommand(
        workingDirectory = studioRoot,
        command = listOf(
            kotlinWrapper.pathString,
            "build",
            "--project-dir",
            studioRoot.pathString,
            "--build-dir",
            buildDirectory.pathString,
            "-m",
            "web",
            "-p",
            "wasmJs",
            "-v",
            "release",
        ),
    )

    val releaseDirectory = buildDirectory.resolve("tasks/_web_buildWasmJsAppWasmJsRelease")
    validateReleaseDirectory(releaseDirectory)
    copyDirectory(releaseDirectory, outputDirectory)
    buildDirectory.deleteRecursively()
}

internal fun validateReleaseDirectory(directory: Path) {
    require(directory.isDirectory()) { "Studio Wasm release 产物不存在: $directory" }
    require(directory.resolve("index.html").isRegularFile()) { "Studio Wasm release 缺少 index.html" }
    require(directory.resolve("web.wasm").isRegularFile()) { "Studio Wasm release 缺少 web.wasm" }
    require(directory.resolve("web.mjs").isRegularFile()) { "Studio Wasm release 缺少 web.mjs" }
    require(directory.resolve("skiko.wasm").isRegularFile()) { "Studio Wasm release 缺少 skiko.wasm" }
    require(directory.resolve("skiko.mjs").isRegularFile()) { "Studio Wasm release 缺少 skiko.mjs" }
    require(directory.resolve("import-map-loader.js").isRegularFile()) { "Studio Wasm release 缺少 import-map-loader.js" }
    require(directory.resolve("vendors").isDirectory()) { "Studio Wasm release 缺少 npm vendors" }
    val index = Files.readString(directory.resolve("index.html"))
    require("import-map-loader.js" in index) { "Studio index.html 未在入口前安装 import map" }
    require("web.mjs" in index) { "Studio index.html 未引用 web.mjs" }
}

private fun copyDirectory(source: Path, target: Path) {
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val destination = target.resolve(source.relativize(path))
            if (path.isDirectory()) {
                destination.createDirectories()
            } else {
                destination.parent.createDirectories()
                Files.copy(path, destination)
            }
        }
    }
}

private fun runCommand(workingDirectory: Path, command: List<String>) {
    val process = ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "命令执行失败，退出码 $exitCode: ${command.joinToString(" ")}"
    }
}
