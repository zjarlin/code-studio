package site.addzero.platform.lowcode.generator

import site.addzero.dto.compiler.KotlinDtoSourceGenerator
import site.addzero.dto.compiler.DtoStructureOrigin
import site.addzero.dto.compiler.LsiDataStructure
import site.addzero.dto.compiler.LsiDtoDefaultValue
import site.addzero.dto.compiler.LsiDtoDefinition
import site.addzero.dto.compiler.LsiDtoProperty
import site.addzero.dto.compiler.LsiDtoType
import site.addzero.validation.compiler.KotlinValidationSourceGenerator
import site.addzero.validation.compiler.LsiValidatedProperty
import site.addzero.validation.compiler.LsiValidatedType
import site.addzero.validation.compiler.LsiValidationRule
import site.addzero.validation.compiler.LsiValidationValueKind
import site.addzero.validation.compiler.ValidationRuleMetadataCatalog

/**
 * 已解析的命名 DTO 类型。
 */
data class LowcodeResolvedDtoType(
    val ref: LsiLowcodeDtoRef,
    val qualifiedName: String,
    val className: String,
    val kind: LowcodeDtoKind?,
    val contributorId: String?,
)

/**
 * 为 Contract 编译提供稳定 DTO 引用解析。
 */
class LowcodeDtoTypeCatalog private constructor(
    private val types: Map<LsiLowcodeDtoRef, LowcodeResolvedDtoType>,
) {
    fun resolve(ref: LsiLowcodeDtoRef): LowcodeResolvedDtoType = types[ref]
        ?: error("未找到低代码 DTO 类型: ${ref.componentSchemaName()}")

    companion object {
        val EMPTY = LowcodeDtoTypeCatalog(emptyMap())

        fun from(
            models: Collection<LowcodeModelMeta>,
            definitions: Collection<LsiLowcodeDtoDefinition> = emptyList(),
        ): LowcodeDtoTypeCatalog {
            val modelTypes = models.flatMap { model ->
                val entityRef = LsiLowcodeDtoRef(model.modelCode)
                listOf(
                    entityRef to LowcodeResolvedDtoType(
                        ref = entityRef,
                        qualifiedName = model.entityQualifiedName(),
                        className = model.entityClassName(),
                        kind = null,
                        contributorId = model.contributorId,
                    ),
                ) + model.dtoDefinitions.map { dto ->
                    val ref = LsiLowcodeDtoRef(model.modelCode, dto.dtoCode)
                    ref to LowcodeResolvedDtoType(
                        ref = ref,
                        qualifiedName = model.featurePackageName.generatedLayout()
                            .qualifiedName(LowcodeGeneratedResourceKind.DTO, dto.className),
                        className = dto.className,
                        kind = dto.kind,
                        contributorId = model.contributorId,
                    )
                }
            }
            val standaloneTypes = definitions.map { definition ->
                definition.ref to LowcodeResolvedDtoType(
                    ref = definition.ref,
                    qualifiedName = definition.featurePackageName.generatedLayout()
                        .qualifiedName(LowcodeGeneratedResourceKind.DTO, definition.className),
                    className = definition.className,
                    kind = definition.kind,
                    contributorId = definition.contributorId,
                )
            }
            return LowcodeDtoTypeCatalog((modelTypes + standaloneTypes).toMap())
        }
    }
}

/**
 * 从模型投影生成直接参与 Kotlin 编译的 DTO 源码。
 */
object LowcodeDtoSourceGenerator {
    /** 将 Studio DTO 按与源码生成相同的规则转换为结构分析输入。 */
    fun toDataStructures(
        definitions: Collection<LsiLowcodeDtoDefinition>,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoCatalog: LowcodeDtoTypeCatalog = LowcodeDtoTypeCatalog.from(modelCatalog, definitions),
    ): List<LsiDataStructure> = definitions
        .filter { definition -> definition.status == 1 }
        .sortedBy(LsiLowcodeDtoDefinition::dtoCode)
        .map { definition ->
            val sourceModel = definition.sourceModelCode?.let { modelCode ->
                modelCatalog.singleOrNull { model -> model.modelCode == modelCode }
                    ?: error("DTO ${definition.dtoCode} 引用了不存在的模型 $modelCode")
            }
            val lsiDefinition = if (sourceModel == null) {
                definition.toStandaloneLsiDefinition(dtoCatalog)
            } else {
                sourceModel.resolveProjection(definition, modelCatalog).definition
            }
            LsiDataStructure(
                qualifiedName = "${lsiDefinition.packageName}.${lsiDefinition.className}",
                properties = lsiDefinition.properties,
                origins = setOf(DtoStructureOrigin.METADATA),
            )
        }

    fun generate(
        models: Collection<LowcodeModelMeta>,
        modelCatalog: Collection<LowcodeModelMeta> = models,
    ): List<LowcodeGeneratedFile> {
        return models
            .filter { model -> model.status == 1 }
            .sortedBy(LowcodeModelMeta::modelCode)
            .flatMap { model ->
                model.dtoDefinitions.sortedBy(LsiLowcodeDto::dtoCode).map { dto ->
                    generate(model, dto, modelCatalog)
                }
            }
    }

