package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeFeatureControllerSourceGeneratorTest {
    @Test
    fun `does not generate controller for service only entity`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "internalRecord",
            name = "Internal record",
            packageName = "example.internal",
            className = "InternalRecord",
            tableName = "internal_record",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val feature = LsiLowcodeFeature(
            featureCode = "internal",
            name = "Internal",
            packageName = model.packageName,
            contributorId = model.contributorId!!,
            modelCodes = listOf(model.modelCode),
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "example.internal.InternalRecord",
            className = model.className,
            description = model.name,
            path = "/internal-records",
            generateController = false,
            enabledOperations = setOf("GET", "PAGE"),
            properties = emptyList(),
            featurePackageName = feature.packageName,
        )

        val controllers = LowcodeFeatureControllerSourceGenerator.generate(
            features = listOf(feature),
            models = listOf(model),
            routeBindings = listOf(LowcodeRouteBinding(route.qualifiedName, feature.contributorId, route)),
        )

        assertTrue(controllers.isEmpty())
    }

    @Test
    fun `does not generate controller for entity without operations`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "internalRecord",
            name = "Internal record",
            packageName = "example.internal",
            className = "InternalRecord",
            tableName = "internal_record",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val feature = LsiLowcodeFeature(
            featureCode = "internal",
            name = "Internal",
            packageName = model.packageName,
            contributorId = model.contributorId!!,
            modelCodes = listOf(model.modelCode),
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "example.internal.generated.entity.InternalRecord",
            className = model.className,
            description = model.name,
            path = "/internal-records",
            enabledOperations = emptySet(),
            properties = emptyList(),
            featurePackageName = feature.packageName,
        )

        val controllers = LowcodeFeatureControllerSourceGenerator.generate(
            features = listOf(feature),
            models = listOf(model),
            routeBindings = listOf(LowcodeRouteBinding(route.qualifiedName, feature.contributorId, route)),
        )

        assertTrue(controllers.isEmpty())
    }

    @Test
    fun `rejects enabled export without metadata columns`() {
        val route = LsiLowcodeRoute(
            packageName = "example.user",
            qualifiedName = "example.user.User",
            className = "User",
            description = "用户",
            path = "/users",
            enabledOperations = emptySet(),
            properties = emptyList(),
            excel = LsiLowcodeExcel(
                importEnabled = false,
                exportEnabled = true,
                customExport = true,
                fileName = "users.xlsx",
                templateFileName = "users-template.xlsx",
                sheetName = "users",
                importColumns = emptyList(),
                exportColumns = emptyList(),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            route.requireValidExcelMetadata()
        }

        assertTrue(error.message.orEmpty().contains("Excel 导出字段不能为空"))
    }

    @Test
    fun `generates editable entity controller with generic CRUD inheritance`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "user",
            name = "用户",
            packageName = "example.user",
            className = "User",
            tableName = "users",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val feature = LsiLowcodeFeature(
            featureCode = "user",
            name = "用户管理",
            packageName = model.packageName,
            contributorId = model.contributorId!!,
            modelCodes = listOf(model.modelCode),
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "example.user.generated.entity.User",
            className = model.className,
            description = model.name,
            path = "/users",
            aliasPaths = listOf("/legacy-users"),
            enabledOperations = setOf("GET", "PAGE", "UPDATE"),
            properties = emptyList(),
            featurePackageName = feature.packageName,
        )

        val controller = LowcodeFeatureControllerSourceGenerator.generate(
            features = listOf(feature),
            models = listOf(model),
            routeBindings = listOf(LowcodeRouteBinding(route.qualifiedName, feature.contributorId, route)),
        ).single()

        assertEquals("UserController", controller.fileName)
        assertEquals("example.user.generated.controller", controller.packageName)
        assertEquals(LowcodeGeneratedFileKind.CONTROLLER_SCAFFOLD, controller.kind)
        assertTrue(controller.content.startsWith(LowcodeFeatureControllerSourceGenerator.EDITABLE_CONTROLLER_MARKER))
        assertFalse(controller.content.contains("请勿手工修改"))
        assertTrue(
            controller.content.lineSequence()
                .any { line -> line.startsWith(LowcodeFeatureControllerSourceGenerator.CONTROLLER_SIGNATURE_PREFIX) },
        )
        assertTrue(controller.content.contains("154cf4583629ed43eb37b5acff64fa9c70aba04abb32d2260523b986d7393e25"))
        assertTrue(controller.content.lineSequence().any { line -> line == "@Single" })
        assertFalse(controller.content.contains("LowcodeContractProvider"))
        assertFalse(controller.content.contains("site.addzero.platform.web"))
        assertTrue(controller.content.contains("override val service: UserService"))
        assertTrue(controller.content.contains(") : CrudController<User>"))
        assertTrue(controller.content.contains("override val routeKey = \"/users\""))
        assertFalse(controller.content.contains("routeKey: String"))
        assertFalse(controller.content.contains("override fun install"))
        assertFalse(controller.content.contains("io.ktor.server.routing"))
        assertFalse(controller.content.contains("/legacy-users"))
        assertFalse(controller.content.contains("private fun Route.register"))
        assertFalse(controller.content.contains("LowcodeRuntimeController"))
        assertFalse(controller.content.contains("LowcodeRouteRuntimeProvider"))
        assertTrue(controller.relativePath.endsWith("/example/user/generated/controller/UserController.kt"))

        val excelColumn = LsiLowcodeProperty(
            name = "name",
            type = "string",
            format = null,
            required = true,
            arrayItemType = null,
            description = "名称。",
        )
        val excelController = LowcodeFeatureControllerSourceGenerator.generate(
            features = listOf(feature),
            models = listOf(model),
            routeBindings = listOf(
                LowcodeRouteBinding(
                    route.qualifiedName,
                    feature.contributorId,
                    route.copy(
                        excel = LsiLowcodeExcel(
                            importEnabled = true,
                            exportEnabled = true,
                            customExport = false,
                            fileName = "users.xlsx",
                            templateFileName = "users-template.xlsx",
                            sheetName = "users",
                            importColumns = listOf(excelColumn),
                            exportColumns = listOf(excelColumn),
                        ),
                    ),
                ),
            ),
        ).single()

        assertTrue(excelController.content.contains(") : CrudController<User>, ExcelController<User>"))
        assertFalse(excelController.content.contains("BaseExcelController"))
        assertFalse(excelController.content.contains("CrudExcelOperations"))
    }

    @Test
    fun `generates graph controller for node entity`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "catalogNode",
            name = "Catalog node",
            packageName = "example.catalog",
            className = "CatalogNode",
            tableName = "catalog_node",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.catalog",
            entityConfig = LsiLowcodeEntityConfig(
                baseMode = LowcodeEntityBaseMode.INHERITED,
                baseModels = listOf(LowcodeBaseModel.NODE),
            ),
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val feature = LsiLowcodeFeature(
            featureCode = "catalog",
            name = "Catalog",
            packageName = model.packageName,
            contributorId = model.contributorId!!,
            modelCodes = listOf(model.modelCode),
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "example.catalog.generated.entity.CatalogNode",
            className = model.className,
            displayName = "Catalog node",
            description = model.name,
            path = "/catalog-nodes",
            enabledOperations = setOf("GET", "PAGE"),
            properties = emptyList(),
            featurePackageName = feature.packageName,
        )

        val graphController = generateController(feature, model, route)
        val crudController = generateController(
            feature,
            model.copy(entityConfig = LsiLowcodeEntityConfig()),
            route,
        )

        val runtimePackage = generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)
        assertEquals(
            """
                ${LowcodeFeatureControllerSourceGenerator.EDITABLE_CONTROLLER_MARKER}
                ${LowcodeFeatureControllerSourceGenerator.CONTROLLER_SIGNATURE_PREFIX}71d517ee8f6d4130f5f8d7e8e5f4e6cd1a15ffda8d9e7629567af6cfbed2fffb
                package example.catalog.generated.controller

                import $runtimePackage.GraphController
                import example.catalog.generated.entity.CatalogNode
                import example.catalog.generated.service.CatalogNodeService
                import org.koin.core.annotation.Single

                /**
                 * Catalog node CRUD Controller。
                 *
                 * 本文件首次由低代码元数据生成，后续由业务代码维护。
                 */
                @Single
                class CatalogNodeController(
                    override val service: CatalogNodeService,
                ) : GraphController<CatalogNode> {
                    override val routeKey = "/catalog-nodes"
                }
            """.trimIndent() + "\n",
            graphController,
        )
        assertTrue(graphController.contains("import $runtimePackage.GraphController"))
        assertTrue(graphController.contains(") : GraphController<CatalogNode>"))
        assertFalse(graphController.contains("CrudController<CatalogNode>"))
        assertFalse(graphController.controllerSignature() == crudController.controllerSignature())
        assertTrue(
            LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
                actualContent = graphController,
                expectedContent = graphController,
            ).isEmpty(),
        )
    }

    @Test
    fun `rejects editable controller that keeps signature but loses CRUD scaffold`() {
        val expected = editableControllerSource()
        val actual = expected
            .replace("    override val service: UserService,\n", "")
            .replace(") : CrudController<User> {", ") : Controller {")
            .replace("override val routeKey = \"/users\"", "override val routeKey = \"/other\"")

        val mismatches = LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
            actualContent = actual,
            expectedContent = expected,
        )

        assertEquals(
            listOf(
                "缺少 CrudController<User>",
                "routeKey 不匹配，期望 \"/users\"",
            ),
            mismatches,
        )
    }

    @Test
    fun `rejects node controller without graph scaffold`() {
        val expected = editableControllerSource().replace("CrudController<User>", "GraphController<User>")
        val actual = editableControllerSource()

        val mismatches = LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
            actualContent = actual,
            expectedContent = expected,
        )

        assertEquals(listOf("缺少 GraphController<User>"), mismatches)
    }

    @Test
    fun `accepts business extensions and ignores handwritten controllers`() {
        val expected = editableControllerSource()
        val extended = expected
            .replace(
                "    override val service: UserService,",
                "    private val queryService: QueryService,",
            )
            .replace(
                "    override val routeKey = \"/users\"",
                "    override val routeKey: String = \"/users\"\n\n    override fun install(route: Route) = Unit",
            )
        val handwritten = "class UserController : Controller"

        assertTrue(
            LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
                actualContent = extended,
                expectedContent = expected,
            ).isEmpty(),
        )
        assertTrue(
            LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
                actualContent = handwritten,
                expectedContent = expected,
            ).isEmpty(),
        )
        assertEquals(
            listOf("缺少预期类声明: UserController"),
            LowcodeFeatureControllerSourceGenerator.editableControllerScaffoldMismatches(
                actualContent = expected.replace("class UserController", "class RenamedController"),
                expectedContent = expected,
            ),
        )
    }

    @Test
    fun `includes normalized custom operations in editable controller signature`() {
        val model = LowcodeModelMeta(
            id = 1,
            modelCode = "file",
            name = "文件",
            packageName = "example.file",
            className = "FileRecord",
            tableName = "file_record",
            kind = LowcodeModelKind.ENTITY,
            status = 1,
            version = 1,
            contributorId = "example.example",
            fields = emptyList(),
            queries = emptyList(),
            relations = emptyList(),
        )
        val feature = LsiLowcodeFeature(
            featureCode = "file",
            name = "文件",
            packageName = model.packageName,
            contributorId = model.contributorId!!,
            modelCodes = listOf(model.modelCode),
        )
        val upload = LsiLowcodeCustomOperation(
            operationCode = "upload",
            name = "上传文件",
            path = "/files/upload",
            method = LowcodeHttpMethod.POST,
            implementation = LowcodeOperationImplementation.EXISTING_REST,
            parameters = listOf(
                LsiLowcodeApiParameter(
                    name = "directory",
                    location = LowcodeApiParameterLocation.QUERY,
                    schema = LsiLowcodeApiSchema(type = "string"),
                ),
            ),
        )
        val content = LsiLowcodeCustomOperation(
            operationCode = "content",
            name = "读取文件",
            path = "/files/content/{id}",
            method = LowcodeHttpMethod.GET,
            authenticated = false,
            implementation = LowcodeOperationImplementation.EXISTING_REST,
        )
        val route = LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = "example.file.generated.entity.FileRecord",
            className = model.className,
            description = model.name,
            path = "/files",
            enabledOperations = setOf("PAGE"),
            properties = emptyList(),
            featurePackageName = feature.packageName,
            customOperations = listOf(upload, content),
        )

        val first = generateController(feature, model, route)
        val reordered = generateController(feature, model, route.copy(customOperations = listOf(content, upload)))
        val changed = generateController(
            feature,
            model,
            route.copy(customOperations = listOf(upload.copy(authenticated = false), content)),
        )

        assertEquals(first.controllerSignature(), reordered.controllerSignature())
        assertFalse(first.controllerSignature() == changed.controllerSignature())
    }

    private fun generateController(
        feature: LsiLowcodeFeature,
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
    ): String = LowcodeFeatureControllerSourceGenerator.generate(
        features = listOf(feature),
        models = listOf(model),
        routeBindings = listOf(LowcodeRouteBinding(route.qualifiedName, feature.contributorId, route)),
    ).single().content

    private fun editableControllerSource(): String = """
        ${LowcodeFeatureControllerSourceGenerator.EDITABLE_CONTROLLER_MARKER}
        ${LowcodeFeatureControllerSourceGenerator.CONTROLLER_SIGNATURE_PREFIX}signature
        package example.user.generated.controller

        class UserController(
            override val service: UserService,
        ) : CrudController<User> {
            override val routeKey = "/users"
        }
    """.trimIndent() + "\n"

    private fun String.controllerSignature(): String = lineSequence()
        .first { line -> line.startsWith(LowcodeFeatureControllerSourceGenerator.CONTROLLER_SIGNATURE_PREFIX) }
}
