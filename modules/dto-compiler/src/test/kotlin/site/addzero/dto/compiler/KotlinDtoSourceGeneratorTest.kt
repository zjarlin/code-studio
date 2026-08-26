package site.addzero.dto.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlinDtoSourceGeneratorTest {
    @Test
    fun `生成无字段标记结构`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(properties = emptyList()),
        )

        assertTrue(source.content.contains("class ExamplePayload"))
        assertTrue(!source.content.contains("data class ExamplePayload"))
    }

    @Test
    fun `生成带泛型导入和可空默认值的 data class`() {
        val source = KotlinDtoSourceGenerator.generate(
            LsiDtoDefinition(
                packageName = "example.generated.dto",
                className = "ExamplePayload",
                description = "示例载荷。",
                properties = listOf(
                    LsiDtoProperty(
                        name = "registry",
                        type = LsiDtoType("example.tools.ToolRegistry"),
                        description = "工具注册表。",
                    ),
                    LsiDtoProperty(
                        name = "policies",
                        type = LsiDtoType.map(
                            LsiDtoType.STRING,
                            LsiDtoType("example.policy.ToolPolicy"),
                        ),
                        description = "工具策略。",
                    ),
                    LsiDtoProperty(
                        name = "when",
                        type = LsiDtoType.STRING.copy(nullable = true),
                        description = "执行时间。",
                        defaultValue = LsiDtoDefaultValue.NULL,
                    ),
                ),
            ),
        )

        assertEquals("ExamplePayload", source.fileName)
        assertTrue(source.content.contains("import example.policy.ToolPolicy\nimport example.tools.ToolRegistry"))
        assertTrue(source.content.contains("val policies: Map<String, ToolPolicy>"))
        assertTrue(source.content.contains("val `when`: String? = null"))
        assertTrue(source.content.endsWith("\n"))
    }

    @Test
    fun `简单名称冲突时保留全限定类型`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(
                properties = listOf(
                    property("first", "first.example.Value"),
                    property("second", "second.example.Value"),
                ),
            ),
        )

        assertTrue(source.content.contains("val first: first.example.Value"))
        assertTrue(source.content.contains("val second: second.example.Value"))
        assertTrue(!source.content.contains("import first.example.Value"))
        assertTrue(source.content.startsWith("package example.generated.dto\n\n/**"))
    }

    @Test
    fun `拒绝重复字段和不可空 null 默认值`() {
        assertThrows(IllegalArgumentException::class.java) {
            KotlinDtoSourceGenerator.generate(
                definition(properties = listOf(property("value"), property("value"))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            KotlinDtoSourceGenerator.generate(
                definition(
                    properties = listOf(
                        property("value").copy(defaultValue = LsiDtoDefaultValue.NULL),
                    ),
                ),
            )
        }
    }

    @Test
    fun `生成模块内部 DTO`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(properties = listOf(property("value"))).copy(visibility = LsiDtoVisibility.INTERNAL),
        )

        assertTrue(source.content.contains("internal data class ExamplePayload("))
    }

    @Test
    fun `生成结构化父类型并稳定导入`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(properties = listOf(property("value"))).copy(
                superTypes = listOf(
                    LsiDtoType("example.contract.Payload"),
                    LsiDtoType(
                        qualifiedName = "example.contract.Tagged",
                        arguments = listOf(LsiDtoType.STRING),
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("import example.contract.Payload\nimport example.contract.Tagged"))
        assertTrue(source.content.contains(") : Payload, Tagged<String>"))
        assertThrows(IllegalArgumentException::class.java) {
            KotlinDtoSourceGenerator.generate(
                definition(properties = listOf(property("value"))).copy(
                    superTypes = listOf(LsiDtoType("example.contract.Payload", nullable = true)),
                ),
            )
        }
    }

    @Test
    fun `生成结构化属性注解`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(
                properties = listOf(
                    property("id", "kotlin.Long").copy(
                        annotations = listOf(
                            LsiDtoAnnotation(
                                qualifiedName = "tools.jackson.databind.annotation.JsonSerialize",
                                useSiteTarget = LsiDtoAnnotationUseSiteTarget.GET,
                                arguments = listOf(
                                    LsiDtoAnnotationArgument(
                                        name = "using",
                                        kind = LsiDtoAnnotationArgumentKind.CLASS,
                                        value = "tools.jackson.databind.ser.std.ToStringSerializer",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("import tools.jackson.databind.annotation.JsonSerialize"))
        assertTrue(source.content.contains("import tools.jackson.databind.ser.std.ToStringSerializer"))
        assertTrue(source.content.contains("@get:JsonSerialize(using = ToStringSerializer::class)"))
    }

    @Test
    fun `生成结构化枚举注解参数`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(
                properties = listOf(
                    property("id", "kotlin.Long").copy(
                        annotations = listOf(
                            LsiDtoAnnotation(
                                qualifiedName = "org.babyfish.jimmer.sql.GeneratedValue",
                                arguments = listOf(
                                    LsiDtoAnnotationArgument(
                                        name = "strategy",
                                        kind = LsiDtoAnnotationArgumentKind.ENUM,
                                        value = "org.babyfish.jimmer.sql.GenerationType.IDENTITY",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("import org.babyfish.jimmer.sql.GeneratedValue"))
        assertTrue(source.content.contains("import org.babyfish.jimmer.sql.GenerationType"))
        assertTrue(source.content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
    }

    @Test
    fun `生成结构化类注解`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(properties = listOf(property("value"))).copy(
                annotations = listOf(
                    LsiDtoAnnotation(
                        qualifiedName = "tools.jackson.databind.annotation.JsonDeserialize",
                        arguments = listOf(
                            LsiDtoAnnotationArgument(
                                name = "using",
                                kind = LsiDtoAnnotationArgumentKind.CLASS,
                                value = "example.JsonValueDeserializer",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("import example.JsonValueDeserializer"))
        assertTrue(source.content.contains("@JsonDeserialize(using = JsonValueDeserializer::class)\ndata class"))
    }

    @Test
    fun `生成结构化标量和空集合默认值`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(
                properties = listOf(
                    property("enabled", "kotlin.Boolean").copy(defaultValue = LsiDtoDefaultValue.boolean(true)),
                    property("count", "kotlin.Int").copy(defaultValue = LsiDtoDefaultValue.integer(2)),
                    property("label", "kotlin.String").copy(defaultValue = LsiDtoDefaultValue.string("示例")),
                    LsiDtoProperty(
                        name = "items",
                        type = LsiDtoType.list(LsiDtoType.STRING),
                        description = "items。",
                        defaultValue = LsiDtoDefaultValue.EMPTY_LIST,
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("val enabled: Boolean = true"))
        assertTrue(source.content.contains("val count: Int = 2"))
        assertTrue(source.content.contains("val label: String = \"示例\""))
        assertTrue(source.content.contains("val items: List<String> = emptyList()"))
    }

    @Test
    fun `生成枚举和空实例默认值`() {
        val source = KotlinDtoSourceGenerator.generate(
            definition(
                properties = listOf(
                    property("mode", "example.Mode").copy(
                        defaultValue = LsiDtoDefaultValue.enum("DEFAULT"),
                    ),
                    property("settings", "example.Settings").copy(
                        defaultValue = LsiDtoDefaultValue.EMPTY_INSTANCE,
                    ),
                ),
            ),
        )

        assertTrue(source.content.contains("val mode: Mode = Mode.DEFAULT"))
        assertTrue(source.content.contains("val settings: Settings = Settings()"))
    }

    private fun definition(properties: List<LsiDtoProperty>) = LsiDtoDefinition(
        packageName = "example.generated.dto",
        className = "ExamplePayload",
        description = "示例载荷。",
        properties = properties,
    )

    private fun property(name: String, type: String = "kotlin.String") = LsiDtoProperty(
        name = name,
        type = LsiDtoType(type),
        description = "$name。",
    )
}