    fun generateDefinitions(
        definitions: Collection<LsiLowcodeDtoDefinition>,
        modelCatalog: Collection<LowcodeModelMeta>,
        dtoCatalog: LowcodeDtoTypeCatalog = LowcodeDtoTypeCatalog.from(modelCatalog, definitions),
    ): List<LowcodeGeneratedFile> = definitions
        .filter { definition -> definition.status == 1 }
        .sortedBy(LsiLowcodeDtoDefinition::dtoCode)
        .map { definition ->
            val sourceModel = definition.sourceModelCode?.let { modelCode ->
                modelCatalog.singleOrNull { model -> model.modelCode == modelCode }
                    ?: error("DTO ${definition.dtoCode} 引用了不存在的模型 $modelCode")
            }
            if (sourceModel == null) {
                generateStandalone(definition, dtoCatalog)
            } else {
                generateProjection(definition, sourceModel, modelCatalog)
            }
        }

    fun generateDefinitionValidations(
        definitions: Collection<LsiLowcodeDtoDefinition>,
        modelCatalog: Collection<LowcodeModelMeta>,
        ruleCatalog: ValidationRuleMetadataCatalog = ValidationRuleMetadataCatalog.load(),
    ): List<LowcodeGeneratedFile> = definitions
        .filter { definition -> definition.status == 1 && definition.kind != LowcodeDtoKind.STRUCTURE }
        .sortedBy(LsiLowcodeDtoDefinition::dtoCode)
        .mapNotNull { definition ->
            val sourceModel = definition.sourceModelCode?.let { modelCode ->
                modelCatalog.singleOrNull { model -> model.modelCode == modelCode }
                    ?: error("DTO ${definition.dtoCode} 引用了不存在的模型 $modelCode")
            }
            val properties = definition.fields.mapNotNull { field ->
                if (field.validations.isEmpty()) {
                    return@mapNotNull null
                }
                val source = sourceModel?.resolveDtoProperty(field.sourcePath, modelCatalog)
                val valueKind = if (source == null && sourceModel == null) {
                    val schema = requireNotNull(field.schema) {
                        "独立 DTO ${definition.dtoCode}.${field.name} 缺少字段类型"
                    }
                    schema.toValidationValueKind()
                } else {
                    requireNotNull(source) {
                        "DTO ${definition.dtoCode}.${field.name} 引用了不存在的属性 ${field.sourcePath}"
                    }.type.toValidationValueKind()
                }
                LsiValidatedProperty(
                    name = field.name,
                    valueKind = valueKind,
                    nullable = when (field.nullability) {
                        LowcodeDtoNullability.INHERIT -> !requireNotNull(source).required
                        LowcodeDtoNullability.NULLABLE -> true
                        LowcodeDtoNullability.NON_NULL -> false
                    },
                    rules = field.validations,
                )
            }
            val layout = definition.featurePackageName.generatedLayout()
            val packageName = layout.packageName(LowcodeGeneratedResourceKind.DTO)
            val source = KotlinValidationSourceGenerator.generate(
                type = LsiValidatedType(packageName, definition.className, properties),
                catalog = ruleCatalog,
                exceptionQualifiedName =
                    "${generationTargetSymbol(GenerationTargetSymbols.CORE_RUNTIME_PACKAGE)}.ValidationException",
            ) ?: return@mapNotNull null
            LowcodeGeneratedFile(
                packageName = source.packageName,
                fileName = source.fileName,
                relativePath = layout.relativeSourcePath(
                    LowcodeGeneratedResourceKind.DTO,
                    source.fileName,
                ),
                content = generatedByStudio(source.content),
                kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
            )
        }

