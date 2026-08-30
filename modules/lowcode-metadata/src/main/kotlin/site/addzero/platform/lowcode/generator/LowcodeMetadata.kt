package site.addzero.platform.lowcode.generator

import site.addzero.dto.compiler.LsiDtoType
import site.addzero.dto.compiler.LsiDtoVisibility
import site.addzero.dto.compiler.LsiDtoAnnotation
import site.addzero.dto.compiler.LsiDtoDefaultValue
import site.addzero.validation.compiler.LsiValidationRule

/**
 * 低代码模型类型。
 */
enum class LowcodeModelKind {
    ENTITY,
    MAPPED_SUPERCLASS,
    EMBEDDABLE,
}

/**
 * 实体基础属性的生成方式。
 */
enum class LowcodeEntityBaseMode {
    DEFAULT,
    INHERITED,
}

/** 平台内置、可组合的 Jimmer 基模型。 */
enum class LowcodeBaseModel {
    BASE_ENTITY,
    SNOWFLAKE_ID,
    CREATE_TIME,
    UPDATE_TIME,
    AUDIT,
    TENANT,
    NAMESPACE,
    NODE,
    NAMED,
    DESCRIPTION,
    REMARK,
    SORT,
    STATUS,
    DELETED,
    DELETED_TIME,
    TREE_LEVEL,
    VERSION,
}

/** 实体 Kotlin 源码的归属方式。 */
enum class LowcodeEntitySourceMode {
    GENERATED,
    EXISTING,
}

/** Jimmer 表继承的物理存储策略。 */
enum class LowcodeInheritanceStrategy {
    SINGLE_TABLE,
    JOINED,
}

/** Jimmer 实体类型的可实例化策略。 */
enum class LowcodeEntityInstantiability {
    AUTO,
    ABSTRACT,
    INSTANTIABLE,
}

/** JOINED 分支表在删除根记录时的解关联策略。 */
enum class LowcodeJoinedTableDissociateAction {
    DELETE,
    LAX,
}

/**
 * Jimmer 表继承根模型配置。
 *
 * 判别字段必须是当前模型或基模型中声明的持久化字段。
 */
data class LsiLowcodeInheritanceRoot(
    val strategy: LowcodeInheritanceStrategy,
    val discriminatorField: String,
    val instantiability: LowcodeEntityInstantiability = LowcodeEntityInstantiability.ABSTRACT,
    val discriminatorValue: String? = null,
    val joinedTableDissociateAction: LowcodeJoinedTableDissociateAction =
        LowcodeJoinedTableDissociateAction.DELETE,
)

/** Jimmer 表继承子类型配置。 */
data class LsiLowcodeInheritanceSubtype(
    val parentModelCode: String,
    val discriminatorValue: String? = null,
    val instantiability: LowcodeEntityInstantiability = LowcodeEntityInstantiability.AUTO,
)

/**
 * 从父类型继承且可供 DTO、路由和迁移使用的属性。
 */
data class LsiLowcodeInheritedProperty(
    val name: String,
    val kotlinType: String,
    val dbColumn: String,
    val required: Boolean,
    val storageKotlinType: String? = null,
    val id: Boolean = false,
    val description: String? = null,
    val maxLength: Int? = null,
    val defaultValue: String? = null,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
    val dictionaryCode: String? = null,
)

/** 基模型编译描述，供源码、DTO、路由和 DDL 生成共享。 */
data class LsiLowcodeBaseModelDefinition(
    val model: LowcodeBaseModel,
    val qualifiedName: String,
    val additionalQualifiedNames: List<String> = emptyList(),
    val properties: List<LsiLowcodeInheritedProperty>,
)

