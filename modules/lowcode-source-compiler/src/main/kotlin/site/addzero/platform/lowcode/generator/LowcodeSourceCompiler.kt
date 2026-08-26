package site.addzero.platform.lowcode.generator

import java.util.Locale
import site.addzero.dto.compiler.KotlinAnnotationSourceRenderer

/**
 * 生成可继续交给 Jimmer KSP 编译的 Kotlin 源码。
 */
object LowcodeSourceCompiler {
    fun generate(contract: LsiLowcodeContract): List<LowcodeGeneratedFile> =
        LowcodeContractCompiler.compile(contract)

    fun generate(
        contract: LsiLowcodeContract,
        models: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition> = emptyList(),
    ): List<LowcodeGeneratedFile> = LowcodeContractCompiler.compile(contract, models, dtoDefinitions)

    /** 使用模型及独立 DTO 快照补全契约引用的运行时结构。 */
    fun resolveContract(
        contract: LsiLowcodeContract,
        models: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition> = emptyList(),
    ): LsiLowcodeContract = LowcodeContractCompiler.resolve(contract, models, dtoDefinitions)

    fun generate(
        models: Set<LowcodeModelMeta>,
    ): List<LowcodeGeneratedFile> = models
        .sortedBy(LowcodeModelMeta::modelCode)
        .flatMap { model -> generate(model, models) }

    /**
     * 生成供 Jimmer KSP 继续处理的实体源码。
     */
    fun generateEntities(
        models: Collection<LowcodeModelMeta>,
        modelCatalog: Collection<LowcodeModelMeta> = models,
    ): List<LowcodeGeneratedFile> {
        val activeCatalog = modelCatalog.filter { model -> model.status == 1 }
        validateLowcodeInheritance(activeCatalog)
        return models
            .filter { model -> model.status == 1 && model.ownsEntitySource() }
            .sortedBy(LowcodeModelMeta::modelCode)
            .map { model -> generateEntity(model, activeCatalog) }
    }

    /**
     * 生成复杂计算属性的业务 Resolver 约定与 Jimmer 适配器。
     */
    fun generateTransientResolverContracts(
        models: Collection<LowcodeModelMeta>,
    ): List<LowcodeGeneratedFile> {
        val activeCatalog = models.filter { model -> model.status == 1 }
        validateLowcodeInheritance(activeCatalog)
        return activeCatalog
            .filter { model -> model.kind == LowcodeModelKind.ENTITY && model.ownsEntitySource() }
            .sortedBy(LowcodeModelMeta::modelCode)
            .flatMap { model -> generateTransientResolverContracts(model, activeCatalog) }
    }