    private fun generate(
        model: LowcodeModelMeta,
        dto: LsiLowcodeDto,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): LowcodeGeneratedFile {
        val layout = model.featurePackageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.DTO)
        val entityQualifiedName = model.entityQualifiedName()
        val entityClassName = model.entityClassName()
        val resolved = model.resolveProjection(
            definition = LsiLowcodeDtoDefinition(
                dtoCode = dto.dtoCode,
                name = "${model.name} ${if (dto.kind.isOutput) "输出" else "输入"}",
                packageName = model.featurePackageName,
                className = dto.className,
                kind = dto.kind,
                sourceModelCode = model.modelCode,
                selectionMode = dto.selectionMode,
                excludedPaths = dto.excludedPaths,
                fields = dto.fields,
                contributorId = model.contributorId,
            ),
            modelCatalog = modelCatalog,
        )
        val definition = resolved.definition
        val resolvedFields = resolved.fields
        val additionalImports = if (dto.kind.isOutput) setOf(entityQualifiedName) else emptySet()
        val generated = KotlinDtoSourceGenerator.generate(definition, additionalImports)
        val content = if (dto.kind.isOutput) {
            generated.content + "\n" + renderProjection(entityClassName, dto.className, resolvedFields)
        } else {
            generated.content
        }
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = dto.className,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.DTO,
                dto.className,
            ),
            content = generatedByStudio(content),
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun generateProjection(
        definition: LsiLowcodeDtoDefinition,
        sourceModel: LowcodeModelMeta,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): LowcodeGeneratedFile {
        val resolved = sourceModel.resolveProjection(definition, modelCatalog)
        val layout = definition.featurePackageName.generatedLayout()
        val packageName = layout.packageName(LowcodeGeneratedResourceKind.DTO)
        val generated = KotlinDtoSourceGenerator.generate(
            resolved.definition,
            if (definition.kind.isOutput) setOf(sourceModel.entityQualifiedName()) else emptySet(),
        )
        val content = if (definition.kind.isOutput) {
            generated.content + "\n" + renderProjection(
                sourceModel.entityClassName(),
                definition.className,
                resolved.fields,
            )
        } else {
            generated.content
        }
        return LowcodeGeneratedFile(
            packageName = packageName,
            fileName = definition.className,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.DTO,
                definition.className,
            ),
            content = generatedByStudio(content),
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun generateStandalone(
        definition: LsiLowcodeDtoDefinition,
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): LowcodeGeneratedFile {
        val layout = definition.featurePackageName.generatedLayout()
        val dtoDefinition = definition.toStandaloneLsiDefinition(dtoCatalog)
        val content = KotlinDtoSourceGenerator.generate(dtoDefinition).content
        return LowcodeGeneratedFile(
            packageName = dtoDefinition.packageName,
            fileName = definition.className,
            relativePath = layout.relativeSourcePath(
                LowcodeGeneratedResourceKind.DTO,
                definition.className,
            ),
            content = generatedByStudio(content),
            kind = LowcodeGeneratedFileKind.COMPILED_SOURCE,
        )
    }

    private fun LsiLowcodeDtoDefinition.toStandaloneLsiDefinition(
        dtoCatalog: LowcodeDtoTypeCatalog,
    ): LsiDtoDefinition {
        require(fields.isNotEmpty() || kind == LowcodeDtoKind.STRUCTURE) {
            "DTO $dtoCode 至少需要一个字段"
        }
        val properties = fields.map { field ->
            val nullable = field.nullability == LowcodeDtoNullability.NULLABLE
            val fieldType = if (kind == LowcodeDtoKind.STRUCTURE) {
                requireNotNull(field.kotlinType) {
                    "结构 DTO $dtoCode.${field.name} 缺少 Kotlin 类型"
                }.copy(nullable = nullable)
            } else {
                field.kotlinType?.copy(nullable = nullable)
                    ?: requireNotNull(field.schema) {
                        "独立 DTO $dtoCode.${field.name} 缺少字段类型"
                    }.toKotlinType(dtoCatalog).toLsiDtoType(nullable)
            }
            LsiDtoProperty(
                name = field.name,
                type = fieldType,
                description = field.description?.takeIf(String::isNotBlank)
                    ?: field.schema?.description?.takeIf(String::isNotBlank)
                    ?: "DTO 字段",
                defaultValue = field.defaultValue
                    ?: if (kind == LowcodeDtoKind.INPUT && nullable) LsiDtoDefaultValue.NULL else null,
                annotations = field.annotations,
            )
        }
        return LsiDtoDefinition(
            packageName = featurePackageName.generatedLayout().packageName(LowcodeGeneratedResourceKind.DTO),
            className = className,
            description = "$name。",
            properties = properties,
            visibility = visibility,
            annotations = annotations,
            superTypes = superTypes,
        )
    }

    private fun LowcodeModelMeta.resolveProjection(
        definition: LsiLowcodeDtoDefinition,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): ResolvedProjection {
        val projection = definition.toProjection()
        val resolvedFields = effectiveDtoFields(projection, modelCatalog).map { field ->
            val source = resolveDtoProperty(field.sourcePath, modelCatalog)
                ?: error("DTO ${definition.dtoCode} 引用了不存在的属性 ${field.sourcePath}")
            ResolvedDtoField(
                name = field.name,
                sourcePath = field.sourcePath,
                accessExpression = source.accessExpression,
                type = source.type,
                description = field.description?.takeIf(String::isNotBlank) ?: source.description,
                nullable = when (field.nullability) {
                    LowcodeDtoNullability.INHERIT -> !source.required
                    LowcodeDtoNullability.NULLABLE -> true
                    LowcodeDtoNullability.NON_NULL -> false
                },
            )
        }
        require(resolvedFields.isNotEmpty()) { "DTO ${definition.dtoCode} 的字段策略未选择到任何属性" }
        return ResolvedProjection(
            definition = LsiDtoDefinition(
                packageName = definition.featurePackageName.generatedLayout()
                    .packageName(LowcodeGeneratedResourceKind.DTO),
                className = definition.className,
                description = "${definition.name}。",
                properties = resolvedFields.map { field ->
                    LsiDtoProperty(
                        name = field.name,
                        type = field.type.toLsiDtoType(field.nullable),
                        description = field.description,
                        defaultValue = if (definition.kind == LowcodeDtoKind.INPUT && field.nullable) {
                            LsiDtoDefaultValue.NULL
                        } else {
                            null
                        },
                    )
                },
                visibility = definition.visibility,
                annotations = definition.annotations,
                superTypes = definition.superTypes,
            ),
            fields = resolvedFields,
        )
    }

    private fun renderProjection(
        entityClassName: String,
        dtoClassName: String,
        fields: List<ResolvedDtoField>,
    ): String {
        val mappings = fields.joinToString("\n") { field ->
            "    ${field.name.escapeIdentifier()} = ${field.accessExpression},"
        }
        return """
            |/** 将实体的已加载属性投影为 $dtoClassName。 */
            |fun $entityClassName.to$dtoClassName(): $dtoClassName = $dtoClassName(
            |$mappings
            |)
        """.trimMargin() + "\n"
    }

    private fun LowcodeModelMeta.scalarPropertyCatalog(
        modelCatalog: Collection<LowcodeModelMeta>,
    ): Map<String, DtoProperty> = buildMap {
        inheritanceLineage(modelCatalog).forEach { model ->
            model.entityConfig.resolvedInheritedProperties(
                includeConventionDefault = model.entityConfig.inheritanceSubtype == null,
            ).forEach { property ->
                put(
                    property.name,
                    DtoProperty(
                        type = property.kotlinType.toKotlinType(),
                        required = property.required,
                        description = property.description?.takeIf(String::isNotBlank) ?: "继承属性",
                    ),
                )
            }
            model.fields.forEach { field ->
                put(
                    field.fieldCode,
                    DtoProperty(
                        type = field.kotlinType.toKotlinType(),
                        required = field.required,
                        description = field.remark?.takeIf(String::isNotBlank) ?: field.label,
                    ),
                )
            }
            model.entityConfig.formulaProperties.forEach { property ->
                put(
                    property.propertyCode,
                    DtoProperty(
                        type = property.kotlinType.toKotlinType(),
                        required = !property.nullable,
                        description = property.description?.takeIf(String::isNotBlank) ?: property.label,
                    ),
                )
            }
            model.entityConfig.transientProperties.forEach { property ->
                put(
                    property.propertyCode,
                    DtoProperty(
                        type = property.kotlinType.toKotlinType(),
                        required = !property.nullable,
                        description = property.description?.takeIf(String::isNotBlank) ?: property.label,
                    ),
                )
            }
        }
    }

    private fun LowcodeModelMeta.resolveDtoProperty(
        sourcePath: String,
        modelCatalog: Collection<LowcodeModelMeta>,
    ): ResolvedDtoProperty? {
        val modelsById = modelCatalog.associateBy(LowcodeModelMeta::id)
        val segments = sourcePath.split('.')
        var currentModel = this
        var nullablePath = false
        val access = mutableListOf<String>()
        segments.dropLast(1).forEach { segment ->
            val relation = currentModel.relations.singleOrNull { candidate -> candidate.relationCode == segment }
                ?: return null
            access += segment.escapeIdentifier() + if (nullablePath || !relation.required) "?" else ""
            nullablePath = nullablePath || !relation.required
            currentModel = modelsById[relation.targetModelId] ?: return null
        }
        val propertyName = segments.lastOrNull() ?: return null
        val property = currentModel.scalarPropertyCatalog(modelCatalog)[propertyName]
            ?: currentModel.relationIdProperty(propertyName)
            ?: return null
        access += propertyName.escapeIdentifier()
        return ResolvedDtoProperty(
            type = property.type,
            required = property.required && !nullablePath,
            accessExpression = access.joinToString("."),
            description = property.description,
        )
    }

    private fun LowcodeModelMeta.relationIdProperty(propertyName: String): DtoProperty? = relations
        .firstNotNullOfOrNull { relation ->
            val idViewCode = relation.idViewCode()
            if (idViewCode != propertyName) return@firstNotNullOfOrNull null
            if (relation.relationKind.isReference()) {
                DtoProperty(KotlinType("Long"), relation.required, "${relation.label}编号")
            } else {
                DtoProperty(KotlinType("List<Long>"), true, "${relation.label}编号集合")
            }
        }

    private fun String.toKotlinType(): KotlinType {
        val raw = trim().removeSuffix("?")
        val canonical = TYPE_ALIASES[raw.lowercase()] ?: raw
        if ('.' !in canonical) {
            return KotlinType(canonical)
        }
        return KotlinType(canonical.substringAfterLast('.'), setOf(canonical))
    }

    private fun KotlinType.toLsiDtoType(nullable: Boolean): LsiDtoType {
        val importsBySimpleName = imports.associateBy { qualifiedName -> qualifiedName.substringAfterLast('.') }
        val parser = KotlinTypeParser(name, importsBySimpleName)
        return parser.parse().copy(nullable = nullable)
    }

    private fun LsiLowcodeApiSchema.toKotlinType(dtoCatalog: LowcodeDtoTypeCatalog): KotlinType {
        typeRef?.let { ref ->
            val resolved = dtoCatalog.resolve(ref)
            return KotlinType(resolved.className, setOf(resolved.qualifiedName))
        }
        return when (type) {
            "string" -> when (format) {
                "date" -> KotlinType("LocalDate", setOf("java.time.LocalDate"))
                "date-time" -> KotlinType("LocalDateTime", setOf("java.time.LocalDateTime"))
                else -> KotlinType("String")
            }
            "integer" -> KotlinType(if (format == "int64") "Long" else "Int")
            "number" -> KotlinType(if (format == "decimal") "BigDecimal" else "Double",
                if (format == "decimal") setOf("java.math.BigDecimal") else emptySet())
            "boolean" -> KotlinType("Boolean")
            "array" -> {
                val itemType = items?.toKotlinType(dtoCatalog) ?: KotlinType("Any")
                KotlinType("List<${itemType.name}>", itemType.imports)
            }
            "object" -> KotlinType("Map<String, Any?>")
            else -> KotlinType("Any")
        }
    }

    private fun LsiLowcodeApiSchema.toValidationValueKind(): LsiValidationValueKind = when (type) {
        "string" -> LsiValidationValueKind.TEXT
        "array" -> if (items?.type == "string") {
            LsiValidationValueKind.TEXT_COLLECTION
        } else {
            LsiValidationValueKind.COLLECTION
        }
        "object" -> LsiValidationValueKind.COLLECTION
        else -> error("Schema 类型 $type 暂不支持字段校验")
    }

    private fun KotlinType.toValidationValueKind(): LsiValidationValueKind = when {
        name == "String" -> LsiValidationValueKind.TEXT
        name.startsWith("List<String>") || name.startsWith("Set<String>") ->
            LsiValidationValueKind.TEXT_COLLECTION
        name.startsWith("List<") || name.startsWith("Set<") || name.startsWith("Map<") ->
            LsiValidationValueKind.COLLECTION
        else -> error("Kotlin 类型 $name 暂不支持字段校验")
    }

    private fun String.escapeIdentifier(): String = if (this in KOTLIN_KEYWORDS) "`$this`" else this

    private data class DtoProperty(
        val type: KotlinType,
        val required: Boolean,
        val description: String,
    )

    private data class ResolvedDtoField(
        val name: String,
        val sourcePath: String,
        val accessExpression: String,
        val type: KotlinType,
        val description: String,
        val nullable: Boolean,
    )

    private data class ResolvedProjection(
        val definition: LsiDtoDefinition,
        val fields: List<ResolvedDtoField>,
    )

    private data class ResolvedDtoProperty(
        val type: KotlinType,
        val required: Boolean,
        val accessExpression: String,
        val description: String,
    )

    private data class KotlinType(
        val name: String,
        val imports: Set<String> = emptySet(),
    )

    private class KotlinTypeParser(
        private val source: String,
        private val imports: Map<String, String>,
    ) {
        private var index = 0

        fun parse(): LsiDtoType {
            val type = parseType()
            skipWhitespace()
            require(index == source.length) { "无法解析 Kotlin DTO 类型: $source" }
            return type
        }

        private fun parseType(): LsiDtoType {
            skipWhitespace()
            val nameStart = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] in "._")) {
                index += 1
            }
            require(index > nameStart) { "无法解析 Kotlin DTO 类型: $source" }
            val rawName = source.substring(nameStart, index)
            skipWhitespace()
            val arguments = if (index < source.length && source[index] == '<') parseArguments() else emptyList()
            skipWhitespace()
            val nullable = index < source.length && source[index] == '?'
            if (nullable) {
                index += 1
            }
            return LsiDtoType(rawName.toQualifiedTypeName(), arguments, nullable)
        }

        private fun parseArguments(): List<LsiDtoType> {
            index += 1
            val arguments = mutableListOf<LsiDtoType>()
            do {
                arguments += parseType()
                skipWhitespace()
                val separator = source.getOrNull(index)
                require(separator == ',' || separator == '>') { "无法解析 Kotlin DTO 泛型: $source" }
                index += 1
            } while (separator == ',')
            return arguments
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index += 1
            }
        }

        private fun String.toQualifiedTypeName(): String {
            if ('.' in this) {
                return this
            }
            return imports[this] ?: KOTLIN_TYPE_QUALIFIED_NAMES[this] ?: this
        }
    }

    private val TYPE_ALIASES = mapOf(
        "string" to "String",
        "text" to "String",
        "long" to "Long",
        "int" to "Int",
        "integer" to "Int",
        "double" to "Double",
        "boolean" to "Boolean",
        "localdate" to "java.time.LocalDate",
        "localdatetime" to "java.time.LocalDateTime",
        "bigdecimal" to "java.math.BigDecimal",
    )
    private val KOTLIN_TYPE_QUALIFIED_NAMES = mapOf(
        "Any" to "kotlin.Any",
        "Boolean" to "kotlin.Boolean",
        "Double" to "kotlin.Double",
        "Int" to "kotlin.Int",
        "List" to "kotlin.collections.List",
        "Long" to "kotlin.Long",
        "Map" to "kotlin.collections.Map",
        "Set" to "kotlin.collections.Set",
        "String" to "kotlin.String",
    )
    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
        "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
        "typeof", "val", "var", "when", "while",
    )
}

