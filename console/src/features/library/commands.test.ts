import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  createLibrary,
  fetchConstants,
  fetchLibraries,
  fetchLibraryFeatures,
  persistConstant,
  persistLibraryFeature,
  persistLibrary,
} from './commands'
import { LibrarySpecKind } from '@generated/openapi/models'

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

  it('loads and persists feature metadata through generated operations', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { list: [null, {
        id: 7, libraryId: 3, parentId: null, featureCode: 'orders', name: '订单', description: null,
      }], total: 1 } }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { valid: true, errors: [], warnings: [] } }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: {
        id: 7, libraryId: 3, parentId: null, featureCode: 'orders', name: '订单', description: null,
      } }))
    vi.stubGlobal('fetch', fetcher)

    await expect(fetchLibraryFeatures(3)).resolves.toHaveLength(1)
    await expect(persistLibraryFeature({
      id: 7,
      libraryId: 3,
      parentId: null,
      featureCode: 'orders',
      name: '订单',
      description: null,
    })).resolves.toMatchObject({ id: 7 })

    expect(fetcher.mock.calls.map(([path, init]) => [String(path), init?.method])).toEqual([
      ['/studio/api/lowcode/library-feature/page?libraryId=3&pageNo=1&pageSize=1000', 'GET'],
      ['/studio/api/lowcode/library-feature/validate', 'POST'],
      ['/studio/api/lowcode/library-feature/update', 'PUT'],
    ])
  })

  it('filters nullable constant groups and validates before saving', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: [null, {
        id: 11, featureId: 7, groupCode: 'orderStatuses', featurePackageName: 'orders',
        contributorId: 'example', objectName: 'OrderStatuses', description: '订单状态', constants: [],
      }] }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { valid: true, errors: [], warnings: [] } }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: {
        id: 11, featureId: 7, groupCode: 'orderStatuses', featurePackageName: 'orders',
        contributorId: 'example', objectName: 'OrderStatuses', description: '订单状态', constants: [],
      } }))
    vi.stubGlobal('fetch', fetcher)

    await expect(fetchConstants(7)).resolves.toHaveLength(1)
    await expect(persistConstant({
      id: 11,
      featureId: 7,
      groupCode: 'orderStatuses',
      objectName: 'OrderStatuses',
      description: '订单状态',
      constants: [],
    })).resolves.toMatchObject({ id: 11 })

    expect(fetcher.mock.calls.map(([path, init]) => [String(path), init?.method])).toEqual([
      ['/studio/api/lowcode/constant/list', 'POST'],
      ['/studio/api/lowcode/constant/validate', 'POST'],
      ['/studio/api/lowcode/constant/save', 'POST'],
    ])
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({ featureId: 7 })
  })

  it('uses the contributor boundary when updating a Library', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: { valid: true, errors: [], warnings: [] } }))
      .mockResolvedValueOnce(Response.json({ code: 0, msg: '', data: true }))
    vi.stubGlobal('fetch', fetcher)

    await persistLibrary({
      id: 3,
      code: 'example',
      displayName: 'Example',
      version: 1,
      status: 1,
      spec: {
        schemaVersion: 3,
        description: null,
        contributorId: 'example',
        packagePrefix: 'com.example',
        scanPackage: 'com.example',
        kind: LibrarySpecKind.BUSINESS,
        runtimeDependencies: [],
        supportedIdentityModes: [],
        applicationSelectable: true,
        dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
      },
    })
    expect(fetcher.mock.calls.map(([path, init]) => [String(path), init?.method])).toEqual([
      ['/studio/api/lowcode/library/validate', 'POST'],
      ['/studio/api/lowcode/library/update', 'PUT'],
    ])
  })
})
