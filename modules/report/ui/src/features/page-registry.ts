import { lazy, type ComponentType, type LazyExoticComponent } from 'react'

import { parseCatalogEntries } from '@/catalog/catalog'
import type { CatalogEntry } from '@/catalog/types'

export interface CatalogPageProps {
  route: CatalogEntry
}

interface CatalogPageModule {
  default: ComponentType<CatalogPageProps>
}

const pageLoaders = import.meta.glob<CatalogPageModule>('./*/page.tsx')
const conventionModules = import.meta.glob<unknown>('./*/catalog.convention.json', {
  eager: true,
  import: 'default',
})
const pages = new Map<string, LazyExoticComponent<ComponentType<CatalogPageProps>>>()

Object.entries(conventionModules).sort(([left], [right]) => left.localeCompare(right)).forEach(([path, entries]) => {
  const routes = parseCatalogEntries(entries).filter((entry) => entry.kind === 'ROUTE')
  if (routes.length !== 1) throw new Error(`页面目录 ${path} 必须声明一个 ROUTE`)

  const routeKey = routes[0]?.routeKey
  const pagePath = path.replace(/catalog\.convention\.json$/, 'page.tsx')
  const loadPage = pageLoaders[pagePath]
  if (!routeKey || !loadPage) throw new Error(`页面目录 ${path} 缺少 page.tsx`)
  if (pages.has(routeKey)) throw new Error(`重复页面 routeKey：${routeKey}`)
  pages.set(routeKey, lazy(loadPage))
})

export function resolvePage(routeKey: string): LazyExoticComponent<ComponentType<CatalogPageProps>> | undefined {
  return pages.get(routeKey)
}
