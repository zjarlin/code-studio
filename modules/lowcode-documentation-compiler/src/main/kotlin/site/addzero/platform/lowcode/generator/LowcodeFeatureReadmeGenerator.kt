package site.addzero.platform.lowcode.generator

/**
 * 从功能、模型和契约元数据生成目录说明。
 */
object LowcodeFeatureReadmeGenerator {
    fun generate(
        features: List<LsiLowcodeFeature>,
        models: List<LowcodeModelMeta>,
        contracts: List<LsiLowcodeContract>,
    ): List<LowcodeGeneratedFile> {
        val modelsByCode = models.associateBy(LowcodeModelMeta::modelCode)
        val contractsByCode = contracts.associateBy(LsiLowcodeContract::contractCode)
        val mergedFeatures = features
            .groupBy { feature -> feature.contributorId to feature.packageName }
            .values
            .map { definitions -> definitions.merge() }
            .sortedWith(compareBy(LsiLowcodeFeature::contributorId, LsiLowcodeFeature::packageName))
        return mergedFeatures.map { feature ->
                renderReadme(
                    feature = feature,
                    models = feature.modelCodes.map(modelsByCode::getValue),
                    contracts = feature.contractCodes.map(contractsByCode::getValue),
                    childFeatures = mergedFeatures.directChildrenOf(feature),
                )
            }
    }

    private fun renderReadme(
        feature: LsiLowcodeFeature,
        models: List<LowcodeModelMeta>,
        contracts: List<LsiLowcodeContract>,
        childFeatures: List<LsiLowcodeFeature>,
    ): LowcodeGeneratedFile {
        val layout = feature.packageName.generatedLayout()
        val title = if (feature.name.endsWith("模块")) feature.name else "${feature.name}模块"
        val description = feature.description?.trim()?.takeIf(String::isNotEmpty)
            ?: "该功能目录由结构化元数据定义。"
        val modelsSource = models
            .sortedBy(LowcodeModelMeta::modelCode)
            .joinToString("\n") { model ->
                "| `${model.modelCode.markdownEscaped()}` | ${model.name.markdownEscaped()} | " +
                    "`${model.entityQualifiedName().markdownEscaped()}` |"
            }
            .ifEmpty { "无。" }
        val modelsSection = if (models.isEmpty()) {
            modelsSource
        } else {
            "| 编码 | 名称 | 类型 |\n| --- | --- | --- |\n$modelsSource"
        }
        val services = buildList {
            models.filter { model -> model.kind == LowcodeModelKind.ENTITY }
                .sortedBy(LowcodeModelMeta::modelCode)
                .forEach { model ->
                    add(
                        "${model.name} Service" to layout.qualifiedName(
                            LowcodeGeneratedResourceKind.SERVICE,
                            model.entityClassName() + "Service",
                        ),
                    )
                }
            contracts
                .filter { contract ->
                    contract.operations.isEmpty() || contract.operations.any(LsiLowcodeCustomOperation::generatesService)
                }
                .sortedBy(LsiLowcodeContract::contractCode)
                .forEach { contract ->
                    add(
                        contract.name to contract.featurePackageName.generatedLayout()
                            .qualifiedName(LowcodeGeneratedResourceKind.SERVICE, contract.className),
                    )
                }
        }
        val servicesSource = services
            .joinToString("\n") { (name, qualifiedName) ->
                "| ${name.markdownEscaped()} | `${qualifiedName.markdownEscaped()}` |"
            }
            .ifEmpty { "无。" }
        val servicesSection = if (services.isEmpty()) {
            servicesSource
        } else {
            "| 名称 | 类型 |\n| --- | --- |\n$servicesSource"
        }
        val metadataSection = """
            ## 元数据

            | 属性 | 值 |
            | --- | --- |
            | 目录 | `${feature.featureCode.markdownEscaped()}` |
            | 包名 | `${feature.packageName.markdownEscaped()}` |
            | Contributor | `${feature.contributorId.markdownEscaped()}` |
        """.trimIndent()
        val childrenSection = childFeatures
            .sortedBy(LsiLowcodeFeature::packageName)
            .joinToString("\n") { child ->
                "| `${child.featureCode.markdownEscaped()}` | ${child.name.markdownEscaped()} | " +
                    "`${child.packageName.markdownEscaped()}` |"
            }
            .let { rows ->
                if (rows.isEmpty()) "无。" else "| 编码 | 名称 | 包名 |\n| --- | --- | --- |\n$rows"
            }
        val content = listOf(
            "# ${title.markdownEscaped()}",
            description.markdownEscaped(),
            metadataSection,
            "## 子功能\n\n$childrenSection",
            "## 实体\n\n$modelsSection",
            "## Service\n\n$servicesSection",
        ).joinToString(separator = "\n\n", postfix = "\n")
        return LowcodeGeneratedFile(
            packageName = layout.featurePackage,
            fileName = "README",
            relativePath = layout.relativeFeatureFilePath("README.md"),
            content = generatedByStudio(content, extensionName = "md"),
            extensionName = "md",
        )
    }

    private fun List<LsiLowcodeFeature>.merge(): LsiLowcodeFeature {
        val first = minBy(LsiLowcodeFeature::featureCode)
        return first.copy(
            modelCodes = flatMap(LsiLowcodeFeature::modelCodes).distinct().sorted(),
            contractCodes = flatMap(LsiLowcodeFeature::contractCodes).distinct().sorted(),
        )
    }

    private fun List<LsiLowcodeFeature>.directChildrenOf(parent: LsiLowcodeFeature): List<LsiLowcodeFeature> =
        filter { feature ->
            feature.contributorId == parent.contributorId &&
                feature.packageName.startsWith("${parent.packageName}.") &&
                none { candidate ->
                    candidate.contributorId == parent.contributorId &&
                        candidate.packageName != parent.packageName &&
                        candidate.packageName != feature.packageName &&
                        feature.packageName.startsWith("${candidate.packageName}.") &&
                        candidate.packageName.startsWith("${parent.packageName}.")
                }
        }

    private fun String.markdownEscaped(): String = replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\n", "<br>")
        .replace("|", "\\|")
        .replace("`", "\\`")
}
