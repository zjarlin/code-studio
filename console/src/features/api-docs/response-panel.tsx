import { Download, LoaderCircle } from 'lucide-react'
import { useMemo, useState } from 'react'

import { downloadApiResponseFile } from '@platform/openapi-workbench'
import type { ApiResponseState } from '@platform/openapi-workbench'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { Button } from '@/components/generated/shadcn/button'
import { Checkbox } from '@/components/generated/shadcn/checkbox'
import { Field, FieldLabel } from '@/components/generated/shadcn/field'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/generated/shadcn/tabs'

interface ResponsePanelProps {
  error: string
  pending: boolean
  response?: ApiResponseState
}

export function ResponsePanel({ error, pending, response }: ResponsePanelProps) {
  const [view, setView] = useState<'body' | 'headers' | 'curl'>('body')
  const [showSensitive, setShowSensitive] = useState(false)
  const [copied, setCopied] = useState(false)
  const body = useMemo(() => {
    if (!response) return ''
    if (typeof response.body === 'string') return response.body
    return JSON.stringify(response.body, null, 2)
  }, [response])
  const curl = response ? (showSensitive ? response.curl : response.redactedCurl) : ''
  const successful = response
    ? response.status >= 200 && response.status < 400
      && (response.applicationCode === undefined || response.applicationCode === 0)
    : false

  async function copyCurl(): Promise<void> {
    if (!curl) return
    await navigator.clipboard.writeText(curl)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1_500)
  }

  return (
    <aside className="api-response-panel" aria-label="响应面板">
      <header className="api-response-heading">
        <div>
          <h2>响应</h2>
          {pending && <span className="api-pending"><LoaderCircle />请求中</span>}
        </div>
        {response && (
          <div className="api-response-metrics">
            <strong className={successful ? 'is-success' : 'is-error'}>{response.status} {response.statusText}</strong>
            <span>{response.durationMs} ms</span>
            <span>{formatSize(response.bodySize)}</span>
          </div>
        )}
      </header>

      {response && (response.applicationCode !== undefined || response.applicationMessage) && (
        <div className={`api-application-status ${successful ? 'is-success' : 'is-error'}`}>
          <span>业务状态</span>
          <strong>{response.applicationCode ?? '-'}</strong>
          <p>{response.applicationMessage || '未返回消息'}</p>
        </div>
      )}

      <Tabs onValueChange={(value) => setView(value as typeof view)} value={view}>
        <TabsList aria-label="响应视图" className="api-panel-tabs">
          <TabsTrigger value="body">正文</TabsTrigger>
          <TabsTrigger value="headers">响应头</TabsTrigger>
          <TabsTrigger value="curl">cURL</TabsTrigger>
        </TabsList>
        <div className="api-response-content">
        {error && !response && <div className="api-response-error" role="alert">{error}</div>}
        {!response && !error && !pending && <div className="api-empty">发送请求后在此查看响应</div>}
        {pending && !response && <div className="api-empty">正在等待服务器响应…</div>}
        {response ? <TabsContent value="body">
          {response.file ? (
            <div className="api-download-result">
              <Download />
              <strong>{response.file.fileName}</strong>
              <span>{response.file.contentType} · {formatSize(response.file.size)}</span>
              <Button onClick={() => downloadApiResponseFile(response.file!)}>
                <Download data-icon="inline-start" />下载文件
              </Button>
            </div>
          ) : <pre>{body || '响应正文为空'}</pre>}
        </TabsContent> : null}
        {response ? <TabsContent value="headers"><pre>{JSON.stringify(response.headers, null, 2)}</pre></TabsContent> : null}
        {response ? <TabsContent value="curl">
          <div className="api-curl-view">
            <Field orientation="horizontal">
              <Checkbox checked={showSensitive} id="show-sensitive-headers" onCheckedChange={(checked) => setShowSensitive(checked === true)} />
              <FieldLabel htmlFor="show-sensitive-headers">显示敏感请求头</FieldLabel>
            </Field>
            <pre>{curl}</pre>
            <CatalogAction elementKey="studio.api-docs.copy-curl" onClick={copyCurl} />
            {copied && <span className="form-success" role="status">已复制</span>}
          </div>
        </TabsContent> : null}
        </div>
      </Tabs>
    </aside>
  )
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
