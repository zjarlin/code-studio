package site.addzero.toolchain.dto

import kotlin.Metadata
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmType
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isData
import kotlin.metadata.isNullable
import kotlin.metadata.isSecondary
import kotlin.metadata.jvm.KotlinClassMetadata
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import site.addzero.dto.compiler.DtoAnalysisSnapshot
import site.addzero.dto.compiler.DtoStructureAnalyzer
import site.addzero.dto.compiler.DtoStructureOrigin
import site.addzero.dto.compiler.LsiDataStructure
import site.addzero.dto.compiler.LsiDtoDefaultValue
import site.addzero.dto.compiler.LsiDtoProperty
import site.addzero.dto.compiler.LsiDtoType
import site.addzero.dto.compiler.dtoStructureFingerprint
import site.addzero.platform.lowcode.generator.LowcodeDtoSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeMetadataSnapshots
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

/** 生成显式编译产物的 DTO 复用候选快照，候选本身永不阻断命令。 */
@TaskAction
@OptIn(ExperimentalPathApi::class)
fun analyzeDtoModels(
    @Input compiledArtifactsDirectories: List<Path>,
    @Input metadataSnapshot: Path,
    @Output reportDirectory: Path,
) {
    val missingArtifacts = compiledArtifactsDirectories.filterNot(Path::isDirectory)
    require(missingArtifacts.isEmpty()) {
        "缺少 JVM 编译产物，请先构建当前模块：${missingArtifacts.joinToString()}"
    }
    val sourceStructures = CompiledDataClassScanner.scan(compiledArtifactsDirectories)
    require(sourceStructures.isNotEmpty()) {
        "未在显式编译产物目录发现 Kotlin data class。请先构建当前模块"
    }
    val metadata = LowcodeMetadataSnapshots.decode(metadataSnapshot.toFile().readText()).metadata
    val metadataStructures = LowcodeDtoSourceGenerator.toDataStructures(metadata.dtoDefinitions, metadata.models)
    val analysisSnapshot = snapshot(sourceStructures, metadataStructures, generatedAt = 0L)
    writeReports(reportDirectory, analysisSnapshot, compiledArtifactsDirectories)
    println(
        "DTO 结构分析完成: ${analysisSnapshot.report.structures.size} 个结构，" +
            "${analysisSnapshot.report.candidates.size} 个复用候选，报告 ${reportDirectory.pathString}",
    )
}

private fun snapshot(
    sourceStructures: List<LsiDataStructure>,
    metadataStructures: List<LsiDataStructure>,
    generatedAt: Long,
): DtoAnalysisSnapshot = DtoAnalysisSnapshot(
    sourceFingerprint = sourceStructures.dtoStructureFingerprint(),
    metadataFingerprint = metadataStructures.dtoStructureFingerprint(),
    generatedAtEpochMillis = generatedAt,
    sourceStructures = sourceStructures,
    metadataStructures = metadataStructures,
    report = DtoStructureAnalyzer.analyze(sourceStructures + metadataStructures),
)

private fun writeReports(
    reportDirectory: Path,
    snapshot: DtoAnalysisSnapshot,
    compiledArtifactsDirectories: List<Path>,
) {
    reportDirectory.createDirectories()
    reportDirectory.resolve("report.json").writeText(
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot) + "\n",
    )
    reportDirectory.resolve("report.md").writeText(snapshot.toMarkdown(compiledArtifactsDirectories))
}

private fun DtoAnalysisSnapshot.toMarkdown(compiledArtifactsDirectories: List<Path>): String = buildString {
    appendLine("# DTO model analysis")
    appendLine()
    appendLine("- Schema version: `$schemaVersion`")
    appendLine("- Generated at: `$generatedAtEpochMillis`")
    appendLine("- Source fingerprint: `$sourceFingerprint`")
    appendLine("- Metadata fingerprint: `$metadataFingerprint`")
    appendLine("- Structures: `${report.structures.size}`")
    appendLine("- Candidates: `${report.candidates.size}`")
    appendLine("- Reusable fragments: `${report.fragments.size}`")
    appendLine()
    appendLine("Candidates are advisory. The command does not merge or rewrite source code.")
    appendLine()
    appendLine("## Candidates")
    appendLine()
    if (report.candidates.isEmpty()) {
        appendLine("No reuse candidates found.")
    } else {
        appendLine("| Relation | Left | Right | Shared | Left coverage | Right coverage | Jaccard | Order | Defaults |")
        appendLine("| --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |")
        report.candidates.forEach { candidate ->
            appendLine(
                "| ${candidate.relation} | `${candidate.leftQualifiedName}` | `${candidate.rightQualifiedName}` | " +
                    "${candidate.sharedProperties.joinToString(", ")} | ${candidate.leftCoverage.formatRatio()} | " +
                    "${candidate.rightCoverage.formatRatio()} | ${candidate.jaccard.formatRatio()} | " +
                    "${candidate.constructorOrderCompatible} | ${candidate.defaultValuesCompatible} |",
            )
        }
    }
    appendLine()
    appendLine("Compiled inputs:")
    compiledArtifactsDirectories
        .map { directory -> directory.toAbsolutePath().normalize() }
        .sorted()
        .forEach { directory -> appendLine("- `$directory`") }
}

