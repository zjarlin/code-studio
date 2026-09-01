import type { ApiRequestInput, ApiResponseFile, ApiResponseState } from './types'
import { requestContentType, requestFormFields } from './openapi'

export interface ApiExecutionOptions {
  fetcher?: typeof fetch
  now?: () => number
}

export type ApiPathMode = 'relative' | 'rooted'

export async function executeApiRequest(
  input: ApiRequestInput,
  options: ApiExecutionOptions = {},
): Promise<ApiResponseState> {
  validateRequiredParameters(input)
  validateRequiredRequestBody(input)
  const requestUrl = buildRequestUrl(input)
  const requestBody = buildRequestBody(input)
  const headers = buildHeaders(input, requestBody)
  const now = options.now ?? (() => performance.now())
  const fetcher = options.fetcher ?? fetch
  const startedAt = now()
  const response = await fetcher(requestUrl, {
    method: input.operation.method.toUpperCase(),
    headers,
    body: requestBody,
  })
  const responseBody = await readResponseBody(input, response)
  const durationMs = Math.round(now() - startedAt)
  const responseHeaders = Object.fromEntries(response.headers.entries())
  const applicationStatus = readApplicationStatus(responseBody.body)
  const curl = buildCurl(input, requestUrl, headers, false)
  const redactedCurl = buildCurl(input, requestUrl, headers, true)
  return {
    status: response.status,
    statusText: response.statusText,
    durationMs,
    headers: responseHeaders,
    bodyText: responseBody.bodyText,
    body: responseBody.body,
    bodySize: responseBody.bodySize,
    applicationCode: applicationStatus.code,
    applicationMessage: applicationStatus.message,
    file: responseBody.file,
    requestUrl,
    curl,
    redactedCurl,
  }
}

