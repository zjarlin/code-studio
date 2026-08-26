import type {
  ApiCodeClientReference,
  ApiCodePreferences,
  ApiDocument,
  ApiOperation,
  ApiParameter,
  ApiParameterLocation,
  ApiSchema,
} from './types'

export const DEFAULT_API_CODE_PREFERENCES: ApiCodePreferences = {
  client: 'axios',
  axios: {
    instanceName: 'request',
    importPath: '@/config/axios',
    importStyle: 'default',
  },
  alova: {
    instanceName: 'alovaInstance',
    importPath: '@/utils/alova',
    importStyle: 'default',
  },
}

interface RenderedArgument {
  declaration: string
  required: boolean
}

interface OperationCodeModel {
  functionName: string
  typeName: string
  url: string
  method: string
  bodyKind: 'none' | 'json' | 'multipart'
  bodyRequired: boolean
  arguments: RenderedArgument[]
  declarations: string[]
  queryArgument?: string
  headerArgument?: string
  bodyArgument?: string
  multipartFields: Array<{ name: string; schema: ApiSchema; required: boolean }>
  responseType: string
  binaryResponse: boolean
}

export function generateTypeScriptRequest(
  operation: ApiOperation,
  document: ApiDocument,
  preferences: ApiCodePreferences,
): string {
  const reference = normalizeClientReference(preferences[preferences.client], preferences.client)
  const model = createOperationCodeModel(operation, document)
  const importSource = renderImport(reference)
  const declarationsSource = model.declarations.join('\n\n')
  const functionSource = preferences.client === 'alova'
    ? renderAlovaFunction(model, reference.instanceName)
    : renderAxiosFunction(model, reference.instanceName)

  return [importSource, declarationsSource, functionSource].filter(Boolean).join('\n\n').trim() + '\n'
}

export function defaultApiFunctionName(operation: ApiOperation): string {
  const operationName = operation.id.includes(':')
    ? `${operation.method}-${operation.path}`
    : conciseOperationId(operation.id)
  const pascalName = toPascalIdentifier(operationName)
  return pascalName ? lowerFirst(pascalName) : `${operation.method}Request`
}

function createOperationCodeModel(operation: ApiOperation, document: ApiDocument): OperationCodeModel {
  const functionName = defaultApiFunctionName(operation)
  const typeName = upperFirst(functionName)
  const declarations: string[] = []
  const argumentsList: RenderedArgument[] = []
  const pathParameters = parametersAt(operation, 'path')
  const queryParameters = parametersAt(operation, 'query')
  const headerParameters = parametersAt(operation, 'header')

  if (pathParameters.length) {
    declarations.push(renderParameterType(`${typeName}Path`, pathParameters, document))
    argumentsList.push({ declaration: `path: ${typeName}Path`, required: true })
  }

  const queryArgument = appendParameterArgument(
    declarations,
    argumentsList,
    `${typeName}Query`,
    'params',
    queryParameters,
    document,
  )
  const headerArgument = appendParameterArgument(
    declarations,
    argumentsList,
    `${typeName}Headers`,
    'headers',
    headerParameters,
    document,
  )

  const [contentType, media] = preferredRequestMedia(operation)
  const bodyKind = contentType === 'multipart/form-data' ? 'multipart' : media ? 'json' : 'none'
  const bodyTypeName = bodyKind === 'multipart' ? `${typeName}Form` : `${typeName}Body`
  const bodyArgument = bodyKind === 'multipart' ? 'form' : bodyKind === 'json' ? 'data' : undefined
  const bodyRequired = operation.requestBody?.required === true
  const multipartFields = bodyKind === 'multipart'
    ? objectFields(media?.schema, document)
    : []

  if (media?.schema && bodyArgument) {
    declarations.push(renderSchemaType(bodyTypeName, media.schema, document))
    argumentsList.push({
      declaration: bodyRequired ? `${bodyArgument}: ${bodyTypeName}` : `${bodyArgument}?: ${bodyTypeName}`,
      required: bodyRequired,
    })
  }

  const responseTypeName = `${typeName}Response`
  const responseSchema = preferredResponseSchema(operation)
  const binaryResponse = responseSchema?.format === 'binary'
  if (binaryResponse) {
    declarations.push(`export type ${responseTypeName} = Blob`)
  } else if (responseSchema) {
    declarations.push(renderSchemaType(responseTypeName, responseSchema, document))
  } else {
    declarations.push(`export type ${responseTypeName} = unknown`)
  }

  return {
    functionName,
    typeName,
    url: renderUrl(operation.path, pathParameters),
    method: operation.method,
    bodyKind,
    bodyRequired,
    arguments: argumentsList.sort((left, right) => Number(right.required) - Number(left.required)),
    declarations,
    queryArgument,
    headerArgument,
    bodyArgument,
    multipartFields,
    responseType: responseTypeName,
    binaryResponse,
  }
}