/**
 * 将模型 DTO 定义收敛为 OpenAPI 可消费的命名结构。
 */
fun LowcodeModelMeta.toLsiDtoSchemas(
    models: Collection<LowcodeModelMeta> = listOf(this),
): List<LsiLowcodeDtoSchema> {
    val modelsById = models.associateBy(LowcodeModelMeta::id)
    return dtoDefinitions.map { dto ->
        val schemas = linkedMapOf<String, LsiLowcodeApiSchema>()
        val required = linkedSetOf<String>()
        val validations = linkedMapOf<String, List<LsiValidationRule>>()
        effectiveDtoFields(dto, models).forEach { field ->
            val property = resolveApiDtoProperty(field.sourcePath, modelsById, models)
                ?: error("DTO $modelCode.${dto.dtoCode} 引用了不存在的属性 ${field.sourcePath}")
            val nullable = when (field.nullability) {
                LowcodeDtoNullability.INHERIT -> !property.required
                LowcodeDtoNullability.NULLABLE -> true
                LowcodeDtoNullability.NON_NULL -> false
            }
            schemas[field.name] = property.schema.copy(
                description = field.description?.takeIf(String::isNotBlank) ?: property.schema.description,
            )
            if (!nullable) {
                required += field.name
            }
            if (field.validations.isNotEmpty()) {
                validations[field.name] = field.validations
            }
        }
        LsiLowcodeDtoSchema(
            ref = LsiLowcodeDtoRef(modelCode, dto.dtoCode),
            className = dto.className,
            properties = schemas,
            required = required,
            validations = validations,
        )
    }
}

