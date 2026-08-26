package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.dto.compiler.LsiDtoType

class LowcodeDtoSchemaResolverTest {
    @Test
    fun `resolves transitive dto schemas from every operation boundary and existing roots`() {
        val parameterInput = definition("parameterInput", LowcodeDtoKind.INPUT)
        val requestInput = definition("requestInput", LowcodeDtoKind.INPUT)
        val responseOutput = definition(
            code = "responseOutput",
            kind = LowcodeDtoKind.OUTPUT,
            fields = listOf(referenceField("nested", "nestedOutput")),
        )
        val nestedOutput = definition(
            code = "nestedOutput",
            kind = LowcodeDtoKind.OUTPUT,
            fields = listOf(referenceField("parent", "responseOutput")),
        )
        val existingNested = definition("existingNested", LowcodeDtoKind.OUTPUT)
        val structure = definition("internalStructure", LowcodeDtoKind.STRUCTURE)
        val inactive = definition("inactiveOutput", LowcodeDtoKind.OUTPUT, status = 0)
        val unreferencedBroken = definition(
            code = "unreferencedBroken",
            kind = LowcodeDtoKind.OUTPUT,
            fields = listOf(
                LsiLowcodeDtoField(
                    name = "missingSchema",
                    nullability = LowcodeDtoNullability.NON_NULL,
                ),
            ),
        )
        val existingRoot = LsiLowcodeDtoSchema(
            ref = LsiLowcodeDtoRef(dtoCode = "existingRoot"),
            className = "ExistingRoot",
            properties = mapOf(
                "nested" to LsiLowcodeApiSchema(typeRef = existingNested.ref),
            ),
            required = setOf("nested"),
        )
        val operation = LsiLowcodeCustomOperation(
            operationCode = "save",
            name = "保存",
            path = "/example/save",
            parameters = listOf(
                LsiLowcodeApiParameter(
                    name = "filter",
                    location = LowcodeApiParameterLocation.QUERY,
                    schema = LsiLowcodeApiSchema(
                        properties = mapOf(
                            "value" to LsiLowcodeApiSchema(typeRef = parameterInput.ref),
                        ),
                    ),
                ),
            ),
            requestBody = LsiLowcodeApiBody(
                schema = LsiLowcodeApiSchema(
                    type = "array",
                    items = LsiLowcodeApiSchema(typeRef = requestInput.ref),
                ),
            ),
            responseBody = LsiLowcodeApiBody(
                schema = LsiLowcodeApiSchema(
                    oneOf = listOf(LsiLowcodeApiSchema(typeRef = responseOutput.ref)),
                ),
            ),
        )

        val schemas = listOf(operation).resolveReferencedDtoSchemas(
            models = emptyList(),
            dtoDefinitions = listOf(
                parameterInput,
                requestInput,
                responseOutput,
                nestedOutput,
                existingNested,
                structure,
                inactive,
                unreferencedBroken,
            ),
            existingSchemas = listOf(existingRoot),
        )
        val dtoCodes = schemas.map { schema -> schema.ref.dtoCode }.toSet()

        assertEquals(
            setOf(
                "existingNested",
                "existingRoot",
                "nestedOutput",
                "parameterInput",
                "requestInput",
                "responseOutput",
            ),
            dtoCodes,
        )
        assertFalse("internalStructure" in dtoCodes)
        assertFalse("inactiveOutput" in dtoCodes)
        assertFalse("unreferencedBroken" in dtoCodes)
    }

    @Test
    fun `rejects missing dto referenced by public operation`() {
        assertUnresolvedPublicRef(
            ref = LsiLowcodeDtoRef(dtoCode = "missingOutput"),
            definitions = emptyList(),
        )
    }

    @Test
    fun `rejects inactive dto referenced by public operation`() {
        val inactive = definition("inactiveOutput", LowcodeDtoKind.OUTPUT, status = 0)

        assertUnresolvedPublicRef(inactive.ref, listOf(inactive))
    }

    @Test
    fun `rejects structure dto referenced by public operation`() {
        val structure = definition("internalStructure", LowcodeDtoKind.STRUCTURE)

        assertUnresolvedPublicRef(structure.ref, listOf(structure))
    }

    @Test
    fun `ignores kotlin types used only by internal service operations`() {
        val operation = LsiLowcodeCustomOperation(
            operationCode = "calculate",
            name = "计算",
            path = "/example/internal/calculate",
            transport = LowcodeOperationTransport.INTERNAL,
            implementation = LowcodeOperationImplementation.SERVICE_ONLY,
            responseBody = LsiLowcodeApiBody(
                schema = LsiLowcodeApiSchema(
                    kotlinType = LsiDtoType("example.internal.CalculationResult"),
                ),
            ),
        )

        val schemas = listOf(operation).resolveReferencedDtoSchemas(
            models = emptyList(),
            dtoDefinitions = emptyList(),
        )

        assertTrue(schemas.isEmpty())
    }

    @Test
    fun `does not register internal dto refs or traverse their broken closure`() {
        val internalOutput = definition(
            code = "internalOutput",
            kind = LowcodeDtoKind.OUTPUT,
            fields = listOf(referenceField("missing", "missingNestedOutput")),
        )
        val operation = LsiLowcodeCustomOperation(
            operationCode = "calculate",
            name = "计算",
            path = "/example/internal/calculate",
            transport = LowcodeOperationTransport.INTERNAL,
            implementation = LowcodeOperationImplementation.SERVICE_ONLY,
            responseBody = LsiLowcodeApiBody(
                schema = LsiLowcodeApiSchema(typeRef = internalOutput.ref),
            ),
        )

        val schemas = listOf(operation).resolveReferencedDtoSchemas(
            models = emptyList(),
            dtoDefinitions = listOf(internalOutput),
        )

        assertTrue(schemas.isEmpty())
    }