export function downloadApiResponseFile(file: ApiResponseFile): void {
  const url = URL.createObjectURL(file.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function buildRequestUrl(input: ApiRequestInput): string {
  const missingPath = input.operation.parameters
    .filter((parameter) => parameter.in === 'path' && parameter.required && !input.pathValues[parameter.name]?.trim())
  if (missingPath.length) {
    throw new Error(`请填写必填路径参数：${missingPath.map((parameter) => parameter.name).join('、')}`)
  }
  const path = input.operation.path.replace(/\{([^}]+)\}/g, (_, name: string) =>
    encodeURIComponent(input.pathValues[name] ?? `{${name}}`),
  )
  const mode = resolveApiPathMode(input.document, input.baseUrl)
  const url = new URL(buildApiUrl(input.baseUrl, path, mode))
  url.search = ''
  url.hash = ''
  Object.entries(input.queryValues).forEach(([name, value]) => {
    if (value.trim()) {
      url.searchParams.set(name, value)
    }
  })
  return url.toString()
}

export function buildApiUrl(baseUrl: string, path: string, mode: ApiPathMode = 'relative'): string {
  const url = new URL(baseUrl)
  url.pathname = mode === 'rooted' ? normalizedPath(path) : joinApiPath(url.pathname, path)
  url.search = ''
  url.hash = ''
  return url.toString()
}

export function resolveApiPathMode(document: ApiRequestInput['document'], baseUrl: string): ApiPathMode {
  const basePath = normalizedPath(new URL(baseUrl).pathname)
  if (basePath === '/') {
    return 'relative'
  }
  const hasPrefixedPath = Object.keys(document.paths ?? {}).some((path) => isPathWithin(path, basePath))
  return hasPrefixedPath ? 'rooted' : 'relative'
}

export function isBusinessOperation(input: Pick<ApiRequestInput, 'baseUrl' | 'document' | 'operation'>): boolean {
  const mode = resolveApiPathMode(input.document, input.baseUrl)
  if (mode === 'relative') {
    return true
  }
  return isPathWithin(input.operation.path, normalizedPath(new URL(input.baseUrl).pathname))
}

function joinApiPath(basePath: string, operationPath: string): string {
  const baseSegments = pathSegments(basePath)
  const operationSegments = pathSegments(operationPath)
  const maximumOverlap = Math.min(baseSegments.length, operationSegments.length)
  let overlap = maximumOverlap
  while (overlap > 0) {
    const baseSuffix = baseSegments.slice(baseSegments.length - overlap)
    const operationPrefix = operationSegments.slice(0, overlap)
    if (baseSuffix.every((segment, index) => segment === operationPrefix[index])) {
      break
    }
    overlap -= 1
  }
  return `/${[...baseSegments, ...operationSegments.slice(overlap)].join('/')}`
}

function pathSegments(path: string): string[] {
  return path.split('/').filter(Boolean)
}

function normalizedPath(path: string): string {
  const normalized = `/${path.split('/').filter(Boolean).join('/')}`
  return normalized === '/' ? normalized : normalized.replace(/\/+$/, '')
}

function isPathWithin(path: string, basePath: string): boolean {
  const normalized = normalizedPath(path)
  return normalized === basePath || normalized.startsWith(`${basePath}/`)
}

function buildHeaders(input: ApiRequestInput, requestBody: BodyInit | undefined): Record<string, string> {
  const headers = Object.fromEntries(
    Object.entries(input.headerValues).filter(([, value]) => value.trim()),
  )
  if (input.accessToken.trim() && !Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')) {
    headers.Authorization = `Bearer ${input.accessToken.trim()}`
  }
  const contentType = requestContentType(input.operation, input.contentType)
  if (requestBody && !(requestBody instanceof FormData) && contentType
    && !Object.keys(headers).some((name) => name.toLowerCase() === 'content-type')) {
    headers['Content-Type'] = contentType
  }
  return headers
}

function buildCurl(
  input: ApiRequestInput,
  requestUrl: string,
  headers: Record<string, string>,
  redactSensitiveHeaders: boolean,
): string {
  const lines = [`curl --request ${input.operation.method.toUpperCase()} '${requestUrl}'`]
  Object.entries(headers).forEach(([name, value]) => {
    const rendered = redactSensitiveHeaders && isSensitiveHeader(name) ? '***' : value
    lines.push(`  --header '${name}: ${escapeShellValue(rendered)}'`)
  })
  const contentType = requestContentType(input.operation, input.contentType)
  if (contentType === 'multipart/form-data') {
    Object.entries(input.multipartValues).forEach(([name, value]) => {
      appendCurlFormValue(lines, name, value)
    })
  } else if (input.bodyFile) {
    lines.push(`  --data-binary '@${escapeShellValue(input.bodyFile.name)}'`)
  } else if (contentType === 'application/x-www-form-urlencoded') {
    const values = buildUrlEncodedBody(input)
    lines.push(`  --data '${escapeShellValue(values.toString())}'`)
  } else if (input.bodyText.trim() && !['get', 'head'].includes(input.operation.method)) {
    lines.push(`  --data-raw '${escapeShellValue(input.bodyText)}'`)
  }
  return lines.join(' \\\n')
}

function buildRequestBody(input: ApiRequestInput): BodyInit | undefined {
  if (['get', 'head'].includes(input.operation.method)) {
    return undefined
  }
  const contentType = requestContentType(input.operation, input.contentType)
  if (contentType === 'application/octet-stream') {
    return input.bodyFile
  }
  if (contentType === 'application/x-www-form-urlencoded') {
    return buildUrlEncodedBody(input)
  }
  if (contentType !== 'multipart/form-data') {
    if (contentType?.includes('json') && input.bodyText.trim()) {
      JSON.parse(input.bodyText)
    }
    return input.bodyText.trim() ? input.bodyText : undefined
  }
  const fields = requestFormFields(input.operation, input.document, contentType)
  const missing = fields.filter((field) => field.required && isEmptyMultipartValue(input.multipartValues[field.name]))
  if (missing.length) {
    throw new Error(`请填写必填表单字段：${missing.map((field) => field.name).join('、')}`)
  }
  const formData = new FormData()
  fields.forEach((field) => appendFormValue(formData, field.name, input.multipartValues[field.name]))
  return formData
}

function buildUrlEncodedBody(input: ApiRequestInput): URLSearchParams {
  const body = new URLSearchParams()
  Object.entries(input.multipartValues).forEach(([name, value]) => appendSearchValue(body, name, value))
  return body
}

function appendSearchValue(body: URLSearchParams, name: string, value: ApiRequestInput['multipartValues'][string]): void {
  if (value === undefined || value === '') {
    return
  }
  if (Array.isArray(value)) {
    value.forEach((item) => appendSearchValue(body, name, item))
    return
  }
  if (value instanceof File) {
    throw new Error(`表单字段 ${name} 不支持文件`)
  }
  body.append(name, String(value))
}

function appendFormValue(formData: FormData, name: string, value: ApiRequestInput['multipartValues'][string]): void {
  if (value === undefined || value === '') {
    return
  }
  if (Array.isArray(value)) {
    value.forEach((item) => appendFormValue(formData, name, item))
    return
  }
  formData.append(name, value instanceof File ? value : String(value))
}

function appendCurlFormValue(
  lines: string[],
  name: string,
  value: ApiRequestInput['multipartValues'][string],
): void {
  if (value === undefined || value === '') {
    return
  }
  if (Array.isArray(value)) {
    value.forEach((item) => appendCurlFormValue(lines, name, item))
    return
  }
  const rendered = value instanceof File ? `@${value.name}` : String(value)
  const escaped = escapeShellValue(rendered)
  lines.push(`  --form '${name}=${escaped}'`)
}

function isEmptyMultipartValue(value: ApiRequestInput['multipartValues'][string]): boolean {
  return value === undefined || value === '' || (Array.isArray(value) && value.length === 0)
}

function parseResponseBody(bodyText: string): unknown {
  if (!bodyText.trim()) {
    return null
  }
  try {
    return JSON.parse(bodyText)
  } catch {
    return bodyText
  }
}

async function readResponseBody(
  input: ApiRequestInput,
  response: Response,
): Promise<{ bodyText: string; body: unknown; bodySize: number; file?: ApiResponseFile }> {
  if (!isBinaryResponse(input, response)) {
    const bodyText = await response.text()
    return { bodyText, body: parseResponseBody(bodyText), bodySize: new TextEncoder().encode(bodyText).byteLength }
  }
  const blob = await response.blob()
  const contentType = normalizedContentType(response.headers.get('content-type')) || blob.type || 'application/octet-stream'
  const fileName = responseFileName(response.headers.get('content-disposition'))
    ?? fallbackFileName(input.operation.path, contentType)
  return {
    bodyText: '',
    body: null,
    bodySize: blob.size,
    file: {
      blob,
      contentType,
      fileName,
      size: blob.size,
    },
  }
}

function validateRequiredParameters(input: ApiRequestInput): void {
  const missing = input.operation.parameters.filter((parameter) => {
    if (!parameter.required || parameter.in === 'cookie') {
      return false
    }
    const values = parameter.in === 'path'
      ? input.pathValues
      : parameter.in === 'query'
        ? input.queryValues
        : input.headerValues
    return !values[parameter.name]?.trim()
  })
  if (missing.length) {
    throw new Error(`请填写必填参数：${missing.map((parameter) => parameter.name).join('、')}`)
  }
}

function validateRequiredRequestBody(input: ApiRequestInput): void {
  if (!input.operation.requestBody?.required || ['get', 'head'].includes(input.operation.method)) return
  const contentType = requestContentType(input.operation, input.contentType)
  if (contentType === 'application/octet-stream') {
    if (!input.bodyFile) throw new Error('请选择必填的二进制文件')
    return
  }
  if (contentType === 'multipart/form-data' || contentType === 'application/x-www-form-urlencoded') {
    if (Object.values(input.multipartValues).every(isEmptyMultipartValue)) throw new Error('请填写必填请求体')
    return
  }
  if (!input.bodyText.trim()) throw new Error('请填写必填请求体')
}

function readApplicationStatus(body: unknown): { code?: number; message?: string } {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    return {}
  }
  const payload = body as Record<string, unknown>
  const code = typeof payload.code === 'number' ? payload.code : undefined
  const messageValue = payload.msg ?? payload.message
  const message = typeof messageValue === 'string' ? messageValue : undefined
  return { code, message }
}

