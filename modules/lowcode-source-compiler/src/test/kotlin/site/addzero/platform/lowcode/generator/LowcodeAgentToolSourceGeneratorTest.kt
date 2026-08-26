package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeAgentToolSourceGeneratorTest {
    @Test
    fun `generates typed tool and contributor only for exposed operations`() {
        val feature = feature()
        val contract = contract(
            exposure = LsiAgentExposure(
                operations = mapOf(
                    "findDictionary" to LsiAgentOperationExposure(LsiAgentConfirmation.AUTO),
                ),
            ),
        )

        val file = LowcodeAgentToolSourceGenerator.generate(
            feature = feature,
            contracts = listOf(contract),
            dtoCatalog = LowcodeDtoTypeCatalog.EMPTY,
        )

        assertNotNull(file)
        val content = checkNotNull(file).content
        assertEquals("DictionaryToolRegistryContributor", file.fileName)
        assertTrue(content.contains("class DictionaryFindDictionaryToolArgs("))
        assertTrue(content.contains("val code: String,"))
        assertTrue(content.contains("service.findDictionary("))
        assertTrue(content.contains("private val callContextProvider: CallContextProvider"))
        assertTrue(content.contains("context.withCallContext(callContextProvider)"))
        assertFalse(content.contains("context: CallContext"))
        assertTrue(content.contains("name = \"dictionary_findDictionary\""))
        assertTrue(content.contains("requiredPermissions = setOf(\"system:dictionary:query\")"))
        assertTrue(content.contains("confirmation = AgentToolConfirmation.AUTO"))
        assertTrue(content.contains("override fun contribute(context: AgentExecutionContext): AgentToolSet"))
        val agentPackage = generationTargetSymbol(GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE)
        assertTrue(content.contains("import $agentPackage.AgentExecutionContext"))
        assertTrue(content.contains("import $agentPackage.generated.dto.AgentToolSet"))
        assertFalse(content.contains("ApplicationCall"))
        assertFalse(content.contains("toCallContext"))
        assertFalse(content.contains("site.addzero.biz"))
    }

    @Test
    fun `does not generate contributor when agent exposure is empty`() {
        val file = LowcodeAgentToolSourceGenerator.generate(
            feature = feature(),
            contracts = listOf(contract()),
            dtoCatalog = LowcodeDtoTypeCatalog.EMPTY,
        )

        assertEquals(null, file)
    }

    private fun feature() = LsiLowcodeFeature(
        featureCode = "dictionary",
        name = "字典",
        packageName = "example.dictionary",
        contributorId = "example.dictionary",
        contractCodes = listOf("dictionary"),
    )

    private fun contract(
        exposure: LsiAgentExposure = LsiAgentExposure(),
    ) = LsiLowcodeContract(
        contractCode = "dictionary",
        name = "字典服务",
        packageName = "example.dictionary",
        className = "DictionaryService",
        path = "/system/dictionary",
        contributorId = "example.dictionary",
        operations = listOf(
            LsiLowcodeCustomOperation(
                operationCode = "findDictionary",
                name = "查询字典",
                description = "按编码查询字典定义",
                path = "/find",
                method = LowcodeHttpMethod.GET,
                authenticated = true,
                permission = "system:dictionary:query",
                callContext = true,
                parameters = listOf(
                    LsiLowcodeApiParameter(
                        name = "code",
                        location = LowcodeApiParameterLocation.QUERY,
                        required = true,
                        schema = LsiLowcodeApiSchema(type = "string"),
                    ),
                ),
                responseBody = LsiLowcodeApiBody(
                    schema = LsiLowcodeApiSchema(type = "string"),
                ),
            ),
        ),
        agentExposure = exposure,
    )
}
