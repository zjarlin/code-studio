import {
  deleteAgentConversations,
  listAgentMessages,
  updateAgentConversationModel,
} from '@generated/openapi/client'
import type { AgentMessageView } from '@generated/openapi/models'

import { applicationRequestOptions } from '@/lib/application-client'
import { requireApiData } from '@/lib/http'

export async function fetchAgentMessages(conversationId: number): Promise<AgentMessageView[]> {
  return requireApiData(await listAgentMessages({ id: conversationId }, await applicationRequestOptions()), 'Agent 消息响应缺少 data')
}

export async function changeAgentConversationModel(conversationId: number, modelId: string): Promise<void> {
  requireApiData(await updateAgentConversationModel({ conversationId, modelId }, await applicationRequestOptions()), 'Agent 模型更新响应缺少 data')
}

export async function removeAgentConversation(conversationId: number): Promise<void> {
  requireApiData(await deleteAgentConversations([conversationId], await applicationRequestOptions()), 'Agent 会话删除响应缺少 data')
}
