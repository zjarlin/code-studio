import { readFile, writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const METHODS = ['get', 'post', 'put', 'patch', 'delete']

export function generateStreamClients(document) {
  const operations = Object.entries(document.paths ?? {}).flatMap(([path, item]) =>
    METHODS.flatMap((method) => {
      const operation = item?.[method]
      const transport = operation?.['x-client-transport']
      return transport === 'sse' || transport === 'websocket'
        ? [{ method, operation, path, transport }]
        : []
    }),
  )
  if (!operations.length) return `${HEADER}\nexport {}\n`

  const modelNames = new Set()
  const functions = operations.map(({ method, operation, path, transport }) => {
    const operationId = requiredOperationId(operation)
    const pathNames = [...path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1])
    const pathArgument = pathNames.length ? 'params: Record<string, string | number>, ' : ''
    const pathExpression = pathNames.reduce(
      (value, name) => value.replace(`{${name}}`, `\${encodeURIComponent(String(params[${JSON.stringify(name)}]))}`),
      path,
    )
    if (transport === 'websocket') {
      return `export function open${pascal(operationId)}Socket(${pathArgument}options?: WebSocketRequestOptions): WebSocket {\n  return openGeneratedWebSocket(\`${pathExpression}\`, options)\n}`
    }

    const bodyType = schemaType(operation.requestBody?.content?.['application/json']?.schema, modelNames, 'undefined')
    const eventType = schemaType(operation.responses?.['200']?.content?.['text/event-stream']?.schema, modelNames, 'unknown')
    const bodyArgument = bodyType === 'undefined' ? '' : `body: ${bodyType}, `
    const bodyInit = bodyType === 'undefined' ? '' : ', body: JSON.stringify(body)'
    return `export function stream${pascal(operationId)}(${pathArgument}${bodyArgument}handlers: ServerSentEventHandlers<${eventType}>, options: ApiRequestOptions = {}): Promise<void> {\n  return openServerSentEvents<${eventType}>(\`${pathExpression}\`, { ...options, method: ${JSON.stringify(method.toUpperCase())}${bodyInit} }, handlers)\n}`
  })

  const modelImport = modelNames.size
    ? `import type { ${[...modelNames].sort().join(', ')} } from './models'\n`
    : ''
  return `${HEADER}\n${modelImport}import type { ApiRequestOptions } from '../../../src/lib/http'\nimport { openGeneratedWebSocket, openServerSentEvents } from '../../../src/lib/streaming'\nimport type { ServerSentEventHandlers, WebSocketRequestOptions } from '../../../src/lib/streaming'\n\n${functions.join('\n\n')}\n`
}

function schemaType(schema, modelNames, fallback) {
  if (!schema) return fallback
  if (schema.$ref) {
    const name = decodeURIComponent(schema.$ref.split('/').at(-1))
    modelNames.add(name)
    return name
  }
  if (schema.type === 'string') return 'string'
  return 'unknown'
}

function requiredOperationId(operation) {
  if (!operation.operationId) throw new Error('流式 OpenAPI 操作必须声明 operationId')
  return operation.operationId
}

function pascal(value) {
  return value.replace(/(^|[^A-Za-z0-9]+)([A-Za-z0-9])/g, (_, __, character) => character.toUpperCase())
}

const HEADER = '/** 此文件由 OpenAPI 流式扩展生成，请勿手工修改。 */'

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , input, output] = process.argv
  if (!input || !output) throw new Error('用法: stream-client-generator <openapi-input> <typescript-output>')
  const document = JSON.parse(await readFile(input, 'utf8'))
  await writeFile(output, generateStreamClients(document), 'utf8')
}
