package site.addzero.platform.lowcode.generator

import tools.jackson.module.kotlin.jacksonObjectMapper

/** 当前 contributor 及其依赖闭包的版本化、离线可编译元数据。 */
data class LowcodeMetadataSnapshot(
    val formatVersion: Int = FORMAT_VERSION,
    val contributorId: String,
    val contributorIds: List<String>,
    val metadata: LowcodeMetadata,
) {
    init {
        require(formatVersion == FORMAT_VERSION) { "不支持的元数据快照版本: $formatVersion" }
        require(contributorId.isNotBlank()) { "元数据快照 contributorId 不能为空" }
        require(contributorIds.isNotEmpty()) { "元数据快照 contributorIds 不能为空" }
        require(contributorIds.distinct().size == contributorIds.size) { "元数据快照 contributorIds 不能重复" }
        require(contributorId in contributorIds) { "元数据快照缺少当前 contributor: $contributorId" }
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
    }
}

object LowcodeMetadataSnapshots {
    fun decode(content: String): LowcodeMetadataSnapshot =
        objectMapper.readValue(content, LowcodeMetadataSnapshot::class.java)

    fun encode(snapshot: LowcodeMetadataSnapshot): String =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot.canonical()) + "\n"

    private val objectMapper = jacksonObjectMapper()
}

private fun LowcodeMetadataSnapshot.canonical(): LowcodeMetadataSnapshot = copy(
    contributorIds = contributorIds.sorted(),
    metadata = metadata.canonical(),
)

private fun LowcodeMetadata.canonical(): LowcodeMetadata = copy(
    models = models.sortedBy(LowcodeModelMeta::modelCode).map { model ->
        model.copy(
            fields = model.fields.sortedWith(compareBy(LowcodeFieldMeta::orderNo, LowcodeFieldMeta::id)),
            queries = model.queries.sortedWith(compareBy(LowcodeQueryMeta::orderNo, LowcodeQueryMeta::id)).map { query ->
                query.copy(items = query.items.sortedWith(compareBy(LowcodeQueryConditionMeta::orderNo, LowcodeQueryConditionMeta::id)))
            },
            relations = model.relations.sortedWith(compareBy(LowcodeRelationMeta::orderNo, LowcodeRelationMeta::id)),
            entityConfig = model.entityConfig.copy(
                fieldAnnotations = model.entityConfig.fieldAnnotations.toSortedMap(),
                relationOrderings = model.entityConfig.relationOrderings.toSortedMap(),
            ),
            routeConfig = model.routeConfig?.canonical(),
        )
    },
    dtoDefinitions = dtoDefinitions.sortedBy(LsiLowcodeDtoDefinition::dtoCode),
    routeBindings = routeBindings.sortedBy(LowcodeRouteBinding::routeCode).map { binding ->
        binding.copy(route = binding.route.canonical())
    },
    contracts = contracts.sortedBy(LsiLowcodeContract::contractCode).map { contract ->
        contract.copy(
            operations = contract.operations.sortedBy(LsiLowcodeCustomOperation::operationCode),
            dtoSchemas = contract.dtoSchemas.sortedBy(LsiLowcodeDtoSchema::schemaName).map(LsiLowcodeDtoSchema::canonical),
            agentExposure = contract.agentExposure.copy(operations = contract.agentExposure.operations.toSortedMap()),
        )
    },
    conventionFiles = conventionFiles.sortedWith(
        compareBy(
            LsiConventionFile::contributorId,
            LsiConventionFile::packageName,
            LsiConventionFile::kind,
            LsiConventionFile::fileCode,
        ),
    ),
    features = features.sortedWith(compareBy(LsiLowcodeFeature::contributorId, LsiLowcodeFeature::featureCode)).map { feature ->
        feature.copy(
            modelCodes = feature.modelCodes.sorted(),
            dtoCodes = feature.dtoCodes.sorted(),
            contractCodes = feature.contractCodes.sorted(),
        )
    },
    dictionaries = dictionaries.sortedBy(LowcodeDictionaryMeta::dictionaryCode).map { dictionary ->
        dictionary.copy(
            items = dictionary.items.sortedWith(
                compareBy(LowcodeDictionaryItemMeta::orderNo, LowcodeDictionaryItemMeta::value),
            ),
        )
    },
    constantGroups = constantGroups.sortedBy(LowcodeConstantGroupMeta::groupCode),
)