/** 将功能级 DTO 收敛为 OpenAPI 和契约生成共享的命名结构。 */
fun LsiLowcodeDtoDefinition.toLsiDtoSchema(
    models: Collection<LowcodeModelMeta> = emptyList(),
): LsiLowcodeDtoSchema {
    require(kind != LowcodeDtoKind.STRUCTURE) {
        "结构 DTO $dtoCode 不能注册为 OpenAPI 或业务接口契约"
    }
    val sourceModel = sourceModelCode?.let { modelCode ->
        models.singleOrNull { model -> model.modelCode == modelCode }
            ?: error("DTO $dtoCode 引用了不存在的模型 $modelCode")
    }
    if (sourceModel != null) {
        val projectedModel = sourceModel.copy(dtoDefinitions = listOf(toProjection()))
        val projected = projectedModel
            .toLsiDtoSchemas(models.filterNot { model -> model.modelCode == sourceModel.modelCode } + sourceModel)
            .single()
        return projected.copy(
            ref = ref,
            className = className,
            description = description ?: name,
        )
    }
    val properties = fields.associate { field ->
        val schema = requireNotNull(field.schema) { "独立 DTO $dtoCode.${field.name} 缺少字段类型" }
        field.name to schema.copy(
            description = field.description?.takeIf(String::isNotBlank)
                ?: schema.description?.takeIf(String::isNotBlank)
                ?: "DTO 字段",
        )
    }
    val required = fields
        .filter { field -> field.nullability == LowcodeDtoNullability.NON_NULL }
        .mapTo(linkedSetOf(), LsiLowcodeDtoField::name)
    val validations = fields
        .filter { field -> field.validations.isNotEmpty() }
        .associate { field -> field.name to field.validations }
    return LsiLowcodeDtoSchema(
        ref = ref,
        className = className,
        properties = properties,
        required = required,
        validations = validations,
        description = description ?: name,
    )
}

