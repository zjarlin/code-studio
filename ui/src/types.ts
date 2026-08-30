export type JsonPrimitive = boolean | number | string | null

export type JsonValue = JsonPrimitive | JsonObject | JsonValue[]

export interface JsonObject {
  [key: string]: JsonValue | undefined
}

export interface JsonSchema extends JsonObject {
  $defs?: Record<string, JsonSchema>
  $ref?: string
  default?: JsonValue
  description?: string
  enum?: JsonPrimitive[]
  items?: JsonSchema
  oneOf?: JsonSchema[]
  properties?: Record<string, JsonSchema>
  required?: string[]
  title?: string
  type?: string | string[]
}

export interface OpenApiDocument {
  components?: {
    schemas?: Record<string, JsonSchema>
  }
}

export interface CommonResult<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  rows: T[]
  totalRowCount: number
  totalPageCount: number
}

export interface LowcodeModelSummary {
  id: number | string
  featureId: number | string
  modelCode: string
  modelType: string
  name: string
  packageName?: string
  className?: string
  tableName?: string
  status: number
  contributorId?: string | null
  version: number
  entityConfig?: LowcodeEntityConfigSummary
  fields?: LowcodeModelFieldSummary[]
  relations?: LowcodeModelRelationSummary[]
  routeConfig?: LowcodeRouteSummary | null
  remark?: string | null
}

export type LowcodeModelFieldSummary = Pick<
  LowcodeFieldDraft,
  'fieldCode' | 'label' | 'kotlinType' | 'required'
>

export type LowcodeModelRelationSummary = Pick<
  LowcodeRelationDraft,
  'relationCode' | 'label' | 'relationType' | 'required'
>

export interface LowcodeEntityConfigSummary {
  baseMode: LowcodeEntityBaseMode
  baseModels: LowcodeBaseModel[]
  inheritedProperties: Array<Pick<
    LowcodeInheritedPropertyDraft,
    'name' | 'description' | 'kotlinType' | 'required'
  >>
  formulaProperties: Array<Pick<
    LowcodeFormulaPropertyDraft,
    'propertyCode' | 'label' | 'kotlinType' | 'nullable'
  >>
  transientProperties: Array<Pick<
    LowcodeTransientPropertyDraft,
    'propertyCode' | 'label' | 'kotlinType' | 'nullable'
  >>
  inheritanceRoot?: Pick<
    LowcodeInheritanceRootDraft,
    'strategy' | 'discriminatorField' | 'instantiability' | 'discriminatorValue' | 'joinedTableDissociateAction'
  > | null
  inheritanceSubtype?: Pick<
    LowcodeInheritanceSubtypeDraft,
    'parentModelCode' | 'discriminatorValue' | 'instantiability'
  > | null
}

export interface LowcodeDtoResourceSummary {
  id: number | string
  featureId: number | string
  dtoCode: string
  name: string
  packageName: string
  className: string
  kind: LowcodeDtoKind
  visibility?: 'PUBLIC' | 'INTERNAL'
  sourceModel?: LowcodeModelSummary | null
  selectionMode: LowcodeDtoSelectionMode
  excludedPaths: string[]
  fields: LowcodeDtoFieldDraft[]
  annotations?: LsiDtoAnnotationDraft[]
  superTypes?: LsiDtoTypeDraft[]
  contributorId?: string | null
  status: number
  version: number
  description?: string | null
}

export interface LowcodeApiOperationSummary {
  operationCode: string
  name: string
  description?: string | null
  path: string
  method: LowcodeHttpMethod
  transport: LowcodeOperationTransport
}

export interface LowcodeRouteSummary {
  className: string
  qualifiedName: string
  description?: string | null
  path: string
  aliasPaths: string[]
  fetchPaths: string[]
  excludePaths: string[]
  enabledOperations: string[]
  customOperations?: LowcodeApiOperationSummary[]
}