function appendParameterArgument(
  declarations: string[],
  argumentsList: RenderedArgument[],
  typeName: string,
  argumentName: string,
  parameters: ApiParameter[],
  document: ApiDocument,
): string | undefined {
  if (!parameters.length) {
    return undefined
  }
  const required = hasRequiredParameter(parameters)
  declarations.push(renderParameterType(typeName, parameters, document))
  argumentsList.push({
    declaration: required ? `${argumentName}: ${typeName}` : `${argumentName}: ${typeName} = {}`,
    required,
  })
  return argumentName
}

function renderAxiosFunction(model: OperationCodeModel, instanceName: string): string {
  const setup = renderMultipartSetup(model)
  const configEntries = [
    `url: ${model.url}`,
    `method: '${model.method.toUpperCase()}'`,
    model.queryArgument ? model.queryArgument : '',
    model.headerArgument ? model.headerArgument : '',
    model.bodyArgument ? renderAxiosBodyEntry(model) : '',
    model.binaryResponse ? "responseType: 'blob'" : '',
  ].filter(Boolean).map((entry) => `    ${entry},`).join('\n')

  return `export function ${model.functionName}(${renderArguments(model.arguments)}) {\n${setup}  return ${instanceName}.request<${model.responseType}>({\n${configEntries}\n  })\n}`
}

function renderAxiosBodyEntry(model: OperationCodeModel): string {
  const value = renderBodyValue(model)
  return value === 'data' ? 'data' : `data: ${value}`
}

function renderAlovaFunction(model: OperationCodeModel, instanceName: string): string {
  const setup = renderMultipartSetup(model)
  const methodName = upperFirst(model.method)
  const configEntries = [
    model.queryArgument ? model.queryArgument : '',
    model.headerArgument ? model.headerArgument : '',
    model.binaryResponse ? "responseType: 'blob'" : '',
  ].filter(Boolean)
  const config = configEntries.length
    ? `{\n${configEntries.map((entry) => `    ${entry},`).join('\n')}\n  }`
    : ''
  const supportsBody = !['get', 'head', 'options'].includes(model.method)
  const body = model.bodyArgument ? renderBodyValue(model) : ''
  const callArguments = supportsBody
    ? [model.url, body || (config ? 'undefined' : ''), config].filter(Boolean).join(', ')
    : [model.url, config].filter(Boolean).join(', ')

  return `export function ${model.functionName}(${renderArguments(model.arguments)}) {\n${setup}  return ${instanceName}.${methodName}<${model.responseType}>(${callArguments})\n}`
}

function renderMultipartSetup(model: OperationCodeModel): string {
  if (model.bodyKind !== 'multipart' || !model.bodyArgument) {
    return ''
  }
  const appendLines = model.multipartFields.flatMap((field) => renderFormDataField(field.name, field.schema, field.required))
  const body = appendLines.map((line) => `  ${model.bodyRequired ? '' : '  '}${line}`).join('\n')
  if (model.bodyRequired) {
    return `  const formData = new FormData()\n${body}\n\n`
  }
  return `  const formData = new FormData()\n  if (form) {\n${body}\n  }\n\n`
}

