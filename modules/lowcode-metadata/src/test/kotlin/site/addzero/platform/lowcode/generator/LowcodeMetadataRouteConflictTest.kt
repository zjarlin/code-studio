package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeMetadataRouteConflictTest {
    @Test
    fun `rejects business route already provided by generated crud controller`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            metadata(
                operation = operation(
                    path = "/records/page",
                    implementation = LowcodeOperationImplementation.GENERATED,
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("GET /records/page"))
    }

    @Test
    fun `allows service only declaration because it does not install a rest route`() {
        assertDoesNotThrow {
            metadata(
                operation = operation(
                    path = "/records/page",
                    implementation = LowcodeOperationImplementation.SERVICE_ONLY,
                ),
            )
        }
    }

    @Test
    fun `rejects route binding operation already provided by model crud controller`() {
        val route = route(
            path = "/records",
            enabledOperations = emptySet(),
            customOperations = listOf(
                operation(
                    path = "/records/page",
                    implementation = LowcodeOperationImplementation.EXISTING_REST,
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            metadata(operation("/records/archive", LowcodeOperationImplementation.SERVICE_ONLY)).copy(
                routeBindings = listOf(LowcodeRouteBinding("recordBinding", "example.example", route)),
            )
        }

        assertTrue(error.message.orEmpty().contains("路由绑定 recordBinding"))
    }

    private fun metadata(operation: LsiLowcodeCustomOperation) = LowcodeMetadata(
        models = listOf(
            LowcodeModelMeta(
                id = 1,
                modelCode = "record",
                name = "记录",
                packageName = "example.record",
                className = "Record",
                tableName = "record",
                kind = LowcodeModelKind.ENTITY,
                status = 1,
                version = 1,
                contributorId = "example.example",
                routeConfig = route(
                    path = "/records",
                    enabledOperations = setOf("PAGE"),
                ),
                fields = emptyList(),
                queries = emptyList(),
                relations = emptyList(),
            ),
        ),
        dtoDefinitions = emptyList(),
        routeBindings = emptyList(),
        contracts = listOf(
            LsiLowcodeContract(
                contractCode = "recordApplication",
                name = "记录业务服务",
                packageName = "example.record",
                className = "RecordApplicationService",
                path = "/records",
                operations = listOf(operation),
            ),
        ),
    )

    private fun operation(
        path: String,
        implementation: LowcodeOperationImplementation,
    ) = LsiLowcodeCustomOperation(
        operationCode = "findPage",
        name = "分页查询",
        path = path,
        method = LowcodeHttpMethod.GET,
        transport = if (implementation == LowcodeOperationImplementation.SERVICE_ONLY) {
            LowcodeOperationTransport.INTERNAL
        } else {
            LowcodeOperationTransport.HTTP
        },
        implementation = implementation,
    )

    private fun route(
        path: String,
        enabledOperations: Set<String>,
        customOperations: List<LsiLowcodeCustomOperation> = emptyList(),
    ) = LsiLowcodeRoute(
        packageName = "example.record",
        qualifiedName = "example.record.generated.entity.Record",
        className = "Record",
        description = null,
        path = path,
        enabledOperations = enabledOperations,
        properties = emptyList(),
        customOperations = customOperations,
    )
}
