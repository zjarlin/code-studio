package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeDictionaryEnumSourceGeneratorTest {
    @Test
    fun `generates only enabled dictionaries into owner feature enums package`() {
        val model = model()
        val enabled = dictionary(generateEnum = true)
        val disabled = dictionary(
            dictionaryCode = "job_display_status",
            className = "JobDisplayStatus",
            generateEnum = false,
        )

        val files = LowcodeDictionaryEnumSourceGenerator.generate(
            dictionaries = listOf(disabled, enabled),
            models = listOf(model),
            contributorId = requireNotNull(model.contributorId),
        )

        assertEquals(1, files.size)
        val file = files.single()
        assertEquals("example.recruitment.generated.enums", file.packageName)
        assertEquals("JobStatus", file.fileName)
        assertTrue(file.content.contains("@EnumType(EnumType.Strategy.NAME)"))
        assertTrue(file.content.contains("@EnumItem(name = \"PENDING\")"))
        assertTrue(file.content.contains("PENDING(\"PENDING\", \"待处理\")"))
        assertTrue(file.content.contains("fun fromCode(code: String): JobStatus?"))
        assertFalse(file.content.contains("JobDisplayStatus"))
        assertTrue(file.relativePath.endsWith("/generated/enums/JobStatus.kt"))
    }

    @Test
    fun `renders ordinal dictionary values as explicit Jimmer ordinals`() {
        val model = model()
        val dictionary = dictionary(generateEnum = true).copy(
            enumStorage = LowcodeEnumStorage.ORDINAL,
            items = listOf(LowcodeDictionaryItemMeta(10, "10", "进行中", "RUNNING")),
        )

        val content = LowcodeDictionaryEnumSourceGenerator.generate(
            dictionaries = listOf(dictionary),
            models = listOf(model),
            contributorId = requireNotNull(model.contributorId),
        ).single().content

        assertTrue(content.contains("@EnumType(EnumType.Strategy.ORDINAL)"))
        assertTrue(content.contains("@EnumItem(ordinal = 10)"))
    }

    @Test
    fun `metadata rejects enabled enum without stable constant names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeMetadata(
                models = listOf(model()),
                dtoDefinitions = emptyList(),
                routeBindings = emptyList(),
                contracts = emptyList(),
                dictionaries = listOf(
                    dictionary(generateEnum = true).copy(
                        items = listOf(LowcodeDictionaryItemMeta(10, "PENDING", "待处理", null)),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("未配置枚举常量名"))
    }

    private fun dictionary(
        dictionaryCode: String = "job_status",
        className: String = "JobStatus",
        generateEnum: Boolean,
    ) = LowcodeDictionaryMeta(
        dictionaryCode = dictionaryCode,
        name = "任务状态",
        generateEnum = generateEnum,
        ownerModelCode = "siteJob",
        enumClassName = className,
        enumStorage = LowcodeEnumStorage.NAME,
        items = listOf(LowcodeDictionaryItemMeta(10, "PENDING", "待处理", "PENDING")),
    )

    private fun model() = LowcodeModelMeta(
        id = 1,
        modelCode = "siteJob",
        name = "招聘职位",
        packageName = "legacy.recruitment",
        className = "SiteJob",
        tableName = "site_job",
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        contributorId = "example.example",
        fields = emptyList(),
        queries = emptyList(),
        relations = emptyList(),
        featurePackageName = "example.recruitment",
    )
}
