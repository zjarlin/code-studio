package site.addzero.studio.metadata

import site.addzero.ddl.compiler.DdlMigrationCompiler
import site.addzero.dto.compiler.DTO_ANALYSIS_SCHEMA_VERSION
import site.addzero.dto.compiler.DtoAnalysisSnapshot
import site.addzero.dto.compiler.DtoFieldCorrelation
import site.addzero.dto.compiler.DtoReusableFragment
import site.addzero.dto.compiler.DtoReuseCandidate
import site.addzero.dto.compiler.DtoStructureAnalyzer
import site.addzero.dto.compiler.LsiDataStructure
import site.addzero.dto.compiler.dtoStructureFingerprint
import site.addzero.platform.lowcode.generator.LowcodeDtoSourceGenerator
import site.addzero.platform.lowcode.generator.LowcodeGeneratedFile
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeModuleCompiler
import site.addzero.platform.lowcode.generator.LowcodeSourceCompiler
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoDefinition
import site.addzero.platform.lowcode.generator.LsiLowcodeFeature
import site.addzero.studio.runtime.GenerationTargetProfile
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class MetadataPreviewFile(
    val filePath: String,
    val content: String,
)

internal data class ModelMetadataPreview(
    val modelId: Long,
    val modelCode: String,
    val files: List<MetadataPreviewFile>,
)

internal data class DtoMetadataPreview(
    val dtoId: Long,
    val dtoCode: String,
    val files: List<MetadataPreviewFile>,
)

internal data class ContractMetadataPreview(
    val contractId: Long,
    val contractCode: String,
    val files: List<MetadataPreviewFile>,
)

internal data class LibraryMetadataPreview(
    val libraryId: Long,
    val featureId: Long?,
    val files: List<MetadataPreviewFile>,
)

internal data class DtoReuseAnalysis(
    val draftQualifiedName: String,
    val snapshotGeneratedAtEpochMillis: Long,
    val metadataStale: Boolean,
    val sourceFingerprint: String,
    val currentMetadataFingerprint: String,
    val candidates: List<DtoReuseCandidate>,
    val reusableFragments: List<DtoReusableFragment>,
    val fieldCorrelations: List<DtoFieldCorrelation>,
    val structures: List<LsiDataStructure>,
)

