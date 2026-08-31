import { requestData } from '@/lib/http'

export interface AgentSettings {
  baseUrl: string
  apiKeyConfigured: boolean
  apiKeyMasked?: string | null
}

export interface AgentSettingsCommand {
  baseUrl: string
  apiKey?: string | null
}

export function fetchAgentSettings(): Promise<AgentSettings> {
  return requestData('/studio/api/agent/settings')
}

export function updateAgentSettings(command: AgentSettingsCommand): Promise<AgentSettings> {
  return requestData('/studio/api/agent/settings', {
    method: 'PUT',
    body: JSON.stringify(command),
  })
}