export interface LowcodeApiContractSummary {
  id: number | string
  featureId: number | string
  contractCode: string
  name: string
  packageName: string
  className: string
  path: string
  contributorId?: string | null
  status: number
  version: number
  description?: string | null
  operations?: LowcodeApiOperationSummary[]
}

export interface AgentDefinitionSummary {
  id: number | string
  agentCode: string
  name: string
  modelCode: string
  status: number
  version: number
}

export interface AgentProviderSettingsView {
  baseUrl: string
  apiKeyConfigured: boolean
  apiKeyMasked?: string | null
}

export interface AgentProviderSettingsCommand {
  baseUrl: string
  apiKey?: string | null
}

export interface AgentProviderModel {
  id: string
  contextWindow: number
  contextWindowEstimated: boolean
}

export type AgentReasoningEffort = 'provider' | 'minimal' | 'low' | 'medium' | 'high' | 'xhigh'
export type AgentSendBehavior = 'queue' | 'steer'

export interface AgentContextUsage {
  inputTokens: number
  outputTokens: number
  totalTokens: number
  contextWindow: number
  contextWindowEstimated: boolean
  compactedMessages: number
}

export interface AgentContextResourceReference {
  type: string
  id: string
}

export interface AgentContextSnapshotRequest {
  scene: string
  resource_refs?: AgentContextResourceReference[]
  draft?: unknown
  state?: unknown
}

export interface AgentContextSnapshot {
  id: string
  expires_at: number
}

export interface AgentResponseFunctionTool {
  type: 'function'
  name: string
  description?: string
  parameters: JsonObject
  strict?: boolean
}

export interface AgentResponseOutputText {
  type: 'output_text'
  text: string
}

export interface AgentResponseMessageItem {
  id: string
  type: 'message'
  status: string
  role: 'assistant'
  content: AgentResponseOutputText[]
}

export interface AgentResponseFunctionCallItem {
  id: string
  type: 'function_call'
  status: string
  call_id: string
  name: string
  arguments: string
}

export type AgentResponseOutputItem = AgentResponseMessageItem | AgentResponseFunctionCallItem

export interface AgentResponseObject {
  id: string
  object: 'response'
  status: string
  output: AgentResponseOutputItem[]
  output_text?: string
  usage?: {
    input_tokens: number
    output_tokens: number
    total_tokens: number
  }
  error?: { code?: string; message: string } | null
}

export interface AgentFunctionCallData {
  itemId: string
  callId: string
  name: string
  arguments: string
  status: 'streaming' | 'pending' | 'submitted'
}

export interface AgentQueuedPrompt {
  id: string
  text: string
  modelId: string
  reasoningEffort: AgentReasoningEffort
  mode?: AgentChatMode
}

export type AgentChatMode = 'auto' | 'configuration' | 'display-text'

export interface AgentConversationSummary {
  id: number | string
  externalId: string
  title: string
  modelId?: string | null
  createTime: string
  updateTime: string
}

export interface AgentObservationData {
  phase: 'agent' | 'model' | 'validation' | string
  state: 'running' | 'completed' | 'failed' | string
  label: string
}

export interface AgentMetadataField {
  fieldCode: string
  label: string
  kotlinType: string
  required?: boolean
  remark?: string | null
}

export interface AgentMetadataModel {
  name: string
  modelCode: string
  description?: string
  fields?: AgentMetadataField[]
  entityConfig?: LowcodeEntityConfigDraft
  queries?: Array<Record<string, unknown>>
  relations?: Array<Record<string, unknown>>
}

export interface AgentMetadataOperation extends JsonObject {
  operationCode: string
  name: string
  method: LowcodeHttpMethod
  path: string
  transport: LowcodeOperationTransport
}

export interface AgentMetadataContract extends JsonObject {
  contractCode: string
  name: string
  packageName: string
  className: string
  path: string
  operations: AgentMetadataOperation[]
}

