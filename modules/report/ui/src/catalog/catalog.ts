import type { CatalogEntry, CatalogIndex, CatalogKind, CommonResult } from './types'

import { getConsoleCatalog, getGetConsoleCatalogUrl } from '@generated/openapi/client'

import { requireApiData } from '@/lib/http'

const CATALOG_KINDS = new Set<CatalogKind>(['SCENE', 'ROUTE', 'ELEMENT'])
const conventionModules = import.meta.glob<unknown>('../**/catalog.convention.json', {
  eager: true,
  import: 'default',
})
const defaultEntries = Object.entries(conventionModules)
  .sort(([left], [right]) => left.localeCompare(right))
  .flatMap(([, entries]) => parseCatalogEntries(entries))

export const CATALOG_ENDPOINT = getGetConsoleCatalogUrl()

export async function loadCatalog(
  fallback: unknown = import.meta.env.DEV ? defaultEntries : undefined,
): Promise<CatalogIndex> {
  try {
    const result = await getConsoleCatalog()
    return createCatalogIndex(reportEntries(parseCatalogEntries(requireApiData(result, '目录响应缺少 data'))))
  } catch (cause) {
    if (fallback !== undefined) {
      return createCatalogIndex(reportEntries(parseCatalogEntries(fallback)))
    }
    const detail = cause instanceof Error ? cause.message : '未知错误'
    throw new Error(`读取界面目录失败：${detail}`, { cause })
  }
}

function reportEntries(entries: CatalogEntry[]): CatalogEntry[] {
  const routeKeys = new Set(entries
    .filter((entry) => entry.kind === 'ROUTE' && entry.parentKey === REPORT_SCENE_KEY)
    .map((entry) => entry.routeKey))
  return entries.filter((entry) =>
    (entry.kind === 'SCENE' && entry.routeKey === REPORT_SCENE_KEY)
    || (entry.kind === 'ROUTE' && routeKeys.has(entry.routeKey))
    || (entry.kind === 'ELEMENT' && routeKeys.has(entry.routeKey)),
  )
}

export function parseCatalogPayload(payload: unknown): CatalogEntry[] {
  if (isObject(payload) && 'code' in payload) {
    const result = payload as Partial<CommonResult<unknown>>
    if (result.code !== 0) {
      throw new Error(typeof result.msg === 'string' && result.msg ? result.msg : '目录服务返回错误')
    }
    return parseCatalogEntries(result.data)
  }
  return parseCatalogEntries(payload)
}

export function parseCatalogEntries(value: unknown): CatalogEntry[] {
  if (!Array.isArray(value)) {
    throw new Error('目录必须是数组')
  }
  return value.map((entry, index) => parseCatalogEntry(entry, index))
}

export function createCatalogIndex(source: CatalogEntry[]): CatalogIndex {
  const entries = [...source]
    .filter((entry) => entry.enabled)
    .sort(compareEntries)
  const scenes = entries.filter((entry) => entry.kind === 'SCENE')
  const declaredRoutes = entries.filter((entry) => entry.kind === 'ROUTE')
  const routesByScene = new Map<string, CatalogEntry[]>()
  const routesByPath = new Map<string, CatalogEntry>()
  const elementsByKey = new Map<string, CatalogEntry>()
  const sceneKeys = new Set(scenes.map((scene) => scene.routeKey))
  const routeKeys = new Set<string>()

  if (sceneKeys.size !== scenes.length) {
    throw new Error('重复场景 routeKey')
  }

  declaredRoutes.forEach((route) => {
    if (!route.parentKey || !sceneKeys.has(route.parentKey)) {
      throw new Error(`路由 ${route.routeKey} 缺少有效场景 parentKey`)
    }
    if (!route.path) {
      throw new Error(`路由 ${route.routeKey} 缺少 path`)
    }
    if (routeKeys.has(route.routeKey)) {
      throw new Error(`重复 routeKey：${route.routeKey}`)
    }
    if (routesByPath.has(route.path)) {
      throw new Error(`重复路由路径：${route.path}`)
    }
    routeKeys.add(route.routeKey)
    routesByPath.set(route.path, route)
    const grouped = routesByScene.get(route.parentKey) ?? []
    grouped.push(route)
    routesByScene.set(route.parentKey, grouped)
  })

  const routes = scenes.flatMap((scene) => routesByScene.get(scene.routeKey) ?? [])

  entries.filter((entry) => entry.kind === 'ELEMENT').forEach((element) => {
    if (!element.elementKey) {
      throw new Error(`路由 ${element.routeKey} 的操作缺少 elementKey`)
    }
    if (!routeKeys.has(element.routeKey)) {
      throw new Error(`操作 ${element.elementKey} 引用未知路由 ${element.routeKey}`)
    }
    if (elementsByKey.has(element.elementKey)) {
      throw new Error(`重复 elementKey：${element.elementKey}`)
    }
    elementsByKey.set(element.elementKey, element)
  })

  return { entries, scenes, routes, routesByScene, routesByPath, elementsByKey }
}

export function resolveCatalogRoute(catalog: CatalogIndex, pathname: string): CatalogEntry | undefined {
  return catalog.routesByPath.get(pathname)
    ?? catalog.routesByPath.get(`/console${pathname.startsWith('/') ? pathname : `/${pathname}`}`)
}

function parseCatalogEntry(value: unknown, index: number): CatalogEntry {
  if (!isObject(value)) {
    throw new Error(`目录第 ${index + 1} 项不是对象`)
  }
  const kind = requiredString(value.kind, 'kind') as CatalogKind
  if (!CATALOG_KINDS.has(kind)) {
    throw new Error(`目录第 ${index + 1} 项 kind 无效：${kind}`)
  }
  if (!Array.isArray(value.permissions) || !value.permissions.every((item) => typeof item === 'string')) {
    throw new Error(`目录第 ${index + 1} 项 permissions 无效`)
  }
  if (typeof value.order !== 'number' || !Number.isFinite(value.order)) {
    throw new Error(`目录第 ${index + 1} 项 order 无效`)
  }
  if (typeof value.enabled !== 'boolean') {
    throw new Error(`目录第 ${index + 1} 项 enabled 无效`)
  }
  return {
    routeKey: requiredString(value.routeKey, 'routeKey'),
    elementKey: optionalString(value.elementKey),
    path: optionalString(value.path),
    parentKey: optionalString(value.parentKey),
    kind,
    name: requiredString(value.name, 'name'),
    description: optionalString(value.description),
    icon: optionalString(value.icon),
    order: value.order,
    permissions: [...value.permissions],
    enabled: value.enabled,
  }
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`目录字段 ${field} 必须是非空字符串`)
  }
  return value.trim()
}

function optionalString(value: unknown): string | null | undefined {
  if (value === null || value === undefined) return value
  if (typeof value !== 'string') throw new Error('目录可选字段必须是字符串')
  return value.trim() || null
}

function compareEntries(left: CatalogEntry, right: CatalogEntry): number {
  return left.order - right.order
    || left.name.localeCompare(right.name, 'zh-CN')
    || (left.elementKey ?? left.routeKey).localeCompare(right.elementKey ?? right.routeKey)
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

const REPORT_SCENE_KEY = 'reports'