function renderBodyValue(model: OperationCodeModel): string {
  if (model.bodyKind !== 'multipart') {
    return model.bodyArgument ?? 'undefined'
  }
  return model.bodyRequired ? 'formData' : 'form ? formData : undefined'
}

function renderFormDataField(name: string, schema: ApiSchema, required: boolean): string[] {
  const access = propertyAccess('form', name)
  const item = schema.items
  if (schema.type === 'array') {
    const appendValue = item?.format === 'binary' ? 'item' : 'String(item)'
    const statement = `${access}.forEach((item) => formData.append('${escapeSingleQuoted(name)}', ${appendValue}))`
    return required ? [statement] : [`${access}?.forEach((item) => formData.append('${escapeSingleQuoted(name)}', ${appendValue}))`]
  }
  const value = schema.format === 'binary'
    ? access
    : isObjectSchema(schema)
      ? `JSON.stringify(${access})`
      : `String(${access})`
  const statement = `formData.append('${escapeSingleQuoted(name)}', ${value})`
  return required ? [statement] : [`if (${access} !== undefined) {`, `  ${statement}`, `}`]
}

function renderArguments(argumentsList: RenderedArgument[]): string {
  if (argumentsList.length <= 2) {
    return argumentsList.map((argument) => argument.declaration).join(', ')
  }
  return `\n  ${argumentsList.map((argument) => argument.declaration).join(',\n  ')},\n`
}

function renderImport(reference: ApiCodeClientReference): string {
  if (reference.importStyle === 'none' || !reference.importPath) {
    return ''
  }
  const path = escapeSingleQuoted(reference.importPath)
  if (reference.importStyle === 'named') {
    return `import { ${reference.instanceName} } from '${path}'`
  }
  return `import ${reference.instanceName} from '${path}'`
}

function renderParameterType(name: string, parameters: ApiParameter[], document: ApiDocument): string {
  const fields = parameters.map((parameter) => ({
    name: parameter.name,
    schema: parameter.schema ?? {},
    required: parameter.required === true,
    description: parameter.description,
  }))
  return renderObjectType(name, fields, document)
}

function renderSchemaType(name: string, schema: ApiSchema, document: ApiDocument): string {
  const type = renderType(schema, document, new Set(), 0)
  return `export type ${name} = ${type}`
}

function renderObjectType(
  name: string,
  fields: Array<{ name: string; schema: ApiSchema; required: boolean; description?: string }>,
  document: ApiDocument,
): string {
  const body = renderObjectFields(fields, document, new Set(), 1)
  return `export interface ${name} {\n${body}\n}`
}

function renderType(schema: ApiSchema, document: ApiDocument, resolving: Set<string>, depth: number): string {
  const nullable = schema.nullable === true || (Array.isArray(schema.type) && schema.type.includes('null'))
  const type = renderNonNullableType(schema, document, resolving, depth)
  return nullable && type !== 'null' ? `${type} | null` : type
}

function renderNonNullableType(schema: ApiSchema, document: ApiDocument, resolving: Set<string>, depth: number): string {
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop() ?? ''
    if (resolving.has(name)) {
      return 'Record<string, unknown>'
    }
    const referenced = document.components?.schemas?.[name]
    if (!referenced) {
      return 'unknown'
    }
    const next = new Set(resolving)
    next.add(name)
    return renderType(referenced, document, next, depth)
  }
  if (schema.enum?.length) {
    return schema.enum.map(renderLiteral).join(' | ')
  }
  if (schema.oneOf?.length || schema.anyOf?.length) {
    return (schema.oneOf ?? schema.anyOf ?? [])
      .map((item) => renderType(item, document, resolving, depth))
      .join(' | ')
  }
  if (schema.allOf?.length) {
    const shape = mergedObjectShape(schema, document, resolving)
    if (shape) {
      return renderInlineObject(shape, document, resolving, depth)
    }
    return schema.allOf.map((item) => renderType(item, document, resolving, depth)).join(' & ')
  }
  if (schema.type === 'array' || (Array.isArray(schema.type) && schema.type.includes('array'))) {
    return `Array<${renderType(schema.items ?? {}, document, resolving, depth)}>`
  }
  if (schema.format === 'binary') {
    return 'File'
  }
  if (isObjectSchema(schema)) {
    const shape = objectShape(schema)
    return renderInlineObject(shape, document, resolving, depth)
  }
  const type = Array.isArray(schema.type) ? schema.type.find((item) => item !== 'null') : schema.type
  switch (type) {
    case 'integer':
    case 'number':
      return 'number'
    case 'boolean':
      return 'boolean'
    case 'string':
      return 'string'
    case 'null':
      return 'null'
    default:
      return 'unknown'
  }
}

