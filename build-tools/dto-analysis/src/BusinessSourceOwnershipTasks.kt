package site.addzero.toolchain.dto

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import site.addzero.platform.lowcode.generator.LsiLowcodeFeature
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import site.addzero.studio.runtime.MetadataContributor
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

/** 业务源码所有权违规类型。 */
enum class BusinessSourceViolationKind {
    HANDWRITTEN_DTO,
    HANDWRITTEN_JIMMER_ENTITY,
    HANDWRITTEN_SERVICE_CONTRACT,
    DANGLING_SERVICE_IMPLEMENTATION,
    MISPLACED_CONTROLLER,
    UNOWNED_SOURCE,
    MISPLACED_GENERATED_SOURCE,
    HANDWRITTEN_GENERATED_SOURCE,
    MISSING_FEATURE_README,
}

/** 可定位到源码声明的业务源码所有权违规。 */
data class BusinessSourceViolation(
    val kind: BusinessSourceViolationKind,
    val path: String,
    val line: Int,
    val symbol: String,
    val reason: String,
)

/** 已正确实现生成 Service 契约的手写实现。 */
data class GeneratedServiceImplementationBinding(
    val path: String,
    val line: Int,
    val implementation: String,
    val contract: String,
)

/** 生产业务源码的生成所有权报告。 */
data class BusinessSourceOwnershipReport(
    val schemaVersion: Int = BUSINESS_SOURCE_REPORT_SCHEMA_VERSION,
    val violations: List<BusinessSourceViolation>,
    val generatedServiceBindings: List<GeneratedServiceImplementationBinding>,
)

/** 输出业务源码生成所有权清单，不阻断迁移过程。 */
@TaskAction
fun analyzeBusinessSourceOwnership(
    @Input contributorManifest: Path,
    @Input metadataSnapshot: Path,
    @Input moduleSourceDirectory: Path,
    @Output reportDirectory: Path,
) {
    val contributorId = readContributorId(contributorManifest)
    val report = BusinessSourceOwnershipScanner.scan(
        moduleSourceDirectory = moduleSourceDirectory,
        contributorId = contributorId,
        features = loadBusinessFeatures(metadataSnapshot, contributorId),
    )
    writeBusinessSourceOwnershipReport(reportDirectory, report)
    println(
        "业务源码所有权分析完成: ${report.violations.size} 个违规，" +
            "${report.generatedServiceBindings.size} 个生成 Service 绑定",
    )
}

/** 要求生产业务 DTO、实体和 Service 契约全部由元数据生成。 */
@TaskAction
fun verifyBusinessSourceOwnership(
    @Input contributorManifest: Path,
    @Input metadataSnapshot: Path,
    @Input moduleSourceDirectory: Path,
    @Output reportDirectory: Path,
) {
    val contributorId = readContributorId(contributorManifest)
    val report = BusinessSourceOwnershipScanner.scan(
        moduleSourceDirectory = moduleSourceDirectory,
        contributorId = contributorId,
        features = loadBusinessFeatures(metadataSnapshot, contributorId),
    )
    writeBusinessSourceOwnershipReport(reportDirectory, report)
    require(report.violations.isEmpty()) {
        "业务源码存在 ${report.violations.size} 个非生成声明，详情见 ${reportDirectory.resolve("report.md").pathString}"
    }
    println("业务源码生成所有权校验通过")
}