/** 将实体本身注册为可被方法 typeRef 复用的 OpenAPI component。 */
fun LowcodeModelMeta.toLsiEntitySchema(
    models: Collection<LowcodeModelMeta> = listOf(this),
): LsiLowcodeDtoSchema {
    val schemas = linkedMapOf<String, LsiLowcodeApiSchema>()
    val required = linkedSetOf<String>()
    val modelsById = models.associateBy(LowcodeModelMeta::id)
    inheritanceLineage(models).forEach { model ->
        model.appendOwnEntityScalarSchemas(models, schemas, required)
        model.relations.sortedBy(LowcodeRelationMeta::orderNo).forEach { relation ->
            schemas[relation.relationCode] = relation.toAssociationApiSchema(modelsById, models)
            val name = relation.idViewCode()
            schemas[name] = if (relation.relationKind.isReference()) {
                "Long".toApiSchema().copy(description = "${relation.label}编号")
            } else {
                LsiLowcodeApiSchema(
                    type = "array",
                    description = "${relation.label}编号集合",
                    items = "Long".toApiSchema(),
                )
            }
            if (relation.required || !relation.relationKind.isReference()) required += name
        }
    }
    return LsiLowcodeDtoSchema(
        ref = LsiLowcodeDtoRef(modelCode),
        className = entityClassName(),
        properties = schemas,
        required = required,
        description = name,
    )
}

private fun LowcodeModelMeta.toShallowEntityApiSchema(
    models: Collection<LowcodeModelMeta>,
): LsiLowcodeApiSchema {
    val schemas = linkedMapOf<String, LsiLowcodeApiSchema>()
    val required = linkedSetOf<String>()
    inheritanceLineage(models).forEach { model ->
        model.appendOwnEntityScalarSchemas(models, schemas, required)
    }
    return LsiLowcodeApiSchema(
        type = "object",
        description = name,
        properties = schemas,
        required = required,
    )
}

