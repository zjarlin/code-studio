import { describe, expect, it, vi } from 'vitest'

import { availableStudioWorkspaces, loadStudioConfig } from './studio-config'

describe('Studio config', () => {
  it('loads the current host contract', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      contributorId: 'example-app',
      displayName: 'Example Application',
      apiBaseUrl: 'http://localhost:8080',
      openApiPath: '/v3/api-docs',
      editableContributorId: 'example-app',
      capabilities: ['metadata', 'api'],
    })))

    await expect(loadStudioConfig(fetcher)).resolves.toMatchObject({
      contributorId: 'example-app',
      editableContributorId: 'example-app',
    })
    expect(fetcher).toHaveBeenCalledWith('/studio/config')
  })

  it('rejects an incomplete host contract', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({
      contributorId: 'example-app',
    })))

    await expect(loadStudioConfig(fetcher)).rejects.toThrow('Studio 配置格式无效')
  })

  it('exposes only workspaces declared by host capabilities', () => {
    expect(availableStudioWorkspaces({
      contributorId: 'example-app',
      displayName: 'Example Application',
      apiBaseUrl: 'http://localhost:8080',
      openApiPath: '/v3/api-docs',
      editableContributorId: 'example-app',
      capabilities: ['metadata', 'api'],
    })).toEqual(['library', 'api'])
  })
})