/** 只扫描当前模块的生产 Kotlin 源码。 */
@OptIn(ExperimentalPathApi::class)
internal object BusinessSourceOwnershipScanner {
    fun scan(
        moduleSourceDirectory: Path,
        contributorId: String,
        features: List<LsiLowcodeFeature>,
    ): BusinessSourceOwnershipReport {
        val sourceFiles = moduleSourceDirectory.walk()
            .filter(Path::isRegularFile)
            .filter { path -> path.fileName.toString().endsWith(".kt") }
            .sortedBy { path -> path.relativeTo(moduleSourceDirectory).pathString }
            .toList()
        val ownedFeatures = features.filter { feature -> feature.contributorId == contributorId }
        val scans = sourceFiles.map { path -> scanFile(moduleSourceDirectory, path, ownedFeatures) }
        val readmeViolations = missingFeatureReadmes(moduleSourceDirectory, ownedFeatures)
        val legacyControllerViolations = legacyControllerViolations(moduleSourceDirectory)
        return BusinessSourceOwnershipReport(
            violations = (scans.flatMap(SourceOwnershipScan::violations) + readmeViolations + legacyControllerViolations)
                .distinctBy { violation -> Triple(violation.kind, violation.path, violation.symbol) }
                .sortedWith(compareBy(BusinessSourceViolation::path, BusinessSourceViolation::line, BusinessSourceViolation::kind)),
            generatedServiceBindings = scans.flatMap(SourceOwnershipScan::bindings)
                .sortedWith(
                    compareBy(
                        GeneratedServiceImplementationBinding::path,
                        GeneratedServiceImplementationBinding::line,
                        GeneratedServiceImplementationBinding::contract,
                    ),
                ),
        )
    }

    private fun scanFile(
        moduleSourceDirectory: Path,
        path: Path,
        features: List<LsiLowcodeFeature>,
    ): SourceOwnershipScan {
        val source = path.readText()
        val code = source.maskCommentsAndLiterals()
        val relativePath = path.relativeTo(moduleSourceDirectory).pathString
        val packageName = PACKAGE_DECLARATION.find(code)?.groupValues?.get(1)
        val matchingFeatures = features
            .filter { feature -> packageName?.belongsTo(feature.packageName) == true }
        val owner = matchingFeatures.maxByOrNull { feature -> feature.packageName.length }
        val generated = path.isGeneratedSource()
        val violations = buildList {
            if (owner == null) {
                add(
                    BusinessSourceViolation(
                        kind = BusinessSourceViolationKind.UNOWNED_SOURCE,
                        path = relativePath,
                        line = 1,
                        symbol = packageName ?: path.nameWithoutExtension,
                        reason = "源码包不属于当前 contributor 的任何 Library feature",
                    ),
                )
            }
            if (generated && owner != null && packageName?.startsWith("${owner.packageName}.generated") != true) {
                add(
                    BusinessSourceViolation(
                        kind = BusinessSourceViolationKind.MISPLACED_GENERATED_SOURCE,
                        path = relativePath,
                        line = 1,
                        symbol = packageName ?: path.nameWithoutExtension,
                        reason = "生成代码必须位于 ${owner.packageName}.generated 子包",
                    ),
                )
            }
            if (generated && !source.lineSequence().take(GENERATED_MARKER_LINE_LIMIT).any { line ->
                    GENERATED_SOURCE_MARKER in line
                }
            ) {
                add(
                    BusinessSourceViolation(
                        kind = BusinessSourceViolationKind.HANDWRITTEN_GENERATED_SOURCE,
                        path = relativePath,
                        line = 1,
                        symbol = path.nameWithoutExtension,
                        reason = "generated 目录只能保存元数据生成源码",
                    ),
                )
            }
            if (generated) return@buildList
            if (packageName?.isDtoPackage() == true) {
                DATA_CLASS.findAll(code).forEach { match ->
                    add(
                        violation(
                            BusinessSourceViolationKind.HANDWRITTEN_DTO,
                            relativePath,
                            code,
                            match.range.first,
                            match.groupValues[1],
                            "dto 包中的 data class 必须由 DTO 元数据生成",
                        ),
                    )
                }
            }
            JIMMER_ENTITY_ANNOTATION.findAll(code).forEach { match ->
                val declaration = TYPE_DECLARATION.find(code, match.range.last + 1)
                add(
                    violation(
                        BusinessSourceViolationKind.HANDWRITTEN_JIMMER_ENTITY,
                        relativePath,
                        code,
                        match.range.first,
                        declaration?.groupValues?.get(1) ?: path.nameWithoutExtension,
                        "业务 Jimmer 实体必须由模型元数据生成",
                    ),
                )
            }
            SERVICE_CONTRACT.findAll(code).forEach { match ->
                add(
                    violation(
                        BusinessSourceViolationKind.HANDWRITTEN_SERVICE_CONTRACT,
                        relativePath,
                        code,
                        match.range.first,
                        match.groupValues[1],
                        "业务 Service 契约必须由 Contract 元数据生成",
                    ),
                )
            }
        }.toMutableList()
        if (generated) return SourceOwnershipScan(violations, emptyList())
        val generatedImports = GENERATED_SERVICE_IMPORT.findAll(code)
            .associate { match -> match.groupValues[2] to match.groupValues[1] }
        val bindings = mutableListOf<GeneratedServiceImplementationBinding>()
        CONCRETE_SERVICE.findAll(code).forEach { match ->
            val implementation = match.groupValues[1]
            val header = code.classHeaderStartingAt(match.range.first)
            val supertypes = header.substringAfterLast(") :", header.substringAfter(':', ""))
            val contracts = (
                generatedImports.entries.filter { (simpleName) ->
                    Regex("\\b${Regex.escape(simpleName)}\\b").containsMatchIn(supertypes)
                }.map { entry -> entry.value } +
                    FULLY_QUALIFIED_GENERATED_SERVICE.findAll(supertypes).map(MatchResult::value)
                ).distinct().sorted()
            if (contracts.isEmpty()) {
                violations += violation(
                    BusinessSourceViolationKind.DANGLING_SERVICE_IMPLEMENTATION,
                    relativePath,
                    code,
                    match.range.first,
                    implementation,
                    "手写 Service 实现必须实现元数据生成的 Service 契约",
                )
            } else {
                contracts.forEach { contract ->
                    bindings += GeneratedServiceImplementationBinding(
                        path = relativePath,
                        line = code.lineNumber(match.range.first),
                        implementation = implementation,
                        contract = contract,
                    )
                }
            }
        }
        return SourceOwnershipScan(violations, bindings)
    }

