package site.addzero.studio.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable enum class ModelKind { ENTITY, MAPPED_SUPERCLASS, EMBEDDABLE }
@Serializable enum class EntitySourceMode { GENERATED, EXISTING }
@Serializable enum class EntityBaseMode { DEFAULT, INHERITED }
@Serializable enum class InheritanceStrategy { SINGLE_TABLE, JOINED }
@Serializable enum class EntityInstantiability { AUTO, ABSTRACT, INSTANTIABLE }
@Serializable enum class JoinedTableDissociateAction { DELETE, LAX }
@Serializable enum class FormulaKind { KOTLIN, SQL }
@Serializable enum class TransientKind { DRAFT, RESOLVER }
@Serializable enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }
@Serializable enum class OperationTransport { HTTP, SSE, WEBSOCKET }
@Serializable enum class OperationImplementation { GENERATED, EXISTING_REST }
@Serializable enum class ApiParameterLocation { PATH, QUERY, HEADER, COOKIE }
@Serializable enum class QueryLogic { AND, OR }
@Serializable enum class QueryOperator {
    EQ, NE, LIKE, STARTS_WITH, ENDS_WITH, GT, GE, LT, LE, IN, NOT_IN, BETWEEN, TIME_RANGE, NULL_STATE, ZERO_STATE,
}
@Serializable enum class QueryValueType { SINGLE, RANGE, DATE_RANGE, DATETIME_RANGE, MULTIPLE }
@Serializable enum class RelationKind { MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY }
@Serializable enum class DissociateAction { NONE, LAX, CHECK, SET_NULL, DELETE }
@Serializable enum class EnumStorage { NAME, ORDINAL }
@Serializable enum class RouteOrderDirection { ASC, DESC }
@Serializable enum class AgentConfirmation { AUTO, REQUIRED }

@Serializable
data class InheritedPropertyCommand(
    val name: String,
    val kotlinType: String,
    val dbColumn: String,
    val required: Boolean = false,
    val id: Boolean = false,
    val description: String? = null,
    val defaultValue: String? = null,
)

@Serializable
data class FormulaPropertyCommand(
    val propertyCode: String,
    val label: String,
    val kotlinType: String,
    val kind: FormulaKind,
    val expression: String,
    val dependencies: List<String> = emptyList(),
    val nullable: Boolean = false,
    val description: String? = null,
)

@Serializable
data class TransientPropertyCommand(
    val propertyCode: String,
    val label: String,
    val kotlinType: String,
    val kind: TransientKind = TransientKind.DRAFT,
    val resolverValueType: String? = null,
    val nullable: Boolean = false,
    val description: String? = null,
    val dictionaryCode: String? = null,
)

@Serializable
data class InheritanceRootCommand(
    val strategy: InheritanceStrategy,
    val discriminatorField: String,
    val instantiability: EntityInstantiability,
    val discriminatorValue: String? = null,
    val joinedTableDissociateAction: JoinedTableDissociateAction = JoinedTableDissociateAction.DELETE,
)

@Serializable
data class InheritanceSubtypeCommand(
    val parentModelCode: String,
    val discriminatorValue: String? = null,
    val instantiability: EntityInstantiability = EntityInstantiability.AUTO,
)

@Serializable
data class EntityConfigCommand(
    val sourceMode: EntitySourceMode = EntitySourceMode.GENERATED,
    val sourceQualifiedName: String? = null,
    val baseMode: EntityBaseMode = EntityBaseMode.DEFAULT,
    val baseModels: List<String> = emptyList(),
    val superTypes: List<String> = emptyList(),
    val relationOrderings: Map<String, List<String>> = emptyMap(),
    val inheritedProperties: List<InheritedPropertyCommand> = emptyList(),
    val inheritedRelationCodes: List<String> = emptyList(),
    val formulaProperties: List<FormulaPropertyCommand> = emptyList(),
    val transientProperties: List<TransientPropertyCommand> = emptyList(),
    val microServiceName: String? = null,
    val inheritanceRoot: InheritanceRootCommand? = null,
    val inheritanceSubtype: InheritanceSubtypeCommand? = null,
)

@Serializable
data class DtoReference(
    val modelCode: String? = null,
    val dtoCode: String,
)

@Serializable
data class ApiSchema(
    val type: String? = null,
    val typeRef: DtoReference? = null,
    val format: String? = null,
    val description: String? = null,
    val properties: Map<String, ApiSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val items: ApiSchema? = null,
    val enumValues: List<String> = emptyList(),
    val oneOf: List<ApiSchema> = emptyList(),
)

@Serializable
data class ApiBodyCommand(
    val contentType: String = "application/json",
    val required: Boolean = true,
    val description: String? = null,
    val schema: ApiSchema = ApiSchema(),
)

@Serializable
data class ApiParameterCommand(
    val name: String,
    val location: ApiParameterLocation,
    val required: Boolean = false,
    val description: String? = null,
    val schema: ApiSchema,
)

@Serializable
data class CustomOperationCommand(
    val operationCode: String,
    val name: String,
    val description: String? = null,
    val path: String,
    val method: HttpMethod,
    val transport: OperationTransport = OperationTransport.HTTP,
    val implementation: OperationImplementation = OperationImplementation.GENERATED,
    val authenticated: Boolean = true,
    val permission: String? = null,
    val callContext: Boolean = false,
    val parameters: List<ApiParameterCommand> = emptyList(),
    val requestBody: ApiBodyCommand? = null,
    val responseBody: ApiBodyCommand? = null,
    val responseEnvelope: Boolean = true,
)

