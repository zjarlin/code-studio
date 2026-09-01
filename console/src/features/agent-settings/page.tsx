import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { getAgentSettings, updateAgentSettings } from '@generated/openapi/client'
import type { AgentProviderSettingsCommand } from '@generated/openapi/models'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { PageHeader } from '@/components/composed/page-header/page-header'
import { QueryState } from '@/components/composed/query-state/query-state'
import type { CatalogPageProps } from '@/features/page-registry'
import { applicationRequestOptions } from '@/lib/application-client'
import { requireApiData } from '@/lib/http'

export default function AgentSettingsPage({ route }: CatalogPageProps) {
  const queryClient = useQueryClient()
  const settings = useQuery({
    queryKey: ['agent-settings'],
    queryFn: async () => requireApiData(
      await getAgentSettings(await applicationRequestOptions()),
      'Agent 设置响应缺少 data',
    ),
  })
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')
  useEffect(() => {
    if (settings.data) setBaseUrl(settings.data.baseUrl)
  }, [settings.data])
  const save = useMutation({
    mutationFn: async (command: AgentProviderSettingsCommand) => requireApiData(
      await updateAgentSettings(command, await applicationRequestOptions()),
      'Agent 设置保存响应缺少 data',
    ),
    onSuccess: (result) => {
      queryClient.setQueryData(['agent-settings'], result)
      setApiKey('')
    },
  })

  return (
    <div className="page-frame">
      <PageHeader route={route} />
      <section className="settings-pane">
        <QueryState error={settings.error} pending={settings.isPending}>
          <form onSubmit={(event) => {
            event.preventDefault()
            save.mutate({ baseUrl: baseUrl.trim(), apiKey: apiKey.trim() || null })
          }}>
            <div className="settings-heading">
              <h2>模型服务</h2>
              <p>设置 OpenAI 兼容服务地址和密钥。</p>
            </div>
            <label>
              <span>Base URL</span>
              <input onChange={(event) => setBaseUrl(event.target.value)} placeholder="https://api.example.com/v1" required type="url" value={baseUrl} />
            </label>
            <label>
              <span>API Key</span>
              <input autoComplete="new-password" onChange={(event) => setApiKey(event.target.value)} placeholder={settings.data?.apiKeyMasked || '未配置'} type="password" value={apiKey} />
              <small>{settings.data?.apiKeyConfigured ? '已在服务端配置，留空可保持当前密钥。' : '尚未配置密钥。'}</small>
            </label>
            {save.error && <p className="form-error" role="alert">{save.error.message}</p>}
            {save.isSuccess && <p className="form-success" role="status">设置已保存</p>}
            <div className="form-actions">
              <CatalogAction disabled={save.isPending} elementKey="agent.settings.save" type="submit" variant="primary" />
            </div>
          </form>
        </QueryState>
      </section>
    </div>
  )
}