export interface AgentMetadataDefinition extends JsonObject {
  agentCode: string
  name: string
  modelCode: string
  instructions: string
}

export interface AgentMetadataResult {
  summary?: string
  models?: AgentMetadataModel[]
  dtos?: LowcodeDtoResourceDraft[]
  contracts?: AgentMetadataContract[]
  agents?: AgentMetadataDefinition[]
  questions?: string[]
}

export interface MetadataConfigurationApplyResult {
  modelIds: Record<string, number | string>
  dtoIds: Record<string, number | string>
  contractIds: Record<string, number | string>
  agentIds: Record<string, number | string>
}

export interface AgentMetric {
  label: string
  value: number | string
  change?: number | string
  unit?: string
}

export interface AgentTableData {
  columns: Array<{ key: string; label: string }>
  rows: Array<Record<string, boolean | number | string | null>>
}

export interface AgentChartData {
  title?: string
  series: Array<{ label: string; value: number; color?: string }>
}

export interface AgentReportData {
  title: string
  period?: string
  summary?: string
  metrics?: AgentMetric[]
  table?: AgentTableData
  chart?: AgentChartData
}

export interface AgentMessageDataParts {
  [key: string]: unknown
  observation: AgentObservationData
  metadata: AgentMetadataResult
  'metadata-patch': MetadataTablePatchResult
  configuration: AgentMetadataResult
  metrics: AgentMetric[]
  table: AgentTableData
  chart: AgentChartData
  report: AgentReportData
  context: AgentContextUsage
  'function-call': AgentFunctionCallData
}

export type MetadataTableColumnKind = 'scalar' | 'enum' | 'boolean' | 'object' | 'collection' | 'map'
export type MetadataTableOperation = 'translate' | 'replace' | 'fill' | 'custom'

export interface MetadataTableColumn {
  key: string
  label: string
  kind: MetadataTableColumnKind
  agentEditable: boolean
  context: boolean
  options?: Array<{ label: string; value: JsonPrimitive }>
}

export interface MetadataTableRowSnapshot {
  rowKey: string
  values: Record<string, JsonValue>
}

export interface MetadataTableSelectionScope {
  rowKeys: string[]
  filteredRowCount: number
}

export interface MetadataTableContext {
  tableId: string
  revision: string
  targetColumnKey: string
  rowIdentityKey: string
  context: Record<string, JsonPrimitive>
  operations: MetadataTableOperation[]
  columns: MetadataTableColumn[]
  rows: MetadataTableRowSnapshot[]
  selection: MetadataTableSelectionScope
}

export interface MetadataValueEdit {
  path: string | null
  match: JsonPrimitive
  replacement: JsonPrimitive
}

export interface MetadataCellPatch {
  rowKey: string
  columnKey: string
  expectedValue: JsonPrimitive
  edits: MetadataValueEdit[]
}

export interface MetadataTablePatchResult {
  summary?: string
  tableId: string
  revision: string
  patches: MetadataCellPatch[]
  questions: string[]
}

export interface AgentTextMessagePart {
  type: 'text'
  text: string
  state?: string
}

export interface AgentStepStartMessagePart {
  type: 'step-start'
}

export type AgentDataMessagePart = {
  [K in keyof AgentMessageDataParts]: {
    type: `data-${K & string}`
    id?: string
    data: AgentMessageDataParts[K]
  }
}[keyof AgentMessageDataParts]

export interface AgentUnknownMessagePart {
  type: string
  id?: string
  text?: string
  state?: string
  data?: unknown
}

export type AgentUiMessagePart =
  | AgentTextMessagePart
  | AgentStepStartMessagePart
  | AgentDataMessagePart
  | AgentUnknownMessagePart

export interface AgentUiMessage {
  id: string
  role: 'assistant' | 'system' | 'user'
  parts: AgentUiMessagePart[]
}

