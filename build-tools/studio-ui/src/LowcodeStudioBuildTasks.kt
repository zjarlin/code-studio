package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
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
    @Input packageJson: Path,
    @Input lockFile: Path,
    @Input indexFile: Path,
    @Input tsConfig: Path,
    @Input viteConfig: Path,
    @Input sourceDirectory: Path,
    @Output generatedResourcesDirectory: Path,
) {
    require(packageJson.isRegularFile()) { "低代码工作台缺少 package.json: $packageJson" }
    require(lockFile.isRegularFile()) { "低代码工作台缺少 pnpm-lock.yaml: $lockFile" }
    require(indexFile.isRegularFile()) { "低代码工作台缺少 index.html: $indexFile" }
    require(tsConfig.isRegularFile()) { "低代码工作台缺少 tsconfig.json: $tsConfig" }
    require(viteConfig.isRegularFile()) { "低代码工作台缺少 vite.config.ts: $viteConfig" }
    require(sourceDirectory.isDirectory()) { "低代码工作台缺少源码目录: $sourceDirectory" }

    val studioDirectory = packageJson.parent
    val outputDirectory = generatedResourcesDirectory.resolve("studio")

    generatedResourcesDirectory.deleteRecursively()
    outputDirectory.createDirectories()

    runCommand(
        workingDirectory = studioDirectory,
        command = pnpmCommand("install", "--frozen-lockfile"),
    )
    runCommand(
        workingDirectory = studioDirectory,
        command = pnpmCommand("run", "build"),
        environment = mapOf("CODE_STUDIO_UI_OUT_DIR" to outputDirectory.pathString),
    )
}

private fun pnpmCommand(vararg arguments: String): List<String> =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        listOf("cmd.exe", "/d", "/c", "pnpm", *arguments)
    } else {
        listOf("pnpm", *arguments)
    }

private fun runCommand(
    workingDirectory: Path,
    command: List<String>,
    environment: Map<String, String> = emptyMap(),
) {
    val processBuilder = ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .inheritIO()
    processBuilder.environment().putAll(environment)

    val process = processBuilder.start()
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "命令执行失败，退出码 $exitCode: ${command.joinToString(" ")}"
    }
}