internal class MetadataPreviewService(
    private val store: MetadataJdbcStore,
    private val targetProfile: GenerationTargetProfile,
) {
    suspend fun model(id: Long): ModelMetadataPreview {
        val modelCode = store.read { modelDetail(id).requiredText("modelCode") }
        return store.compile { metadata ->
            val model = metadata.models.singleOrNull { candidate -> candidate.id == id }
                ?: badRequest("模型已停用，不能生成预览: $modelCode")
            val sourceFiles = LowcodeSourceCompiler.generate(model, metadata.models)
            val migration = DdlMigrationCompiler.compile(model, metadata.models)
            ModelMetadataPreview(id, modelCode, (sourceFiles + listOfNotNull(migration)).toPreviewFiles())
        }
    }

    suspend fun dto(id: Long): DtoMetadataPreview {
        val dtoCode = store.read { dtoDetail(id).requiredText("dtoCode") }
        return store.compile { metadata ->
            val definition = metadata.dtoDefinitions.singleOrNull { candidate -> candidate.dtoCode == dtoCode }
                ?: badRequest("DTO 已停用，不能生成预览: $dtoCode")
            val files = LowcodeDtoSourceGenerator.generateDefinitions(listOf(definition), metadata.models) +
                LowcodeDtoSourceGenerator.generateDefinitionValidations(listOf(definition), metadata.models)
            DtoMetadataPreview(id, dtoCode, files.toPreviewFiles())
        }
    }

    suspend fun contract(id: Long): ContractMetadataPreview {
        val contractCode = store.read { contractDetail(id).requiredText("contractCode") }
        return store.compile { metadata ->
            val contract = metadata.contracts.singleOrNull { candidate -> candidate.contractCode == contractCode }
                ?: badRequest("Service 契约已停用，不能生成预览: $contractCode")
            val files = LowcodeSourceCompiler.generate(contract, metadata.models, metadata.dtoDefinitions)
            ContractMetadataPreview(id, contractCode, files.toPreviewFiles())
        }
    }

    suspend fun library(id: Long, featureId: Long?): LibraryMetadataPreview {
        val contributorId = store.read {
            val library = libraryDetail(id)
            if (library["status"].asInt() != 1) {
                badRequest("Library 已停用，不能生成预览")
            }
            featureId?.let { selectedId ->
                val feature = featureLocation(selectedId) ?: notFound("Library 功能不存在: $selectedId")
                if (feature.libraryId != id) {
                    badRequest("所选功能不属于 Library: $id")
                }
            }
            library.requiredText("code")
        }
        return store.compile { metadata ->
            val featurePackages = metadata.selectedFeaturePackages(id, featureId)
            val files = LowcodeModuleCompiler.generate(
                metadata = metadata,
                contributorId = contributorId,
                featurePackages = featurePackages,
                targetProfile = targetProfile,
            )
            LibraryMetadataPreview(id, featureId, files.toPreviewFiles())
        }
    }

    suspend fun analyzeDtoReuse(command: JsonNode): DtoReuseAnalysis {
        val input = store.read {
            val validation = validateDto(command)
            if (!validation.valid) {
                badRequest(validation.errors.joinToString("；"))
            }
            DtoReuseInput(toDtoDefinition(command), requireDtoAnalysisSnapshot())
        }
        return store.compile { metadata -> analyzeDtoReuse(input, metadata) }
    }

    private fun MetadataSession.requireDtoAnalysisSnapshot(): DtoAnalysisSnapshot {
        val sql = "SELECT report FROM $schema.lowcode_structure_analysis_snapshot WHERE id = 1"
        return connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                if (!rows.next()) {
                    badRequest("尚无 DTO 源码分析快照，请先运行宿主的 DTO 分析任务")
                }
                mapper.readValue(rows.getString("report"), DtoAnalysisSnapshot::class.java)
            }
        }.also { snapshot ->
            if (snapshot.schemaVersion != DTO_ANALYSIS_SCHEMA_VERSION) {
                badRequest("DTO 源码分析快照版本已过期，请重新运行宿主的 DTO 分析任务")
            }
        }
    }

    private fun MetadataSession.toDtoDefinition(command: JsonNode): LsiLowcodeDtoDefinition {
        val featureId = command.requiredLong("featureId")
        val location = requireEditableFeature(featureId)
        val normalized = command.deepCopy() as? ObjectNode ?: badRequest("DTO 命令必须是 JSON 对象")
        normalized.remove("id")
        normalized.remove("featureId")
        normalized.put("packageName", location.packageName)
        normalized.put("featurePackageName", location.packageName)
        normalized.put("contributorId", location.contributorId)
        return mapper.treeToValue(normalized, LsiLowcodeDtoDefinition::class.java)
    }

    private fun analyzeDtoReuse(input: DtoReuseInput, metadata: LowcodeMetadata): DtoReuseAnalysis {
        val persisted = LowcodeDtoSourceGenerator.toDataStructures(metadata.dtoDefinitions, metadata.models)
        val currentMetadataFingerprint = persisted.dtoStructureFingerprint()
        val draft = LowcodeDtoSourceGenerator
            .toDataStructures(listOf(input.definition), metadata.models)
            .single()
        val currentStructures = persisted.filterNot { structure -> structure.qualifiedName == draft.qualifiedName } + draft
        val report = DtoStructureAnalyzer.analyze(input.snapshot.sourceStructures + currentStructures)
        val candidates = report.candidates.filter { candidate ->
            candidate.leftQualifiedName == draft.qualifiedName || candidate.rightQualifiedName == draft.qualifiedName
        }
        val candidateNames = candidates.flatMapTo(linkedSetOf()) { candidate ->
            listOf(candidate.leftQualifiedName, candidate.rightQualifiedName)
        }
        return DtoReuseAnalysis(
            draftQualifiedName = draft.qualifiedName,
            snapshotGeneratedAtEpochMillis = input.snapshot.generatedAtEpochMillis,
            metadataStale = currentMetadataFingerprint != input.snapshot.metadataFingerprint,
            sourceFingerprint = input.snapshot.sourceFingerprint,
            currentMetadataFingerprint = currentMetadataFingerprint,
            candidates = candidates,
            reusableFragments = report.fragments.filter { fragment ->
                draft.qualifiedName in fragment.structureQualifiedNames
            },
            fieldCorrelations = report.fieldCorrelations.filter { correlation ->
                draft.properties.any { property ->
                    property.name == correlation.firstProperty || property.name == correlation.secondProperty
                }
            },
            structures = report.structures.filter { structure -> structure.qualifiedName in candidateNames },
        )
    }
}

private data class DtoReuseInput(
    val definition: LsiLowcodeDtoDefinition,
    val snapshot: DtoAnalysisSnapshot,
)

private fun LowcodeMetadata.selectedFeaturePackages(libraryId: Long, featureId: Long?): Set<String>? {
    if (featureId == null) {
        return null
    }
    val libraryFeatures = features.filter { feature -> feature.libraryId == libraryId }
    if (libraryFeatures.none { feature -> feature.featureId == featureId }) {
        notFound("Library 功能不存在: $featureId")
    }
    val selectedIds = linkedSetOf(featureId)
    var changed: Boolean
    do {
        val sizeBefore = selectedIds.size
        changed = libraryFeatures.filter { feature -> feature.parentId in selectedIds }
            .mapTo(selectedIds, LsiLowcodeFeature::featureId)
            .let { selectedIds.size != sizeBefore }
    } while (changed)
    return libraryFeatures.filter { feature -> feature.featureId in selectedIds }
        .mapTo(linkedSetOf(), LsiLowcodeFeature::packageName)
}

private fun List<LowcodeGeneratedFile>.toPreviewFiles(): List<MetadataPreviewFile> =
    sortedBy(LowcodeGeneratedFile::relativePath)
        .map { file -> MetadataPreviewFile(file.relativePath, file.content) }

internal fun archivePreviewFiles(files: List<MetadataPreviewFile>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
        files.sortedBy(MetadataPreviewFile::filePath).forEach { file ->
            require(file.filePath.isNotBlank() && !file.filePath.startsWith('/') && ".." !in file.filePath.split('/')) {
                "生成文件路径不安全: ${file.filePath}"
            }
            val entry = ZipEntry(file.filePath)
            entry.time = 0L
            zip.putNextEntry(entry)
            zip.write(file.content.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