export interface AgentStructuredOutputDraft extends JsonObject {
  name: string
  description?: string | null
  schema: JsonObject
  strict: boolean
}

export interface AgentDefinitionDraft extends JsonObject {
  id?: number | string
  agentCode: string
  name: string
  modelCode: string
  instructions: string
  toolCodes: string[]
  temperature?: number | null
  maxOutputTokens?: number | null
  structuredOutput: AgentStructuredOutputDraft
  status: number
  version: number
  description?: string | null
}

export interface LowcodeValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface LowcodePreviewFile {
  filePath: string
  content: string
}

export interface LowcodePreview {
  modelId: number | string
  modelCode: string
  files: LowcodePreviewFile[]
}

export interface LowcodeContractPreview {
  contractId: number | string
  contractCode: string
  files: LowcodePreviewFile[]
}

export interface LowcodeDtoPreview {
  dtoId: number | string
  dtoCode: string
  files: LowcodePreviewFile[]
}

export type ApplicationIdentityMode = 'EXTERNAL_JWT' | 'LOCAL'

export type LibraryKind = 'BUSINESS' | 'BUILT_IN'

export interface LsiApplicationFeature {
  featureId: number | string
  featureCode: string
  name: string
  description?: string | null
  packageName: string
  contributorId: string
  modelCodes: string[]
  dtoCodes: string[]
  contractCodes: string[]
}

export interface LsiLibraryFeature {
  id: number | string
  libraryId: number | string
  parentId?: number | string | null
  featureCode: string
  name: string
  description?: string | null
}

export interface LibraryDataScopeDescriptor {
  tenantScoped: boolean
  userScoped: boolean
  departmentScoped: boolean
}

export interface LsiLibrarySpec {
  schemaVersion: number
  description?: string | null
  contributorId: string
  packagePrefix: string
  scanPackage: string
  kind: LibraryKind
  runtimeDependencies: string[]
  supportedIdentityModes: ApplicationIdentityMode[]
  applicationSelectable: boolean
  dataScope: LibraryDataScopeDescriptor
}

export interface LsiLibraryDefinition {
  id: number | string
  code: string
  displayName: string
  version: number
  status: number
  spec: LsiLibrarySpec
  features: LsiLibraryFeature[]
}

export interface LibraryDefinitionCommand {
  id?: number | string
  code: string
  displayName: string
  version: number
  status: number
  spec: LsiLibrarySpec
}

export interface LibraryDefinitionDraft extends LibraryDefinitionCommand {}

export interface LibraryDefinitionPreview {
  libraryId: number | string
  featureId?: number | string | null
  files: LowcodePreviewFile[]
}

export type LowcodeModelKind = 'ENTITY' | 'MAPPED_SUPERCLASS' | 'EMBEDDABLE'

export type LowcodeModelDesignerSection = 'model' | 'fields' | 'api' | 'queries' | 'relations'

export interface LowcodeModelRecentChanges {
  sections: LowcodeModelDesignerSection[]
  fieldKeys: string[]
}

export type LowcodeEntityBaseMode = 'DEFAULT' | 'INHERITED'

export type LowcodeBaseModel =
  | 'BASE_ENTITY'
  | 'SNOWFLAKE_ID'
  | 'CREATE_TIME'
  | 'UPDATE_TIME'
  | 'AUDIT'
  | 'TENANT'
  | 'NAMESPACE'
  | 'NODE'
  | 'NAMED'
  | 'DESCRIPTION'
  | 'REMARK'
  | 'SORT'
  | 'STATUS'
  | 'DELETED'
  | 'DELETED_TIME'
  | 'TREE_LEVEL'
  | 'VERSION'

export type LowcodeEntitySourceMode = 'GENERATED' | 'EXISTING'

export type LowcodeInheritanceStrategy = 'SINGLE_TABLE' | 'JOINED'

export type LowcodeEntityInstantiability = 'AUTO' | 'ABSTRACT' | 'INSTANTIABLE'