function renderInlineObject(
  shape: { properties: Record<string, ApiSchema>; required: Set<string> },
  document: ApiDocument,
  resolving: Set<string>,
  depth: number,
): string {
  const fields = Object.entries(shape.properties).map(([name, schema]) => ({
    name,
    schema,
    required: shape.required.has(name),
    description: schema.description,
  }))
  if (!fields.length) {
    return 'Record<string, unknown>'
  }
  const body = renderObjectFields(fields, document, resolving, depth + 1)
  const indent = '  '.repeat(depth)
  return `{\n${body}\n${indent}}`
}

function renderObjectFields(
  fields: Array<{ name: string; schema: ApiSchema; required: boolean; description?: string }>,
  document: ApiDocument,
  resolving: Set<string>,
  depth: number,
): string {
  const indent = '  '.repeat(depth)
  return fields.flatMap((field) => {
    const comment = field.description?.trim()
      ? [`${indent}/** ${escapeComment(field.description.trim())} */`]
      : []
    const property = `${indent}${renderPropertyName(field.name)}${field.required ? '' : '?'}: ${renderType(field.schema, document, resolving, depth)}`
    return [...comment, property]
  }).join('\n')
}

function mergedObjectShape(
  schema: ApiSchema,
  document: ApiDocument,
  resolving: Set<string>,
): { properties: Record<string, ApiSchema>; required: Set<string> } | undefined {
  const schemas = schema.allOf ?? []
  const shapes = schemas.map((item) => resolvedObjectShape(item, document, resolving))
  if (shapes.some((shape) => !shape)) {
    return undefined
  }
  const properties: Record<string, ApiSchema> = {}
  const required = new Set<string>()
  shapes.forEach((shape) => {
    Object.assign(properties, shape?.properties)
    shape?.required.forEach((name) => required.add(name))
  })
  Object.assign(properties, schema.properties)
  schema.required?.forEach((name) => required.add(name))
  return { properties, required }
}

function resolvedObjectShape(
  schema: ApiSchema,
  document: ApiDocument,
  resolving: Set<string>,
): { properties: Record<string, ApiSchema>; required: Set<string> } | undefined {
  if (schema.$ref) {
    const name = schema.$ref.split('/').pop() ?? ''
    if (resolving.has(name)) {
      return undefined
    }
    const referenced = document.components?.schemas?.[name]
    if (!referenced) {
      return undefined
    }
    const next = new Set(resolving)
    next.add(name)
    return resolvedObjectShape(referenced, document, next)
  }
  if (schema.allOf?.length) {
    return mergedObjectShape(schema, document, resolving)
  }
  return isObjectSchema(schema) ? objectShape(schema) : undefined
}

function objectShape(schema: ApiSchema): { properties: Record<string, ApiSchema>; required: Set<string> } {
  return {
    properties: schema.properties ?? {},
    required: new Set(schema.required ?? []),
  }
}

function objectFields(
  schema: ApiSchema | undefined,
  document: ApiDocument,
): Array<{ name: string; schema: ApiSchema; required: boolean }> {
  if (!schema) {
    return []
  }
  const shape = resolvedObjectShape(schema, document, new Set())
  return Object.entries(shape?.properties ?? {}).map(([name, property]) => ({
    name,
    schema: property,
    required: shape?.required.has(name) ?? false,
  }))
}

function preferredRequestMedia(operation: ApiOperation): [string, { schema?: ApiSchema } | undefined] {
  const content = operation.requestBody?.content ?? {}
  const contentType = ['application/json', 'multipart/form-data'].find((type) => content[type]) ?? Object.keys(content)[0] ?? ''
  return [contentType, content[contentType]]
}