    /**
     * 为数据库中的实体路由绑定生成自定义操作契约。
     */
    fun generateRouteBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta> = emptyList(),
    ): List<LowcodeGeneratedFile> = generateRouteBindings(bindings, models, emptyList())

    fun generateRouteBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): List<LowcodeGeneratedFile> = resolveRouteBindings(
        bindings = bindings,
        models = models,
        modelsRequiringRoutes = emptyList(),
        dtoDefinitions = dtoDefinitions,
    )
        .flatMap { binding -> generateContracts(binding.route) }

    /** 用模型快照补全路由中的实体类型、属性、查询与 DTO 元数据。 */
    fun resolveRouteBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta> = emptyList(),
        modelsRequiringRoutes: Collection<LowcodeModelMeta> = emptyList(),
    ): List<LowcodeRouteBinding> = resolveRouteBindings(
        bindings = bindings,
        models = models,
        modelsRequiringRoutes = modelsRequiringRoutes,
        dtoDefinitions = emptyList(),
    )

    fun resolveRouteBindings(
        bindings: Collection<LowcodeRouteBinding>,
        models: Collection<LowcodeModelMeta>,
        modelsRequiringRoutes: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): List<LowcodeRouteBinding> =
        LowcodeRouteCompiler.resolveBindings(bindings, models, modelsRequiringRoutes, dtoDefinitions)

    fun generate(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(model),
    ): List<LowcodeGeneratedFile> {
        val activeCatalog = modelCatalog.filter { candidate -> candidate.status == 1 }
        validateLowcodeInheritance(activeCatalog)
        return buildList {
            if (model.ownsEntitySource()) {
                add(generateEntity(model, activeCatalog))
                addAll(generateTransientResolverContracts(model, activeCatalog))
            }
            addAll(LowcodeDtoSourceGenerator.generate(listOf(model), activeCatalog))
            model.routeConfig?.let {
                val route = model.toLsiRoute(activeCatalog)
                addAll(generateContracts(route))
            }
        }
    }

    private fun generateEntity(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): LowcodeGeneratedFile {
        val packageName = generatedPackage(model)
        val imports = sortedSetOf<String>()
        val superTypes = model.resolvedEntitySuperTypes(modelCatalog)
        val inheritanceRoot = model.entityConfig.inheritanceRoot
        val inheritanceSubtype = model.entityConfig.inheritanceSubtype
        val rootModel = model.inheritanceRootModel(modelCatalog)
        val rootInheritance = rootModel?.entityConfig?.inheritanceRoot
        val singleTableSubtype = inheritanceSubtype != null &&
            rootInheritance?.strategy == LowcodeInheritanceStrategy.SINGLE_TABLE
        val typeAnnotations = when (model.kind) {
            LowcodeModelKind.ENTITY -> {
                imports += "org.babyfish.jimmer.sql.Entity"
                val annotations = mutableListOf(buildEntityAnnotation(model, imports))
                if (!singleTableSubtype) {
                    imports += "org.babyfish.jimmer.sql.Table"
                    annotations += "@Table(name = \"${model.tableName.escapeKotlinString()}\")"
                }
                inheritanceRoot?.let { root -> annotations += buildInheritanceAnnotations(root, imports) }
                val discriminatorValue = inheritanceRoot?.discriminatorValue ?: inheritanceSubtype?.discriminatorValue
                discriminatorValue?.takeIf(String::isNotBlank)?.let { value ->
                    imports += "org.babyfish.jimmer.sql.DiscriminatorValue"
                    annotations += "@DiscriminatorValue(\"${value.escapeKotlinString()}\")"
                }
                annotations
            }
            LowcodeModelKind.MAPPED_SUPERCLASS -> {
                imports += "org.babyfish.jimmer.sql.MappedSuperclass"
                listOf("@MappedSuperclass")
            }
            LowcodeModelKind.EMBEDDABLE -> {
                imports += "org.babyfish.jimmer.sql.Embeddable"
                listOf("@Embeddable")
            }
        }
        if (model.fields.isNotEmpty()) {
            imports += "org.babyfish.jimmer.sql.Column"
        }
        val fieldCodes = model.fields.map(LowcodeFieldMeta::fieldCode).toSet()
        val unknownAnnotationFields = model.entityConfig.fieldAnnotations.keys - fieldCodes
        require(unknownAnnotationFields.isEmpty()) {
            "模型 ${model.modelCode} 的字段注解引用了不存在的字段: ${unknownAnnotationFields.sorted().joinToString()}"
        }
        val relationsByCode = model.relations.associateBy(LowcodeRelationMeta::relationCode)
        require(model.entityConfig.inheritedRelationCodes.isEmpty() || model.entityConfig.superTypes.isNotEmpty()) {
            "模型 ${model.modelCode} 的继承关系要求配置自定义父类型"
        }
        val unknownInheritedRelations = model.entityConfig.inheritedRelationCodes - relationsByCode.keys
        require(unknownInheritedRelations.isEmpty()) {
            "模型 ${model.modelCode} 的继承关系引用了不存在的关系: ${unknownInheritedRelations.sorted().joinToString()}"
        }
        val declaredRelations = model.relations.filterNot { relation ->
            relation.relationCode in model.entityConfig.inheritedRelationCodes
        }
        val unknownOrderedRelations = model.entityConfig.relationOrderings.keys - relationsByCode.keys
        require(unknownOrderedRelations.isEmpty()) {
            "模型 ${model.modelCode} 的关系排序引用了不存在的关系: ${unknownOrderedRelations.sorted().joinToString()}"
        }
        model.entityConfig.relationOrderings.forEach { (relationCode, properties) ->
            val relation = relationsByCode.getValue(relationCode)
            require(!relation.relationKind.isReference()) {
                "模型 ${model.modelCode} 的引用关系 $relationCode 不能配置集合排序"
            }
            require(properties.isNotEmpty() && properties.all(String::isNotBlank)) {
                "模型 ${model.modelCode} 的关系 $relationCode 必须配置非空排序属性"
            }
            require(properties.distinct().size == properties.size) {
                "模型 ${model.modelCode} 的关系 $relationCode 存在重复排序属性"
            }
        }
        model.entityConfig.fieldAnnotations.values.flatten().forEach { annotation ->
            KotlinAnnotationSourceRenderer.validate(annotation, "模型 ${model.modelCode}")
            imports += KotlinAnnotationSourceRenderer.referencedClassifiers(annotation)
        }
        if (inheritanceRoot != null && model.fields.any { field -> field.fieldCode == inheritanceRoot.discriminatorField }) {
            imports += "org.babyfish.jimmer.sql.Discriminator"
        }
        if (model.fields.any(LowcodeFieldMeta::serialized)) {
            imports += "org.babyfish.jimmer.sql.Serialized"
        }
        if (model.fields.any(LowcodeFieldMeta::key)) {
            imports += "org.babyfish.jimmer.sql.Key"
        }
        if (model.fields.any { field -> !field.defaultValue.isNullOrBlank() }) {
            imports += "org.babyfish.jimmer.sql.Default"
        }
        if (model.entityConfig.formulaProperties.isNotEmpty()) {
            imports += "org.babyfish.jimmer.Formula"
        }
        if (model.entityConfig.transientProperties.isNotEmpty()) {
            imports += "org.babyfish.jimmer.sql.Transient"
        }
        model.entityConfig.transientProperties
            .filter { property -> property.kind == LowcodeTransientKind.RESOLVER }
            .forEach { property -> imports += transientResolverAdapterQualifiedName(model, property) }
        val renderedSuperTypes = superTypes.map(::kotlinType)
        renderedSuperTypes.forEach { type -> imports += type.imports }
        if (model.fields.any { field -> !field.dictCode.isNullOrBlank() } ||
            model.entityConfig.transientProperties.any { property -> !property.dictionaryCode.isNullOrBlank() }
        ) {
            imports += DICT_ANNOTATION
        }
        model.fields.map { field -> kotlinType(field.kotlinType) }.forEach { type -> imports += type.imports }
        model.entityConfig.formulaProperties
            .map { property -> kotlinType(property.kotlinType) }
            .forEach { type -> imports += type.imports }
        model.entityConfig.transientProperties
            .map { property -> kotlinType(property.kotlinType) }
            .forEach { type -> imports += type.imports }
        declaredRelations.forEach { relation ->
            imports += relationImport(relation.relationKind)
            if (model.entityConfig.relationOrderings[relation.relationCode].orEmpty().isNotEmpty()) {
                imports += "org.babyfish.jimmer.sql.OrderedProp"
            }
            if (relation.dissociateAction != LowcodeDissociateAction.NONE) {
                imports += "org.babyfish.jimmer.sql.DissociateAction"
                imports += "org.babyfish.jimmer.sql.OnDissociate"
            }
            if (!relation.joinColumn.isNullOrBlank()) {
                imports += "org.babyfish.jimmer.sql.ForeignKeyType"
                imports += "org.babyfish.jimmer.sql.JoinColumn"
            }
            if (!relation.joinTable.isNullOrBlank()) {
                imports += "org.babyfish.jimmer.sql.JoinTable"
            }
            val targetModel = modelCatalog.firstOrNull { candidate ->
                candidate.modelCode == relation.targetModelCode
            } ?: modelCatalog.firstOrNull { candidate ->
                candidate.packageName == relation.targetPackageName &&
                    candidate.className == relation.targetClassName
            }
            val targetPackage = when {
                targetModel == null -> {
                    val targetFeaturePackage = relation.targetPackageName
                        ?.takeIf(String::isNotBlank)
                        ?: model.packageName
                    targetFeaturePackage.generatedLayout().packageName(LowcodeGeneratedResourceKind.ENTITY)
                }
                else -> targetModel.entityPackageName()
            }
            val targetClass = targetModel?.entityClassName() ?: relation.targetClassName ?: "UnknownTarget"
            imports += "$targetPackage.$targetClass"
        }
        val properties = buildList {
            addAll(
                model.fields.sortedBy(LowcodeFieldMeta::orderNo).map { field ->
                    buildField(
                        field = field,
                        annotations = model.entityConfig.fieldAnnotations[field.fieldCode].orEmpty(),
                        packageName = packageName,
                        discriminator = field.fieldCode == inheritanceRoot?.discriminatorField,
                    )
                },
            )
            addAll(model.entityConfig.formulaProperties.sortedBy(LsiLowcodeFormulaProperty::propertyCode).map(::buildFormula))
            addAll(
                model.entityConfig.transientProperties
                    .sortedBy(LsiLowcodeTransientProperty::propertyCode)
                    .map { property -> buildTransient(model, property) },
            )
            addAll(
                declaredRelations.sortedBy(LowcodeRelationMeta::orderNo)
                    .map { relation ->
                        buildRelation(
                            relation = relation,
                            orderedProperties = model.entityConfig.relationOrderings[relation.relationCode].orEmpty(),
                        )
                    },
            )
        }
        val content = buildString {
            appendLine("package $packageName")
            appendLine()
            imports.forEach { value -> appendLine("import $value") }
            appendLine()
            appendLine("/**")
            appendLine(" * ${model.name.escapeKDoc()}")
            appendLine(" */")
            typeAnnotations.forEach(::appendLine)
            val superTypeClause = if (renderedSuperTypes.isEmpty()) {
                ""
            } else {
                renderedSuperTypes.joinToString(", ", prefix = " : ", transform = KotlinType::name)
            }
            appendLine("interface ${model.className}$superTypeClause {")
            if (properties.isNotEmpty()) {
                appendLine(properties.joinToString("\n\n"))
            }
            appendLine("}")
        }
        return kotlinFile(
            packageName = packageName,
            fileName = model.className,
            content = content,
            kind = LowcodeGeneratedResourceKind.ENTITY,
            featurePackage = model.featurePackageName,
        )
    }

    private fun buildEntityAnnotation(
        model: LowcodeModelMeta,
        imports: MutableSet<String>,
    ): String {
        val instantiability = model.entityConfig.inheritanceRoot?.instantiability
            ?: model.entityConfig.inheritanceSubtype?.instantiability
            ?: LowcodeEntityInstantiability.AUTO
        val arguments = buildList {
            model.entityConfig.microServiceName?.takeIf(String::isNotBlank)?.let { value ->
                add("microServiceName = \"${value.escapeKotlinString()}\"")
            }
            if (instantiability != LowcodeEntityInstantiability.AUTO) {
                imports += "org.babyfish.jimmer.sql.EntityInstantiability"
                add("instantiability = EntityInstantiability.${instantiability.name}")
            }
        }
        return if (arguments.isEmpty()) "@Entity" else "@Entity(${arguments.joinToString()})"
    }

    private fun buildInheritanceAnnotations(
        root: LsiLowcodeInheritanceRoot,
        imports: MutableSet<String>,
    ): String {
        imports += "org.babyfish.jimmer.sql.Inheritance"
        imports += "org.babyfish.jimmer.sql.InheritanceType"
        val arguments = buildList {
            add("strategy = InheritanceType.${root.strategy.name}")
            if (root.joinedTableDissociateAction != LowcodeJoinedTableDissociateAction.DELETE) {
                imports += "org.babyfish.jimmer.sql.JoinedTableDissociateAction"
                add(
                    "joinedTableDissociateAction = " +
                        "JoinedTableDissociateAction.${root.joinedTableDissociateAction.name}",
                )
            }
        }
        return "@Inheritance(${arguments.joinToString()})"
    }

    private fun generateContracts(route: LsiLowcodeRoute): List<LowcodeGeneratedFile> =
        LowcodeRouteSourceGenerator.generateContracts(route).map { source ->
            kotlinFile(
                packageName = source.packageName,
                fileName = source.fileName,
                content = source.content,
                kind = LowcodeGeneratedResourceKind.CONTROLLER,
                featurePackage = route.featurePackageName,
            )
        }

    private fun buildField(
        field: LowcodeFieldMeta,
        annotations: List<site.addzero.dto.compiler.LsiDtoAnnotation>,
        packageName: String,
        discriminator: Boolean = false,
    ): String = buildList {
        add("    /**")
        add("     * ${field.label.escapeKDoc()}")
        add("     */")
        field.dictCode?.takeIf(String::isNotBlank)?.let { dictCode ->
            add("    @get:Dict(\"${dictCode.escapeKotlinString()}\")")
        }
        if (field.serialized) {
            add("    @Serialized")
        }
        if (field.key) {
            add("    @Key")
        }
        if (discriminator) {
            add("    @Discriminator")
        }
        field.defaultValue?.takeIf(String::isNotBlank)?.let { defaultValue ->
            add("    @Default(\"${defaultValue.escapeKotlinString()}\")")
        }
        annotations.forEach { annotation ->
            add("    ${KotlinAnnotationSourceRenderer.render(annotation, packageName)}")
        }
        add("    @Column(name = \"${field.dbColumn.escapeKotlinString()}\")")
        val nullableSuffix = if (field.required) "" else "?"
        add("    val ${field.fieldCode.escapeIdentifier()}: ${kotlinType(field.kotlinType).name}$nullableSuffix")
    }.joinToString("\n")

    private fun buildFormula(property: LsiLowcodeFormulaProperty): String = buildList {
        add("    /**")
        add("     * ${property.label.escapeKDoc()}")
        add("     */")
        when (property.kind) {
            LowcodeFormulaKind.KOTLIN -> {
                val dependencies = property.dependencies
                    .sorted()
                    .joinToString(", ") { dependency -> "\"${dependency.escapeKotlinString()}\"" }
                add("    @Formula(dependencies = [$dependencies])")
            }
            LowcodeFormulaKind.SQL -> {
                add("    @Formula(sql = \"${property.expression.escapeKotlinString()}\")")
            }
        }
        val nullableSuffix = if (property.nullable) "?" else ""
        add("    val ${property.propertyCode.escapeIdentifier()}: ${kotlinType(property.kotlinType).name}$nullableSuffix")
        if (property.kind == LowcodeFormulaKind.KOTLIN) {
            add("        get() = ${property.expression}")
        }
    }.joinToString("\n")

    private fun buildTransient(
        model: LowcodeModelMeta,
        property: LsiLowcodeTransientProperty,
    ): String = buildList {
        add("    /**")
        add("     * ${property.label.escapeKDoc()}")
        add("     */")
        property.dictionaryCode?.takeIf(String::isNotBlank)?.let { dictionaryCode ->
            add("    @get:Dict(\"${dictionaryCode.escapeKotlinString()}\")")
        }
        val annotation = when (property.kind) {
            LowcodeTransientKind.DRAFT -> "    @Transient"
            LowcodeTransientKind.RESOLVER ->
                "    @Transient(${transientResolverAdapterName(model, property)}::class)"
        }
        add(annotation)
        val nullableSuffix = if (property.nullable) "?" else ""
        add("    val ${property.propertyCode.escapeIdentifier()}: ${kotlinType(property.kotlinType).name}$nullableSuffix")
    }.joinToString("\n")

    private fun generateTransientResolverContracts(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): List<LowcodeGeneratedFile> =
        model.entityConfig.transientProperties
            .filter { property -> property.kind == LowcodeTransientKind.RESOLVER }
            .sortedBy(LsiLowcodeTransientProperty::propertyCode)
            .map { property -> generateTransientResolverContract(model, property, modelCatalog) }

    private fun generateTransientResolverContract(
        model: LowcodeModelMeta,
        property: LsiLowcodeTransientProperty,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): LowcodeGeneratedFile {
        val packageName = transientResolverPackage(model)
        val contractName = transientResolverContractName(model, property)
        val adapterName = transientResolverAdapterName(model, property)
        val idType = resolverSourceIdType(model, modelCatalog)
        val valueType = resolverValueType(property)
        val imports = sortedSetOf(
            "org.babyfish.jimmer.sql.kt.KTransientResolver",
            "org.koin.core.annotation.Single",
        ).apply {
            addAll(idType.imports)
            addAll(valueType.imports)
        }
        val importsSource = imports.joinToString("\n            ") { importName -> "import $importName" }
        val content = """
            package $packageName

            $importsSource

            /**
             * ${model.name.escapeKDoc()}.${property.label.escapeKDoc()} 复杂计算约定。
             *
             * 在手写源码中实现此接口，并使用 `@Single` 注册。
             */
            interface $contractName : KTransientResolver<${idType.name}, ${valueType.name}>

            /** 把业务 Resolver 约定适配为 Jimmer 实体注解可引用的具体类型。 */
            @Single
            class $adapterName(
                delegate: $contractName,
            ) : KTransientResolver<${idType.name}, ${valueType.name}> by delegate
        """.trimIndent() + "\n"
        return kotlinFile(
            packageName = packageName,
            fileName = contractName,
            content = content,
            kind = LowcodeGeneratedResourceKind.SERVICE,
            featurePackage = model.featurePackageName,
        )
    }

    private fun resolverSourceIdType(
        model: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): KotlinType {
        val configuredType = requireNotNull(
            model.inheritanceLineage(modelCatalog)
                .flatMap { lineageModel ->
                    lineageModel.entityConfig.resolvedInheritedProperties(
                        includeConventionDefault = lineageModel.entityConfig.inheritanceSubtype == null,
                    )
                }
                .distinctBy(LsiLowcodeInheritedProperty::name)
                .singleOrNull(LsiLowcodeInheritedProperty::id),
        ) { "复杂计算属性要求实体 ${model.modelCode} 的基模型声明唯一主键" }.kotlinType
        return kotlinType(configuredType.trim().removeSuffix("?"))
    }

    private fun resolverValueType(property: LsiLowcodeTransientProperty): KotlinType {
        val configuredType = property.resolverValueType?.trim()?.takeIf(String::isNotEmpty)
        if (configuredType != null) {
            return kotlinType(configuredType)
        }
        val propertyType = property.kotlinType.trim().removeSuffix("?")
        return kotlinType(propertyType + if (property.nullable) "?" else "")
    }

    private fun buildRelation(
        relation: LowcodeRelationMeta,
        orderedProperties: List<String>,
    ): String = buildList {
        add("    /**")
        add("     * ${relation.label.escapeKDoc()}")
        add("     */")
        val mappedBy = relation.mappedBy?.takeIf(String::isNotBlank)
        val relationName = relationAnnotationName(relation.relationKind)
        val fakeForeignKey = relation.joinColumn?.isNotBlank() == true
        val relationArguments = buildList {
            if (mappedBy != null) {
                add("mappedBy = \"${mappedBy.escapeKotlinString()}\"")
            }
            if (fakeForeignKey && relation.required && relation.relationKind.isReference()) {
                add("inputNotNull = true")
            }
            if (orderedProperties.isNotEmpty()) {
                val orderedProps = orderedProperties.joinToString(", ") { property ->
                    "OrderedProp(\"${property.escapeKotlinString()}\")"
                }
                add("orderedProps = [$orderedProps]")
            }
        }
        val relationArgumentsSource = relationArguments
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(prefix = "(", postfix = ")")
            .orEmpty()
        add("    @$relationName$relationArgumentsSource")
        relation.joinColumn?.takeIf(String::isNotBlank)?.let { joinColumn ->
            add("    @JoinColumn(name = \"${joinColumn.escapeKotlinString()}\", foreignKeyType = ForeignKeyType.FAKE)")
        }
        if (relation.dissociateAction != LowcodeDissociateAction.NONE) {
            add("    @OnDissociate(DissociateAction.${relation.dissociateAction.name})")
        }
        relation.joinTable?.takeIf(String::isNotBlank)?.let { joinTable ->
            val joinColumn = relation.joinTableJoinColumn ?: "${camelToSnake(relation.relationCode)}_id"
            val inverseColumn = relation.joinTableInverseColumn ?: "${camelToSnake(relation.targetClassName ?: "target")}_id"
            val filter = relation.joinTableFilterColumn?.takeIf(String::isNotBlank)
                ?.takeIf { relation.joinTableFilterValues.isNotEmpty() }
                ?.let { column ->
                    val values = relation.joinTableFilterValues.joinToString(", ") { value ->
                        "\"${value.escapeKotlinString()}\""
                    }
                    ", filter = JoinTable.JoinTableFilter(" +
                        "columnName = \"${column.escapeKotlinString()}\", values = [$values])"
                }
                .orEmpty()
            add(
                "    @JoinTable(name = \"${joinTable.escapeKotlinString()}\", " +
                    "joinColumnName = \"${joinColumn.escapeKotlinString()}\", " +
                    "inverseJoinColumnName = \"${inverseColumn.escapeKotlinString()}\"$filter)",
            )
        }
        val targetClass = relation.targetClassName ?: "UnknownTarget"
        val targetType = when (relation.relationKind) {
            LowcodeRelationKind.ONE_TO_MANY,
            LowcodeRelationKind.MANY_TO_MANY,
            -> "List<$targetClass>"
            LowcodeRelationKind.MANY_TO_ONE,
            LowcodeRelationKind.ONE_TO_ONE,
            -> if (fakeForeignKey || !relation.required) "$targetClass?" else targetClass
        }
        add("    val ${relation.relationCode.escapeIdentifier()}: $targetType")
    }.joinToString("\n")

    fun LowcodeModelMeta.toLsiRoute(
        modelCatalog: Collection<LowcodeModelMeta> = listOf(this),
    ): LsiLowcodeRoute = toLsiRoute(modelCatalog, emptyList())

    fun LowcodeModelMeta.toLsiRoute(
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): LsiLowcodeRoute = LowcodeRouteCompiler.compile(this, modelCatalog, dtoDefinitions)

    fun LowcodeModelMeta.toLsiRoute(
        route: LsiLowcodeRoute,
        modelCatalog: Collection<LowcodeModelMeta> = listOf(this),
    ): LsiLowcodeRoute = toLsiRoute(route, modelCatalog, emptyList())

    fun LowcodeModelMeta.toLsiRoute(
        route: LsiLowcodeRoute,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    ): LsiLowcodeRoute = LowcodeRouteCompiler.compile(this, route, modelCatalog, dtoDefinitions)

    private fun kotlinFile(
        packageName: String,
        fileName: String,
        content: String,
        kind: LowcodeGeneratedResourceKind,
        featurePackage: String,
    ): LowcodeGeneratedFile =
        LowcodeGeneratedFile(
            packageName = packageName,
            fileName = fileName,
            relativePath = featurePackage.generatedLayout().relativeSourcePath(kind, fileName),
            content = generatedByStudio(content),
            kind = if (kind == LowcodeGeneratedResourceKind.SERVICE) {
                LowcodeGeneratedFileKind.COMPILED_SOURCE
            } else {
                LowcodeGeneratedFileKind.SOURCE
            },
        )

    private fun generatedPackage(model: LowcodeModelMeta): String =
        model.featurePackageName.generatedLayout().packageName(LowcodeGeneratedResourceKind.ENTITY)

    private fun transientResolverPackage(model: LowcodeModelMeta): String =
        model.featurePackageName.generatedLayout().packageName(LowcodeGeneratedResourceKind.SERVICE)

    private fun transientResolverContractName(
        model: LowcodeModelMeta,
        property: LsiLowcodeTransientProperty,
    ): String = "${model.className}${property.propertyCode.toGeneratedTypeSegment()}Resolver"

    private fun transientResolverAdapterName(
        model: LowcodeModelMeta,
        property: LsiLowcodeTransientProperty,
    ): String = "${transientResolverContractName(model, property)}Adapter"

    private fun transientResolverAdapterQualifiedName(
        model: LowcodeModelMeta,
        property: LsiLowcodeTransientProperty,
    ): String = "${transientResolverPackage(model)}.${transientResolverAdapterName(model, property)}"

    private fun String.toGeneratedTypeSegment(): String = split(Regex("[^A-Za-z0-9]+"))
        .filter(String::isNotEmpty)
        .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

    private fun relationImport(kind: LowcodeRelationKind): String =
        "org.babyfish.jimmer.sql.${relationAnnotationName(kind)}"

    private fun relationAnnotationName(kind: LowcodeRelationKind): String = when (kind) {
        LowcodeRelationKind.MANY_TO_ONE -> "ManyToOne"
        LowcodeRelationKind.ONE_TO_MANY -> "OneToMany"
        LowcodeRelationKind.ONE_TO_ONE -> "OneToOne"
        LowcodeRelationKind.MANY_TO_MANY -> "ManyToMany"
    }

    private fun LowcodeFieldMeta.normalizedType(): String = kotlinType.trim().lowercase(Locale.ROOT)

    private fun kotlinType(value: String): KotlinType {
        val normalized = value.trim()
        return when (normalized.lowercase(Locale.ROOT)) {
            "string", "text" -> KotlinType("String")
            "long" -> KotlinType("Long")
            "int", "integer" -> KotlinType("Int")
            "double" -> KotlinType("Double")
            "boolean", "bool" -> KotlinType("Boolean")
            "bigdecimal", "decimal" -> KotlinType("BigDecimal", setOf("java.math.BigDecimal"))
            "localdate" -> KotlinType("LocalDate", setOf("java.time.LocalDate"))
            "localdatetime" -> KotlinType("LocalDateTime", setOf("java.time.LocalDateTime"))
            else -> {
                val imports = QUALIFIED_TYPE_PATTERN.findAll(normalized)
                    .map { match -> match.value }
                    .toSet()
                val rendered = QUALIFIED_TYPE_PATTERN.replace(normalized) { match ->
                    match.value.substringAfterLast('.')
                }
                KotlinType(rendered.ifBlank { "String" }, imports)
            }
        }
    }

    private fun camelToSnake(value: String): String = value
        .replace(Regex("([a-z0-9])([A-Z])"), "\$1_\$2")
        .replace('-', '_')
        .lowercase(Locale.ROOT)

    private fun String.escapeKotlinString(): String = buildString {
        this@escapeKotlinString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun String.escapeKDoc(): String = replace("*/", "* /")

    private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

    private data class KotlinType(
        val name: String,
        val imports: Set<String> = emptySet(),
    )

    private val QUALIFIED_TYPE_PATTERN = Regex("(?:[a-z_][A-Za-z0-9_]*\\.)+[A-Z][A-Za-z0-9_]*")

    private val DICT_ANNOTATION = generationTargetSymbol(GenerationTargetSymbols.DICTIONARY_ANNOTATION)
    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
        "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",
    )
}
