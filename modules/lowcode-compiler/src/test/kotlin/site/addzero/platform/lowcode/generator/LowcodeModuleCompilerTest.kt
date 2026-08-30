package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import site.addzero.studio.runtime.GenerationTargetProfile

class LowcodeModuleCompilerTest {
    @Test
    fun `父功能预览包含后代且叶功能不包含父级或兄弟`() {
        val module = "example.library"
        val parent = feature("inspection", "example.inspection", module)
        val plan = feature("inspection.plan", "example.inspection.plan", module)
        val task = feature("inspection.task", "example.inspection.task", module)
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            features = listOf(parent, plan, task),
        )

        val parentFiles = LowcodeModuleCompiler.generate(
            metadata,
            module,
            setOf(parent.packageName, plan.packageName, task.packageName),
            targetProfile = EMPTY_PROFILE,
        )
        val leafFiles = LowcodeModuleCompiler.generate(
            metadata,
            module,
            setOf(task.packageName),
            targetProfile = EMPTY_PROFILE,
        )

        assertTrue(parentFiles.any { file -> file.packageName == parent.packageName })
        assertTrue(parentFiles.any { file -> file.packageName == plan.packageName })
        assertTrue(parentFiles.any { file -> file.packageName == task.packageName })
        assertTrue(leafFiles.any { file -> file.packageName == task.packageName })
        assertFalse(leafFiles.any { file -> file.packageName == parent.packageName })
        assertFalse(leafFiles.any { file -> file.packageName == plan.packageName })
    }

    @Test
    fun `生成目标替换宿主运行时符号`() {
        val module = "example-library"
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "example",
            name = "Example",
            packageName = "example.feature",
            className = "Example",
            tableName = "example",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = module,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val metadata = LowcodeMetadata(
            models = listOf(model),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            features = emptyList(),
        )
        val profile = GenerationTargetProfile(
            id = "custom",
            symbols = mapOf(
                GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE to "example.persistence",
                GenerationTargetSymbols.AUDIT_PRINCIPAL to "example.identity.AuditPrincipal",
            ),
        )

        val files = LowcodeModuleCompiler.generate(metadata, module, targetProfile = profile)

        assertTrue(files.any { file -> "import org.babyfish.jimmer.sql.Entity" in file.content })
        assertTrue(files.any { file -> "import example.persistence.BaseEntity" in file.content })
        assertFalse(files.any { file -> "code.studio.target." in file.content })
    }

    @Test
    fun `未映射的宿主运行时符号会使编译失败`() {
        val contributorId = "example-library"
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "example",
            name = "Example",
            packageName = "example.feature",
            className = "Example",
            tableName = "example",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = contributorId,
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val metadata = LowcodeMetadata(
            models = listOf(model),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            features = emptyList(),
        )

        val error = assertThrows<IllegalArgumentException> {
            LowcodeModuleCompiler.generate(metadata, contributorId, targetProfile = EMPTY_PROFILE)
        }

        assertTrue(error.message.orEmpty().contains(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE))
    }

    @Test
    fun `Agent 适配仅在 profile 显式启用 capability 时生成`() {
        val contributorId = "example.automation"
        val contract = LsiLowcodeContract(
            contractCode = "automation",
            name = "Automation",
            packageName = "example.automation",
            className = "AutomationService",
            path = "/automation",
            contributorId = contributorId,
            operations = listOf(
                LsiLowcodeCustomOperation(
                    operationCode = "status",
                    name = "Status",
                    path = "/automation/status",
                    method = LowcodeHttpMethod.GET,
                    responseBody = LsiLowcodeApiBody(schema = LsiLowcodeApiSchema(type = "string")),
                ),
            ),
            agentExposure = LsiAgentExposure(
                operations = mapOf("status" to LsiAgentOperationExposure(LsiAgentConfirmation.AUTO)),
            ),
        )
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = listOf(contract),
            features = listOf(
                feature("automation", "example.automation", contributorId).copy(
                    contractCodes = listOf(contract.contractCode),
                ),
            ),
        )
        val runtimeSymbols = mapOf(
            GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE to "example.runtime.lowcode",
            GenerationTargetSymbols.WEB_RUNTIME_PACKAGE to "example.runtime.web",
        )

        val withoutAgent = LowcodeModuleCompiler.generate(
            metadata,
            contributorId,
            targetProfile = GenerationTargetProfile("plain", runtimeSymbols),
        )
        val missingAgentSymbols = assertThrows<IllegalArgumentException> {
            LowcodeModuleCompiler.generate(
                metadata,
                contributorId,
                targetProfile = GenerationTargetProfile(
                    id = "broken-agent",
                    symbols = runtimeSymbols,
                    capabilities = setOf("agent"),
                ),
            )
        }
        val withAgent = LowcodeModuleCompiler.generate(
            metadata,
            contributorId,
            targetProfile = GenerationTargetProfile(
                id = "agent",
                symbols = runtimeSymbols +
                    mapOf(
                        GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE to "example.runtime.agent",
                        GenerationTargetSymbols.CORE_RUNTIME_PACKAGE to "example.runtime.core",
                    ),
                capabilities = setOf("agent"),
            ),
        )

        assertFalse(withoutAgent.any { file -> file.fileName.endsWith("ToolRegistryContributor") })
        assertTrue(missingAgentSymbols.message.orEmpty().contains(GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE))
        assertTrue(withAgent.any { file -> file.fileName.endsWith("ToolRegistryContributor") })
        assertFalse(withAgent.any { file -> "site.addzero.biz" in file.content })
    }

    @Test
    fun `定时任务约定文件使用目标运行时的调度契约`() {
        val contributorId = "example.library"
        val packageName = "example.cleanup"
        val metadata = LowcodeMetadata(
            models = emptyList(),
            dtoDefinitions = emptyList(),
            routeBindings = emptyList(),
            contracts = emptyList(),
            conventionFiles = listOf(
                LsiConventionFile(
                    fileCode = "cleanup",
                    name = "清理任务",
                    className = "CleanupJob",
                    kind = LsiConventionFileKind.SCHEDULED_JOB,
                    packageName = packageName,
                    contributorId = contributorId,
                ),
            ),
            features = listOf(feature("cleanup", packageName, contributorId)),
        )
        val profile = GenerationTargetProfile(
            id = "application",
            symbols = mapOf(GenerationTargetSymbols.CORE_RUNTIME_PACKAGE to "example.runtime.core"),
        )

        val file = LowcodeModuleCompiler.generate(metadata, contributorId, targetProfile = profile)
            .single { candidate -> candidate.fileName == "CleanupJob" }

        assertTrue(file.content.contains("import example.runtime.core.ScheduledJob"))
        assertFalse(file.content.contains("code.studio.target"))
    }

    private fun feature(code: String, packageName: String, contributorId: String) = LsiLowcodeFeature(
        featureCode = code,
        name = code,
        packageName = packageName,
        contributorId = contributorId,
    )

    private companion object {
        val EMPTY_PROFILE = GenerationTargetProfile("test", emptyMap())
    }
}
