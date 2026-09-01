export type ApiHttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete' | 'head' | 'options'

export type ApiParameterLocation = 'path' | 'query' | 'header' | 'cookie'

export type ApiSecurityRequirement = Record<string, string[]>

export interface ApiSchema {
  $ref?: string
  type?: string | string[]
  format?: string
  title?: string
  description?: string
  default?: unknown
  example?: unknown
  enum?: unknown[]
  properties?: Record<string, ApiSchema>
  required?: string[]
  items?: ApiSchema
  allOf?: ApiSchema[]
  oneOf?: ApiSchema[]
  anyOf?: ApiSchema[]
  nullable?: boolean
  'x-lowcode-reference'?: ApiLowcodeReference
}

export interface ApiLowcodeReference {
  targetModelCode: string
  propertyName?: string
}

export interface ApiParameter {
  name: string
  in: ApiParameterLocation
  required?: boolean
  description?: string
  schema?: ApiSchema
  example?: unknown
}

export interface ApiRequestBody {
  required?: boolean
  description?: string
  content?: Record<string, {
    schema?: ApiSchema
    example?: unknown
    examples?: Record<string, { value?: unknown }>
  }>
}

export interface ApiResponse {
  description?: string
  headers?: Record<string, ApiHeader>
  content?: Record<string, {
    schema?: ApiSchema
    example?: unknown
    examples?: Record<string, { value?: unknown }>
  }>
}

export interface ApiHeader {
  description?: string
  required?: boolean
  schema?: ApiSchema
  example?: unknown
}

export interface ApiOperationDocument {
  operationId?: string
  summary?: string
  description?: string
  tags?: string[]
  parameters?: ApiParameter[]
  requestBody?: ApiRequestBody
  responses?: Record<string, ApiResponse>
  security?: ApiSecurityRequirement[]
  'x-lowcode-contract'?: boolean
  'x-lowcode-transport'?: 'HTTP' | 'SSE' | 'WEBSOCKET'
  'x-permission'?: string
}

export interface ApiPathItem extends Partial<Record<ApiHttpMethod, ApiOperationDocument>> {
  parameters?: ApiParameter[]
}

export interface ApiSecurityScheme {
  type?: string
  scheme?: string
  bearerFormat?: string
  description?: string
  name?: string
  in?: ApiParameterLocation
}

export interface ApiDocument {
  openapi?: string
  info?: { title?: string; version?: string; description?: string }
  servers?: Array<{ url: string; description?: string }>
  tags?: Array<{ name: string; description?: string }>
  paths?: Record<string, ApiPathItem | undefined>
  components?: { schemas?: Record<string, ApiSchema>; securitySchemes?: Record<string, ApiSecurityScheme> }
}

export interface ApiOperation {
  id: string
  method: ApiHttpMethod
  path: string
  addresses: ApiOperationAddress[]
  summary: string
  description?: string
  tags: string[]
  parameters: ApiParameter[]
  requestBody?: ApiRequestBody
  responses: Record<string, ApiResponse>
  lowcodeContract: boolean
  transport: 'HTTP' | 'SSE' | 'WEBSOCKET'
  permission?: string
  metadataIssues: string[]
  security: ApiSecurityRequirement[]
}

export interface ApiOperationAddress {
  path: string
  permission?: string
}

export interface ApiGroup {
  name: string
  description?: string
  operations: ApiOperation[]
}

export interface ApiRequestInput {
  baseUrl: string
  document: ApiDocument
  operation: ApiOperation
  pathValues: Record<string, string>
  queryValues: Record<string, string>
  headerValues: Record<string, string>
  bodyText: string
  bodyFile?: File
  contentType?: string
  multipartValues: ApiMultipartValues
  accessToken: string
}

export type ApiMultipartValue = string | string[] | boolean | File | File[] | undefined
export type ApiMultipartValues = Record<string, ApiMultipartValue>

export interface ApiMultipartField {
  name: string
  schema: ApiSchema
  required: boolean
}

export interface ApiFileReferenceField {
  name: string
  schema: ApiSchema
  required: boolean
  uploadOperationId: string
}

export interface ApiFileReferenceUploadState {
  loading: boolean
  fileName?: string
  fileId?: string
  error?: string
}

export interface FileUploadOutput {
  id: string
  url: string
}

export type ApiCodeClient = 'axios' | 'alova'

export type ApiCodeImportStyle = 'default' | 'named' | 'none'

export interface ApiCodeClientReference {
  instanceName: string
  importPath: string
  importStyle: ApiCodeImportStyle
}

export interface ApiCodePreferences {
  client: ApiCodeClient
  axios: ApiCodeClientReference
  alova: ApiCodeClientReference
}

export interface ApiResponseFile {
  blob: Blob
  contentType: string
  fileName: string
  size: number
}

export interface ApiResponseState {
  status: number
  statusText: string
  durationMs: number
  headers: Record<string, string>
  bodyText: string
  body: unknown
  bodySize: number
  applicationCode?: number
  applicationMessage?: string
  file?: ApiResponseFile
  requestUrl: string
  curl: string
  redactedCurl: string
}

export interface ApiHistoryEntry {
  id: string
  method: ApiHttpMethod
  path: string
  status?: number
  durationMs?: number
  createdAt: string
}
