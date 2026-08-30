package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class SourceTemplateCatalogTest {
    @Test
    fun `读取目录模板并渲染自定义内容`(@TempDir directory: Path) {
        writeDefaultTemplates(directory)
        Files.writeString(
            directory.resolve(SourceTemplateKind.CONVENTION_SERVICE.fileName),
            """
            {{header}}

            {{documentation}}
            @Single
            class {{className}} {
                val source = "custom"
            }
            """.trimIndent() + "\n",
        )

        val rendered = SourceTemplateCatalog.read(directory).render(
            SourceTemplateKind.CONVENTION_SERVICE,
            mapOf(
                "header" to "// header",
                "documentation" to "/** 示例。 */",
                "className" to "ExampleService",
            ),
        )

        assertTrue(rendered.contains("class ExampleService"))
        assertTrue(rendered.contains("val source = \"custom\""))
        assertEquals('\n', rendered.last())
    }

    @Test
    fun `拒绝丢失生成主干变量的模板`(@TempDir directory: Path) {
        writeDefaultTemplates(directory)
        Files.writeString(
            directory.resolve(SourceTemplateKind.CONTROLLER.fileName),
            "{{header}}\n\nclass FixedController\n",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            SourceTemplateCatalog.read(directory)
        }

        assertTrue(error.message.orEmpty().contains("缺少变量"))
    }

    @Test
    fun `拒绝未知模板变量`(@TempDir directory: Path) {
        writeDefaultTemplates(directory)
        val source = Files.readString(directory.resolve(SourceTemplateKind.CONVENTION_SERVICE.fileName))
        Files.writeString(
            directory.resolve(SourceTemplateKind.CONVENTION_SERVICE.fileName),
            source + "{{unknown}}\n",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            SourceTemplateCatalog.read(directory)
        }

        assertTrue(error.message.orEmpty().contains("未知变量"))
    }

    @Test
    fun `按 contributor 从运行时资源读取模板`(@TempDir directory: Path) {
        val contributorId = "example.library"
        SourceTemplateKind.entries.forEach { kind ->
            val file = directory.resolve(kind.resourcePath(contributorId))
            Files.createDirectories(file.parent)
            val source = SourceTemplateCatalog.DEFAULT.source(kind).let { value ->
                if (kind == SourceTemplateKind.CONVENTION_SERVICE) value + "// custom\n" else value
            }
            Files.writeString(file, source)
        }

        URLClassLoader(arrayOf(directory.toUri().toURL()), null).use { classLoader ->
            val catalog = SourceTemplateCatalog.load(contributorId, classLoader)

            assertTrue(catalog.source(SourceTemplateKind.CONVENTION_SERVICE).contains("// custom"))
        }
    }

    private fun writeDefaultTemplates(directory: Path) {
        SourceTemplateKind.entries.forEach { kind ->
            Files.writeString(directory.resolve(kind.fileName), SourceTemplateCatalog.DEFAULT.source(kind))
        }
    }
}
