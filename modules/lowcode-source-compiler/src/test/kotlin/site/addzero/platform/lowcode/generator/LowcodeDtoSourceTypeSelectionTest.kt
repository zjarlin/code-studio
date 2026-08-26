package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.dto.compiler.LsiDtoType

class LowcodeDtoSourceTypeSelectionTest {
    @Test
    fun `explicit kotlin types drive source while schemas drive the public contract`() {
        val definition = LsiLowcodeDtoDefinition(
            dtoCode = "catalogOutput",
            name = "目录输出",
            packageName = "example.catalog",
            className = "CatalogOutput",
            kind = LowcodeDtoKind.OUTPUT,
            fields = listOf(
                LsiLowcodeDtoField(
                    name = "catalog",
                    nullability = LowcodeDtoNullability.NON_NULL,
                    kotlinType = LsiDtoType("example.catalog.ApplicationCatalog"),
                    schema = LsiLowcodeApiSchema(type = "object"),
                ),
                LsiLowcodeDtoField(
                    name = "tags",
                    nullability = LowcodeDtoNullability.NON_NULL,
                    kotlinType = LsiDtoType("kotlin.collections.Set", listOf(LsiDtoType.STRING)),
                    schema = LsiLowcodeApiSchema(
                        type = "array",
                        items = LsiLowcodeApiSchema(type = "string"),
                    ),
                ),
            ),
        )

        val source = LowcodeDtoSourceGenerator.generateDefinitions(listOf(definition), emptyList()).single().content
        val schema = definition.toLsiDtoSchema()

        assertTrue(source.contains("import example.catalog.ApplicationCatalog"))
        assertTrue(source.contains("val catalog: ApplicationCatalog"))
        assertTrue(source.contains("val tags: Set<String>"))
        assertFalse(source.contains("Map<String, Any?>"))
        assertEquals("object", schema.properties.getValue("catalog").type)
        assertEquals("array", schema.properties.getValue("tags").type)
        assertEquals("string", schema.properties.getValue("tags").items?.type)
    }
}
