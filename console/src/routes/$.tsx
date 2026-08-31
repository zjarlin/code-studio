import { createFileRoute, useLocation } from '@tanstack/react-router'
import { Suspense } from 'react'

import { resolveCatalogRoute } from '@/catalog/catalog'
import { useCatalog } from '@/catalog/context'
import { resolvePage } from '@/features/page-registry'

export const Route = createFileRoute('/$')({
  validateSearch: parseConsoleSearch,
  component: CatalogPage,
})

export interface ConsoleSearch {
  reportId?: string
  mode?: 'preview' | 'view'
}

function CatalogPage() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const route = resolveCatalogRoute(useCatalog(), pathname)
  if (!route) return <div className="content-status">界面目录未声明当前路径</div>

  const Page = resolvePage(route.routeKey)
  if (!Page) return <div className="content-status">界面 {route.routeKey} 没有可用视图</div>
  return (
    <Suspense fallback={<div className="content-status" aria-busy="true">正在加载界面…</div>}>
      <Page route={route} />
    </Suspense>
  )
}

function parseConsoleSearch(search: Record<string, unknown>): ConsoleSearch {
  const reportId = typeof search.reportId === 'string' && search.reportId.trim()
    ? search.reportId.trim()
    : undefined
  const mode = search.mode === 'preview' || search.mode === 'view' ? search.mode : undefined
  return { reportId, mode }
}