export type LowcodeJoinedTableDissociateAction = 'DELETE' | 'LAX'

export type LowcodeDtoKind = 'INPUT' | 'OUTPUT' | 'STRUCTURE' | 'VIEW'

export type LowcodeDtoSelectionMode =
  | 'EXPLICIT'
  | 'ALL_SCALAR_FIELDS'
  | 'ALL_TABLE_FIELDS'
  | 'ALL_DEEP_FIELDS'

export type LowcodeDtoNullability = 'INHERIT' | 'NULLABLE' | 'NON_NULL'

export type LsiValidationValueKind = 'TEXT' | 'COLLECTION' | 'TEXT_COLLECTION'

export interface LsiValidationRule extends JsonObject {
  code: string
  message?: string | null
  parameters?: Record<string, string>
}

export interface LsiValidationParameterMetadata {
  code: string
  name: string
  description: string
  kind: 'INTEGER'
  required: boolean
  minimum?: number | null
  maximum?: number | null
}

export interface LsiValidationRuleMetadata {
  code: string
  name: string
  description: string
  predicate: 'BLANK' | 'EMPTY' | 'BLANK_ELEMENTS' | 'MAX_LENGTH'
  supportedValueKinds: LsiValidationValueKind[]
  defaultMessage: string
  parameters: LsiValidationParameterMetadata[]
}

export type LowcodeFormulaKind = 'KOTLIN' | 'SQL'

export type LowcodeTransientKind = 'DRAFT' | 'RESOLVER'

export type LowcodeHttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type LowcodeOperationTransport = 'HTTP' | 'SSE' | 'WEBSOCKET'

export type LowcodeOperationImplementation = 'GENERATED' | 'EXISTING_REST'

export type LowcodeAgentConfirmation = 'AUTO' | 'REQUIRED'

export interface LowcodeAgentOperationExposureDraft extends JsonObject {
  confirmation: LowcodeAgentConfirmation
}

export interface LowcodeAgentExposureDraft extends JsonObject {
  operations: Record<string, LowcodeAgentOperationExposureDraft>
}

export type LowcodeApiParameterLocation = 'PATH' | 'QUERY' | 'HEADER' | 'COOKIE'

export type LowcodeQueryLogic = 'AND' | 'OR'

export type LowcodeQueryOperator =
  | 'EQ'
  | 'NE'
  | 'LIKE'
  | 'STARTS_WITH'
  | 'ENDS_WITH'
  | 'GT'
  | 'GE'
  | 'LT'
  | 'LE'
  | 'IN'
  | 'NOT_IN'
  | 'BETWEEN'
  | 'TIME_RANGE'
  | 'NULL_STATE'
  | 'ZERO_STATE'

export type LowcodeQueryValueType = 'SINGLE' | 'RANGE' | 'DATE_RANGE' | 'DATETIME_RANGE' | 'MULTIPLE'

export type LowcodeRelationKind = 'MANY_TO_ONE' | 'ONE_TO_MANY' | 'ONE_TO_ONE' | 'MANY_TO_MANY'

export type LowcodeDissociateAction = 'NONE' | 'LAX' | 'CHECK' | 'SET_NULL' | 'DELETE'

export type LowcodeEnumStorage = 'NAME' | 'ORDINAL'

export interface LowcodeInheritedPropertyDraft extends JsonObject {
  name: string
  kotlinType: string
  dbColumn: string
  required: boolean
  id: boolean
  description?: string | null
  defaultValue?: string | null
}

export interface LowcodeFormulaPropertyDraft extends JsonObject {
  propertyCode: string
  label: string
  kotlinType: string
  kind: LowcodeFormulaKind
  expression: string
  dependencies: string[]
  nullable: boolean
  description?: string | null
}