function isSensitiveHeader(name: string): boolean {
  const normalized = name.toLowerCase()
  return normalized === 'authorization'
    || normalized === 'cookie'
    || normalized.includes('token')
    || normalized.includes('api-key')
}

function escapeShellValue(value: string): string {
  return value.replace(/'/g, "'\\''")
}

function isBinaryResponse(input: ApiRequestInput, response: Response): boolean {
  const contentType = normalizedContentType(response.headers.get('content-type'))
  if (isTextContentType(contentType)) {
    return false
  }
  const disposition = response.headers.get('content-disposition') ?? ''
  if (/\battachment\b/i.test(disposition) || isBinaryContentType(contentType)) {
    return true
  }
  const documentedResponse = input.operation.responses[String(response.status)]
    ?? input.operation.responses.default
    ?? input.operation.responses['200']
    ?? Object.values(input.operation.responses)[0]
  return Object.values(documentedResponse?.content ?? {}).some((media) => media.schema?.format === 'binary')
}

function responseFileName(contentDisposition: string | null): string | undefined {
  if (!contentDisposition) {
    return undefined
  }
  const encoded = contentDisposition.match(/filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i)?.[1]
  if (encoded) {
    return safeFileName(decodeHeaderValue(encoded))
  }
  const regular = contentDisposition.match(/filename\s*=\s*(?:"([^"]+)"|([^;]+))/i)
  return safeFileName((regular?.[1] ?? regular?.[2] ?? '').trim())
}

function decodeHeaderValue(value: string): string {
  const normalized = value.trim().replace(/^['"]|['"]$/g, '')
  try {
    return decodeURIComponent(normalized)
  } catch {
    return normalized
  }
}

function safeFileName(value: string): string | undefined {
  const fileName = value.split(/[\\/]/).pop()?.replace(/[\u0000-\u001f\u007f]/g, '').trim()
  return fileName || undefined
}

function fallbackFileName(path: string, contentType: string): string {
  const baseName = path.split('/').filter(Boolean).pop() ?? 'download'
  const extension = CONTENT_TYPE_EXTENSIONS[contentType] ?? ''
  return `${baseName}${extension}`
}

function normalizedContentType(contentType: string | null): string {
  return contentType?.split(';')[0]?.trim().toLowerCase() ?? ''
}

function isTextContentType(contentType: string): boolean {
  return contentType.startsWith('text/')
    || contentType.includes('json')
    || contentType.includes('xml')
    || contentType === 'application/x-www-form-urlencoded'
}

function isBinaryContentType(contentType: string): boolean {
  return contentType === 'application/octet-stream'
    || contentType === 'application/pdf'
    || contentType === 'application/zip'
    || contentType.startsWith('application/vnd.')
    || contentType.startsWith('image/')
    || contentType.startsWith('audio/')
    || contentType.startsWith('video/')
}

const CONTENT_TYPE_EXTENSIONS: Record<string, string> = {
  'application/pdf': '.pdf',
  'application/vnd.ms-excel': '.xls',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': '.xlsx',
  'application/zip': '.zip',
  'image/jpeg': '.jpg',
  'image/png': '.png',
}
