package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import site.addzero.studio.contract.CATALOG_CONTRIBUTION_RESOURCE
import site.addzero.studio.contract.CatalogContributions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TaskAction
@OptIn(ExperimentalPathApi::class)
fun buildLowcodeStudio(
    @Input studioPackageJson: Path,
    @Input consolePackageJson: Path,
    @Input lockFile: Path,
    @Input studioIndexFile: Path,
    @Input studioTsConfig: Path,
    @Input studioViteConfig: Path,
    @Input studioSourceDirectory: Path,
    @Input studioPublicDirectory: Path,
    @Input consoleTsConfig: Path,
    @Input consoleViteConfig: Path,
    @Input consoleSourceDirectory: Path,
    @Output generatedResourcesDirectory: Path,
) {
    require(studioPackageJson.isRegularFile()) { "Studio 工作台缺少 package.json: $studioPackageJson" }
    require(consolePackageJson.isRegularFile()) { "Console 工作台缺少 package.json: $consolePackageJson" }
    require(lockFile.isRegularFile()) { "低代码工作台缺少 pnpm-lock.yaml: $lockFile" }
    require(studioIndexFile.isRegularFile()) { "Studio 工作台缺少 index.html: $studioIndexFile" }
    require(studioTsConfig.isRegularFile()) { "Studio 工作台缺少 tsconfig.json: $studioTsConfig" }
    require(studioViteConfig.isRegularFile()) { "Studio 工作台缺少 vite.config.ts: $studioViteConfig" }
    require(studioSourceDirectory.isDirectory()) { "Studio 工作台缺少源码目录: $studioSourceDirectory" }
    require(studioPublicDirectory.isDirectory()) { "Studio 工作台缺少公共资源目录: $studioPublicDirectory" }
    require(consoleTsConfig.isRegularFile()) { "Console 工作台缺少 tsconfig.json: $consoleTsConfig" }
    require(consoleViteConfig.isRegularFile()) { "Console 工作台缺少 vite.config.ts: $consoleViteConfig" }
    require(consoleSourceDirectory.isDirectory()) { "Console 工作台缺少源码目录: $consoleSourceDirectory" }

    val studioDirectory = studioPackageJson.parent
    val consoleDirectory = consolePackageJson.parent
    val studioOutputDirectory = generatedResourcesDirectory.resolve("studio")
    val consoleOutputDirectory = generatedResourcesDirectory.resolve("console")

    generatedResourcesDirectory.deleteRecursively()
    studioOutputDirectory.createDirectories()
    consoleOutputDirectory.createDirectories()

    runCommand(
        workingDirectory = studioDirectory,
        command = pnpmCommand("install", "--frozen-lockfile"),
    )
    runCommand(
        workingDirectory = studioDirectory,
        command = pnpmCommand("run", "build"),
        environment = mapOf("CODE_STUDIO_UI_OUT_DIR" to studioOutputDirectory.pathString),
    )
    runCommand(
        workingDirectory = consoleDirectory,
        command = pnpmCommand("run", "build"),
    )
    copyDirectory(consoleDirectory.resolve("dist/client"), consoleOutputDirectory)
    packageCatalog(consoleSourceDirectory, generatedResourcesDirectory)
}

private fun packageCatalog(sourceDirectory: Path, generatedResourcesDirectory: Path) {
    val contributions = Files.walk(sourceDirectory).use { paths ->
        paths.filter { path -> path.isRegularFile() && path.fileName.toString() == CATALOG_CONVENTION_FILE }
            .sorted()
            .map { path -> CatalogContributions.decode(path.readText()) }
            .toList()
    }
    require(contributions.isNotEmpty()) {
        "Console 工作台缺少 $CATALOG_CONVENTION_FILE"
    }
    val catalogEntries = CatalogContributions.resolve(contributions)
    val target = generatedResourcesDirectory.resolve(CATALOG_CONTRIBUTION_RESOURCE)
    target.createParentDirectories()
    target.writeText(CatalogContributions.encode(catalogEntries))
}

private const val CATALOG_CONVENTION_FILE = "catalog.convention.json"

private fun copyDirectory(source: Path, target: Path) {
    require(source.isDirectory()) { "Console 构建缺少 client 产物: $source" }
    Files.walk(source).use { paths ->
        paths.sorted().forEach { path ->
            val destination = target.resolve(source.relativize(path).toString())
            if (path.isDirectory()) {
                destination.createDirectories()
            } else {
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
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
