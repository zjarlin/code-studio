package site.addzero.platform.lowcode.generator

/** 返回从表继承根模型到当前模型的完整继承链。 */
fun LowcodeModelMeta.inheritanceLineage(
    modelCatalog: Collection<LowcodeModelMeta>,
): List<LowcodeModelMeta> {
    val modelsByCode = modelCatalog.associateBy(LowcodeModelMeta::modelCode)
    val lineage = mutableListOf(this)
    val visited = linkedSetOf(modelCode)
    var current = this
    while (true) {
        val parentCode = current.entityConfig.inheritanceSubtype?.parentModelCode ?: break
        check(visited.add(parentCode)) {
            "表继承存在循环: ${(lineage.map(LowcodeModelMeta::modelCode) + parentCode).joinToString(" -> ")}"
        }
        val parent = checkNotNull(modelsByCode[parentCode]) {
            "表继承父模型不存在: ${current.modelCode} -> $parentCode"
        }
        lineage += parent
        current = parent
    }
    return lineage.asReversed()
}

/** 返回当前模型所属的表继承根模型。 */
fun LowcodeModelMeta.inheritanceRootModel(
    modelCatalog: Collection<LowcodeModelMeta>,
): LowcodeModelMeta? {
    if (entityConfig.inheritanceRoot != null) {
        return this
    }
    if (entityConfig.inheritanceSubtype == null) {
        return null
    }
    return inheritanceLineage(modelCatalog).first().takeIf { model -> model.entityConfig.inheritanceRoot != null }
}

/** 返回当前继承层次中可实例化类型的判别值映射。 */
fun LowcodeModelMeta.inheritanceDiscriminatorMapping(
    modelCatalog: Collection<LowcodeModelMeta>,
): Map<String, LowcodeModelMeta> {
    val root = inheritanceRootModel(modelCatalog) ?: return emptyMap()
    return buildMap {
        root.entityConfig.inheritanceRoot
            ?.takeUnless { inheritance -> inheritance.instantiability == LowcodeEntityInstantiability.ABSTRACT }
            ?.discriminatorValue
            ?.takeIf(String::isNotBlank)
            ?.let { value -> put(value, root) }
        modelCatalog
            .filter { model -> model.inheritanceRootModel(modelCatalog)?.modelCode == root.modelCode }
            .sortedBy(LowcodeModelMeta::modelCode)
            .forEach { model ->
                model.entityConfig.inheritanceSubtype
                    ?.takeUnless { inheritance -> inheritance.instantiability == LowcodeEntityInstantiability.ABSTRACT }
                    ?.discriminatorValue
                    ?.takeIf(String::isNotBlank)
                    ?.let { value -> put(value, model) }
            }
    }
}

fun LowcodeModelMeta.discriminatorEnumValues(
    field: LowcodeFieldMeta,
    modelCatalog: Collection<LowcodeModelMeta>,
): List<String> {
    if (field.enumStorage != LowcodeEnumStorage.NAME) return emptyList()
    val root = inheritanceRootModel(modelCatalog) ?: return emptyList()
    val discriminatorField = root.entityConfig.inheritanceRoot?.discriminatorField ?: return emptyList()
    return if (field.fieldCode == discriminatorField) {
        root.inheritanceDiscriminatorMapping(modelCatalog).keys.sorted()
    } else {
        emptyList()
    }
}

/** 解析实体生成时的直接父类型。 */
fun LowcodeModelMeta.resolvedEntitySuperTypes(
    modelCatalog: Collection<LowcodeModelMeta>,
): List<String> {
    val subtype = entityConfig.inheritanceSubtype
    if (subtype == null) {
        return entityConfig.resolvedSuperTypes(includeConventionDefault = kind == LowcodeModelKind.ENTITY)
    }
    val parent = inheritanceLineage(modelCatalog).dropLast(1).last()
    return listOf(parent.entityQualifiedName()) + entityConfig.resolvedSuperTypes(includeConventionDefault = false)
}

/** 校验所有已启用模型的 Jimmer 表继承契约。 */
fun validateLowcodeInheritance(models: Collection<LowcodeModelMeta>) {
    val modelsByCode = models.associateBy(LowcodeModelMeta::modelCode)
    models.forEach { model -> validateInheritanceRole(model, modelsByCode) }
    models.filter { model -> model.entityConfig.inheritanceSubtype != null }.forEach { model ->
        val lineage = model.inheritanceLineage(models)
        check(lineage.first().entityConfig.inheritanceRoot != null) {
            "表继承子类型 ${model.modelCode} 的继承链未指向继承根模型"
        }
    }
    models.filter { model -> model.entityConfig.inheritanceRoot != null }.forEach { root ->
        validateDiscriminatorValues(root, models)
    }
}

private fun validateInheritanceRole(
    model: LowcodeModelMeta,
    modelsByCode: Map<String, LowcodeModelMeta>,
) {
    val root = model.entityConfig.inheritanceRoot
    val subtype = model.entityConfig.inheritanceSubtype
    check(root == null || subtype == null) {
        "模型 ${model.modelCode} 不能同时是表继承根模型和子类型"
    }
    if (root == null && subtype == null) {
        return
    }
    check(model.kind == LowcodeModelKind.ENTITY) {
        "只有 ENTITY 模型可以配置表继承: ${model.modelCode}"
    }
    check(model.entityConfig.sourceMode == LowcodeEntitySourceMode.GENERATED) {
        "表继承模型必须由平台生成实体源码: ${model.modelCode}"
    }
    if (root != null) {
        validateInheritanceRoot(model, root)
    }
    if (subtype != null) {
        validateInheritanceSubtype(model, subtype, modelsByCode)
    }
}

