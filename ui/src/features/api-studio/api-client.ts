import type { ApiRequestInput, ApiResponseFile, ApiResponseState } from './types'
import { multipartFields, requestContentType } from './openapi'

export async function executeApiRequest(input: ApiRequestInput): Promise<ApiResponseState> {
  const requestUrl = buildRequestUrl(input)
  const requestBody = buildRequestBody(input)
  const headers = buildHeaders(input, requestBody)
  const startedAt = performance.now()
  const response = await fetch(requestUrl, {
    method: input.operation.method.toUpperCase(),
    headers,
    body: requestBody,
  })
  const responseBody = await readResponseBody(input, response)
  const durationMs = Math.round(performance.now() - startedAt)
  const responseHeaders = Object.fromEntries(response.headers.entries())
  return {
    status: response.status,
    statusText: response.statusText,
    durationMs,
    headers: responseHeaders,
    bodyText: responseBody.bodyText,
    body: responseBody.body,
    file: responseBody.file,
    requestUrl,
    curl: buildCurl(input, requestUrl, headers),
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
  const path = input.operation.path.replace(/\{([^}]+)\}/g, (_, name: string) =>
    encodeURIComponent(input.pathValues[name] ?? `{${name}}`),
  )
  const url = new URL(buildApiUrl(input.baseUrl, path))
  url.search = ''
  url.hash = ''
  Object.entries(input.queryValues).forEach(([name, value]) => {
    if (value.trim()) {
      url.searchParams.set(name, value)
    }
  })
  return url.toString()
}

export function buildApiUrl(baseUrl: string, path: string): string {
  const url = new URL(baseUrl.trim() || window.location.origin)
  url.pathname = joinApiPath(url.pathname, path)
  url.search = ''
  url.hash = ''
  return url.toString()
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

function buildHeaders(input: ApiRequestInput, requestBody: BodyInit | undefined): Record<string, string> {
  const headers = Object.fromEntries(
    Object.entries(input.headerValues).filter(([, value]) => value.trim()),
  )
  if (input.accessToken.trim() && !Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')) {
    headers.Authorization = `Bearer ${input.accessToken.trim()}`
  }
  if (requestBody && !(requestBody instanceof FormData)
    && !Object.keys(headers).some((name) => name.toLowerCase() === 'content-type')) {
    headers['Content-Type'] = 'application/json'
  }
  return headers
}

function buildCurl(input: ApiRequestInput, requestUrl: string, headers: Record<string, string>): string {
  const lines = [`curl --request ${input.operation.method.toUpperCase()} '${requestUrl}'`]
  Object.entries(headers).forEach(([name, value]) => lines.push(`  --header '${name}: ${value}'`))
  if (requestContentType(input.operation) === 'multipart/form-data') {
    Object.entries(input.multipartValues).forEach(([name, value]) => {
      appendCurlFormValue(lines, name, value)
    })
  } else if (input.bodyText.trim() && !['get', 'head'].includes(input.operation.method)) {
    lines.push(`  --data-raw '${input.bodyText.replace(/'/g, "'\\''")}'`)
  }
  return lines.join(' \\\n')
}

function buildRequestBody(input: ApiRequestInput): BodyInit | undefined {
  if (['get', 'head'].includes(input.operation.method)) {
    return undefined
  }
  if (requestContentType(input.operation) !== 'multipart/form-data') {
    return input.bodyText.trim() ? input.bodyText : undefined
  }
  const fields = multipartFields(input.operation, input.document)
  const missing = fields.filter((field) => field.required && isEmptyMultipartValue(input.multipartValues[field.name]))
  if (missing.length) {
    throw new Error(`请填写必填表单字段：${missing.map((field) => field.name).join('、')}`)
  }
  const formData = new FormData()
  fields.forEach((field) => appendFormValue(formData, field.name, input.multipartValues[field.name]))
  return formData
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
  const rendered = value instanceof File ? `@${value.name}` : String(value).replace(/'/g, "'\\''")
  lines.push(`  --form '${name}=${rendered}'`)
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
): Promise<{ bodyText: string; body: unknown; file?: ApiResponseFile }> {
  if (!isBinaryResponse(input, response)) {
    const bodyText = await response.text()
    return { bodyText, body: parseResponseBody(bodyText) }
  }
  const blob = await response.blob()
  const contentType = normalizedContentType(response.headers.get('content-type')) || blob.type || 'application/octet-stream'
  const fileName = responseFileName(response.headers.get('content-disposition'))
    ?? fallbackFileName(input.operation.path, contentType)
  return {
    bodyText: '',
    body: null,
    file: {
      blob,
      contentType,
      fileName,
      size: blob.size,
    },
  }
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
  return contentType?.split(';')[0].trim().toLowerCase() ?? ''
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
