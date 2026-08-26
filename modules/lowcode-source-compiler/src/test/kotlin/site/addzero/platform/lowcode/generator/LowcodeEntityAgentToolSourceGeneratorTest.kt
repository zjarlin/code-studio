package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeEntityAgentToolSourceGeneratorTest {
    @Test
    fun `generates request scoped entity query and delete tools`() {
        val model = model()
        val file = LowcodeEntityAgentToolSourceGenerator.generate(
            feature = feature(model),
            models = listOf(model),
            routeBindings = listOf(
                routeBinding(
                    model = model,
                    operations = mapOf(
                        "PAGE" to LsiAgentOperationExposure(LsiAgentConfirmation.AUTO),
                        "DELETE" to LsiAgentOperationExposure(LsiAgentConfirmation.REQUIRED),
                    ),
                ),
            ),
        )

        val source = requireNotNull(file).content
        assertEquals("DictionaryEntityToolRegistryContributor", file.fileName)
        assertTrue(source.contains("name = \"dictionaryEntry_page\""))
        assertTrue(source.contains("name = \"dictionaryEntry_delete\""))
        assertTrue(source.contains("val code: String? = null"))
        assertTrue(source.contains("service.page(args.pageNo, args.pageSize, args.conditions.toQueryMap()"))
        assertTrue(source.contains("service.delete(args.id)"))
        assertTrue(source.contains("private val callContextProvider: CallContextProvider"))
        assertTrue(source.contains("context.withCallContext(callContextProvider)"))
        assertTrue(source.contains("requiredPermissions = setOf(\"GET:/system/dictionary-entry/**\")"))
        assertTrue(source.contains("requiredPermissions = setOf(\"DELETE:/system/dictionary-entry/**\")"))
        assertTrue(source.contains("confirmation = AgentToolConfirmation.REQUIRED"))
        assertTrue(source.contains("override fun contribute(context: AgentExecutionContext): AgentToolSet"))
        val agentPackage = generationTargetSymbol(GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE)
        assertTrue(source.contains("import $agentPackage.AgentExecutionContext"))
        assertTrue(source.contains("import $agentPackage.generated.dto.AgentToolSet"))
        assertFalse(source.contains("ApplicationCall"))
        assertFalse(source.contains("toCallContext"))
        assertFalse(source.contains("Map<String, Any?>"))
        assertFalse(source.contains("site.addzero.biz"))
    }

    @Test
    fun `rejects entity writes without a named service input dto`() {
        val model = model()
        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeEntityAgentToolSourceGenerator.generate(
                feature = feature(model),
                models = listOf(model),
                routeBindings = listOf(
                    routeBinding(
                        model = model,
                        operations = mapOf(
                            "CREATE" to LsiAgentOperationExposure(LsiAgentConfirmation.REQUIRED),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("命名输入 DTO"))
    }

    private fun model() = LowcodeModelMeta(
        id = 1,
        modelCode = "dictionaryEntry",
        name = "字典项",
        packageName = "example.dictionary",
        className = "DictionaryEntry",
        tableName = "dictionary_entry",
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        contributorId = "example.dictionary",
        fields = emptyList(),
        queries = emptyList(),
        relations = emptyList(),
    )

    private fun feature(model: LowcodeModelMeta) = LsiLowcodeFeature(
        featureCode = "dictionary",
        name = "字典",
        packageName = model.packageName,
        contributorId = requireNotNull(model.contributorId),
        modelCodes = listOf(model.modelCode),
    )

    private fun routeBinding(
        model: LowcodeModelMeta,
        operations: Map<String, LsiAgentOperationExposure>,
    ): LowcodeRouteBinding {
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = model.entityQualifiedName(),
            className = model.className,
            displayName = model.name,
            description = model.name,
            path = "/system/dictionary-entry",
            enabledOperations = operations.keys,
            properties = emptyList(),
            queryFields = listOf(
                LsiLowcodeQueryField(
                    propertyName = "code",
                    parameterName = "code",
                    operator = "EQ",
                    type = "string",
                    format = null,
                    description = "字典编码",
                ),
            ),
            featurePackageName = model.featurePackageName,
            modelCode = model.modelCode,
            agentExposure = LsiAgentExposure(operations),
        )
        return LowcodeRouteBinding(route.qualifiedName, requireNotNull(model.contributorId), route)
    }
}
