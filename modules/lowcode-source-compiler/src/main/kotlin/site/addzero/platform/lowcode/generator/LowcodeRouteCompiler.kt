package site.addzero.platform.lowcode.generator

import java.util.Locale

/** 使用完整模型目录编译路由 LSI 和数据库路由绑定。 */
object LowcodeRouteCompiler {
    fun resolveBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta> = emptyList(),
        modelsRequiringRoutes: Collection<LowcodeModelMeta> = emptyList(),
    ): List<LowcodeRouteBinding> = resolveBindings(
        bindings = bindings,
        models = models,
        modelsRequiringRoutes = modelsRequiringRoutes,
        dtoDefinitions = emptyList(),
    )

    fun resolveBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta>,
        modelsRequiringRoutes: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): List<LowcodeRouteBinding> {
        val dtoSchemaCatalog = LowcodeDtoSchemaCatalog(models, dtoDefinitions)
        val completeBindings = bindings + modelsRequiringRoutes
            .filter { model -> model.status == 1 && model.kind == LowcodeModelKind.ENTITY }
            .filter { model ->
                bindings.none { binding ->
                    binding.route.modelCode == model.modelCode ||
                        binding.route.packageName == model.packageName && binding.route.className == model.className
                }
            }
            .map { model ->
                LowcodeRouteBinding(
                    routeCode = model.entityQualifiedName(),
                    contributorId = requireNotNull(model.contributorId) {
                        "实体默认路由缺少 contributor: ${model.modelCode}"
                    },
                    route = compile(model, models, dtoSchemaCatalog),
                )
            }
        val modelsByType = models
            .flatMap { model ->
                buildSet {
                    add(model.packageName to model.className)
                    add(model.entityPackageName() to model.entityClassName())
                    model.routeConfig?.let { route -> add(route.packageName to route.className) }
                }.map { type -> type to model }
            }
            .toMap()
        return completeBindings
            .sortedBy(LowcodeRouteBinding::routeCode)
            .map { binding ->
                val route = modelsByType[binding.route.packageName to binding.route.className]
                    ?.let { model -> compile(model, binding.route, models, dtoSchemaCatalog) }
                    ?: binding.route.withReferencedDtoSchemas(dtoSchemaCatalog)
                binding.copy(route = route)
            }
    }

    fun compile(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(model),
    ): LsiLowcodeRoute = compile(model, modelCatalog, emptyList())

    fun compile(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): LsiLowcodeRoute = compile(
        model = model,
        modelCatalog = modelCatalog,
        dtoSchemaCatalog = LowcodeDtoSchemaCatalog(modelCatalog, dtoDefinitions),
    )

    private fun compile(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoSchemaCatalog: LowcodeDtoSchemaCatalog,
    ): LsiLowcodeRoute {
        val route = model.routeConfig ?: LsiLowcodeRoute(
            packageName = model.packageName,
            qualifiedName = model.entityQualifiedName(),
            className = model.entityClassName(),
            displayName = model.name,
            description = model.name,
            path = "/${model.modelCode.camelToKebab()}",
            enabledOperations = DEFAULT_ROUTE_OPERATIONS,
            properties = emptyList(),
            featurePackageName = model.featurePackageName,
        )
        return compile(model, route, modelCatalog, dtoSchemaCatalog)
    }

    fun compile(
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(model),
    ): LsiLowcodeRoute = compile(model, route, modelCatalog, emptyList())

    fun compile(
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): LsiLowcodeRoute = compile(
        model = model,
        route = route,
        modelCatalog = modelCatalog,
        dtoSchemaCatalog = LowcodeDtoSchemaCatalog(modelCatalog, dtoDefinitions),
    )

    private fun compile(
        model: LowcodeModelMeta,
        route: LsiLowcodeRoute,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoSchemaCatalog: LowcodeDtoSchemaCatalog,
    ): LsiLowcodeRoute {
        val properties = model.buildRouteProperties(modelCatalog)
        val discriminatorMapping = model.inheritanceDiscriminatorMapping(modelCatalog)
        val discriminator = model.entityConfig.inheritanceRoot
            ?.takeIf { discriminatorMapping.isNotEmpty() }
            ?.let { root ->
                LsiLowcodeDiscriminator(
                    propertyName = root.discriminatorField,
                    mapping = discriminatorMapping.mapValues { (_, subtype) ->
                        LsiLowcodeDtoRef(subtype.modelCode)
                    },
                )
            }
        val subtypeSchemas = discriminatorMapping.values.map { subtype ->
            subtype.toLsiEntitySchema(modelCatalog)
        }
        val availablePaths = (properties.map(LsiLowcodeProperty::name) +
            subtypeSchemas.flatMap { schema -> schema.properties.keys }).flatMapTo(linkedSetOf()) { propertyName ->
            listOfNotNull(
                propertyName,
                propertyName.removeSuffix("Id").takeIf { propertyName.endsWith("Id") },
            )
        }
        val duplicateOrderProperties = route.defaultOrders
            .groupingBy(LsiLowcodeOrder::propertyName)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateOrderProperties.isEmpty()) {
            "默认排序字段重复: ${duplicateOrderProperties.sorted().joinToString()}"
        }
        val unknownOrderProperties = route.defaultOrders
            .map(LsiLowcodeOrder::propertyName)
            .filterNot(availablePaths::contains)
        require(unknownOrderProperties.isEmpty()) {
            "默认排序字段不存在: ${unknownOrderProperties.distinct().sorted().joinToString()}"
        }
        return route.copy(
            packageName = model.packageName,
            qualifiedName = model.entityQualifiedName(),
            className = model.entityClassName(),
            modelCode = model.modelCode,
            displayName = model.name,
            fetchPaths = route.fetchPaths.filter { path -> path.substringBefore('.') in availablePaths }.distinct(),
            excludePaths = route.excludePaths.filter { path -> path.substringBefore('.') in availablePaths }.distinct(),
            properties = properties,
            queryFields = if (model.queries.isEmpty()) {
                route.queryFields
            } else {
                LowcodeQueryCompiler.compile(model, modelCatalog)
            },
            dtoSchemas = route.customOperations.resolveReferencedDtoSchemas(
                catalog = dtoSchemaCatalog,
                existingSchemas = route.dtoSchemas + subtypeSchemas,
            ),
            discriminator = discriminator,
            featurePackageName = model.featurePackageName,
        )
    }

    private fun LsiLowcodeRoute.withReferencedDtoSchemas(
        dtoSchemaCatalog: LowcodeDtoSchemaCatalog,
    ): LsiLowcodeRoute = copy(
        dtoSchemas = customOperations.resolveReferencedDtoSchemas(
            catalog = dtoSchemaCatalog,
            existingSchemas = dtoSchemas,
        ),
    )

    private fun LowcodeModelMeta.buildRouteProperties(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): List<LsiLowcodeProperty> = inheritanceLineage(modelCatalog)
        .flatMap { model -> model.buildOwnRouteProperties(modelCatalog) }
        .distinctBy(LsiLowcodeProperty::name)

    private fun LowcodeModelMeta.buildOwnRouteProperties(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): List<LsiLowcodeProperty> = buildList {
        if (kind == LowcodeModelKind.ENTITY) {
            entityConfig.resolvedInheritedProperties(
                includeConventionDefault = entityConfig.inheritanceSubtype == null,
            ).forEach { property ->
                val schema = property.kotlinType.toOpenApiType()
                add(
                    LsiLowcodeProperty(
                        name = property.name,
                        type = schema.type,
                        format = schema.format,
                        required = property.required,
                        identifier = property.id,
                        createWritable = property.createWritable,
                        updateWritable = property.updateWritable,
                        arrayItemType = schema.arrayItemType,
                        description = property.description,
                        dictionaryCode = property.dictionaryCode,
                        maxLength = property.maxLength,
                    ),
                )
            }
        }
        fields.sortedBy(LowcodeFieldMeta::orderNo).forEach { field ->
            val schema = field.kotlinType.toOpenApiType(
                field.enumStorage,
                discriminatorEnumValues(field, modelCatalog),
            )
            add(
                LsiLowcodeProperty(
                    name = field.fieldCode,
                    type = schema.type,
                    format = schema.format,
                    required = field.required,
                    arrayItemType = schema.arrayItemType,
                    description = field.remark ?: field.label,
                    dictionaryCode = field.dictCode,
                    enumValues = schema.enumValues,
                    maxLength = field.maxLength,
                    createWritable = field.createWritable,
                    updateWritable = field.updateWritable,
                ),
            )
        }
        entityConfig.formulaProperties.sortedBy(LsiLowcodeFormulaProperty::propertyCode).forEach { property ->
            val schema = property.kotlinType.toOpenApiType()
            add(
                LsiLowcodeProperty(
                    name = property.propertyCode,
                    type = schema.type,
                    format = schema.format,
                    required = !property.nullable,
                    arrayItemType = schema.arrayItemType,
                    description = property.description ?: property.label,
                    createWritable = false,
                    updateWritable = false,
                ),
            )
        }
        entityConfig.transientProperties.sortedBy(LsiLowcodeTransientProperty::propertyCode).forEach { property ->
            val schema = property.kotlinType.toOpenApiType()
            add(
                LsiLowcodeProperty(
                    name = property.propertyCode,
                    type = schema.type,
                    format = schema.format,
                    required = !property.nullable,
                    arrayItemType = schema.arrayItemType,
                    description = property.description ?: property.label,
                    dictionaryCode = property.dictionaryCode,
                    createWritable = false,
                    updateWritable = false,
                ),
            )
        }
        relations.sortedBy(LowcodeRelationMeta::orderNo).forEach { relation ->
            val isList = relation.relationKind == LowcodeRelationKind.ONE_TO_MANY ||
                relation.relationKind == LowcodeRelationKind.MANY_TO_MANY
            add(
                LsiLowcodeProperty(
                    name = relation.relationCode,
                    type = if (isList) "array" else "object",
                    format = null,
                    required = false,
                    arrayItemType = if (isList) "object" else null,
                    description = relation.label,
                    createWritable = relation.createWritable,
                    updateWritable = relation.updateWritable,
                ),
            )
            if (relation.relationKind.isReference()) {
                add(
                    LsiLowcodeProperty(
                        name = "${relation.relationCode}Id",
                        type = "integer",
                        format = "int64",
                        required = relation.required,
                        arrayItemType = null,
                        description = "${relation.label}编号",
                        referenceTargetModelCode = relation.targetModelCode,
                        referencePropertyName = relation.relationCode,
                        createWritable = relation.createWritable,
                        updateWritable = relation.updateWritable,
                    ),
                )
            } else {
                add(
                    LsiLowcodeProperty(
                        name = relation.relationCode.toCollectionIdViewCode(),
                        type = "array",
                        format = null,
                        required = false,
                        arrayItemType = "integer",
                        description = "${relation.label}编号集合",
                        referenceTargetModelCode = relation.targetModelCode,
                        referencePropertyName = relation.relationCode,
                        createWritable = relation.createWritable,
                        updateWritable = relation.updateWritable,
                    ),
                )
            }
        }
    }

    private fun String.camelToKebab(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "\$1-\$2")
            .replace('_', '-')
            .lowercase(Locale.ROOT)

    private val DEFAULT_ROUTE_OPERATIONS = setOf(
        "CREATE",
        "UPSERT",
        "UPDATE",
        "DELETE",
        "DELETE_LIST",
        "GET",
        "PAGE",
        "SIMPLE_LIST",
        "LIST_BY_CONDITION",
    )
}

internal fun String.toCollectionIdViewCode(): String = when {
    endsWith("ies") -> dropLast(3) + "yIds"
    endsWith("s") -> dropLast(1) + "Ids"
    else -> this + "Ids"
}
