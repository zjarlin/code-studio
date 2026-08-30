package site.addzero.platform.lowcode.generator

import site.addzero.studio.runtime.GenerationTargetProfile

/** 将一个 contributor 的完整低代码元数据编译为确定性文件集合。 */
object LowcodeModuleCompiler {
    fun generate(
        metadata: LowcodeMetadata,
        contributorId: String,
        featurePackages: Set<String>? = null,
        targetProfile: GenerationTargetProfile,
    ): List<LowcodeGeneratedFile> {
        targetProfile.requireSupportedCapabilities()
        val moduleModels = metadata.models.filter { model -> model.contributorId == contributorId }
        val entityModels = metadata.models.filter { model -> model.entitySourceContributorId() == contributorId }
        val moduleFeatures = metadata.features.filter { feature -> feature.contributorId == contributorId }
        val featureModelCodes = moduleFeatures.flatMap(LsiLowcodeFeature::modelCodes).toSet()
        val featureEntityModels = metadata.models.filter { model ->
            model.contributorId == contributorId && model.modelCode in featureModelCodes
        }
        val moduleDtos = metadata.dtoDefinitions.filter { dto -> dto.contributorId == contributorId }
        val resolvedRouteBindings = LowcodeSourceCompiler.resolveRouteBindings(
            bindings = metadata.routeBindings.filter { binding -> binding.contributorId == contributorId },
            models = metadata.models,
            modelsRequiringRoutes = featureEntityModels,
            dtoDefinitions = metadata.dtoDefinitions,
        )
        val files = buildList {
            addAll(LowcodeSourceCompiler.generateEntities(entityModels, metadata.models))
            addAll(LowcodeSourceCompiler.generateTransientResolverContracts(moduleModels))
            addAll(LowcodeDtoSourceGenerator.generate(moduleModels, metadata.models))
            addAll(LowcodeDtoSourceGenerator.generateDefinitions(moduleDtos, metadata.models))
            addAll(LowcodeDtoSourceGenerator.generateDefinitionValidations(moduleDtos, metadata.models))
            addAll(LowcodeSourceCompiler.generateRouteBindings(resolvedRouteBindings))
            metadata.contracts
                .filter { contract -> contract.contributorId == contributorId }
                .flatMap { contract ->
                    LowcodeSourceCompiler.generate(contract, metadata.models, metadata.dtoDefinitions)
                }
                .filter { file -> file.extensionName == "kt" }
                .let(::addAll)
            addAll(
                LowcodeFeatureServiceSourceGenerator.generate(
                    features = moduleFeatures,
                    models = metadata.models,
                    contracts = metadata.contracts,
                    routeBindings = resolvedRouteBindings,
                    dtoDefinitions = metadata.dtoDefinitions,
                    includeAgentTools = AGENT_CAPABILITY in targetProfile.capabilities,
                ),
            )
            addAll(
                LowcodeDictionaryEnumSourceGenerator.generate(
                    dictionaries = metadata.dictionaries,
                    models = metadata.models,
                    contributorId = contributorId,
                ),
            )
            addAll(LowcodeConstantSourceGenerator.generate(metadata.constantGroups, contributorId))
            addAll(
                LowcodeFeatureControllerSourceGenerator.generate(
                    features = moduleFeatures,
                    models = metadata.models,
                    routeBindings = resolvedRouteBindings,
                ),
            )
            addAll(
                ConventionFileSourceGenerator.generate(
                    metadata.conventionFiles.filter { file -> file.contributorId == contributorId },
                ),
            )
            addAll(
                LowcodeFeatureReadmeGenerator.generate(
                    features = moduleFeatures,
                    models = metadata.models,
                    contracts = metadata.contracts,
                ),
            )
        }
        val selectedFiles = featurePackages?.let { packages ->
            files.filter { file -> packages.any(file.packageName::belongsToFeaturePackage) }
        } ?: files
        return selectedFiles
            .groupBy(LowcodeGeneratedFile::relativePath)
            .map { (path, candidates) ->
                require(candidates.map(LowcodeGeneratedFile::content).distinct().size == 1) {
                    "低代码模块生成了内容冲突的重复文件: $path"
                }
                candidates.first()
            }
            .sortedBy(LowcodeGeneratedFile::relativePath)
            .map { file -> file.applyTargetProfile(targetProfile) }
    }

    private fun LowcodeGeneratedFile.applyTargetProfile(profile: GenerationTargetProfile): LowcodeGeneratedFile {
        val requiredSymbols = GenerationTargetSymbols.referencedKeys(content)
        val missingSymbols = requiredSymbols - profile.symbols.keys
        require(missingSymbols.isEmpty()) {
            "生成目标 ${profile.id} 缺少宿主语义符号: ${missingSymbols.sorted().joinToString()}"
        }
        return copy(content = GenerationTargetSymbols.render(content, profile.symbols))
    }

    private fun GenerationTargetProfile.requireSupportedCapabilities() {
        if (AGENT_CAPABILITY !in capabilities) return
        val required = setOf(
            GenerationTargetSymbols.AGENT_RUNTIME_PACKAGE,
            GenerationTargetSymbols.CORE_RUNTIME_PACKAGE,
        )
        val missing = required - symbols.keys
        require(missing.isEmpty()) {
            "生成目标 $id 启用 $AGENT_CAPABILITY capability 时缺少语义符号: ${missing.sorted().joinToString()}"
        }
    }

    private const val AGENT_CAPABILITY = "agent"
}
