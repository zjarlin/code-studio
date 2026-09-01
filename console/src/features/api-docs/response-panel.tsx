import { Download, LoaderCircle } from 'lucide-react'
import { useMemo, useState } from 'react'

import { downloadApiResponseFile } from '@platform/openapi-workbench'
import type { ApiResponseState } from '@platform/openapi-workbench'

import { Button } from '@/components/button'
import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'

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

      <nav className="api-panel-tabs" aria-label="响应视图">
        <button aria-pressed={view === 'body'} onClick={() => setView('body')} type="button">正文</button>
        <button aria-pressed={view === 'headers'} onClick={() => setView('headers')} type="button">响应头</button>
        <button aria-pressed={view === 'curl'} onClick={() => setView('curl')} type="button">cURL</button>
      </nav>

      <div className="api-response-content">
        {error && !response && <div className="api-response-error" role="alert">{error}</div>}
        {!response && !error && !pending && <div className="api-empty">发送请求后在此查看响应</div>}
        {pending && !response && <div className="api-empty">正在等待服务器响应…</div>}
        {response && view === 'body' && (
          response.file ? (
            <div className="api-download-result">
              <Download />
              <strong>{response.file.fileName}</strong>
              <span>{response.file.contentType} · {formatSize(response.file.size)}</span>
              <Button onClick={() => downloadApiResponseFile(response.file!)} variant="primary">
                <Download />下载文件
              </Button>
            </div>
          ) : <pre>{body || '响应正文为空'}</pre>
        )}
        {response && view === 'headers' && <pre>{JSON.stringify(response.headers, null, 2)}</pre>}
        {response && view === 'curl' && (
          <div className="api-curl-view">
            <label>
              <input checked={showSensitive} onChange={(event) => setShowSensitive(event.target.checked)} type="checkbox" />
              显示敏感请求头
            </label>
            <pre>{curl}</pre>
            <CatalogAction elementKey="studio.api-docs.copy-curl" onClick={copyCurl} />
            {copied && <span className="form-success" role="status">已复制</span>}
          </div>
        )}
      </div>
    </aside>
  )
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
