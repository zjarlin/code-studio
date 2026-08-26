package site.addzero.dto.compiler

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 数据结构的发现来源。 */
enum class DtoStructureOrigin {
    SOURCE,
    GENERATED,
    METADATA,
}

/** 可参与复用分析的语言无关数据结构。 */
data class LsiDataStructure(
    val qualifiedName: String,
    val properties: List<LsiDtoProperty>,
    val origins: Set<DtoStructureOrigin>,
)

/** 复用候选相对左侧结构的关系。 */
enum class DtoReuseRelation {
    EXACT,
    CONTAINS,
    OVERLAP,
}

/** 两个结构之间的复用候选。 */
data class DtoReuseCandidate(
    val leftQualifiedName: String,
    val rightQualifiedName: String,
    val relation: DtoReuseRelation,
    val sharedProperties: List<String>,
    val leftCoverage: Double,
    val rightCoverage: Double,
    val jaccard: Double,
    val constructorOrderCompatible: Boolean,
    val defaultValuesCompatible: Boolean,
)

/** 至少被两个结构共同拥有的字段片段。 */
data class DtoReusableFragment(
    val properties: List<String>,
    val structureQualifiedNames: List<String>,
)

/** 两个字段在全部结构中的共现关系。 */
data class DtoFieldCorrelation(
    val firstProperty: String,
    val secondProperty: String,
    val coOccurrenceCount: Int,
    val firstConfidence: Double,
    val secondConfidence: Double,
    val jaccard: Double,
)

/** 全部数据结构及其非阻断复用建议。 */
data class DtoAnalysisReport(
    val schemaVersion: Int = DTO_ANALYSIS_SCHEMA_VERSION,
    val structures: List<LsiDataStructure>,
    val candidates: List<DtoReuseCandidate>,
    val fragments: List<DtoReusableFragment>,
    val fieldCorrelations: List<DtoFieldCorrelation>,
)

/** 可持久化、可校验过期状态的结构分析快照。 */
data class DtoAnalysisSnapshot(
    val schemaVersion: Int = DTO_ANALYSIS_SCHEMA_VERSION,
    val sourceFingerprint: String,
    val metadataFingerprint: String,
    val generatedAtEpochMillis: Long,
    val sourceStructures: List<LsiDataStructure>,
    val metadataStructures: List<LsiDataStructure>,
    val report: DtoAnalysisReport,
)