    private fun missingFeatureReadmes(
        moduleSourceDirectory: Path,
        features: List<LsiLowcodeFeature>,
    ): List<BusinessSourceViolation> = features.mapNotNull { feature ->
        val readme = moduleSourceDirectory.resolve(feature.packageName.replace('.', '/'))
            .resolve("README.md")
        if (readme.isRegularFile()) return@mapNotNull null
        BusinessSourceViolation(
            kind = BusinessSourceViolationKind.MISSING_FEATURE_README,
            path = readme.relativeTo(moduleSourceDirectory).pathString,
            line = 1,
            symbol = feature.featureCode,
            reason = "Library feature 必须包含元数据生成的 README.md",
        )
    }

    private fun legacyControllerViolations(moduleSourceDirectory: Path): List<BusinessSourceViolation> {
        return moduleSourceDirectory.walk()
            .filter(Path::isRegularFile)
            .filter { path -> path.fileName.toString().endsWith(".kt") }
            .mapNotNull { path ->
                val source = path.readText()
                val code = source.maskCommentsAndLiterals()
                val legacy = code.legacyControllerSymbol(path) ?: return@mapNotNull null
                BusinessSourceViolation(
                    kind = BusinessSourceViolationKind.MISPLACED_CONTROLLER,
                    path = path.relativeTo(moduleSourceDirectory).pathString,
                    line = code.lineNumber(legacy.offset),
                    symbol = legacy.symbol,
                    reason = "HTTP 传输入口必须归入 *Controller；有对应生成 Controller 时必须合入 generated/controller",
                )
            }
            .sortedWith(compareBy(BusinessSourceViolation::path, BusinessSourceViolation::line))
            .toList()
    }
}

private data class SourceOwnershipScan(
    val violations: List<BusinessSourceViolation>,
    val bindings: List<GeneratedServiceImplementationBinding>,
)

private fun loadBusinessFeatures(
    metadataSnapshot: Path,
    contributorId: String,
): List<LsiLowcodeFeature> {
    val snapshot = LowcodeMetadataSnapshots.decode(metadataSnapshot.readText())
    require(snapshot.contributorId == contributorId) {
        "元数据快照归属 ${snapshot.contributorId} 与当前 manifest $contributorId 不一致"
    }
    return snapshot.metadata.features
}

private fun readContributorId(path: Path): String =
    businessSourceObjectMapper.readValue(path.toFile(), MetadataContributor::class.java).id

