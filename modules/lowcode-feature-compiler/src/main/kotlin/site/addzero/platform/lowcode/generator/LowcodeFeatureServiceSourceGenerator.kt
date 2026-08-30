package site.addzero.platform.lowcode.generator

import java.security.MessageDigest

/** 生成实体 IService/ServiceImpl 和显式业务 Service 契约。 */
object LowcodeFeatureServiceSourceGenerator {
    const val SERVICE_IMPLEMENTATION_SIGNATURE_PREFIX = "// 低代码 Service 实现脚手架签名: "

    fun generate(
        features: List<LsiLowcodeFeature>,
        models: List<LowcodeModelMeta>,
        contracts: List<LsiLowcodeContract>,
        routeBindings: List<LowcodeRouteBinding>,
        dtoDefinitions: List<LsiLowcodeDtoDefinition> = emptyList(),
        includeAgentTools: Boolean = false,
        templates: SourceTemplateCatalog = SourceTemplateCatalog.DEFAULT,
    ): List<LowcodeGeneratedFile> {
        val modelsByCode = models.associateBy(LowcodeModelMeta::modelCode)
        val contractsByCode = contracts.associateBy(LsiLowcodeContract::contractCode)
        return features
            .groupBy { feature -> feature.contributorId to feature.packageName }
            .values
            .map { values -> values.merge() }
            .sortedWith(compareBy(LsiLowcodeFeature::contributorId, LsiLowcodeFeature::packageName))
            .flatMap { feature ->
                val entityModels = feature.modelCodes
                    .map(modelsByCode::getValue)
                    .filter { model -> model.kind == LowcodeModelKind.ENTITY }
                    .sortedBy(LowcodeModelMeta::modelCode)
                val featureContracts = feature.contractCodes
                    .map(contractsByCode::getValue)
                    .filter { contract ->
                        contract.operations.isEmpty() || contract.operations.any(LsiLowcodeCustomOperation::generatesService)
                    }
                    .sortedBy(LsiLowcodeContract::contractCode)
                buildList {
                    entityModels.forEach { model ->
                        val route = model.resolveRouteBinding(routeBindings)
                        route.requireValidExcelMetadata()
                        if (!route.requiresEntityService()) {
                            return@forEach
                        }
                        add(generateEntityService(feature, model, route))
                        add(generateEntityServiceImplementation(feature, model, route, templates))
                    }
                    if (includeAgentTools) {
                        LowcodeAgentToolSourceGenerator.generate(
                            feature = feature,
                            contracts = featureContracts,
                            dtoCatalog = LowcodeDtoTypeCatalog.from(models, dtoDefinitions),
                        )?.let(::add)
                        LowcodeEntityAgentToolSourceGenerator.generate(
                            feature = feature,
                            models = entityModels,
                            routeBindings = routeBindings,
                        )?.let(::add)
                    }
                }
            }
    }

    private fun generateEntityService(
        feature: LsiLowcodeFeature,
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
    ): LowcodeGeneratedFile {
        val layout = feature.packageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.SERVICE)
        val className = model.entityServiceClassName()
        val entityName = model.entityClassName()
        val serviceType = if (route.hasExcelCapability()) "IExcelService" else "IService"
        val imports = sortedSetOf(
            model.entityQualifiedName(),
            "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.$serviceType",
        )
        val content = """
            |package $packageName
            |
            |${imports.joinToString("\n") { importName -> "import $importName" }}
            |
            |/** ${model.name.escapeKDoc()}通用 Service。 */
            |interface $className : $serviceType<$entityName>
        """.trimMargin().lineSequence().joinToString("\n") { line -> line.trimEnd() } + "\n"
        return generatedServiceFile(
            feature = feature,
            className = className,
            content = content,
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun generateEntityServiceImplementation(
        feature: LsiLowcodeFeature,
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
        templates: SourceTemplateCatalog,
    ): LowcodeGeneratedFile {
        val layout = feature.packageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.SERVICE)
        val serviceName = model.entityServiceClassName()
        val className = "${serviceName}Impl"
        val entityName = model.entityClassName()
        val implementationType = if (route.hasExcelCapability()) "ExcelServiceImpl" else "ServiceImpl"
        val imports = sortedSetOf(
            model.entityQualifiedName(),
            "org.koin.core.annotation.Single",
            "${generationTargetSymbol(GenerationTargetSymbols.LOWCODE_RUNTIME_PACKAGE)}.$implementationType",
        )
        val importsSource = imports.joinToString("\n") { importName -> "import $importName" }
        val header = """
            |$SERVICE_IMPLEMENTATION_SIGNATURE_PREFIX${serviceImplementationSignature(model, route)}
            |package $packageName
            |
            |$importsSource
        """.trimMargin()
        val content = templates.render(
            SourceTemplateKind.SERVICE_IMPLEMENTATION,
            mapOf(
                "header" to header,
                "documentation" to "/** ${model.name.escapeKDoc()}的元数据 CRUD 实现。 */",
                "className" to className,
                "implementationType" to implementationType,
                "entityName" to entityName,
                "serviceName" to serviceName,
            ),
        )
        return generatedServiceFile(
            feature = feature,
            className = className,
            content = content,
            kind = LowcodeGeneratedFileKind.SERVICE_IMPLEMENTATION_SCAFFOLD,
        )
    }

    private fun serviceImplementationSignature(
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
    ): String {
        val value = listOf(
            model.modelCode,
            model.entityQualifiedName(),
            model.entityServiceClassName(),
            if (route.hasExcelCapability()) "ExcelServiceImpl" else "ServiceImpl",
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun generatedServiceFile(
        feature: LsiLowcodeFeature,
        className: String,
        content: String,
        kind: LowcodeGeneratedFileKind,
    ): LowcodeGeneratedFile {
        val layout = feature.packageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.SERVICE)
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = className,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.SERVICE,
                className,
            ),
            content = generatedByStudio(content),
            kind = kind,
        )
    }

    private fun List<LsiLowcodeFeature>.merge(): LsiLowcodeFeature {
        val first = minBy(LsiLowcodeFeature::featureCode)
        return first.copy(
            modelCodes = flatMap(LsiLowcodeFeature::modelCodes).distinct().sorted(),
            contractCodes = flatMap(LsiLowcodeFeature::contractCodes).distinct().sorted(),
        )
    }

    private fun LowcodeModelMeta.entityServiceClassName(): String = entityClassName() + "Service"

    private fun LsiLowcodeRoute.hasExcelCapability(): Boolean =
        excel?.let { value -> value.importEnabled || value.exportEnabled } == true

    private fun LowcodeModelMeta.resolveRouteBinding(bindings: List<LowcodeRouteBinding>): LsiLowcodeRoute =
        requireNotNull(
            bindings.firstOrNull { binding ->
                binding.contributorId == contributorId &&
                    binding.route.className == className &&
                    binding.route.packageName == packageName
            }?.route,
        ) {
            "实体 Service 缺少路由元数据: $modelCode"
        }

    private fun String.escapeKDoc(): String = replace("*/", "* /")

}
