import { createFileRoute, useLocation } from '@tanstack/react-router'
import { Suspense } from 'react'

import { resolveCatalogRoute } from '@/catalog/catalog'
import { useCatalog } from '@/catalog/context'
import { resolvePage } from '@/features/page-registry'

export const Route = createFileRoute('/$')({
  validateSearch: parseReportSearch,
  component: CatalogPage,
})

export interface ReportSearch {
  reportKey?: string
  templateKey?: string
  mode?: 'edit' | 'preview'
}

function CatalogPage() {
  const pathname = useLocation({ select: (location) => location.pathname })
  const route = resolveCatalogRoute(useCatalog(), pathname)
  if (!route) return <div className="content-status">报表目录未声明当前路径</div>
  const Page = resolvePage(route.routeKey)
  if (!Page) return <div className="content-status">界面 {route.routeKey} 没有可用视图</div>
  return (
    <Suspense fallback={<div aria-busy="true" className="content-status">正在加载界面…</div>}>
      <Page route={route} />
    </Suspense>
  )
}

function parseReportSearch(search: Record<string, unknown>): ReportSearch {
  const reportKey = typeof search.reportKey === 'string' && search.reportKey.trim()
    ? search.reportKey.trim()
    : undefined
  const mode = search.mode === 'edit' || search.mode === 'preview' ? search.mode : undefined
  const templateKey = typeof search.templateKey === 'string' && search.templateKey.trim()
    ? search.templateKey.trim()
    : undefined
  return { reportKey, templateKey, mode }
}