private fun LowcodeModelMeta.appendOwnEntityScalarSchemas(
    models: Collection<LowcodeModelMeta>,
    schemas: MutableMap<String, LsiLowcodeApiSchema>,
    required: MutableSet<String>,
) {
    entityConfig.resolvedInheritedProperties(
        includeConventionDefault = entityConfig.inheritanceSubtype == null,
    ).forEach { property ->
        schemas[property.name] = property.kotlinType.toApiSchema().copy(description = property.description)
        if (property.required) required += property.name
    }
    fields.sortedBy(LowcodeFieldMeta::orderNo).forEach { field ->
        schemas[field.fieldCode] = field.kotlinType.toApiSchema(
            field.enumStorage,
            discriminatorEnumValues(field, models),
        ).copy(description = field.remark ?: field.label)
        if (field.required) required += field.fieldCode
    }
    entityConfig.formulaProperties.forEach { property ->
        schemas[property.propertyCode] = property.kotlinType.toApiSchema()
            .copy(description = property.description ?: property.label)
        if (!property.nullable) required += property.propertyCode
    }
    entityConfig.transientProperties.forEach { property ->
        schemas[property.propertyCode] = property.kotlinType.toApiSchema()
            .copy(description = property.description ?: property.label)
        if (!property.nullable) required += property.propertyCode
    }
}

private fun LowcodeRelationMeta.toAssociationApiSchema(
    modelsById: Map<Long, LowcodeModelMeta>,
    models: Collection<LowcodeModelMeta>,
): LsiLowcodeApiSchema {
    val targetSchema = modelsById[targetModelId]
        ?.toShallowEntityApiSchema(models)
        ?: LsiLowcodeApiSchema(type = "object")
    if (relationKind.isReference()) {
        return targetSchema.copy(description = label)
    }
    return LsiLowcodeApiSchema(
        type = "array",
        description = label,
        items = targetSchema,
    )
}

internal fun LowcodeModelMeta.effectiveDtoFields(
    dto: LsiLowcodeDto,
    modelCatalog: Collection<LowcodeModelMeta>,
): List<LsiLowcodeDtoField> {
    if (dto.selectionMode == LowcodeDtoSelectionMode.EXPLICIT) return dto.fields
    val modelsById = modelCatalog.associateBy(LowcodeModelMeta::id)
    val sourcePaths = when (dto.selectionMode) {
        LowcodeDtoSelectionMode.EXPLICIT -> emptyList()
        LowcodeDtoSelectionMode.ALL_SCALAR_FIELDS -> scalarPropertyPaths(modelCatalog)
        LowcodeDtoSelectionMode.ALL_TABLE_FIELDS -> tablePropertyPaths(modelCatalog)
        LowcodeDtoSelectionMode.ALL_DEEP_FIELDS -> deepPropertyPaths(modelsById, modelCatalog)
    }
    return sourcePaths
        .filterNot { path -> dto.excludedPaths.any { excluded -> path == excluded || path.startsWith("$excluded.") } }
        .distinct()
        .map { path -> LsiLowcodeDtoField(name = path.toDtoFieldName(), sourcePath = path) }
        .distinctBy(LsiLowcodeDtoField::name)
}

private fun LowcodeModelMeta.scalarPropertyPaths(
    modelCatalog: Collection<LowcodeModelMeta>,
): List<String> = inheritanceLineage(modelCatalog).flatMap { model ->
    buildList {
        addAll(
            model.entityConfig.resolvedInheritedProperties(
                includeConventionDefault = model.entityConfig.inheritanceSubtype == null,
            ).map(LsiLowcodeInheritedProperty::name),
        )
        addAll(model.fields.sortedBy(LowcodeFieldMeta::orderNo).map(LowcodeFieldMeta::fieldCode))
        addAll(model.entityConfig.formulaProperties.map(LsiLowcodeFormulaProperty::propertyCode))
        addAll(model.entityConfig.transientProperties.map(LsiLowcodeTransientProperty::propertyCode))
    }
}.distinct()

private fun LowcodeModelMeta.tablePropertyPaths(
    modelCatalog: Collection<LowcodeModelMeta>,
): List<String> = inheritanceLineage(modelCatalog).flatMap { model ->
    buildList {
        addAll(
            model.entityConfig.resolvedInheritedProperties(
                includeConventionDefault = model.entityConfig.inheritanceSubtype == null,
            ).map(LsiLowcodeInheritedProperty::name),
        )
        addAll(model.fields.sortedBy(LowcodeFieldMeta::orderNo).map(LowcodeFieldMeta::fieldCode))
        addAll(model.relations.sortedBy(LowcodeRelationMeta::orderNo).map(LowcodeRelationMeta::idViewCode))
    }
}.distinct()

private fun LowcodeModelMeta.deepPropertyPaths(
    modelsById: Map<Long, LowcodeModelMeta>,
    modelCatalog: Collection<LowcodeModelMeta>,
    prefix: String = "",
    depth: Int = 0,
    visited: Set<Long> = emptySet(),
): List<String> = buildList {
    addAll(scalarPropertyPaths(modelCatalog).map { property -> "$prefix$property" })
    inheritanceLineage(modelCatalog).flatMap(LowcodeModelMeta::relations)
        .sortedBy(LowcodeRelationMeta::orderNo).forEach { relation ->
        add("$prefix${relation.idViewCode()}")
        if (!relation.relationKind.isReference() || depth >= MAX_DEEP_RELATION_DEPTH) return@forEach
        val target = modelsById[relation.targetModelId] ?: return@forEach
        if (target.id in visited) return@forEach
        addAll(
            target.deepPropertyPaths(
                modelsById = modelsById,
                modelCatalog = modelCatalog,
                prefix = "$prefix${relation.relationCode}.",
                depth = depth + 1,
                visited = visited + id,
            ),
        )
    }
}