export interface LowcodeTransientPropertyDraft extends JsonObject {
  propertyCode: string
  label: string
  kotlinType: string
  kind: LowcodeTransientKind
  resolverValueType?: string | null
  nullable: boolean
  description?: string | null
  dictionaryCode?: string | null
}

export interface LowcodeInheritanceRootDraft extends JsonObject {
  strategy: LowcodeInheritanceStrategy
  discriminatorField: string
  instantiability: LowcodeEntityInstantiability
  discriminatorValue?: string | null
  joinedTableDissociateAction: LowcodeJoinedTableDissociateAction
}

export interface LowcodeInheritanceSubtypeDraft extends JsonObject {
  parentModelCode: string
  discriminatorValue?: string | null
  instantiability: LowcodeEntityInstantiability
}

export interface LowcodeEntityConfigDraft extends JsonObject {
  sourceMode: LowcodeEntitySourceMode
  sourceQualifiedName?: string | null
  baseMode: LowcodeEntityBaseMode
  baseModels: LowcodeBaseModel[]
  superTypes: string[]
  relationOrderings: Record<string, string[]>
  inheritedProperties: LowcodeInheritedPropertyDraft[]
  inheritedRelationCodes: string[]
  formulaProperties: LowcodeFormulaPropertyDraft[]
  transientProperties: LowcodeTransientPropertyDraft[]
  microServiceName?: string | null
  inheritanceRoot?: LowcodeInheritanceRootDraft | null
  inheritanceSubtype?: LowcodeInheritanceSubtypeDraft | null
}

export interface LowcodeDtoFieldDraft extends JsonObject {
  name: string
  sourcePath: string
  description?: string | null
  nullability: LowcodeDtoNullability
  schema?: LowcodeApiSchemaDraft | null
  kotlinType?: LsiDtoTypeDraft | null
  validations: LsiValidationRule[]
  annotations: LsiDtoAnnotationDraft[]
  defaultValue?: LsiDtoDefaultValueDraft | null
}

export interface LsiDtoDefaultValueDraft extends JsonObject {
  kind: 'NULL' | 'DECLARED' | 'BOOLEAN' | 'INTEGER' | 'STRING' | 'ENUM' | 'EMPTY_INSTANCE' | 'EMPTY_LIST' | 'EMPTY_MAP' | 'EMPTY_SET'
  value?: string | null
}

export interface LsiDtoAnnotationDraft extends JsonObject {
  qualifiedName: string
  useSiteTarget?: 'GET' | 'PARAM' | 'FIELD' | 'PROPERTY' | null
  arguments: LsiDtoAnnotationArgumentDraft[]
}

export interface LsiDtoAnnotationArgumentDraft extends JsonObject {
  value: string
  kind: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'CLASS'
  name?: string | null
}

export interface LsiDtoTypeDraft extends JsonObject {
  qualifiedName: string
  arguments: LsiDtoTypeDraft[]
  nullable: boolean
}

export type DtoStructureOrigin = 'SOURCE' | 'GENERATED' | 'METADATA'

export type DtoReuseRelation = 'EXACT' | 'CONTAINS' | 'OVERLAP'

export interface LsiDtoPropertyView extends JsonObject {
  name: string
  type: LsiDtoTypeDraft
  description: string
  defaultValue?: LsiDtoDefaultValueDraft | null
}

export interface LsiDataStructureView extends JsonObject {
  qualifiedName: string
  properties: LsiDtoPropertyView[]
  origins: DtoStructureOrigin[]
}

export interface DtoReuseCandidateView extends JsonObject {
  leftQualifiedName: string
  rightQualifiedName: string
  relation: DtoReuseRelation
  sharedProperties: string[]
  leftCoverage: number
  rightCoverage: number
  jaccard: number
  constructorOrderCompatible: boolean
  defaultValuesCompatible: boolean
}

export interface DtoReusableFragmentView extends JsonObject {
  properties: string[]
  structureQualifiedNames: string[]
}