private fun Double.formatRatio(): String = "%.3f".format(this)


@OptIn(ExperimentalPathApi::class)
private object CompiledDataClassScanner {
    fun scan(compiledArtifactsDirectories: List<Path>): List<LsiDataStructure> {
        val structures = compiledArtifactsDirectories
            .asSequence()
            .filter(Path::isDirectory)
            .flatMap { output ->
                output.walk()
                    .filter { classFile -> classFile.isRegularFile() && classFile.extension == "class" }
                    .mapNotNull { classFile -> readStructure(classFile) }
            }
            .toList()
        return DtoStructureAnalyzer.analyze(structures).structures
    }

    private fun readStructure(classFile: Path): LsiDataStructure? {
        val visitor = MetadataClassVisitor()
        ClassReader(classFile.readBytes()).accept(
            visitor,
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        if (visitor.synthetic || visitor.localOrAnonymous) return null
        val metadata = visitor.metadata.toMetadata() ?: return null
        val classMetadata = KotlinClassMetadata.readLenient(metadata) as? KotlinClassMetadata.Class ?: return null
        val kmClass = classMetadata.kmClass
        if (!kmClass.isData) return null
        val primaryConstructor = kmClass.constructors.singleOrNull { constructor -> !constructor.isSecondary }
            ?: return null
        val qualifiedName = kmClass.name.replace('/', '.')
        val origin = if (".generated." in qualifiedName) DtoStructureOrigin.GENERATED else DtoStructureOrigin.SOURCE
        return LsiDataStructure(
            qualifiedName = qualifiedName,
            properties = primaryConstructor.valueParameters.map { parameter ->
                LsiDtoProperty(
                    name = parameter.name,
                    type = parameter.type.toLsiType(),
                    description = parameter.name,
                    defaultValue = if (parameter.declaresDefaultValue) LsiDtoDefaultValue.DECLARED else null,
                )
            },
            origins = setOf(origin),
        )
    }

    private fun KmType.toLsiType(): LsiDtoType {
        val qualifiedName = when (val value = classifier) {
            is KmClassifier.Class -> value.name.replace('/', '.')
            is KmClassifier.TypeAlias -> value.name.replace('/', '.')
            is KmClassifier.TypeParameter -> "kotlin.Any"
        }
        return LsiDtoType(
            qualifiedName = qualifiedName,
            arguments = arguments.map { projection ->
                projection.type?.toLsiType() ?: LsiDtoType("kotlin.Any", nullable = true)
            },
            nullable = isNullable,
        )
    }
}

private class MetadataClassVisitor : ClassVisitor(Opcodes.ASM9) {
    val metadata = MetadataValues()
    var synthetic: Boolean = false
        private set
    var localOrAnonymous: Boolean = false
        private set

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        synthetic = access and Opcodes.ACC_SYNTHETIC != 0
    }

    override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
        localOrAnonymous = true
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
        if (descriptor == METADATA_DESCRIPTOR) MetadataAnnotationVisitor(metadata) else null
}

private class MetadataAnnotationVisitor(
    private val target: MetadataValues,
) : AnnotationVisitor(Opcodes.ASM9) {
    override fun visit(name: String, value: Any) {
        when (name) {
            "k" -> target.kind = value as Int
            "mv" -> target.metadataVersion = (value as IntArray).toList()
            "d1" -> target.data1 = (value as Array<*>).filterIsInstance<String>()
            "d2" -> target.data2 = (value as Array<*>).filterIsInstance<String>()
            "xs" -> target.extraString = value as String
            "pn" -> target.packageName = value as String
            "xi" -> target.extraInt = value as Int
        }
    }

    override fun visitArray(name: String): AnnotationVisitor = object : AnnotationVisitor(Opcodes.ASM9) {
        private val values = mutableListOf<Any>()

        override fun visit(name: String?, value: Any) {
            values += value
        }

        override fun visitEnd() {
            when (name) {
                "mv" -> target.metadataVersion = values.filterIsInstance<Int>()
                "d1" -> target.data1 = values.filterIsInstance<String>()
                "d2" -> target.data2 = values.filterIsInstance<String>()
            }
        }
    }
}

private data class MetadataValues(
    var kind: Int? = null,
    var metadataVersion: List<Int> = emptyList(),
    var data1: List<String> = emptyList(),
    var data2: List<String> = emptyList(),
    var extraString: String = "",
    var packageName: String = "",
    var extraInt: Int = 0,
) {
    fun toMetadata(): Metadata? = kind?.let { resolvedKind ->
        Metadata(
            kind = resolvedKind,
            metadataVersion = metadataVersion.toIntArray(),
            data1 = data1.toTypedArray(),
            data2 = data2.toTypedArray(),
            extraString = extraString,
            packageName = packageName,
            extraInt = extraInt,
        )
    }
}

private val objectMapper = jacksonObjectMapper()
private const val METADATA_DESCRIPTOR = "Lkotlin/Metadata;"