private fun validateInheritanceRoot(
    model: LowcodeModelMeta,
    root: LsiLowcodeInheritanceRoot,
) {
    val discriminator = model.fields.singleOrNull { field -> field.fieldCode == root.discriminatorField }
    val inheritedDiscriminator = model.entityConfig.resolvedInheritedProperties()
        .singleOrNull { property -> property.name == root.discriminatorField }
    check(discriminator != null || inheritedDiscriminator != null) {
        "表继承根模型 ${model.modelCode} 缺少判别字段 ${root.discriminatorField}"
    }
    check(discriminator == null || inheritedDiscriminator == null) {
        "表继承判别字段 ${model.modelCode}.${root.discriminatorField} 不能在模型和基模型中重复声明"
    }
    val discriminatorRequired = discriminator?.required ?: requireNotNull(inheritedDiscriminator).required
    check(discriminatorRequired && discriminator?.serialized != true) {
        "表继承判别字段 ${model.modelCode}.${root.discriminatorField} 必须是非空标量字段"
    }
    val discriminatorKotlinType = discriminator?.kotlinType ?: requireNotNull(inheritedDiscriminator).kotlinType
    val discriminatorType = discriminatorKotlinType.substringAfterLast('.').lowercase()
    check(
        discriminatorType == "string" ||
            discriminatorType == "text" ||
            discriminator?.enumStorage == LowcodeEnumStorage.NAME,
    ) {
        "表继承判别字段 ${model.modelCode}.${root.discriminatorField} 必须使用字符串或 NAME 枚举"
    }
    check(!discriminatorKotlinType.contains('<')) {
        "表继承判别字段 ${model.modelCode}.${root.discriminatorField} 不能是容器类型"
    }
    check(root.instantiability != LowcodeEntityInstantiability.INSTANTIABLE || !root.discriminatorValue.isNullOrBlank()) {
        "可实例化的表继承根模型 ${model.modelCode} 必须配置判别值"
    }
    check(root.instantiability != LowcodeEntityInstantiability.ABSTRACT || root.discriminatorValue.isNullOrBlank()) {
        "抽象表继承根模型 ${model.modelCode} 不能配置判别值"
    }
    check(
        root.strategy == LowcodeInheritanceStrategy.JOINED ||
            root.joinedTableDissociateAction == LowcodeJoinedTableDissociateAction.DELETE,
    ) {
        "SINGLE_TABLE 继承根模型 ${model.modelCode} 不能配置 JOINED 解关联策略"
    }
}

private fun validateInheritanceSubtype(
    model: LowcodeModelMeta,
    subtype: LsiLowcodeInheritanceSubtype,
    modelsByCode: Map<String, LowcodeModelMeta>,
) {
    check(subtype.parentModelCode != model.modelCode) {
        "表继承子类型 ${model.modelCode} 不能继承自身"
    }
    val parent = checkNotNull(modelsByCode[subtype.parentModelCode]) {
        "表继承父模型不存在: ${model.modelCode} -> ${subtype.parentModelCode}"
    }
    check(parent.kind == LowcodeModelKind.ENTITY) {
        "表继承父模型必须是 ENTITY: ${subtype.parentModelCode}"
    }
    check(parent.contributorId == model.contributorId) {
        "表继承父子模型必须属于同一 contributor: ${model.modelCode}"
    }
    check(model.entityConfig.baseModels.isEmpty() && model.entityConfig.inheritedProperties.none { property -> property.id }) {
        "表继承子类型 ${model.modelCode} 必须从父模型继承主键，不能重复配置基模型或继承主键"
    }
    check(subtype.instantiability == LowcodeEntityInstantiability.ABSTRACT || !subtype.discriminatorValue.isNullOrBlank()) {
        "可实例化的表继承子类型 ${model.modelCode} 必须配置判别值"
    }
    check(subtype.instantiability != LowcodeEntityInstantiability.ABSTRACT || subtype.discriminatorValue.isNullOrBlank()) {
        "抽象表继承子类型 ${model.modelCode} 不能配置判别值"
    }
}

private fun validateDiscriminatorValues(
    root: LowcodeModelMeta,
    models: Collection<LowcodeModelMeta>,
) {
    val hierarchy = models.filter { model -> model.inheritanceRootModel(models)?.modelCode == root.modelCode }
    val values = buildList {
        root.entityConfig.inheritanceRoot?.discriminatorValue?.takeIf(String::isNotBlank)?.let(::add)
        hierarchy.mapNotNullTo(this) { model -> model.entityConfig.inheritanceSubtype?.discriminatorValue?.takeIf(String::isNotBlank) }
    }
    val duplicateValues = values.groupingBy { value -> value }.eachCount().filterValues { count -> count > 1 }.keys
    check(duplicateValues.isEmpty()) {
        "表继承根模型 ${root.modelCode} 的判别值重复: ${duplicateValues.sorted().joinToString()}"
    }
}
