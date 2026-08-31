export type CatalogKind = 'SCENE' | 'ROUTE' | 'ELEMENT'

export interface CatalogEntry {
  routeKey: string
  elementKey?: string | null
  path?: string | null
  parentKey?: string | null
  kind: CatalogKind
  name: string
  description?: string | null
  icon?: string | null
  order: number
  permissions: string[]
  enabled: boolean
}

export interface CatalogIndex {
  entries: CatalogEntry[]
  scenes: CatalogEntry[]
  routes: CatalogEntry[]
  routesByScene: ReadonlyMap<string, CatalogEntry[]>
  routesByPath: ReadonlyMap<string, CatalogEntry>
  elementsByKey: ReadonlyMap<string, CatalogEntry>
}

export interface CommonResult<T> {
  code: number
  msg: string
  data?: T | null
}
