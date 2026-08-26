package site.addzero.platform.lowcode.generator

/** 按引用惰性物化模型 DTO 与功能级 DTO。 */
internal class LowcodeDtoSchemaCatalog(
    private val models: Collection<LowcodeModelMeta>,
    dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
) {
    private val entityModelsByRef = models.associateBy { model ->
        LsiLowcodeDtoRef(modelCode = model.modelCode)
    }
    private val modelDtosByRef = buildMap {
        models.forEach { model ->
            model.dtoDefinitions.forEach { dto ->
                put(LsiLowcodeDtoRef(model.modelCode, dto.dtoCode), ModelDto(model, dto))
            }
        }
    }
    private val standaloneDefinitionsByRef = dtoDefinitions
        .filter { definition ->
            definition.status == 1 &&
                definition.kind != LowcodeDtoKind.STRUCTURE
        }
        .associateBy(LsiLowcodeDtoDefinition::ref)
    private val resolvedSchemas = mutableMapOf<LsiLowcodeDtoRef, LsiLowcodeDtoSchema>()

    fun resolve(ref: LsiLowcodeDtoRef): LsiLowcodeDtoSchema? {
        resolvedSchemas[ref]?.let { schema -> return schema }
        val schema = entityModelsByRef[ref]?.toLsiEntitySchema(models)
            ?: modelDtosByRef[ref]?.toLsiDtoSchema(models)
            ?: standaloneDefinitionsByRef[ref]?.toLsiDtoSchema(models)
            ?: return null
        resolvedSchemas[ref] = schema
        return schema
    }

    private data class ModelDto(
        val model: LowcodeModelMeta,
        val dto: LsiLowcodeDto,
    ) {
        fun toLsiDtoSchema(models: Collection<LowcodeModelMeta>): LsiLowcodeDtoSchema =
            model.copy(dtoDefinitions = listOf(dto)).toLsiDtoSchemas(models).single()
    }
}

/** 解析接口操作与已有结构的 DTO 引用闭包。 */
internal fun Collection<LsiLowcodeCustomOperation>.resolveReferencedDtoSchemas(
    models: Collection<LowcodeModelMeta>,
    dtoDefinitions: Collection<LsiLowcodeDtoDefinition>,
    existingSchemas: Collection<LsiLowcodeDtoSchema> = emptyList(),
): List<LsiLowcodeDtoSchema> = resolveReferencedDtoSchemas(
    catalog = LowcodeDtoSchemaCatalog(models, dtoDefinitions),
    existingSchemas = existingSchemas,
)

/** 使用共享目录解析接口操作与已有结构的 DTO 引用闭包。 */
internal fun Collection<LsiLowcodeCustomOperation>.resolveReferencedDtoSchemas(
    catalog: LowcodeDtoSchemaCatalog,
    existingSchemas: Collection<LsiLowcodeDtoSchema> = emptyList(),
): List<LsiLowcodeDtoSchema> {
    val existingSchemasByRef = existingSchemas.associateBy(LsiLowcodeDtoSchema::ref)
    val roots = openApiReferencedDtoRefs() + existingSchemasByRef.keys
    val pending = ArrayDeque(roots.sortedBy(LsiLowcodeDtoRef::componentSchemaName))
    val resolved = linkedMapOf<LsiLowcodeDtoRef, LsiLowcodeDtoSchema>()
    while (pending.isNotEmpty()) {
        val ref = pending.removeFirst()
        if (ref in resolved) {
            continue
        }
        val schema = existingSchemasByRef[ref] ?: catalog.resolve(ref) ?: continue
        resolved[ref] = schema
        schema.referencedDtoRefs()
            .sortedBy(LsiLowcodeDtoRef::componentSchemaName)
            .forEach(pending::addLast)
    }
    requireOpenApiDtoSchemaClosure(resolved)
    existingSchemas.requireDtoSchemaClosure(resolved)
    return resolved.values.sortedBy(LsiLowcodeDtoSchema::schemaName)
}

private fun Collection<LsiLowcodeCustomOperation>.requireOpenApiDtoSchemaClosure(
    schemasByRef: Map<LsiLowcodeDtoRef, LsiLowcodeDtoSchema>,
) {
    filter { operation -> operation.transport != LowcodeOperationTransport.INTERNAL }
        .sortedBy(LsiLowcodeCustomOperation::operationCode)
        .forEach { operation ->
            requireDtoSchemaClosure(
                owner = "OpenAPI 操作 ${operation.operationCode}",
                roots = operation.referencedDtoRefs(),
                schemasByRef = schemasByRef,
            )
        }
}

private fun Collection<LsiLowcodeDtoSchema>.requireDtoSchemaClosure(
    schemasByRef: Map<LsiLowcodeDtoRef, LsiLowcodeDtoSchema>,
) {
    sortedBy(LsiLowcodeDtoSchema::schemaName).forEach { schema ->
        requireDtoSchemaClosure(
            owner = "OpenAPI DTO ${schema.schemaName}",
            roots = setOf(schema.ref),
            schemasByRef = schemasByRef,
        )
    }
}

private fun requireDtoSchemaClosure(
    owner: String,
    roots: Set<LsiLowcodeDtoRef>,
    schemasByRef: Map<LsiLowcodeDtoRef, LsiLowcodeDtoSchema>,
) {
    val pending = ArrayDeque(roots.sortedBy(LsiLowcodeDtoRef::componentSchemaName))
    val visited = mutableSetOf<LsiLowcodeDtoRef>()
    while (pending.isNotEmpty()) {
        val ref = pending.removeFirst()
        if (!visited.add(ref)) {
            continue
        }
        val schema = schemasByRef[ref]
            ?: error("$owner 引用了未解析 DTO: ${ref.componentSchemaName()}")
        schema.referencedDtoRefs()
            .sortedBy(LsiLowcodeDtoRef::componentSchemaName)
            .forEach(pending::addLast)
    }
}

private fun Collection<LsiLowcodeCustomOperation>.openApiReferencedDtoRefs(): Set<LsiLowcodeDtoRef> = buildSet {
    this@openApiReferencedDtoRefs
        .filter { operation -> operation.transport != LowcodeOperationTransport.INTERNAL }
        .forEach { operation -> addAll(operation.referencedDtoRefs()) }
}

private fun LsiLowcodeCustomOperation.referencedDtoRefs(): Set<LsiLowcodeDtoRef> = buildSet {
    parameters.forEach { parameter -> parameter.schema.collectDtoRefs(this) }
    requestBody?.schema?.collectDtoRefs(this)
    responseBody?.schema?.collectDtoRefs(this)
}

private fun LsiLowcodeDtoSchema.referencedDtoRefs(): Set<LsiLowcodeDtoRef> = buildSet {
    properties.values.forEach { property -> property.collectDtoRefs(this) }
}

private fun LsiLowcodeApiSchema.collectDtoRefs(destination: MutableSet<LsiLowcodeDtoRef>) {
    typeRef?.let(destination::add)
    properties.values.forEach { schema -> schema.collectDtoRefs(destination) }
    items?.collectDtoRefs(destination)
    oneOf.forEach { schema -> schema.collectDtoRefs(destination) }
}