@Serializable
data class RoutePropertyCommand(
    val name: String,
    val type: String,
    val format: String? = null,
    val required: Boolean = false,
    val identifier: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
    val arrayItemType: String? = null,
    val description: String? = null,
    val dictionaryCode: String? = null,
)

@Serializable
data class RouteQueryFieldCommand(
    val propertyName: String,
    val parameterName: String,
    val operator: String,
    val type: String,
    val format: String? = null,
    val endParameterName: String? = null,
    val required: Boolean = false,
    val stateCases: List<JsonObject> = emptyList(),
    val description: String? = null,
)

@Serializable
data class RouteTreeCommand(
    val parentIdProperty: String,
    val childrenProperty: String,
    val keywordProperty: String,
    val sortProperty: String? = null,
)

@Serializable
data class RouteExcelCommand(
    val importEnabled: Boolean = false,
    val exportEnabled: Boolean = false,
    val customImport: Boolean = false,
    val customExport: Boolean = false,
    val fileName: String,
    val templateFileName: String,
    val sheetName: String,
    val templateSheetName: String = sheetName,
    val importColumns: List<RoutePropertyCommand> = emptyList(),
    val exportColumns: List<RoutePropertyCommand> = emptyList(),
)

@Serializable
data class NamedDtoSchemaCommand(
    val ref: DtoReference,
    val className: String,
    val properties: Map<String, ApiSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val description: String? = null,
)

@Serializable
data class RouteOrderCommand(
    val propertyName: String,
    val direction: RouteOrderDirection = RouteOrderDirection.ASC,
)

@Serializable
data class AgentExposureCommand(
    val operations: Map<String, AgentConfirmation> = emptyMap(),
)

@Serializable
data class RouteCommand(
    val packageName: String = "",
    val qualifiedName: String = "",
    val className: String = "",
    val modelCode: String? = null,
    val description: String? = null,
    val path: String,
    val aliasPaths: List<String> = emptyList(),
    val fetchPaths: List<String> = emptyList(),
    val excludePaths: List<String> = emptyList(),
    val enabledOperations: List<String> = emptyList(),
    val tree: RouteTreeCommand? = null,
    val excel: RouteExcelCommand? = null,
    val properties: List<RoutePropertyCommand> = emptyList(),
    val queryFields: List<RouteQueryFieldCommand> = emptyList(),
    val defaultOrders: List<RouteOrderCommand> = emptyList(),
    val customOperations: List<CustomOperationCommand> = emptyList(),
    val dtoSchemas: List<NamedDtoSchemaCommand> = emptyList(),
    val agentExposure: AgentExposureCommand = AgentExposureCommand(),
)

@Serializable
data class FieldCommand(
    val id: Long? = null,
    val orderNo: Int,
    val fieldCode: String,
    val label: String,
    val kotlinType: String,
    val dbColumn: String,
    val required: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
    val listVisible: Boolean = true,
    val formVisible: Boolean = true,
    val formControl: String = "INPUT",
    val dictCode: String? = null,
    val enumStorage: EnumStorage? = null,
    val defaultValue: String? = null,
    val remark: String? = null,
    val serialized: Boolean = false,
    val key: Boolean = false,
    val maxLength: Int? = null,
)

@Serializable
data class QueryConditionCommand(
    val id: Long? = null,
    val orderNo: Int,
    val fieldCode: String,
    val operator: QueryOperator,
    val valueType: QueryValueType,
    val paramName: String? = null,
)

@Serializable
data class QueryCommand(
    val id: Long? = null,
    val orderNo: Int,
    val queryCode: String,
    val label: String,
    val logic: QueryLogic = QueryLogic.AND,
    val items: List<QueryConditionCommand> = emptyList(),
)

@Serializable
data class RelationCommand(
    val id: Long? = null,
    val orderNo: Int,
    val relationCode: String,
    val label: String,
    val relationType: RelationKind,
    val targetModelId: Long? = null,
    val targetModelCode: String? = null,
    val joinColumn: String? = null,
    val mappedBy: String? = null,
    val joinTable: String? = null,
    val joinTableJoinColumn: String? = null,
    val joinTableInverseColumn: String? = null,
    val joinTableFilterColumn: String? = null,
    val joinTableFilterValues: List<String> = emptyList(),
    val dissociateAction: DissociateAction = DissociateAction.NONE,
    val required: Boolean = false,
    val createWritable: Boolean = true,
    val updateWritable: Boolean = true,
    val listVisible: Boolean = true,
    val formVisible: Boolean = true,
)

@Serializable
data class ModelCommand(
    val id: Long? = null,
    val featureId: Long,
    val modelCode: String,
    val name: String,
    val packageName: String = "",
    val className: String,
    val tableName: String,
    val modelType: ModelKind = ModelKind.ENTITY,
    val status: Int = 1,
    val version: Int = 1,
    val contributorId: String? = null,
    val entityConfig: EntityConfigCommand = EntityConfigCommand(),
    val routeConfig: RouteCommand? = null,
    val remark: String? = null,
    val fields: List<FieldCommand> = emptyList(),
    val queries: List<QueryCommand> = emptyList(),
    val relations: List<RelationCommand> = emptyList(),
)

@Serializable
data class ModelPageCommand(
    val pageNumber: Int = 1,
    val pageSize: Int = 20,
    val condition: ModelPageCondition = ModelPageCondition(),
)

@Serializable
data class ModelPageCondition(
    val featureId: Long? = null,
    val contributorId: String? = null,
    val keyword: String? = null,
    val modelCode: String? = null,
)
