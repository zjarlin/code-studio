import { describe, expect, it, vi } from 'vitest'

import { CATALOG_ENDPOINT, createCatalogIndex, loadCatalog, parseCatalogPayload, resolveCatalogRoute } from './catalog'
import type { CatalogEntry } from './types'

describe('catalog contract', () => {
  it('unwraps CommonResult and builds sorted navigation indexes', () => {
    const index = createCatalogIndex(parseCatalogPayload({
      code: 0,
      msg: 'ok',
      data: [
        entry({ routeKey: 'studio.api', path: '/console/studio/api', parentKey: 'studio', kind: 'ROUTE', name: 'API', order: 20 }),
        entry({ routeKey: 'studio', path: '/console/studio/library', kind: 'SCENE', name: 'Studio', order: 10 }),
        entry({ routeKey: 'studio.library', path: '/console/studio/library', parentKey: 'studio', kind: 'ROUTE', name: '库', order: 10 }),
        entry({ routeKey: 'studio.library', elementKey: 'studio.library.create', parentKey: 'studio.library', kind: 'ELEMENT', name: '创建', order: 10 }),
      ],
    }))

    expect(index.routes.map(({ routeKey }) => routeKey)).toEqual(['studio.library', 'studio.api'])
    expect(index.routesByScene.get('studio')?.map(({ routeKey }) => routeKey)).toEqual(['studio.library', 'studio.api'])
    expect(index.routesByPath.get('/console/studio/library')?.routeKey).toBe('studio.library')
    expect(resolveCatalogRoute(index, '/studio/library')?.routeKey).toBe('studio.library')
    expect(index.elementsByKey.get('studio.library.create')?.name).toBe('创建')
  })

  it('rejects an element whose route was permission-filtered out', () => {
    expect(() => createCatalogIndex([
      entry({ routeKey: 'studio', path: '/console/studio', kind: 'SCENE', name: 'Studio' }),
      entry({ routeKey: 'missing', elementKey: 'missing.create', kind: 'ELEMENT', name: '创建' }),
    ])).toThrow('引用未知路由')
  })

  it('uses the supplied fallback when the catalog endpoint is unavailable', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new Error('offline'))
    const fallback = [
      entry({ routeKey: 'studio', path: '/console/studio/library', kind: 'SCENE', name: 'Studio' }),
      entry({ routeKey: 'studio.library', path: '/console/studio/library', parentKey: 'studio', kind: 'ROUTE', name: '库' }),
    ]

    const catalog = await loadCatalog(fetcher, fallback)

    expect(fetcher).toHaveBeenCalledWith(CATALOG_ENDPOINT, { headers: { Accept: 'application/json' } })
    expect(catalog.routes[0]?.routeKey).toBe('studio.library')
  })

  it('scans scene and feature convention files for the development catalog', async () => {
    const fetcher = vi.fn<typeof fetch>().mockRejectedValue(new Error('offline'))

    const catalog = await loadCatalog(fetcher)

    expect(catalog.scenes.map(({ routeKey }) => routeKey)).toEqual(['studio', 'agent'])
    expect(catalog.routes.map(({ routeKey }) => routeKey)).toEqual([
      'studio.library',
      'studio.api-docs',
      'agent.chat',
      'agent.settings',
    ])
    expect(catalog.elementsByKey.has('studio.library.create')).toBe(true)
    expect(catalog.elementsByKey.has('agent.settings.save')).toBe(true)
  })
})

function entry(overrides: Partial<CatalogEntry> & Pick<CatalogEntry, 'kind' | 'name' | 'routeKey'>): CatalogEntry {
  return {
    routeKey: overrides.routeKey,
    elementKey: overrides.elementKey,
    path: overrides.path,
    parentKey: overrides.parentKey,
    kind: overrides.kind,
    name: overrides.name,
    description: overrides.description,
    icon: overrides.icon,
    order: overrides.order ?? 0,
    permissions: overrides.permissions ?? [],
    enabled: overrides.enabled ?? true,
  }
}