private fun violation(
    kind: BusinessSourceViolationKind,
    path: String,
    source: String,
    offset: Int,
    symbol: String,
    reason: String,
): BusinessSourceViolation = BusinessSourceViolation(
    kind = kind,
    path = path,
    line = source.lineNumber(offset),
    symbol = symbol,
    reason = reason,
)

private fun writeBusinessSourceOwnershipReport(
    reportDirectory: Path,
    report: BusinessSourceOwnershipReport,
) {
    reportDirectory.createDirectories()
    reportDirectory.resolve("report.json").writeText(
        businessSourceObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n",
    )
    reportDirectory.resolve("report.md").writeText(report.toMarkdown())
}

private fun BusinessSourceOwnershipReport.toMarkdown(): String {
    val violationsSource = violations.joinToString(separator = "\n") { violation ->
        "| ${violation.kind} | `${violation.path}:${violation.line}` | `${violation.symbol}` | ${violation.reason} |"
    }.ifEmpty { "No ownership violations found." }
    val bindingsSource = generatedServiceBindings.joinToString(separator = "\n") { binding ->
        "| `${binding.path}:${binding.line}` | `${binding.implementation}` | `${binding.contract}` |"
    }.ifEmpty { "No generated Service bindings found." }
    return """
        # Business source ownership

        - Schema version: `$schemaVersion`
        - Violations: `${violations.size}`
        - Generated Service bindings: `${generatedServiceBindings.size}`

        Ownership and legacy HTTP transport naming are checked only in the configured module source directory.

        ## Violations

        | Kind | Location | Symbol | Reason |
        | --- | --- | --- | --- |
        $violationsSource

        ## Generated Service bindings

        | Location | Implementation | Generated contract |
        | --- | --- | --- |
        $bindingsSource
    """.trimIndent().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
}

private fun Path.isGeneratedSource(): Boolean = iterator().asSequence().any { segment -> segment.toString() == "generated" }

private fun String.belongsTo(featurePackage: String): Boolean =
    this == featurePackage || startsWith("$featurePackage.")

private fun String.isDtoPackage(): Boolean = split('.').any { segment -> segment == "dto" }

private fun String.legacyControllerSymbol(path: Path): LegacyControllerSymbol? {
    val legacyType = LEGACY_CONTROLLER_TYPE.find(this)
    if (legacyType != null) {
        return LegacyControllerSymbol(legacyType.groupValues[1], legacyType.range.first)
    }
    if (path.nameWithoutExtension.endsWith("Routes")) {
        return LegacyControllerSymbol(path.nameWithoutExtension, 0)
    }
    val routeFunction = LEGACY_ROUTE_FUNCTION.find(this) ?: return null
    return LegacyControllerSymbol(routeFunction.groupValues[1], routeFunction.range.first)
}

private data class LegacyControllerSymbol(
    val symbol: String,
    val offset: Int,
)

private fun String.lineNumber(offset: Int): Int = take(offset).count { character -> character == '\n' } + 1

private fun String.classHeaderStartingAt(offset: Int): String {
    val end = indexOf('{', startIndex = offset).takeIf { index -> index >= 0 } ?: length
    return substring(offset, end.coerceAtMost(offset + MAX_CLASS_HEADER_LENGTH))
}