/** 按结构全限定名与字段签名计算稳定指纹。 */
fun Collection<LsiDataStructure>.dtoStructureFingerprint(): String {
    val canonical = sortedBy(LsiDataStructure::qualifiedName).joinToString("\n") { structure ->
        val origins = structure.origins.map(DtoStructureOrigin::name).sorted().joinToString(",")
        val properties = structure.properties.joinToString("|") { property ->
            "${property.name}:${property.type.canonicalName()}:${property.defaultValue?.canonicalName().orEmpty()}"
        }
        "${structure.qualifiedName}#$origins#$properties"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

/** 对统一结构模型执行确定性的重复与相关性分析。 */
object DtoStructureAnalyzer {
    fun analyze(structures: Collection<LsiDataStructure>): DtoAnalysisReport {
        val canonical = structures.canonicalize()
        val candidates = canonical.indices.flatMap { leftIndex ->
            (leftIndex + 1 until canonical.size).mapNotNull { rightIndex ->
                canonical[leftIndex].compareTo(canonical[rightIndex])
            }
        }.sortedWith(compareBy(DtoReuseCandidate::relation, DtoReuseCandidate::leftQualifiedName, DtoReuseCandidate::rightQualifiedName))
        return DtoAnalysisReport(
            structures = canonical,
            candidates = candidates,
            fragments = candidates.toReusableFragments(),
            fieldCorrelations = canonical.fieldCorrelations(),
        )
    }

    private fun Collection<LsiDataStructure>.canonicalize(): List<LsiDataStructure> =
        groupBy(LsiDataStructure::qualifiedName)
            .map { (qualifiedName, definitions) ->
                val selected = definitions.maxWith(
                    compareBy<LsiDataStructure> { structure ->
                        structure.origins.maxOf { origin -> origin.priority }
                    }
                        .thenBy { structure -> structure.properties.joinToString("|") { property -> property.signature() } },
                )
                selected.copy(
                    qualifiedName = qualifiedName,
                    origins = definitions.flatMapTo(linkedSetOf(), LsiDataStructure::origins),
                )
            }
            .sortedBy(LsiDataStructure::qualifiedName)

    private fun LsiDataStructure.compareTo(other: LsiDataStructure): DtoReuseCandidate? {
        val leftSignatures = properties.associateBy { property -> property.signature() }
        val rightSignatures = other.properties.associateBy { property -> property.signature() }
        val shared = leftSignatures.keys intersect rightSignatures.keys
        if (shared.size < MINIMUM_SHARED_PROPERTY_COUNT) {
            return null
        }
        val unionSize = leftSignatures.size + rightSignatures.size - shared.size
        val jaccard = shared.size.toDouble() / unionSize
        val relation = when {
            leftSignatures.keys == rightSignatures.keys -> DtoReuseRelation.EXACT
            leftSignatures.keys.containsAll(rightSignatures.keys) -> DtoReuseRelation.CONTAINS
            rightSignatures.keys.containsAll(leftSignatures.keys) -> DtoReuseRelation.CONTAINS
            jaccard >= MINIMUM_JACCARD -> DtoReuseRelation.OVERLAP
            else -> return null
        }
        val leftContainsRight = leftSignatures.keys.containsAll(rightSignatures.keys)
        val orderedPair = if (relation == DtoReuseRelation.CONTAINS && !leftContainsRight) {
            other to this
        } else {
            this to other
        }
        val orderedLeft = orderedPair.first
        val orderedRight = orderedPair.second
        val orderedLeftSignatures = orderedLeft.properties.map { property -> property.signature() }
        val orderedRightSignatures = orderedRight.properties.map { property -> property.signature() }
        return DtoReuseCandidate(
            leftQualifiedName = orderedLeft.qualifiedName,
            rightQualifiedName = orderedRight.qualifiedName,
            relation = relation,
            sharedProperties = shared.map { signature -> signature.propertyName() }.sorted(),
            leftCoverage = shared.size.toDouble() / orderedLeft.properties.size,
            rightCoverage = shared.size.toDouble() / orderedRight.properties.size,
            jaccard = jaccard,
            constructorOrderCompatible = orderedLeftSignatures == orderedRightSignatures,
            defaultValuesCompatible = orderedLeft.sharedDefaultsEqual(orderedRight, shared),
        )
    }

    private fun LsiDataStructure.sharedDefaultsEqual(
        other: LsiDataStructure,
        sharedSignatures: Set<String>,
    ): Boolean {
        val leftDefaults = properties
            .filter { property -> property.signature() in sharedSignatures }
            .associate { property -> property.signature() to property.defaultValue }
        val rightDefaults = other.properties
            .filter { property -> property.signature() in sharedSignatures }
            .associate { property -> property.signature() to property.defaultValue }
        return leftDefaults == rightDefaults
    }

    private fun List<DtoReuseCandidate>.toReusableFragments(): List<DtoReusableFragment> {
        val grouped = groupBy { candidate -> candidate.sharedProperties.joinToString("\u0000") }
            .map { (_, matches) ->
                DtoReusableFragment(
                    properties = matches.first().sharedProperties,
                    structureQualifiedNames = matches.flatMapTo(sortedSetOf()) { candidate ->
                        listOf(candidate.leftQualifiedName, candidate.rightQualifiedName)
                    }.toList(),
                )
            }
        return grouped.filterNot { fragment ->
            grouped.any { other ->
                fragment !== other &&
                    other.structureQualifiedNames == fragment.structureQualifiedNames &&
                    other.properties.size > fragment.properties.size &&
                    other.properties.containsAll(fragment.properties)
            }
        }.sortedWith(compareByDescending<DtoReusableFragment> { fragment -> fragment.structureQualifiedNames.size }
            .thenByDescending { fragment -> fragment.properties.size }
            .thenBy { fragment -> fragment.properties.joinToString() })
    }

    private fun List<LsiDataStructure>.fieldCorrelations(): List<DtoFieldCorrelation> {
        val structuresByProperty = buildMap<String, MutableSet<String>> {
            this@fieldCorrelations.forEach { structure ->
                structure.properties.map { property -> property.signature() }.distinct().forEach { signature ->
                    getOrPut(signature, ::linkedSetOf) += structure.qualifiedName
                }
            }
        }
        val signatures = structuresByProperty.keys.sorted()
        return signatures.indices.flatMap { firstIndex ->
            (firstIndex + 1 until signatures.size).mapNotNull { secondIndex ->
                val first = signatures[firstIndex]
                val second = signatures[secondIndex]
                val firstStructures = structuresByProperty.getValue(first)
                val secondStructures = structuresByProperty.getValue(second)
                val coOccurrence = (firstStructures intersect secondStructures).size
                if (coOccurrence < MINIMUM_CORRELATION_OCCURRENCE) {
                    return@mapNotNull null
                }
                DtoFieldCorrelation(
                    firstProperty = first.propertyName(),
                    secondProperty = second.propertyName(),
                    coOccurrenceCount = coOccurrence,
                    firstConfidence = coOccurrence.toDouble() / firstStructures.size,
                    secondConfidence = coOccurrence.toDouble() / secondStructures.size,
                    jaccard = coOccurrence.toDouble() /
                        (firstStructures.size + secondStructures.size - coOccurrence),
                )
            }
        }.sortedWith(compareByDescending(DtoFieldCorrelation::coOccurrenceCount)
            .thenByDescending(DtoFieldCorrelation::jaccard)
            .thenBy(DtoFieldCorrelation::firstProperty)
            .thenBy(DtoFieldCorrelation::secondProperty))
    }

    private fun LsiDtoProperty.signature(): String = "$name:${type.canonicalName()}"

    private fun String.propertyName(): String = substringBefore(':')

    private val DtoStructureOrigin.priority: Int
        get() = when (this) {
            DtoStructureOrigin.SOURCE -> 0
            DtoStructureOrigin.GENERATED -> 1
            DtoStructureOrigin.METADATA -> 2
        }
}

const val DTO_ANALYSIS_SCHEMA_VERSION = 1
private const val MINIMUM_SHARED_PROPERTY_COUNT = 2
private const val MINIMUM_CORRELATION_OCCURRENCE = 2
private const val MINIMUM_JACCARD = 0.6