/** 平台内置基模型目录。宿主自定义父类型仍通过 superTypes 和 inheritedProperties 扩展。 */
object LowcodeBaseModelCatalog {
    private val persistenceModelPackage =
        generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)
    private val auditModelPackage = "$persistenceModelPackage.generated.entity"
    private val auditPrincipalQualifiedName =
        generationTargetSymbol(GenerationTargetSymbols.AUDIT_PRINCIPAL)

    private val snowflakeIdProperties = listOf(
        property(
            "id",
            "Long",
            "id",
            required = true,
            id = true,
            createWritable = false,
            description = "主键。",
        ),
    )
    private val createTimeProperties = listOf(
        property(
            "createTime",
            "LocalDateTime",
            "create_time",
            required = true,
            createWritable = false,
            updateWritable = false,
            description = "创建时间。",
        ),
    )
    private val updateTimeProperties = listOf(
        property(
            "updateTime",
            "LocalDateTime",
            "update_time",
            createWritable = false,
            updateWritable = false,
            description = "更新时间。",
        ),
    )
    private val auditProperties = listOf(
        property(
            "updater",
            auditPrincipalQualifiedName,
            "updater",
            storageKotlinType = "Long",
            createWritable = false,
            updateWritable = false,
            description = "更新人。",
        ),
        property(
            "creator",
            auditPrincipalQualifiedName,
            "creator",
            storageKotlinType = "Long",
            createWritable = false,
            updateWritable = false,
            description = "创建人。",
        ),
    )
    private val namespaceProperty = property(
        "namespace",
        "String",
        "namespace",
        description = "命名空间。",
    )
    private val nameProperty = property(
        "name",
        "String",
        "name",
        description = "名称。",
    )
    private val nodeTypeProperty = property(
        "nodeType",
        "String",
        "node_type",
        required = true,
        createWritable = false,
        updateWritable = false,
        description = "节点类型。",
        dictionaryCode = "node_type",
    )

    val definitions: List<LsiLowcodeBaseModelDefinition> = listOf(
        definition(
            LowcodeBaseModel.BASE_ENTITY,
            "BaseEntity",
            snowflakeIdProperties + createTimeProperties + updateTimeProperties + auditProperties,
        ),
        definition(LowcodeBaseModel.SNOWFLAKE_ID, "BaseSnowflakeId", snowflakeIdProperties),
        definition(LowcodeBaseModel.CREATE_TIME, "BaseCreateTime", createTimeProperties),
        definition(LowcodeBaseModel.UPDATE_TIME, "BaseUpdateTime", updateTimeProperties),
        definition(
            LowcodeBaseModel.AUDIT,
            "BaseAudit",
            auditProperties,
            packageName = auditModelPackage,
        ),
        definition(
            LowcodeBaseModel.TENANT,
            "BaseTenant",
            property("tenantId", "Long", "tenant_id", description = "租户编号。"),
        ),
        definition(
            LowcodeBaseModel.NAMESPACE,
            "BaseNamespace",
            namespaceProperty,
        ),
        definition(
            LowcodeBaseModel.NODE,
            "BaseNode",
            snowflakeIdProperties + listOf(nameProperty, namespaceProperty, nodeTypeProperty),
        ),
        definition(
            LowcodeBaseModel.NAMED,
            "BaseNamed",
            nameProperty,
        ),
        definition(
            LowcodeBaseModel.DESCRIPTION,
            "BaseDescription",
            property("description", "String", "description", description = "描述。"),
        ),
        definition(
            LowcodeBaseModel.REMARK,
            "BaseRemark",
            property("remark", "String", "remark", description = "备注。"),
        ),
        definition(
            LowcodeBaseModel.SORT,
            "BaseSort",
            property("sort", "Int", "sort", description = "排序值。"),
        ),
        definition(
            LowcodeBaseModel.STATUS,
            "BaseStatus",
            property("status", "Int", "status", required = true, description = "状态。", defaultValue = "1"),
        ),
        definition(
            LowcodeBaseModel.DELETED,
            "BaseDeleted",
            property("deleted", "Int", "deleted", required = true, description = "逻辑删除标识。", defaultValue = "0"),
        ),
        definition(
            LowcodeBaseModel.DELETED_TIME,
            "BaseDeletedTime",
            property("deletedTime", "LocalDateTime", "deleted_time", description = "删除时间。"),
        ),
        definition(
            LowcodeBaseModel.TREE_LEVEL,
            "BaseTreeLevel",
            property("treeLevel", "Int", "tree_level", description = "树节点层级。"),
        ),
        definition(
            LowcodeBaseModel.VERSION,
            "BaseVersion",
            property("version", "Int", "version", required = true, description = "乐观锁版本。"),
        ),
    )

    private val definitionsByModel = definitions.associateBy(LsiLowcodeBaseModelDefinition::model)

    fun get(model: LowcodeBaseModel): LsiLowcodeBaseModelDefinition = definitionsByModel.getValue(model)

    private fun definition(
        model: LowcodeBaseModel,
        typeName: String,
        vararg properties: LsiLowcodeInheritedProperty,
    ) = definition(model, typeName, properties.toList())

    private fun definition(
        model: LowcodeBaseModel,
        typeName: String,
        properties: List<LsiLowcodeInheritedProperty>,
        additionalTypeNames: List<String> = emptyList(),
        packageName: String = persistenceModelPackage,
    ) = LsiLowcodeBaseModelDefinition(
        model = model,
        qualifiedName = "$packageName.$typeName",
        additionalQualifiedNames = additionalTypeNames.map { additionalTypeName ->
            "$packageName.$additionalTypeName"
        },
        properties = properties,
    )

    private fun property(
        name: String,
        kotlinType: String,
        dbColumn: String,
        required: Boolean = false,
        storageKotlinType: String? = null,
        id: Boolean = false,
        createWritable: Boolean = true,
        updateWritable: Boolean = true,
        description: String,
        dictionaryCode: String? = null,
        defaultValue: String? = null,
    ) = LsiLowcodeInheritedProperty(
        name = name,
        kotlinType = kotlinType,
        dbColumn = dbColumn,
        required = required,
        storageKotlinType = storageKotlinType,
        id = id,
        createWritable = createWritable,
        updateWritable = updateWritable,
        description = description,
        dictionaryCode = dictionaryCode,
        defaultValue = defaultValue,
    )

}

