import { requestData } from '@/lib/http'

export interface AgentConversation {
  id: number | string
  externalId: string
  title: string
  modelId?: string | null
  createTime: string
  updateTime: string
}

export interface AgentModel {
  id: string
  contextWindow: number
  contextWindowEstimated: boolean
}

export function fetchConversations(): Promise<AgentConversation[]> {
  return requestData('/studio/api/agent/conversations')
}

export function fetchAgentModels(): Promise<AgentModel[]> {
  return requestData('/studio/api/agent/models')
}

export function createConversation(title: string, modelId: string): Promise<number | string> {
  return requestData('/studio/api/agent/conversations', {
    method: 'POST',
    body: JSON.stringify({ title: title.trim() || undefined, modelId }),
  })
}
