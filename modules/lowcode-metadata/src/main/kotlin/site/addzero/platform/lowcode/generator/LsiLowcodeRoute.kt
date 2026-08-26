package site.addzero.platform.lowcode.generator

import com.fasterxml.jackson.annotation.JsonIgnore
import site.addzero.dto.compiler.LsiDtoType

data class LsiLowcodeRoute(
    val packageName: String = "",
    val qualifiedName: String = "",
    val className: String,
    val displayName: String? = null,
    val description: String?,
    val path: String,
    val generateController: Boolean = true,
    val aliasPaths: List<String> = emptyList(),
    val fetchPaths: List<String> = emptyList(),
    val excludePaths: List<String> = emptyList(),
    val enabledOperations: Set<String>,
    val tree: LsiLowcodeTree? = null,
    val excel: LsiLowcodeExcel? = null,
    val properties: List<LsiLowcodeProperty>,
    val queryFields: List<LsiLowcodeQueryField> = emptyList(),
    val defaultOrders: List<LsiLowcodeOrder> = emptyList(),
    val customOperations: List<LsiLowcodeCustomOperation> = emptyList(),
    val dtoSchemas: List<LsiLowcodeDtoSchema> = emptyList(),
    val discriminator: LsiLowcodeDiscriminator? = null,
    val authenticated: Boolean = true,
    val featurePackageName: String = packageName,
    val modelCode: String? = null,
    val agentExposure: LsiAgentExposure = LsiAgentExposure(),
)

data class LsiLowcodeOrder(
    val propertyName: String,
    val direction: LsiLowcodeOrderDirection = LsiLowcodeOrderDirection.ASC,
)

enum class LsiLowcodeOrderDirection {
    ASC,
    DESC,
}

fun LsiLowcodeRoute.requiresEntityService(): Boolean =
    enabledOperations.isNotEmpty() || excel?.let { value -> value.importEnabled || value.exportEnabled } == true

fun LsiLowcodeRoute.generatesEntityController(): Boolean =
    generateController && requiresEntityService()

/**
 * 组织模型和独立契约的功能目录。
 */
data class LsiLowcodeFeature(
    val featureCode: String,
    val name: String,
    val description: String? = null,
    val packageName: String,
    val contributorId: String,
    val modelCodes: List<String> = emptyList(),
    val dtoCodes: List<String> = emptyList(),
    val contractCodes: List<String> = emptyList(),
    val featureId: Long = 0,
    val libraryId: Long = 0,
    val parentId: Long? = null,
)

/**
 * 不依赖实体模型的业务接口契约。
 */
data class LsiLowcodeContract(
    val contractCode: String,
    val name: String,
    val description: String? = null,
    val packageName: String,
    val className: String,
    val path: String,
    val contributorId: String? = null,
    val operations: List<LsiLowcodeCustomOperation> = emptyList(),
    val dtoSchemas: List<LsiLowcodeDtoSchema> = emptyList(),
    val featurePackageName: String = packageName,
    val agentExposure: LsiAgentExposure = LsiAgentExposure(),
) {
    @get:JsonIgnore
    val qualifiedName: String
        get() = "$packageName.$className"
}

data class LsiAgentExposure(
    val operations: Map<String, LsiAgentOperationExposure> = emptyMap(),
)

data class LsiAgentOperationExposure(
    val confirmation: LsiAgentConfirmation = LsiAgentConfirmation.REQUIRED,
)

enum class LsiAgentConfirmation {
    AUTO,
    REQUIRED,
}

data class LsiLowcodeCustomOperation(
    val operationCode: String,
    val name: String,
    val description: String? = null,
    val path: String,
    val method: LowcodeHttpMethod = LowcodeHttpMethod.POST,
    val transport: LowcodeOperationTransport = LowcodeOperationTransport.HTTP,
    val authenticated: Boolean = true,
    /** 领域 Service 方法是否为 suspend。 */
    val suspending: Boolean = true,
    val permission: String? = null,
    val callContext: Boolean = false,
    val parameters: List<LsiLowcodeApiParameter> = emptyList(),
    val requestBody: LsiLowcodeApiBody? = null,
    val responseBody: LsiLowcodeApiBody? = null,
    val responseEnvelope: Boolean = true,
    val implementation: LowcodeOperationImplementation = LowcodeOperationImplementation.GENERATED,
) {
    init {
        require(parameters.none { parameter ->
            parameter.schema.kotlinType?.qualifiedName?.substringAfterLast('.') == CALL_CONTEXT_TYPE_NAME
        }) {
            "调用上下文不能声明为接口参数，请使用 callContext 操作语义"
        }
        require(transport != LowcodeOperationTransport.INTERNAL || implementation == LowcodeOperationImplementation.SERVICE_ONLY) {
            "INTERNAL 操作只能生成 Service 契约: $operationCode"
        }
        require(implementation != LowcodeOperationImplementation.SERVICE_ONLY || transport == LowcodeOperationTransport.INTERNAL) {
            "SERVICE_ONLY 操作必须使用 INTERNAL 传输: $operationCode"
        }
    }

    @get:JsonIgnore
    val isGenerated: Boolean
        get() = implementation == LowcodeOperationImplementation.GENERATED

    /** 是否由编译器生成领域 Service 方法。 */
    @get:JsonIgnore
    val generatesService: Boolean
        get() = implementation != LowcodeOperationImplementation.EXISTING_REST
}