/**
 * Jimmer 实体生成配置。
 */
data class LsiLowcodeEntityConfig(
    val sourceMode: LowcodeEntitySourceMode = LowcodeEntitySourceMode.GENERATED,
    val sourceQualifiedName: String? = null,
    val sourceContributorId: String? = null,
    val baseMode: LowcodeEntityBaseMode = LowcodeEntityBaseMode.DEFAULT,
    val baseModels: List<LowcodeBaseModel> = emptyList(),
    val superTypes: List<String> = emptyList(),
    val inheritedProperties: List<LsiLowcodeInheritedProperty> = emptyList(),
    val inheritedRelationCodes: List<String> = emptyList(),
    val fieldAnnotations: Map<String, List<LsiDtoAnnotation>> = emptyMap(),
    val relationOrderings: Map<String, List<String>> = emptyMap(),
    val formulaProperties: List<LsiLowcodeFormulaProperty> = emptyList(),
    val transientProperties: List<LsiLowcodeTransientProperty> = emptyList(),
    val microServiceName: String? = null,
    val inheritanceRoot: LsiLowcodeInheritanceRoot? = null,
    val inheritanceSubtype: LsiLowcodeInheritanceSubtype? = null,
)

/** 解析显式基模型；旧 DEFAULT 元数据缺少 baseModels 时按约定使用 BaseEntity。 */
fun LsiLowcodeEntityConfig.resolvedBaseModels(
    includeConventionDefault: Boolean = true,
): List<LsiLowcodeBaseModelDefinition> {
    val selected = if (baseModels.isEmpty() && baseMode == LowcodeEntityBaseMode.DEFAULT && includeConventionDefault) {
        listOf(LowcodeBaseModel.BASE_ENTITY)
    } else {
        baseModels
    }
    val normalized = selected.distinct()
    val withoutEntityComponents = if (LowcodeBaseModel.BASE_ENTITY in normalized) {
        normalized.filterNot { model -> model in BASE_ENTITY_COMPONENT_MODELS }
    } else {
        normalized
    }
    val effective = if (LowcodeBaseModel.NODE in withoutEntityComponents) {
        withoutEntityComponents.filterNot { model -> model in NODE_COMPONENT_MODELS }
    } else {
        withoutEntityComponents
    }
    return effective.map(LowcodeBaseModelCatalog::get)
}

private val BASE_ENTITY_COMPONENT_MODELS = setOf(
    LowcodeBaseModel.SNOWFLAKE_ID,
    LowcodeBaseModel.CREATE_TIME,
    LowcodeBaseModel.UPDATE_TIME,
    LowcodeBaseModel.AUDIT,
)

private val NODE_COMPONENT_MODELS = setOf(
    LowcodeBaseModel.BASE_ENTITY,
    LowcodeBaseModel.SNOWFLAKE_ID,
    LowcodeBaseModel.NAMESPACE,
    LowcodeBaseModel.NAMED,
)

