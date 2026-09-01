package site.addzero.toolchain.lowcode

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import site.addzero.toolchain.web.copyDirectory
import site.addzero.toolchain.web.packageCatalog
import site.addzero.toolchain.web.pnpmCommand
import site.addzero.toolchain.web.runCommand
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

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
