package site.addzero.openapi.compiler

import site.addzero.platform.lowcode.generator.LsiLowcodeContract
import site.addzero.platform.lowcode.generator.LsiLowcodeCustomOperation
import site.addzero.platform.lowcode.generator.LsiLowcodeDtoSchema
import site.addzero.platform.lowcode.generator.LsiLowcodeDiscriminator
import site.addzero.platform.lowcode.generator.LsiLowcodeProperty
import site.addzero.platform.lowcode.generator.LsiLowcodeRoute
import site.addzero.platform.lowcode.generator.LowcodeMetadata
import site.addzero.platform.lowcode.generator.LowcodeSourceCompiler

/**
 * OpenAPI 编译器唯一接收的契约中间态。
 */
data class LsiOpenApiContract(
    val name: String,
    val description: String? = null,
    val schemaName: String,
    val path: String,
    val aliasPaths: List<String> = emptyList(),
    val enabledOperations: Set<String> = emptySet(),
    val properties: List<LsiOpenApiProperty> = emptyList(),
    val queryFields: List<LsiOpenApiQueryField> = emptyList(),
    val excel: LsiOpenApiExcel? = null,
    val customOperations: List<LsiLowcodeCustomOperation> = emptyList(),
    val dtoSchemas: List<LsiLowcodeDtoSchema> = emptyList(),
    val discriminator: LsiLowcodeDiscriminator? = null,
    val authenticated: Boolean = true,
    val modelCode: String? = null,
) {
    val paths: List<String>
        get() = listOf(path) + aliasPaths
}

data class LsiOpenApiProperty(
    val name: String,
    val type: String,
    val format: String? = null,
    val required: Boolean,
    val arrayItemType: String? = null,
    val description: String? = null,
    val referenceTargetModelCode: String? = null,
    val referencePropertyName: String? = null,
    val enumValues: List<String> = emptyList(),
    val maxLength: Int? = null,
    val identifier: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
)

data class LsiOpenApiQueryField(
    val parameterName: String,
    val operator: String,
    val type: String,
    val format: String? = null,
    val endParameterName: String? = null,
    val required: Boolean = false,
    val description: String? = null,
    val enumValues: List<String> = emptyList(),
)

data class LsiOpenApiExcel(
    val importEnabled: Boolean,
    val exportEnabled: Boolean,
    val fileName: String,
    val templateFileName: String,
)

/**
 * 将实体路由元数据收敛为 OpenAPI 编译输入。
 */
fun LsiLowcodeRoute.toLsiOpenApiContract(): LsiOpenApiContract = LsiOpenApiContract(
    name = className,
    modelCode = modelCode,
    description = description,
    schemaName = qualifiedName.replace('.', '_'),
    path = path,
    aliasPaths = aliasPaths,
    enabledOperations = enabledOperations,
    authenticated = authenticated,
    properties = properties.map(LsiLowcodeProperty::toLsiOpenApiProperty),
    queryFields = queryFields.map { field ->
        LsiOpenApiQueryField(
            parameterName = field.parameterName,
            operator = field.operator,
            type = field.type,
            format = field.format,
            endParameterName = field.endParameterName,
            required = field.required,
            description = field.description,
            enumValues = field.enumValues,
        )
    },
    excel = excel?.let { value ->
        LsiOpenApiExcel(
            importEnabled = value.importEnabled,
            exportEnabled = value.exportEnabled,
            fileName = value.fileName,
            templateFileName = value.templateFileName,
        )
    },
    customOperations = customOperations,
    dtoSchemas = dtoSchemas,
    discriminator = discriminator,
)

/**
 * 将独立业务契约收敛为 OpenAPI 编译输入。
 */
fun LsiLowcodeContract.toLsiOpenApiContract(): LsiOpenApiContract = LsiOpenApiContract(
    name = name,
    description = description,
    schemaName = qualifiedName.replace('.', '_'),
    path = path,
    customOperations = operations,
    dtoSchemas = dtoSchemas,
)

/**
 * 将低代码数据库元数据收敛为完整的 OpenAPI 编译输入。
 */
fun LowcodeMetadata.toLsiOpenApiContracts(): List<LsiOpenApiContract> {
    val routes = LowcodeSourceCompiler.resolveRouteBindings(
        bindings = routeBindings,
        models = models,
        modelsRequiringRoutes = emptyList(),
        dtoDefinitions = dtoDefinitions,
    )
    return routes.map { binding -> binding.route.toLsiOpenApiContract() } +
        contracts.map { contract ->
            LowcodeSourceCompiler.resolveContract(contract, models, dtoDefinitions).toLsiOpenApiContract()
        }
}

private fun LsiLowcodeProperty.toLsiOpenApiProperty(): LsiOpenApiProperty = LsiOpenApiProperty(
    name = name,
    type = type,
    format = format,
    required = required,
    identifier = identifier,
    createWritable = createWritable,
    updateWritable = updateWritable,
    arrayItemType = arrayItemType,
    description = description,
    referenceTargetModelCode = referenceTargetModelCode,
    referencePropertyName = referencePropertyName,
    enumValues = enumValues,
    maxLength = maxLength,
)