fun LsiLowcodeEntityConfig.resolvedSuperTypes(
    includeConventionDefault: Boolean = true,
): List<String> {
    val resolved = resolvedBaseModels(includeConventionDefault)
        .flatMap { definition -> listOf(definition.qualifiedName) + definition.additionalQualifiedNames } + superTypes
    return resolved.flatMap { qualifiedName ->
        if (qualifiedName.substringAfterLast('.') == BASE_ENTITY_TYPE_NAME) {
            listOf(qualifiedName, BASE_AUDIT_QUALIFIED_NAME)
        } else {
            listOf(qualifiedName)
        }
    }.distinct()
}

fun LsiLowcodeEntityConfig.resolvedInheritedProperties(
    includeConventionDefault: Boolean = true,
): List<LsiLowcodeInheritedProperty> =
    resolvedBaseModels(includeConventionDefault).flatMap(LsiLowcodeBaseModelDefinition::properties) + inheritedProperties

/** Jimmer 瞬态属性的取值方式。 */
enum class LowcodeTransientKind {
    /** 仅由 Jimmer Draft 动态填充。 */
    DRAFT,

    /** 通过生成的 [org.babyfish.jimmer.sql.kt.KTransientResolver] 约定批量计算。 */
    RESOLVER,
}

/** 不映射数据库列的 Jimmer 瞬态属性。 */
data class LsiLowcodeTransientProperty(
    val propertyCode: String,
    val label: String,
    val kotlinType: String,
    val kind: LowcodeTransientKind = LowcodeTransientKind.DRAFT,
    /**
     * Resolver 返回值类型。标量属性默认与 [kotlinType] 一致；
     * 实体关联属性需显式填写目标实体的 ID 类型。
     */
    val resolverValueType: String? = null,
    val nullable: Boolean = false,
    val description: String? = null,
    val dictionaryCode: String? = null,
)

/** Jimmer 计算属性实现方式。 */
enum class LowcodeFormulaKind {
    KOTLIN,
    SQL,
}

/** 不落库、可供 DTO 投影的 Jimmer Formula 属性。 */
data class LsiLowcodeFormulaProperty(
    val propertyCode: String,
    val label: String,
    val kotlinType: String,
    val kind: LowcodeFormulaKind,
    val expression: String,
    val dependencies: List<String> = emptyList(),
    val nullable: Boolean = false,
    val description: String? = null,
)

/**
 * 低代码 DTO 用途。
 */
enum class LowcodeDtoKind {
    INPUT,
    OUTPUT,
    STRUCTURE,
    /** 兼容历史元数据，新建 DTO 使用 [OUTPUT]。 */
    VIEW,
    ;

    val isOutput: Boolean
        get() = this == OUTPUT || this == VIEW
}

/** DTO 字段集合的元数据选择策略。 */
enum class LowcodeDtoSelectionMode {
    EXPLICIT,
    ALL_SCALAR_FIELDS,
    ALL_TABLE_FIELDS,
    ALL_DEEP_FIELDS,
}

/**
 * DTO 字段的可空覆盖策略。
 */
enum class LowcodeDtoNullability {
    INHERIT,
    NULLABLE,
    NON_NULL,
}

/**
 * 低代码 DTO 字段投影。
 */
data class LsiLowcodeDtoField(
    val name: String,
    val sourcePath: String = name,
    val nullability: LowcodeDtoNullability = LowcodeDtoNullability.INHERIT,
    val schema: LsiLowcodeApiSchema? = null,
    val kotlinType: LsiDtoType? = null,
    val validations: List<LsiValidationRule> = emptyList(),
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val defaultValue: LsiDtoDefaultValue? = null,
    val description: String? = null,
)

/**
 * 从模型属性生成的命名 DTO。
 */
data class LsiLowcodeDto(
    val dtoCode: String,
    val className: String,
    val kind: LowcodeDtoKind,
    val fields: List<LsiLowcodeDtoField> = emptyList(),
    val selectionMode: LowcodeDtoSelectionMode = LowcodeDtoSelectionMode.EXPLICIT,
    val excludedPaths: List<String> = emptyList(),
)