private fun String.maskCommentsAndLiterals(): String {
    val result = StringBuilder(length)
    var index = 0
    var state = KotlinLexicalState.CODE
    var blockDepth = 0
    while (index < length) {
        val current = this[index]
        val next = getOrNull(index + 1)
        val tripleQuote = startsWith("\"\"\"", index)
        when (state) {
            KotlinLexicalState.CODE -> when {
                current == '/' && next == '/' -> {
                    result.append("  ")
                    index += 2
                    state = KotlinLexicalState.LINE_COMMENT
                }
                current == '/' && next == '*' -> {
                    result.append("  ")
                    index += 2
                    blockDepth = 1
                    state = KotlinLexicalState.BLOCK_COMMENT
                }
                tripleQuote -> {
                    result.append("   ")
                    index += 3
                    state = KotlinLexicalState.TRIPLE_STRING
                }
                current == '"' -> {
                    result.append(' ')
                    index++
                    state = KotlinLexicalState.STRING
                }
                current == '\'' -> {
                    result.append(' ')
                    index++
                    state = KotlinLexicalState.CHAR
                }
                else -> {
                    result.append(current)
                    index++
                }
            }
            KotlinLexicalState.LINE_COMMENT -> {
                result.append(if (current == '\n') '\n' else ' ')
                index++
                if (current == '\n') {
                    state = KotlinLexicalState.CODE
                }
            }
            KotlinLexicalState.BLOCK_COMMENT -> when {
                current == '/' && next == '*' -> {
                    result.append("  ")
                    index += 2
                    blockDepth++
                }
                current == '*' && next == '/' -> {
                    result.append("  ")
                    index += 2
                    blockDepth--
                    if (blockDepth == 0) {
                        state = KotlinLexicalState.CODE
                    }
                }
                else -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    index++
                }
            }
            KotlinLexicalState.STRING,
            KotlinLexicalState.CHAR -> {
                val escaped = current == '\\' && next != null
                result.append(if (current == '\n') '\n' else ' ')
                index++
                if (escaped) {
                    result.append(if (next == '\n') '\n' else ' ')
                    index++
                } else if (
                    state == KotlinLexicalState.STRING && current == '"' ||
                    state == KotlinLexicalState.CHAR && current == '\''
                ) {
                    state = KotlinLexicalState.CODE
                }
            }
            KotlinLexicalState.TRIPLE_STRING -> if (tripleQuote) {
                result.append("   ")
                index += 3
                state = KotlinLexicalState.CODE
            } else {
                result.append(if (current == '\n') '\n' else ' ')
                index++
            }
        }
    }
    return result.toString()
}

private enum class KotlinLexicalState {
    CODE,
    LINE_COMMENT,
    BLOCK_COMMENT,
    STRING,
    TRIPLE_STRING,
    CHAR,
}

private val DATA_CLASS = Regex("\\bdata\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)")
private val PACKAGE_DECLARATION = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*$")
private val JIMMER_ENTITY_ANNOTATION = Regex("(?m)^\\s*@(Entity|MappedSuperclass|Embeddable)\\b")
private val TYPE_DECLARATION = Regex("\\b(?:class|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)")
private val SERVICE_CONTRACT = Regex(
    "(?m)^\\s*(?:(?:public|internal|private)\\s+)?(?:sealed\\s+)?(?:interface|abstract\\s+class)\\s+" +
        "([A-Za-z_][A-Za-z0-9_]*Service)\\b",
)
private val CONCRETE_SERVICE = Regex(
    "(?m)^\\s*(?:(?:public|internal|private)\\s+)?(?:open\\s+)?class\\s+" +
        "([A-Za-z_][A-Za-z0-9_]*Service(?:Impl)?)\\b",
)
private val LEGACY_CONTROLLER_TYPE = Regex(
    "\\b(?:class|object|interface)\\s+([A-Za-z_][A-Za-z0-9_]*(?:RouteContributor|Routes))\\b",
)
private val LEGACY_ROUTE_FUNCTION = Regex(
    "\\bfun\\s+Route\\.([A-Za-z_][A-Za-z0-9_]*Routes)\\s*\\(",
)
private val GENERATED_SERVICE_IMPORT = Regex(
    "(?m)^\\s*import\\s+([A-Za-z_][A-Za-z0-9_.]*\\.generated\\.service\\.([A-Za-z_][A-Za-z0-9_]*Service))\\s*$",
)
private val FULLY_QUALIFIED_GENERATED_SERVICE = Regex(
    "[A-Za-z_][A-Za-z0-9_.]*\\.generated\\.service\\.[A-Za-z_][A-Za-z0-9_]*Service",
)
private val businessSourceObjectMapper = jacksonObjectMapper()
private const val BUSINESS_SOURCE_REPORT_SCHEMA_VERSION = 2
private const val MAX_CLASS_HEADER_LENGTH = 8_192
private const val GENERATED_MARKER_LINE_LIMIT = 8
private const val GENERATED_SOURCE_MARKER = "generated by studio"
