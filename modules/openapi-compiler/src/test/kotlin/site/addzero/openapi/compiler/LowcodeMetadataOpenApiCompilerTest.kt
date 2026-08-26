package site.addzero.openapi.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.platform.lowcode.generator.LsiLowcodeApiBody
import site.addzero.platform.lowcode.generator.LsiLowcodeApiSchema
import site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoDefinition
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoField
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoRef
import site.addzero.platform.lowcode.generator.LsiLowcodeRoute
import site.addzero.platform.lowcode.generator.LowcodeDtoKind
import site.addzero.platform.lowcode.generator.LowcodeDtoNullability
import site.addzero.platform.lowcode.generator.LowcodeHttpMethod
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeRouteBinding
import tools.jackson.databind.ObjectMapper

class LowcodeMetadataOpenApiCompilerTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `publishes the complete transitive dto closure from metadata`() {
        val detail = definition(
            code = "detailOutput",
            fields = listOf(
                LsiLowcodeDtoField(
                    name = "value",
                    nullability = LowcodeDtoNullability.NON_NULL,
                    schema = LsiLowcodeApiSchema(type = "string"),
                ),
            ),
        )
        val envelope = definition(
            code = "envelopeOutput",
            fields = listOf(
                LsiLowcodeDtoField(
                    name = "detail",
                    nullability = LowcodeDtoNullability.NON_NULL,
                    schema = LsiLowcodeApiSchema(typeRef = detail.ref),
                ),
            ),
        )
        val metadata = metadata(
            responseRef = envelope.ref,
            definitions = listOf(envelope, detail),
        )

        val document = OpenApiCompiler.compile(baseDocument(), metadata.toLsiOpenApiContracts())
        val operation = document["paths"]["/example/result"]["get"]
        val responseSchema = operation["responses"]["200"]["content"]["application/json"]["schema"]
        val dataSchema = responseSchema["allOf"][1]["properties"]["data"]
        val schemas = document["components"]["schemas"]

        assertEquals("#/components/schemas/envelopeOutput", dataSchema["\$ref"].asString())
        assertEquals(
            "#/components/schemas/detailOutput",
            schemas["envelopeOutput"]["properties"]["detail"]["\$ref"].asString(),
        )
        assertEquals("string", schemas["detailOutput"]["properties"]["value"]["type"].asString())
    }

    @Test
    fun `rejects an unresolved public dto before compiling OpenAPI`() {
        val missingRef = LsiLowcodeDtoRef(dtoCode = "missingOutput")
        val metadata = metadata(responseRef = missingRef, definitions = emptyList())

        val error = assertThrows(IllegalStateException::class.java) {
            OpenApiCompiler.compile(baseDocument(), metadata.toLsiOpenApiContracts())
        }

        assertTrue(error.message.orEmpty().contains("OpenAPI 操作 getResult"))
        assertTrue(error.message.orEmpty().contains("missingOutput"))
    }

    private fun metadata(
        responseRef: LsiLowcodeDtoRef,
        definitions: List<LsiLowcodeDtoDefinition>,
    ): LowcodeMetadata {
        val route = LsiLowcodeRoute(
            packageName = "example.contract",
            qualifiedName = "example.contract.ExampleController",
            className = "ExampleController",
            description = "示例接口。",
            path = "/example",
            enabledOperations = emptySet(),
            properties = emptyList(),
            customOperations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "getResult",
                    name = "查询结果",
                    path = "/example/result",
                    method = LowcodeHttpMethod.GET,
                    responseBody = LsiLowcodeApiBody(
                        schema = LsiLowcodeApiSchema(typeRef = responseRef),
                    ),
                ),
            ),
        )
        return LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = definitions,
            routeBindings = listOf(
                LowcodeRouteBinding(
                    routeCode = route.qualifiedName,
                    contributorId = "example.example",
                    route = route,
                ),
            ),
            contracts = emptyList(),
        )
    }

    private fun definition(
        code: String,
        fields: List<LsiLowcodeDtoField>,
    ): LsiLowcodeDtoDefinition = LsiLowcodeDtoDefinition(
        dtoCode = code,
        name = code,
        packageName = "example.contract",
        className = code.replaceFirstChar(Char::uppercaseChar),
        kind = LowcodeDtoKind.OUTPUT,
        fields = fields,
    )

    private fun baseDocument() = objectMapper.createObjectNode().apply {
        put("openapi", "3.1.1")
        putObject("paths")
        putObject("components")
    }
}