/** 功能级 DTO 定义，可选择从实体投影或直接声明结构。 */
data class LsiLowcodeDtoDefinition(
    val dtoCode: String,
    val name: String,
    val packageName: String,
    val className: String,
    val kind: LowcodeDtoKind,
    val visibility: LsiDtoVisibility = LsiDtoVisibility.PUBLIC,
    val contributorId: String? = null,
    val status: Int = 1,
    val version: Int = 1,
    val description: String? = null,
    val annotations: List<LsiDtoAnnotation> = emptyList(),
    val superTypes: List<LsiDtoType> = emptyList(),
    val sourceModelCode: String? = null,
    val selectionMode: LowcodeDtoSelectionMode = LowcodeDtoSelectionMode.EXPLICIT,
    val excludedPaths: List<String> = emptyList(),
    val fields: List<LsiLowcodeDtoField> = emptyList(),
    val featurePackageName: String = packageName,
) {
    val ref: LsiLowcodeDtoRef
        get() = LsiLowcodeDtoRef(dtoCode = dtoCode)

    fun toProjection(): LsiLowcodeDto = LsiLowcodeDto(
        dtoCode = dtoCode,
        className = className,
        kind = kind,
        fields = fields,
        selectionMode = selectionMode,
        excludedPaths = excludedPaths,
    )
}

/**
 * Jimmer 关联类型。
 */
enum class LowcodeRelationKind {
    MANY_TO_ONE,
    ONE_TO_MANY,
    ONE_TO_ONE,
    MANY_TO_MANY,
}

/** Jimmer 在删除关联目标时采用的解关联策略。 */
enum class LowcodeDissociateAction {
    NONE,
    LAX,
    CHECK,
    SET_NULL,
    DELETE,
}

fun LowcodeRelationKind.isReference(): Boolean =
    this == LowcodeRelationKind.MANY_TO_ONE || this == LowcodeRelationKind.ONE_TO_ONE

/**
 * 查询操作符。
 */
enum class LowcodeQueryOperator {
    EQ,
    NE,
    LIKE,
    STARTS_WITH,
    ENDS_WITH,
    GT,
    GE,
    LT,
    LE,
    IN,
    NOT_IN,
    BETWEEN,
    TIME_RANGE,
    NULL_STATE,
    ZERO_STATE,
}

/**
 * 查询组逻辑。
 */
enum class LowcodeQueryLogic {
    AND,
    OR,
}

/**
 * 查询值类型。
 */
enum class LowcodeQueryValueType {
    SINGLE,
    RANGE,
    DATE_RANGE,
    DATETIME_RANGE,
    MULTIPLE,
}

/**
 * 低代码模型元数据。
 */
data class LowcodeModelMeta(
    val id: Long,
    val modelCode: String,
    val name: String,
    val packageName: String,
    val className: String,
    val tableName: String,
    val kind: LowcodeModelKind,
    val status: Int,
    val version: Int,
    val contributorId: String? = null,
    val entityConfig: LsiLowcodeEntityConfig = LsiLowcodeEntityConfig(),
    val dtoDefinitions: List<LsiLowcodeDto> = emptyList(),
    val routeConfig: LsiLowcodeRoute? = null,
    val fields: List<LowcodeFieldMeta>,
    val queries: List<LowcodeQueryMeta>,
    val relations: List<LowcodeRelationMeta>,
    val featurePackageName: String = packageName,
) {
    init {
        require(modelCode.isNotBlank()) { "模型编码不能为空" }
        require(!packageName.contains(".generated")) { "元数据包名只配置业务根包，generated 由生成器追加" }
    }
}

fun LowcodeModelMeta.ownsEntitySource(): Boolean =
    entityConfig.sourceMode == LowcodeEntitySourceMode.GENERATED

fun LsiLowcodeEntityConfig.resolveEntityQualifiedName(
    modelCode: String,
    packageName: String,
    className: String,
    featurePackageName: String = packageName,
): String = when (sourceMode) {
    LowcodeEntitySourceMode.GENERATED -> featurePackageName.generatedLayout()
        .qualifiedName(LowcodeGeneratedResourceKind.ENTITY, className)
    LowcodeEntitySourceMode.EXISTING -> requireNotNull(sourceQualifiedName?.takeIf(String::isNotBlank)) {
        "复用既有实体源码的模型 $modelCode 必须配置 sourceQualifiedName"
    }
}

fun LowcodeModelMeta.entityQualifiedName(): String =
    entityConfig.resolveEntityQualifiedName(modelCode, packageName, className, featurePackageName)

fun LowcodeModelMeta.entityPackageName(): String = entityQualifiedName().substringBeforeLast('.')

fun LowcodeModelMeta.entityClassName(): String = entityQualifiedName().substringAfterLast('.')

/** 返回实体源码的实际 contributor 归属，未单独配置时沿用模型 contributor。 */
fun LowcodeModelMeta.entitySourceContributorId(): String? = entityConfig.sourceContributorId ?: contributorId