export interface DtoFieldCorrelationView extends JsonObject {
  firstProperty: string
  secondProperty: string
  coOccurrenceCount: number
  firstConfidence: number
  secondConfidence: number
  jaccard: number
}

export interface LowcodeDtoReuseAnalysis extends JsonObject {
  draftQualifiedName: string
  snapshotGeneratedAtEpochMillis: number
  metadataStale: boolean
  sourceFingerprint: string
  currentMetadataFingerprint: string
  candidates: DtoReuseCandidateView[]
  reusableFragments: DtoReusableFragmentView[]
  fieldCorrelations: DtoFieldCorrelationView[]
  structures: LsiDataStructureView[]
}

export interface LowcodeDtoRefDraft extends JsonObject {
  modelCode?: string | null
  dtoCode: string
}

export interface LowcodeApiTypeOption {
  modelCode?: string | null
  dtoCode: string
  className: string
  kind: 'ENTITY' | LowcodeDtoKind
}

export interface LowcodeDtoResourceDraft extends JsonObject {
  id?: number | string
  featureId: number | string
  dtoCode: string
  name: string
  packageName: string
  className: string
  kind: LowcodeDtoKind
  visibility: 'PUBLIC' | 'INTERNAL'
  sourceModelCode?: string | null
  selectionMode: LowcodeDtoSelectionMode
  excludedPaths: string[]
  fields: LowcodeDtoFieldDraft[]
  annotations: LsiDtoAnnotationDraft[]
  superTypes: LsiDtoTypeDraft[]
  contributorId?: string | null
  status: number
  version: number
  description?: string | null
}

export interface LowcodeRoutePropertyDraft extends JsonObject {
  name: string
  type: string
  format?: string | null
  required: boolean
  identifier?: boolean
  createWritable?: boolean
  updateWritable?: boolean
  arrayItemType?: string | null
  description?: string | null
  dictionaryCode?: string | null
}

export interface LowcodeRouteQueryFieldDraft extends JsonObject {
  propertyName: string
  parameterName: string
  operator: string
  type: string
  format?: string | null
  endParameterName?: string | null
  required: boolean
  stateCases: JsonObject[]
  description?: string | null
}

export interface LowcodeRouteTreeDraft extends JsonObject {
  parentIdProperty: string
  childrenProperty: string
  keywordProperty: string
  sortProperty?: string | null
}

export interface LowcodeRouteExcelDraft extends JsonObject {
  importEnabled: boolean
  exportEnabled: boolean
  customImport: boolean
  customExport: boolean
  fileName: string
  templateFileName: string
  sheetName: string
  templateSheetName: string
  importColumns: LowcodeRoutePropertyDraft[]
  exportColumns: LowcodeRoutePropertyDraft[]
}

export interface LowcodeNamedDtoSchemaDraft extends JsonObject {
  ref: LowcodeDtoRefDraft
  className: string
  properties: Record<string, LowcodeApiSchemaDraft>
  required: string[]
  description?: string | null
}

export interface LowcodeRouteDraft extends JsonObject {
  packageName: string
  qualifiedName: string
  className: string
  modelCode?: string | null
  description?: string | null
  path: string
  aliasPaths: string[]
  fetchPaths: string[]
  excludePaths: string[]
  enabledOperations: string[]
  tree?: LowcodeRouteTreeDraft | null
  excel?: LowcodeRouteExcelDraft | null
  properties: LowcodeRoutePropertyDraft[]
  queryFields: LowcodeRouteQueryFieldDraft[]
  defaultOrders: LowcodeRouteOrderDraft[]
  customOperations: LowcodeCustomOperationDraft[]
  dtoSchemas: LowcodeNamedDtoSchemaDraft[]
  agentExposure: LowcodeAgentExposureDraft
}

export type LowcodeRouteOrderDirection = 'ASC' | 'DESC'

