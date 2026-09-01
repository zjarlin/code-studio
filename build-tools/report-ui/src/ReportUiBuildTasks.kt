package site.addzero.toolchain.report

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import site.addzero.toolchain.web.copyDirectory
import site.addzero.toolchain.web.packageCatalog
import site.addzero.toolchain.web.pnpmCommand
import site.addzero.toolchain.web.runCommand
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@TaskAction
@OptIn(ExperimentalPathApi::class)
fun buildReportUi(
    @Input packageJson: Path,
    @Input lockFile: Path,
    @Input tsConfig: Path,
    @Input viteConfig: Path,
    @Input sourceDirectory: Path,
    @Output generatedResourcesDirectory: Path,
) {
    require(packageJson.isRegularFile()) { "报表工作台缺少 package.json: $packageJson" }
    require(lockFile.isRegularFile()) { "报表工作台缺少 pnpm-lock.yaml: $lockFile" }
    require(tsConfig.isRegularFile()) { "报表工作台缺少 tsconfig.json: $tsConfig" }
    require(viteConfig.isRegularFile()) { "报表工作台缺少 vite.config.ts: $viteConfig" }
    require(sourceDirectory.isDirectory()) { "报表工作台缺少源码目录: $sourceDirectory" }

    val workingDirectory = packageJson.parent
    val buildDirectory = generatedResourcesDirectory.resolve("report-ui-build")
    val resourceDirectory = generatedResourcesDirectory.resolve("report")
    generatedResourcesDirectory.deleteRecursively()
    buildDirectory.createDirectories()
    resourceDirectory.createDirectories()

    runCommand(
        workingDirectory = workingDirectory,
        command = pnpmCommand("install", "--frozen-lockfile"),
    )
    runCommand(
        workingDirectory = workingDirectory,
        command = pnpmCommand("run", "build"),
        environment = mapOf("REPORT_UI_OUT_DIR" to buildDirectory.pathString),
    )
    copyDirectory(buildDirectory.resolve("client"), resourceDirectory)
    buildDirectory.deleteRecursively()
    packageCatalog(sourceDirectory, generatedResourcesDirectory)
}