private const val BASE_ENTITY_TYPE_NAME = "BaseEntity"
private val BASE_AUDIT_QUALIFIED_NAME =
    "${generationTargetSymbol(GenerationTargetSymbols.PERSISTENCE_MODEL_PACKAGE)}.generated.entity.BaseAudit"

/**
 * 低代码字段元数据。
 */
data class LowcodeFieldMeta(
    val id: Long,
    val modelId: Long,
    val orderNo: Int,
    val fieldCode: String,
    val label: String,
    val kotlinType: String,
    val dbColumn: String,
    val required: Boolean,
    val listVisible: Boolean,
    val formVisible: Boolean,
    val formControl: String,
    val dictCode: String?,
    val defaultValue: String?,
    val remark: String?,
    val serialized: Boolean = false,
    val maxLength: Int? = null,
    val enumStorage: LowcodeEnumStorage? = null,
    val key: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
)

/** 枚举属性在数据库列中的持久化方式。 */
enum class LowcodeEnumStorage {
    NAME,
    ORDINAL,
}

/** 可由编译器生成强类型枚举的字典快照。 */
data class LowcodeDictionaryMeta(
    val dictionaryCode: String,
    val name: String,
    val generateEnum: Boolean,
    val ownerModelCode: String?,
    val enumClassName: String?,
    val enumStorage: LowcodeEnumStorage,
    val items: List<LowcodeDictionaryItemMeta>,
)

/** 字典项及其稳定的 Kotlin 枚举常量名。 */
data class LowcodeDictionaryItemMeta(
    val orderNo: Int,
    val value: String,
    val label: String,
    val enumName: String?,
)

/**
 * 低代码查询组元数据。
 */
data class LowcodeQueryMeta(
    val id: Long,
    val modelId: Long,
    val orderNo: Int,
    val queryCode: String,
    val label: String,
    val logic: LowcodeQueryLogic,
    val items: List<LowcodeQueryConditionMeta>,
)

/**
 * 低代码查询条件元数据。
 */
data class LowcodeQueryConditionMeta(
    val id: Long,
    val queryId: Long,
    val orderNo: Int,
    val fieldCode: String,
    val operator: LowcodeQueryOperator,
    val valueType: LowcodeQueryValueType,
    val paramName: String?,
)

/**
 * 低代码关联元数据。
 */
data class LowcodeRelationMeta(
    val id: Long,
    val modelId: Long,
    val orderNo: Int,
    val relationCode: String,
    val label: String,
    val relationKind: LowcodeRelationKind,
    val targetModelId: Long,
    val targetModelCode: String?,
    val targetPackageName: String?,
    val targetClassName: String?,
    val joinColumn: String?,
    val mappedBy: String?,
    val joinTable: String?,
    val joinTableJoinColumn: String?,
    val joinTableInverseColumn: String?,
    val joinTableFilterColumn: String? = null,
    val joinTableFilterValues: List<String> = emptyList(),
    val dissociateAction: LowcodeDissociateAction = LowcodeDissociateAction.NONE,
    val required: Boolean,
    val listVisible: Boolean,
    val formVisible: Boolean,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
)

/**
 * 生成文件。
 */
data class LowcodeGeneratedFile(
    val packageName: String,
    val fileName: String,
    val relativePath: String,
    val content: String,
    val extensionName: String = "kt",
    val kind: LowcodeGeneratedFileKind = LowcodeGeneratedFileKind.SOURCE,
) {
    init {
        require(content.lineSequence().take(STUDIO_GENERATED_MARKER_LINE_LIMIT).any { line ->
            STUDIO_GENERATED_MARKER in line
        }) {
            "Studio 生成文件缺少 generated by studio 注释: $relativePath"
        }
    }
}

/**
 * 生成文件的交付语义。
 */
enum class LowcodeGeneratedFileKind {
    SOURCE,
    /** 仅写入构建任务输出目录并参与编译，不物化到源码树。 */
    COMPILED_SOURCE,
    /** 首次生成后由业务代码维护。 */
    CONTROLLER_SCAFFOLD,
    /** 首次生成后允许承载业务逻辑，后续生成只校验契约签名，不覆盖源码。 */
    SERVICE_IMPLEMENTATION_SCAFFOLD,
    /** 首次生成后完全由 IDE 中的业务源码维护。 */
    CONVENTION_FILE_SCAFFOLD,
    CONTRACT_CONTROLLER,
    DOCUMENTATION,
    RUNTIME_METADATA,
}