    @Test
    fun `keeps explicit schemas as roots and validates their closure`() {
        val nested = definition("explicitNested", LowcodeDtoKind.OUTPUT)
        val root = LsiLowcodeDtoSchema(
            ref = LsiLowcodeDtoRef(dtoCode = "explicitRoot"),
            className = "ExplicitRoot",
            properties = mapOf("nested" to LsiLowcodeApiSchema(typeRef = nested.ref)),
        )

        val schemas = emptyList<LsiLowcodeCustomOperation>().resolveReferencedDtoSchemas(
            models = emptyList(),
            dtoDefinitions = listOf(nested),
            existingSchemas = listOf(root),
        )

        assertEquals(setOf(root.ref, nested.ref), schemas.mapTo(linkedSetOf(), LsiLowcodeDtoSchema::ref))

        val missing = root.copy(
            properties = mapOf(
                "nested" to LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef(dtoCode = "missingExplicitNested")),
            ),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            emptyList<LsiLowcodeCustomOperation>().resolveReferencedDtoSchemas(
                models = emptyList(),
                dtoDefinitions = emptyList(),
                existingSchemas = listOf(missing),
            )
        }
        assertTrue(error.message.orEmpty().contains("OpenAPI DTO explicitRoot"))
        assertTrue(error.message.orEmpty().contains("missingExplicitNested"))
    }

    @Test
    fun `does not materialize an unreferenced broken model dto`() {
        val output = definition("uploadOutput", LowcodeDtoKind.OUTPUT)
        val brokenModel = LowcodeModelMeta(
            id = 1,
            modelCode = "brokenModel",
            name = "损坏模型",
            packageName = "example.broken",
            className = "BrokenModel",
            tableName = "broken_model",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            dtoDefinitions = listOf(
                LsiLowcodeDto(
                    dtoCode = "brokenView",
                    className = "BrokenView",
                    kind = LowcodeDtoKind.VIEW,
                    fields = listOf(LsiLowcodeDtoField(name = "missing", sourcePath = "missing")),
                ),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val operation = publicResponseOperation(output.ref)

        val schemas = listOf(operation).resolveReferencedDtoSchemas(
            models = listOf(brokenModel),
            dtoDefinitions = listOf(output),
        )

        assertEquals(listOf(output.ref), schemas.map(LsiLowcodeDtoSchema::ref))
    }

    @Test
    fun `route binding receives standalone dto schemas from operation references`() {
        val output = definition("uploadOutput", LowcodeDtoKind.OUTPUT)
        val route = LsiLowcodeRoute(
            packageName = "example.file",
            qualifiedName = "example.file.UploadController",
            className = "UploadController",
            description = "上传。",
            path = "/example/upload",
            enabledOperations = emptySet(),
            properties = emptyList(),
            customOperations = listOf(publicResponseOperation(output.ref)),
        )
        val binding = LowcodeRouteBinding(
            routeCode = route.qualifiedName,
            contributorId = "example.example",
            route = route,
        )

        val resolved = LowcodeSourceCompiler.resolveRouteBindings(
            bindings = listOf(binding),
            models = emptyList(),
            modelsRequiringRoutes = emptyList(),
            dtoDefinitions = listOf(output),
        ).single()

        assertEquals(listOf(output.ref), resolved.route.dtoSchemas.map(LsiLowcodeDtoSchema::ref))
    }
}

private fun assertUnresolvedPublicRef(
    ref: LsiLowcodeDtoRef,
    definitions: List<LsiLowcodeDtoDefinition>,
) {
    val error = assertThrows(IllegalStateException::class.java) {
        listOf(publicResponseOperation(ref)).resolveReferencedDtoSchemas(
            models = emptyList(),
            dtoDefinitions = definitions,
        )
    }

    assertTrue(error.message.orEmpty().contains("OpenAPI 操作 upload"))
    assertTrue(error.message.orEmpty().contains(ref.componentSchemaName()))
}

private fun publicResponseOperation(ref: LsiLowcodeDtoRef) = LsiLowcodeCustomOperation(
    operationCode = "upload",
    name = "上传",
    path = "/example/upload",
    responseBody = LsiLowcodeApiBody(
        schema = LsiLowcodeApiSchema(typeRef = ref),
    ),
)

private fun definition(
    code: String,
    kind: LowcodeDtoKind,
    fields: List<LsiLowcodeDtoField> = emptyList(),
    status: Int = 1,
): LsiLowcodeDtoDefinition = LsiLowcodeDtoDefinition(
    dtoCode = code,
    name = code,
    packageName = "example.contract",
    className = code.replaceFirstChar(Char::uppercaseChar),
    kind = kind,
    status = status,
    fields = fields,
)

private fun referenceField(name: String, dtoCode: String): LsiLowcodeDtoField = LsiLowcodeDtoField(
    name = name,
    nullability = LowcodeDtoNullability.NON_NULL,
    schema = LsiLowcodeApiSchema(typeRef = LsiLowcodeDtoRef(dtoCode = dtoCode)),
)
