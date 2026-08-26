package site.addzero.dto.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DtoStructureAnalyzerTest {
    @Test
    fun `识别精确重复包含和高相似结构`() {
        val report = DtoStructureAnalyzer.analyze(
            listOf(
                structure("example.First", "registry", "policies"),
                structure("example.Second", "registry", "policies"),
                structure("example.Container", "registry", "policies", "traceId", "tenantId"),
                structure("example.Overlap", "registry", "policies", "traceId", "requestId"),
                structure("example.Unrelated", "name", "status", "remark"),
            ),
        )

        assertTrue(report.candidates.any { candidate ->
            candidate.relation == DtoReuseRelation.EXACT &&
                candidate.leftQualifiedName == "example.First" &&
                candidate.rightQualifiedName == "example.Second"
        })
        assertTrue(report.candidates.any { candidate ->
            candidate.relation == DtoReuseRelation.CONTAINS &&
                candidate.leftQualifiedName == "example.Container"
        })
        assertTrue(report.candidates.any { candidate ->
            candidate.relation == DtoReuseRelation.OVERLAP &&
                candidate.rightQualifiedName == "example.Overlap"
        })
        assertFalse(report.candidates.any { candidate ->
            candidate.leftQualifiedName == "example.Unrelated" || candidate.rightQualifiedName == "example.Unrelated"
        })
    }

    @Test
    fun `同名结构按元数据优先并合并来源`() {
        val source = structure("example.Payload", "sourceA", "sourceB")
        val metadata = structure(
            "example.Payload",
            "metadataA",
            "metadataB",
            origin = DtoStructureOrigin.METADATA,
        )

        val report = DtoStructureAnalyzer.analyze(listOf(source, metadata))

        assertEquals(1, report.structures.size)
        assertEquals(listOf("metadataA", "metadataB"), report.structures.single().properties.map(LsiDtoProperty::name))
        assertEquals(setOf(DtoStructureOrigin.SOURCE, DtoStructureOrigin.METADATA), report.structures.single().origins)
    }

    @Test
    fun `输出共享片段和字段相关性`() {
        val report = DtoStructureAnalyzer.analyze(
            listOf(
                structure("example.First", "registry", "policies"),
                structure("example.Second", "registry", "policies"),
                structure("example.Third", "registry", "policies", "traceId"),
            ),
        )

        assertTrue(report.fragments.any { fragment -> fragment.properties == listOf("policies", "registry") })
        assertTrue(report.fieldCorrelations.any { correlation ->
            setOf(correlation.firstProperty, correlation.secondProperty) == setOf("policies", "registry") &&
                correlation.coOccurrenceCount == 3
        })
    }

    @Test
    fun `结构指纹与输入集合顺序无关但保留构造顺序`() {
        val first = structure("example.First", "registry", "policies")
        val second = structure("example.Second", "traceId", "tenantId")

        assertEquals(listOf(first, second).dtoStructureFingerprint(), listOf(second, first).dtoStructureFingerprint())
        assertFalse(
            listOf(first).dtoStructureFingerprint() ==
                listOf(structure("example.First", "policies", "registry")).dtoStructureFingerprint(),
        )
    }

    private fun structure(
        qualifiedName: String,
        vararg properties: String,
        origin: DtoStructureOrigin = DtoStructureOrigin.SOURCE,
    ) = LsiDataStructure(
        qualifiedName = qualifiedName,
        properties = properties.map { name ->
            LsiDtoProperty(name, LsiDtoType.STRING, "$name。")
        },
        origins = setOf(origin),
    )
}