private const val CALL_CONTEXT_TYPE_NAME = "CallContext"

enum class LowcodeHttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
}

enum class LowcodeOperationTransport {
    HTTP,
    SSE,
    WEBSOCKET,
    INTERNAL,
}

/** REST 操作实现来源。 */
enum class LowcodeOperationImplementation {
    GENERATED,
    /** 生成无传输层的内部 Service 契约。 */
    SERVICE_ONLY,
    EXISTING_REST,
}

data class LsiLowcodeApiParameter(
    val name: String,
    val location: LowcodeApiParameterLocation,
    val required: Boolean = false,
    val description: String? = null,
    val schema: LsiLowcodeApiSchema,
)

enum class LowcodeApiParameterLocation {
    PATH,
    QUERY,
    HEADER,
    COOKIE,
}

data class LsiLowcodeApiBody(
    val contentType: String = "application/json",
    val required: Boolean = true,
    val schema: LsiLowcodeApiSchema,
    val description: String? = null,
)

/** 引用实体投影 DTO 或独立 DTO；仅独立 DTO 可以省略 [modelCode]。 */
data class LsiLowcodeDtoRef(
    val modelCode: String? = null,
    val dtoCode: String = "",
)

data class LsiLowcodeDiscriminator(
    val propertyName: String,
    val mapping: Map<String, LsiLowcodeDtoRef>,
)

/**
 * 可注册为 OpenAPI component 的命名 DTO 结构。
 */
data class LsiLowcodeDtoSchema(
    val ref: LsiLowcodeDtoRef,
    val className: String,
    val properties: Map<String, LsiLowcodeApiSchema>,
    val required: Set<String> = emptySet(),
    val validations: Map<String, List<site.addzero.validation.compiler.LsiValidationRule>> = emptyMap(),
    val description: String? = null,
) {
    @get:JsonIgnore
    val schemaName: String
        get() = ref.componentSchemaName()
}

fun LsiLowcodeDtoRef.componentSchemaName(): String = when {
    modelCode.isNullOrBlank() -> dtoCode
    dtoCode.isBlank() -> "${modelCode}_entity"
    else -> "${modelCode}_${dtoCode}"
}

data class LsiLowcodeApiSchema(
    val type: String? = null,
    val typeRef: LsiLowcodeDtoRef? = null,
    /** 仅供 SERVICE_ONLY 的 Kotlin 内部契约使用，不参与 REST/OpenAPI。 */
    val kotlinType: LsiDtoType? = null,
    val format: String? = null,
    val description: String? = null,
    val properties: Map<String, LsiLowcodeApiSchema> = emptyMap(),
    val required: Set<String> = emptySet(),
    val items: LsiLowcodeApiSchema? = null,
    val enumValues: List<String> = emptyList(),
    val oneOf: List<LsiLowcodeApiSchema> = emptyList(),
)

data class LsiLowcodeTree(
    val parentIdProperty: String,
    val childrenProperty: String,
    val keywordProperty: String,
    val sortProperty: String?,
)

data class LsiLowcodeExcel(
    val importEnabled: Boolean,
    val exportEnabled: Boolean,
    val customImport: Boolean = false,
    val customExport: Boolean,
    val fileName: String,
    val templateFileName: String,
    val sheetName: String,
    val templateSheetName: String = sheetName,
    val importColumns: List<LsiLowcodeProperty>,
    val exportColumns: List<LsiLowcodeProperty>,
)

fun LsiLowcodeRoute.requireValidExcelMetadata() {
    val excel = excel ?: return
    require(!excel.importEnabled || excel.importColumns.isNotEmpty()) {
        "实体 $qualifiedName 的 Excel 导入字段不能为空"
    }
    require(!excel.exportEnabled || excel.exportColumns.isNotEmpty()) {
        "实体 $qualifiedName 的 Excel 导出字段不能为空"
    }
}

data class LsiLowcodeProperty(
    val name: String,
    val type: String,
    val format: String?,
    val required: Boolean,
    val arrayItemType: String?,
    val description: String?,
    val dictionaryCode: String? = null,
    val referenceTargetModelCode: String? = null,
    val referencePropertyName: String? = null,
    val enumValues: List<String> = emptyList(),
    val maxLength: Int? = null,
    val identifier: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
)

data class LsiLowcodeQueryField(
    val propertyName: String,
    val parameterName: String,
    val operator: String,
    val type: String,
    val format: String?,
    val endParameterName: String? = null,
    val required: Boolean = false,
    val stateCases: List<LsiLowcodeStateCase> = emptyList(),
    val description: String? = null,
    val enumValues: List<String> = emptyList(),
)

data class LsiLowcodeStateCase(
    val parameterValue: String,
    val operator: String,
    val expression: String,
)

data class LowcodeGeneratedSource(
    val packageName: String,
    val fileName: String,
    val content: String,
)
