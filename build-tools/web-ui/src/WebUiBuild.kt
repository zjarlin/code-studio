package site.addzero.toolchain.web

import site.addzero.studio.contract.CATALOG_CONTRIBUTION_RESOURCE
import site.addzero.studio.contract.CatalogContributions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun packageCatalog(sourceDirectory: Path, generatedResourcesDirectory: Path) {
    val contributions = Files.walk(sourceDirectory).use { paths ->
        paths.filter { path -> path.isRegularFile() && path.fileName.toString() == CATALOG_CONVENTION_FILE }
            .sorted()
            .map { path -> CatalogContributions.decode(path.readText()) }
            .toList()
    }
    require(contributions.isNotEmpty()) {
        "Web 工作台缺少 $CATALOG_CONVENTION_FILE"
    }
    val catalogEntries = CatalogContributions.resolve(contributions)
    val target = generatedResourcesDirectory.resolve(CATALOG_CONTRIBUTION_RESOURCE)
    target.createParentDirectories()
    target.writeText(CatalogContributions.encode(catalogEntries))
}

fun copyDirectory(source: Path, target: Path) {
    require(source.isDirectory()) { "Web 构建缺少 client 产物: $source" }
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

fun pnpmCommand(vararg arguments: String): List<String> =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        listOf("cmd.exe", "/d", "/c", "pnpm", *arguments)
    } else {
        listOf("pnpm", *arguments)
    }

fun runCommand(
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

private const val CATALOG_CONVENTION_FILE = "catalog.convention.json"