private fun LowcodeRelationMeta.idViewCode(): String = if (relationKind.isReference()) {
    "${relationCode}Id"
} else {
    when {
        relationCode.endsWith("ies") -> relationCode.dropLast(3) + "yIds"
        relationCode.endsWith('s') -> relationCode.dropLast(1) + "Ids"
        else -> relationCode + "Ids"
    }
}

private fun String.toDtoFieldName(): String {
    val parts = split('.')
    return parts.first() + parts.drop(1).joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
}

private const val MAX_DEEP_RELATION_DEPTH = 2

private fun LowcodeModelMeta.resolveApiDtoProperty(
    sourcePath: String,
    modelsById: Map<Long, LowcodeModelMeta>,
    modelCatalog: Collection<LowcodeModelMeta>,
): ApiDtoProperty? {
    val segments = sourcePath.split('.')
    var currentModel = this
    var requiredPath = true
    segments.dropLast(1).forEach { segment ->
        val relation = currentModel.relations.singleOrNull { candidate -> candidate.relationCode == segment }
            ?: return null
        requiredPath = requiredPath && relation.required
        currentModel = modelsById[relation.targetModelId] ?: return null
    }
    val propertyName = segments.lastOrNull() ?: return null
    val lineage = currentModel.inheritanceLineage(modelCatalog)
    val inherited = lineage.flatMap { model ->
        model.entityConfig.resolvedInheritedProperties(
            includeConventionDefault = model.entityConfig.inheritanceSubtype == null,
        )
    }.singleOrNull { property -> property.name == propertyName }
    if (inherited != null) {
        return ApiDtoProperty(
            inherited.kotlinType.toApiSchema().copy(
                description = inherited.description?.takeIf(String::isNotBlank) ?: "继承属性",
            ),
            requiredPath && inherited.required,
        )
    }
    val field = lineage.mapNotNull { model ->
        model.fields.singleOrNull { candidate -> candidate.fieldCode == propertyName }?.let { value -> model to value }
    }.singleOrNull()
    if (field != null) {
        val (owner, value) = field
        return ApiDtoProperty(
            value.kotlinType.toApiSchema(value.enumStorage, owner.discriminatorEnumValues(value, modelCatalog))
                .copy(description = value.remark ?: value.label),
            requiredPath && value.required,
        )
    }
    val formula = lineage.flatMap { model -> model.entityConfig.formulaProperties }
        .singleOrNull { candidate -> candidate.propertyCode == propertyName }
    if (formula != null) {
        return ApiDtoProperty(
            formula.kotlinType.toApiSchema().copy(description = formula.description ?: formula.label),
            requiredPath && !formula.nullable,
        )
    }
    val transient = lineage.flatMap { model -> model.entityConfig.transientProperties }
        .singleOrNull { candidate -> candidate.propertyCode == propertyName }
    if (transient != null) {
        return ApiDtoProperty(
            transient.kotlinType.toApiSchema().copy(description = transient.description ?: transient.label),
            requiredPath && !transient.nullable,
        )
    }
    val relation = lineage.flatMap(LowcodeModelMeta::relations)
        .singleOrNull { candidate -> candidate.idViewCode() == propertyName }
        ?: return null
    return if (relation.relationKind.isReference()) {
        ApiDtoProperty(
            "Long".toApiSchema().copy(description = "${relation.label}编号"),
            requiredPath && relation.required,
        )
    } else {
        ApiDtoProperty(
            "List<Long>".toApiSchema().copy(description = "${relation.label}编号集合"),
            requiredPath,
        )
    }
}

private data class ApiDtoProperty(
    val schema: LsiLowcodeApiSchema,
    val required: Boolean,
)

private fun String.toApiSchema(
    enumStorage: LowcodeEnumStorage? = null,
    enumValues: List<String> = emptyList(),
): LsiLowcodeApiSchema {
    when (enumStorage) {
        LowcodeEnumStorage.NAME -> return LsiLowcodeApiSchema(type = "string", enumValues = enumValues)
        LowcodeEnumStorage.ORDINAL -> return LsiLowcodeApiSchema(type = "integer", format = "int32")
        null -> Unit
    }
    return when (trim().removeSuffix("?").substringAfterLast('.').lowercase()) {
    "list<long>" -> LsiLowcodeApiSchema(type = "array", items = LsiLowcodeApiSchema(type = "integer", format = "int64"))
    "string", "text" -> LsiLowcodeApiSchema(type = "string")
    "long" -> LsiLowcodeApiSchema(type = "integer", format = "int64")
    "int", "integer" -> LsiLowcodeApiSchema(type = "integer", format = "int32")
    "double" -> LsiLowcodeApiSchema(type = "number", format = "double")
    "bigdecimal", "decimal" -> LsiLowcodeApiSchema(type = "number")
    "boolean" -> LsiLowcodeApiSchema(type = "boolean")
    "localdate" -> LsiLowcodeApiSchema(type = "string", format = "date")
    "localdatetime" -> LsiLowcodeApiSchema(type = "string", format = "date-time")
    else -> LsiLowcodeApiSchema(type = "object")
}
}
