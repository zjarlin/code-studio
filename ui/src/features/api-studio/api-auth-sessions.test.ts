import { describe, expect, it } from 'vitest'

import { createApiAuthSession, useApiAuthSessions } from './api-auth-sessions'

describe('API Studio auth sessions', () => {
  it('creates a normalized in-memory Bearer session', () => {
    const session = createApiAuthSession({
      contributorId: ' example-app ',
      name: ' 只读令牌 ',
      accessToken: ' access-token ',
    }, {
      idFactory: () => 'session-1',
      now: () => new Date('2026-08-20T10:00:00Z'),
    })

    expect(session).toEqual({
      id: 'session-1',
      contributorId: 'example-app',
      name: '只读令牌',
      accessToken: 'access-token',
      updatedAt: '2026-08-20T10:00:00.000Z',
    })
  })

  it('switches multiple identities per contributor without persistent storage', () => {
    const sessions = useApiAuthSessions()
    sessions.upsert(authSession('admin', 'example-app', '管理员'))
    sessions.upsert(authSession('viewer', 'example-app', '只读用户'))
    sessions.upsert(authSession('operator', 'other-app', '操作员'))

    expect(sessions.sessionsFor('example-app').map((session) => session.id)).toEqual(['viewer', 'admin'])
    expect(sessions.activeSession('example-app')?.id).toBe('viewer')
    expect(sessions.activeSession('other-app')?.id).toBe('operator')

    sessions.select('example-app', 'admin')
    expect(sessions.activeSession('example-app')?.accessToken).toBe('token-admin')

    sessions.remove('example-app', 'admin')
    expect(sessions.activeSession('example-app')?.id).toBe('viewer')
    expect(useApiAuthSessions().sessionsFor('example-app')).toHaveLength(0)
  })

  it('rejects an empty token', () => {
    expect(() => createApiAuthSession({
      contributorId: 'example-app',
      name: '默认',
      accessToken: ' ',
    })).toThrow('Bearer Token 不能为空')
  })
})

function authSession(id: string, contributorId: string, name: string) {
  return {
    id,
    contributorId,
    name,
    accessToken: `token-${id}`,
    updatedAt: `2026-08-20T10:00:0${id === 'admin' ? '0' : '1'}.000Z`,
  }
}
