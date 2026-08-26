package site.addzero.constant.compiler

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ServiceLoader

class KotlinConstantSourceGeneratorTest {
    @Test
    fun `registers KSP processor provider`() {
        val providers = ServiceLoader.load(SymbolProcessorProvider::class.java).toList()

        assertTrue(providers.any { provider -> provider is ConstantProcessorProvider })
    }

    @Test
    fun `generates typed constants in deterministic order`() {
        val source = KotlinConstantSourceGenerator.generate(
            LsiConstantGroup(
                packageName = "example.generated",
                objectName = "ExampleConstants",
                description = "示例常量。",
                constants = listOf(
                    LsiConstant("TEXT_VALUE", LsiConstantType.STRING, "line\n\"quoted\"", "文本。"),
                    LsiConstant("LONG_VALUE", LsiConstantType.LONG, "42", "长整数。"),
                    LsiConstant("INT_VALUE", LsiConstantType.INT, "7", "整数。"),
                    LsiConstant("BOOLEAN_VALUE", LsiConstantType.BOOLEAN, "true", "布尔值。"),
                ),
            ),
        )

        assertEquals("ExampleConstants", source.fileName)
        assertTrue(source.content.indexOf("BOOLEAN_VALUE") < source.content.indexOf("INT_VALUE"))
        assertTrue(source.content.contains("const val INT_VALUE: Int = 7"))
        assertTrue(source.content.contains("const val LONG_VALUE: Long = 42L"))
        assertTrue(source.content.contains("const val TEXT_VALUE: String = \"line\\n\\\"quoted\\\"\""))
    }

    @Test
    fun `rejects duplicate constant names`() {
        val group = LsiConstantGroup(
            packageName = "example.generated",
            objectName = "ExampleConstants",
            description = "示例常量。",
            constants = listOf(
                LsiConstant("VALUE", LsiConstantType.INT, "1", "整数。"),
                LsiConstant("VALUE", LsiConstantType.INT, "2", "整数。"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            KotlinConstantSourceGenerator.generate(group)
        }
    }

    @Test
    fun `rejects empty constant group`() {
        val group = LsiConstantGroup(
            packageName = "example.generated",
            objectName = "ExampleConstants",
            description = "示例常量。",
            constants = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            KotlinConstantSourceGenerator.generate(group)
        }
    }

    @Test
    fun `rejects value incompatible with declared type`() {
        val group = LsiConstantGroup(
            packageName = "example.generated",
            objectName = "ExampleConstants",
            description = "示例常量。",
            constants = listOf(
                LsiConstant("INT_VALUE", LsiConstantType.INT, "not-an-int", "整数。"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            KotlinConstantSourceGenerator.generate(group)
        }
    }
}