function preferredResponseSchema(operation: ApiOperation): ApiSchema | undefined {
  const response = operation.responses['200'] ?? operation.responses['201'] ?? Object.values(operation.responses)[0]
  const content = response?.content ?? {}
  return (content['application/json'] ?? Object.values(content)[0])?.schema
}

function parametersAt(operation: ApiOperation, location: ApiParameterLocation): ApiParameter[] {
  return operation.parameters.filter((parameter) => parameter.in === location)
}

function hasRequiredParameter(parameters: ApiParameter[]): boolean {
  return parameters.some((parameter) => parameter.required === true)
}

function renderUrl(path: string, parameters: ApiParameter[]): string {
  if (!parameters.length) {
    return `'${escapeSingleQuoted(path)}'`
  }
  const parameterNames = new Set(parameters.map((parameter) => parameter.name))
  const source = path.replace(/\{([^}]+)\}/g, (placeholder, name: string) => {
    if (!parameterNames.has(name)) {
      return placeholder
    }
    return `\${encodeURIComponent(String(${propertyAccess('path', name)}))}`
  })
  return `\`${source.replace(/`/g, '\\`')}\``
}

function normalizeClientReference(reference: ApiCodeClientReference, client: ApiCodePreferences['client']): ApiCodeClientReference {
  const fallback = DEFAULT_API_CODE_PREFERENCES[client]
  return {
    instanceName: validIdentifier(reference.instanceName) ? reference.instanceName : fallback.instanceName,
    importPath: reference.importPath.trim(),
    importStyle: reference.importStyle,
  }
}

function toPascalIdentifier(value: string): string {
  const words = value
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .split(/[^A-Za-z0-9$]+/)
    .filter(Boolean)
  return words.map((word) => upperFirst(word)).join('').replace(/^([0-9])/, '_$1')
}

function conciseOperationId(value: string): string {
  const segments = value.split('_').filter(Boolean)
  const owner = segments[segments.length - 2] ?? ''
  return /(Api|Contract|Controller|Service)$/.test(owner) ? segments[segments.length - 1] : value
}

function validIdentifier(value: string): boolean {
  const identifier = value.trim()
  return /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(identifier) && !TYPESCRIPT_RESERVED_WORDS.has(identifier)
}

function renderPropertyName(value: string): string {
  return validIdentifier(value) ? value : `'${escapeSingleQuoted(value)}'`
}

function propertyAccess(target: string, property: string): string {
  return validIdentifier(property) ? `${target}.${property}` : `${target}['${escapeSingleQuoted(property)}']`
}

function isObjectSchema(schema: ApiSchema): boolean {
  return schema.type === 'object' || schema.properties !== undefined
}

function renderLiteral(value: unknown): string {
  return typeof value === 'string' ? `'${escapeSingleQuoted(value)}'` : JSON.stringify(value)
}

function escapeSingleQuoted(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

function escapeComment(value: string): string {
  return value.replace(/\*\//g, '* /').replace(/\s+/g, ' ')
}

function upperFirst(value: string): string {
  return value ? value[0].toUpperCase() + value.slice(1) : value
}

function lowerFirst(value: string): string {
  const identifier = value ? value[0].toLowerCase() + value.slice(1) : value
  return TYPESCRIPT_RESERVED_WORDS.has(identifier) ? `request${upperFirst(identifier)}` : identifier
}

const TYPESCRIPT_RESERVED_WORDS = new Set([
  'await', 'break', 'case', 'catch', 'class', 'const', 'continue', 'debugger', 'default', 'delete',
  'do', 'else', 'enum', 'export', 'extends', 'false', 'finally', 'for', 'function', 'if', 'import',
  'in', 'instanceof', 'interface', 'let', 'new', 'null', 'package', 'private', 'protected', 'public',
  'return', 'static', 'super', 'switch', 'this', 'throw', 'true', 'try', 'typeof', 'undefined', 'var',
  'void', 'while', 'with', 'yield',
])
