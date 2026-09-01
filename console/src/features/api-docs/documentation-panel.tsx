import {
  requestBodySample,
  requestBodySchema,
  responseDocumentation,
  schemaRows,
} from '@platform/openapi-workbench'
import type {
  ApiDocument,
  ApiOperation,
  ApiSchemaRow,
} from '@platform/openapi-workbench'

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/generated/shadcn/table'

interface DocumentationPanelProps {
  document: ApiDocument
  operation: ApiOperation
}

export function DocumentationPanel({ document, operation }: DocumentationPanelProps) {
  const requestRows = schemaRows(requestBodySchema(operation), document)
  const requestSample = requestBodySample(operation, document)
  const responses = responseDocumentation(operation, document)
  const security = operation.security.flatMap((requirement) => Object.keys(requirement))

  return (
    <div className="api-documentation">
      <section>
        <h3>接口说明</h3>
        <p>{operation.description || '未提供接口说明。'}</p>
        <dl className="api-definition-grid">
          <div><dt>Operation ID</dt><dd><code>{operation.id}</code></dd></div>
          <div><dt>权限</dt><dd>{operation.permission || '无额外声明'}</dd></div>
          <div><dt>鉴权</dt><dd>{security.length ? security.join('、') : '沿用宿主登录态'}</dd></div>
          <div><dt>请求内容类型</dt><dd>{Object.keys(operation.requestBody?.content ?? {}).join('、') || '无请求体'}</dd></div>
        </dl>
      </section>

      <section>
        <h3>参数</h3>
        {operation.parameters.length ? (
          <Table className="api-schema-table">
            <TableHeader><TableRow><TableHead>名称</TableHead><TableHead>位置</TableHead><TableHead>类型</TableHead><TableHead>必填</TableHead><TableHead>说明</TableHead></TableRow></TableHeader>
            <TableBody>
              {operation.parameters.map((parameter) => (
                <TableRow key={`${parameter.in}:${parameter.name}`}>
                  <TableCell><code>{parameter.name}</code></TableCell>
                  <TableCell>{parameter.in}</TableCell>
                  <TableCell>{schemaType(parameter.schema?.type)}</TableCell>
                  <TableCell>{parameter.required ? '是' : '否'}</TableCell>
                  <TableCell>{parameter.description || '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : <p className="api-muted">无参数</p>}
      </section>

      {operation.requestBody && (
        <section>
          <h3>请求 Schema</h3>
          {operation.requestBody.description && <p>{operation.requestBody.description}</p>}
          <SchemaTable rows={requestRows} />
          {requestSample && <CodeSample title="请求示例" value={requestSample} />}
        </section>
      )}

      <section>
        <h3>响应</h3>
        {responses.map((response) => {
          const media = Object.values(response.response.content ?? {})[0]
          const sample = media?.example ?? Object.values(media?.examples ?? {})[0]?.value
          const rows = schemaRows(media?.schema, document)
          return (
            <article className="api-response-doc" key={response.status}>
              <header>
                <strong>{response.status}</strong>
                <span>{response.description}</span>
                <small>{response.contentTypes.join('、') || '无正文'}</small>
              </header>
              <SchemaTable rows={rows} />
              {sample !== undefined && <CodeSample title="响应示例" value={formatSample(sample)} />}
              {response.headers.length > 0 && (
                <dl className="api-response-headers-doc">
                  {response.headers.map((header) => (
                    <div key={header.name}>
                      <dt><code>{header.name}</code></dt>
                      <dd>{header.type} · {header.description}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </article>
          )
        })}
      </section>
    </div>
  )
}

function SchemaTable({ rows }: Readonly<{ rows: ApiSchemaRow[] }>) {
  if (!rows.length) return <p className="api-muted">未声明结构化 Schema</p>
  return (
    <Table className="api-schema-table">
      <TableHeader><TableRow><TableHead>字段</TableHead><TableHead>类型</TableHead><TableHead>必填</TableHead><TableHead>说明</TableHead></TableRow></TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={`${row.path}:${row.depth}`}>
            <TableCell><code style={{ paddingInlineStart: row.depth * 12 }}>{row.path}</code></TableCell>
            <TableCell>{row.type}</TableCell>
            <TableCell>{row.required ? '是' : '否'}</TableCell>
            <TableCell>{row.description || '-'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

function CodeSample({ title, value }: Readonly<{ title: string; value: string }>) {
  return <div className="api-code-sample"><h4>{title}</h4><pre>{value}</pre></div>
}

function formatSample(value: unknown): string {
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
}

function schemaType(type: string | string[] | undefined): string {
  return Array.isArray(type) ? type.join(' | ') : type || 'unknown'
}