private fun LsiLowcodeRoute.canonical(): LsiLowcodeRoute = copy(
    aliasPaths = aliasPaths.sorted(),
    fetchPaths = fetchPaths.sorted(),
    excludePaths = excludePaths.sorted(),
    enabledOperations = enabledOperations.toSortedSet(),
    properties = properties.map { property -> property.copy(enumValues = property.enumValues.sorted()) },
    queryFields = queryFields.map { field -> field.copy(enumValues = field.enumValues.sorted()) },
    customOperations = customOperations.sortedBy(LsiLowcodeCustomOperation::operationCode),
    dtoSchemas = dtoSchemas.sortedBy(LsiLowcodeDtoSchema::schemaName).map(LsiLowcodeDtoSchema::canonical),
    discriminator = discriminator?.copy(mapping = discriminator.mapping.toSortedMap()),
    agentExposure = agentExposure.copy(operations = agentExposure.operations.toSortedMap()),
)

private fun LsiLowcodeDtoSchema.canonical(): LsiLowcodeDtoSchema = copy(
    properties = properties.toSortedMap().mapValues { (_, schema) -> schema.canonical() },
    required = required.toSortedSet(),
    validations = validations.toSortedMap().mapValues { (_, rules) -> rules.sortedBy { rule -> rule.code } },
)

private fun LsiLowcodeApiSchema.canonical(): LsiLowcodeApiSchema = copy(
    properties = properties.toSortedMap().mapValues { (_, schema) -> schema.canonical() },
    required = required.toSortedSet(),
    items = items?.canonical(),
    enumValues = enumValues.sorted(),
    oneOf = oneOf.map(LsiLowcodeApiSchema::canonical),
)

/** 仅保留 manifest 依赖闭包内的 LSI，防止共享开发库泄入无关应用元数据。 */
fun LowcodeMetadata.restrictToContributors(contributorIds: Set<String>): LowcodeMetadata {
    require(contributorIds.isNotEmpty()) { "contributor 闭包不能为空" }
    val selectedModels = models.filter { model -> model.contributorId in contributorIds }
    val selectedModelCodes = selectedModels.map(LowcodeModelMeta::modelCode).toSet()
    val usedDictionaryCodes = buildSet {
        selectedModels.flatMap(LowcodeModelMeta::fields).mapNotNullTo(this) { field -> field.dictCode }
        selectedModels.flatMap { model -> model.entityConfig.inheritedProperties }
            .mapNotNullTo(this) { property -> property.dictionaryCode }
        selectedModels.flatMap { model -> model.entityConfig.transientProperties }
            .mapNotNullTo(this) { property -> property.dictionaryCode }
        routeBindings.filter { binding -> binding.contributorId in contributorIds }
            .flatMap { binding -> binding.route.properties }
            .mapNotNullTo(this) { property -> property.dictionaryCode }
    }
    return LowcodeMetadata(
        models = selectedModels,
        dtoDefinitions = dtoDefinitions.filter { dto -> dto.contributorId in contributorIds },
        routeBindings = routeBindings.filter { binding -> binding.contributorId in contributorIds },
        contracts = contracts.filter { contract -> contract.contributorId in contributorIds },
        conventionFiles = conventionFiles.filter { file -> file.contributorId in contributorIds },
        features = features.filter { feature -> feature.contributorId in contributorIds },
        dictionaries = dictionaries.filter { dictionary ->
            dictionary.ownerModelCode in selectedModelCodes || dictionary.dictionaryCode in usedDictionaryCodes
        },
        constantGroups = constantGroups.filter { group -> group.contributorId in contributorIds },
    )
}