export interface LowcodeRouteOrderDraft extends JsonObject {
  propertyName: string
  direction: LowcodeRouteOrderDirection
}

export interface LowcodeApiSchemaDraft extends JsonObject {
  type?: string | null
  typeRef?: LowcodeDtoRefDraft | null
  format?: string | null
  description?: string | null
  properties: Record<string, LowcodeApiSchemaDraft>
  required: string[]
  items?: LowcodeApiSchemaDraft | null
  enumValues: string[]
  oneOf: LowcodeApiSchemaDraft[]
}

export interface LowcodeApiBodyDraft extends JsonObject {
  contentType: string
  required: boolean
  description?: string | null
  schema: LowcodeApiSchemaDraft
}

export interface LowcodeApiParameterDraft extends JsonObject {
  name: string
  location: LowcodeApiParameterLocation
  required: boolean
  description?: string | null
  schema: LowcodeApiSchemaDraft
}

export interface LowcodeCustomOperationDraft extends JsonObject {
  operationCode: string
  name: string
  description?: string | null
  path: string
  method: LowcodeHttpMethod
  transport: LowcodeOperationTransport
  implementation?: LowcodeOperationImplementation
  authenticated: boolean
  permission?: string | null
  callContext: boolean
  parameters: LowcodeApiParameterDraft[]
  requestBody?: LowcodeApiBodyDraft | null
  responseBody?: LowcodeApiBodyDraft | null
  responseEnvelope: boolean
}

export interface LowcodeApiContractDraft extends JsonObject {
  id?: number | string
  featureId: number | string
  contractCode: string
  name: string
  packageName: string
  className: string
  path: string
  contributorId?: string | null
  status: number
  version: number
  description?: string | null
  operations: LowcodeCustomOperationDraft[]
  agentExposure: LowcodeAgentExposureDraft
}

export interface LowcodeFieldDraft extends JsonObject {
  id?: number | string
  orderNo: number
  fieldCode: string
  label: string
  kotlinType: string
  dbColumn: string
  required: boolean
  createWritable: boolean
  updateWritable: boolean
  listVisible: boolean
  formVisible: boolean
  formControl: string
  dictCode?: string | null
  enumStorage?: LowcodeEnumStorage | null
  defaultValue?: string | null
  remark?: string | null
  serialized: boolean
  key: boolean
  maxLength?: number | null
}

export interface LowcodeQueryConditionDraft extends JsonObject {
  id?: number | string
  orderNo: number
  fieldCode: string
  operator: LowcodeQueryOperator
  valueType: LowcodeQueryValueType
  paramName?: string | null
}

export interface LowcodeQueryDraft extends JsonObject {
  id?: number | string
  orderNo: number
  queryCode: string
  label: string
  logic: LowcodeQueryLogic
  items: LowcodeQueryConditionDraft[]
}

export interface LowcodeRelationDraft extends JsonObject {
  id?: number | string
  orderNo: number
  relationCode: string
  label: string
  relationType: LowcodeRelationKind
  targetModelId?: number | string | null
  targetModelCode?: string | null
  joinColumn?: string | null
  mappedBy?: string | null
  joinTable?: string | null
  joinTableJoinColumn?: string | null
  joinTableInverseColumn?: string | null
  dissociateAction: LowcodeDissociateAction
  required: boolean
  createWritable: boolean
  updateWritable: boolean
  listVisible: boolean
  formVisible: boolean
}

export interface LowcodeModelDraft extends JsonObject {
  id?: number | string
  featureId: number | string
  modelCode: string
  name: string
  packageName: string
  className: string
  tableName: string
  modelType: LowcodeModelKind
  status: number
  version: number
  contributorId?: string | null
  entityConfig: LowcodeEntityConfigDraft
  routeConfig: LowcodeRouteDraft
  remark?: string | null
  fields: LowcodeFieldDraft[]
  queries: LowcodeQueryDraft[]
  relations: LowcodeRelationDraft[]
}
