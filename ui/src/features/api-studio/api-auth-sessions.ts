import { computed, ref } from 'vue'

export interface ApiAuthSession {
  id: string
  contributorId: string
  name: string
  accessToken: string
  updatedAt: string
}

export interface ApiAuthSessionDraft {
  id?: string
  contributorId: string
  name: string
  accessToken: string
}

interface CreateApiAuthSessionOptions {
  idFactory?: () => string
  now?: () => Date
}

export function useApiAuthSessions() {
  const sessions = ref<ApiAuthSession[]>([])
  const activeSessionIds = ref<Record<string, string>>({})

  function sessionsFor(contributorId: string): ApiAuthSession[] {
    return sessions.value
      .filter((session) => session.contributorId === contributorId)
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  }

  function activeSession(contributorId: string): ApiAuthSession | undefined {
    const activeId = activeSessionIds.value[contributorId]
    return sessions.value.find((session) => session.contributorId === contributorId && session.id === activeId)
  }

  function upsert(session: ApiAuthSession): void {
    sessions.value = [
      session,
      ...sessions.value.filter((candidate) => candidate.id !== session.id),
    ]
    select(session.contributorId, session.id)
  }

  function select(contributorId: string, sessionId?: string): void {
    const next = { ...activeSessionIds.value }
    if (sessionId) {
      next[contributorId] = sessionId
    } else {
      delete next[contributorId]
    }
    activeSessionIds.value = next
  }

  function remove(contributorId: string, sessionId: string): void {
    sessions.value = sessions.value.filter((session) => session.id !== sessionId)
    if (activeSessionIds.value[contributorId] === sessionId) {
      select(contributorId, sessionsFor(contributorId)[0]?.id)
    }
  }

  return {
    activeSession,
    activeSessionIds: computed(() => activeSessionIds.value),
    remove,
    select,
    sessions: computed(() => sessions.value),
    sessionsFor,
    upsert,
  }
}

export function createApiAuthSession(
  draft: ApiAuthSessionDraft,
  options: CreateApiAuthSessionOptions = {},
): ApiAuthSession {
  const accessToken = required(draft.accessToken, 'Bearer Token 不能为空')
  return {
    id: draft.id ?? (options.idFactory ?? createSessionId)(),
    contributorId: required(draft.contributorId, '宿主 contributor ID 不能为空'),
    name: required(draft.name, '登录态名称不能为空'),
    accessToken,
    updatedAt: (options.now ?? (() => new Date()))().toISOString(),
  }
}

function required(value: string, message: string): string {
  const normalized = value.trim()
  if (!normalized) {
    throw new Error(message)
  }
  return normalized
}

function createSessionId(): string {
  return crypto.randomUUID()
}
