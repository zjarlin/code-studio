package site.addzero.validation.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinValidationSourceGeneratorTest {
    @Test
    fun `loads standard rule metadata through service loader`() {
        val codes = ValidationRuleMetadataCatalog.load().metadata.map(LsiValidationRuleMetadata::code)

        assertEquals(listOf("maxLength", "noBlankElements", "notBlank", "notEmpty"), codes)
    }

    @Test
    fun `generates deterministic validation extension`() {
        val source = requireNotNull(
            KotlinValidationSourceGenerator.generate(
                type = LsiValidatedType(
                    packageName = "example.generated.dto",
                    className = "ExampleCommand",
                    properties = listOf(
                        LsiValidatedProperty(
                            name = "name",
                            valueKind = LsiValidationValueKind.TEXT,
                            nullable = false,
                            rules = listOf(
                                LsiValidationRule("notBlank", "名称不能为空"),
                                LsiValidationRule("maxLength", parameters = mapOf("value" to "12")),
                            ),
                        ),
                        LsiValidatedProperty(
                            name = "codes",
                            valueKind = LsiValidationValueKind.TEXT_COLLECTION,
                            nullable = false,
                            rules = listOf(
                                LsiValidationRule("notEmpty", "至少选择一个编码"),
                                LsiValidationRule("noBlankElements", "编码不能为空"),
                            ),
                        ),
                    ),
                ),
                exceptionQualifiedName = "example.ValidationException",
            ),
        )

        assertEquals("ExampleCommandValidation", source.fileName)
        assertTrue(source.content.contains("import example.ValidationException"))
        assertTrue(source.content.contains("this@validate.name.isBlank()"))
        assertTrue(source.content.contains("this@validate.name.length > 12"))
        assertTrue(source.content.contains("字段长度不能超过 12"))
        assertTrue(source.content.contains("this@validate.codes.isEmpty()"))
        assertTrue(source.content.contains("this@validate.codes.any(String::isBlank)"))
        assertTrue(source.content.contains("throw ValidationException(errors.joinToString(\"；\"))"))
    }
}
