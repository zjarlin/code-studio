import { afterEach, describe, expect, it, vi } from 'vitest'

import { createLibrary, fetchLibraries } from './commands'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Library OpenAPI client', () => {
  it('uses the generated page operation', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(Response.json({
      code: 0,
      msg: '',
      data: { list: [], total: 0 },
    }))
    vi.stubGlobal('fetch', fetcher)

    await expect(fetchLibraries()).resolves.toEqual([])

    expect(fetcher).toHaveBeenCalledOnce()
    expect(String(fetcher.mock.calls[0]?.[0])).toBe('/studio/api/lowcode/library/page?pageNo=1&pageSize=1000')
  })

  it('validates the normalized command before creating it', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { valid: true, errors: [], warnings: [] } }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: 1 }))
    vi.stubGlobal('fetch', fetcher)

    await createLibrary({
      code: ' example ',
      contributorId: 'example',
      displayName: ' Example Library ',
      packagePrefix: ' com.example.application ',
    })

    expect(fetcher.mock.calls.map(([path, init]) => [String(path), init?.method])).toEqual([
      ['/studio/api/lowcode/library/validate', 'POST'],
      ['/studio/api/lowcode/library/add', 'POST'],
    ])
    const command = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))
    expect(command).toMatchObject({
      code: 'example',
      displayName: 'Example Library',
      spec: {
        contributorId: 'example',
        packagePrefix: 'com.example.application',
        scanPackage: 'com.example.application',
      },
    })
  })
})
