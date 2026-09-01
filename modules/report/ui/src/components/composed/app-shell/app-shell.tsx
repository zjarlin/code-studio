import { Link, Outlet, useLocation } from '@tanstack/react-router'

import { resolveCatalogRoute } from '@/catalog/catalog'
import { useCatalog } from '@/catalog/context'
import type { CatalogEntry } from '@/catalog/types'

import { CatalogIcon } from '../catalog-icon/catalog-icon'

export function AppShell() {
  const catalog = useCatalog()
  const pathname = useLocation({ select: (location) => location.pathname })
  const activeRoute = resolveCatalogRoute(catalog, pathname)
  const sideRoutes = catalog.routesByScene.get('reports') ?? []

  return (
    <div className="admin-shell">
      <header className="topbar">
        <a aria-label="Report Studio" className="brand" href="/report/">
          <span className="brand-mark">R</span>
          <strong>Report Studio</strong>
        </a>
        <nav aria-label="场景" className="scene-nav">
          <a className="scene-link" href="/console/">
            <CatalogIcon name="arrow-left" />
            <span>管理后台</span>
          </a>
          <span aria-current="page" className="scene-link">
            <CatalogIcon name="chart-no-axes-combined" />
            <span>报表中心</span>
          </span>
        </nav>
      </header>
      <aside className="sidebar">
        <nav aria-label="报表导航">
          {sideRoutes.map((route) => (
            <CatalogLink active={route.routeKey === activeRoute?.routeKey} entry={route} key={route.routeKey} />
          ))}
        </nav>
      </aside>
      <main className="page"><Outlet /></main>
    </div>
  )
}

function CatalogLink({ active, entry }: Readonly<{ active: boolean; entry: CatalogEntry }>) {
  return (
    <Link
      aria-current={active ? 'page' : undefined}
      className="side-link"
      params={{ _splat: toSplat(entry.path) }}
      title={entry.description ?? entry.name}
      to="/$"
    >
      <CatalogIcon name={entry.icon} />
      <span>{entry.name}</span>
    </Link>
  )
}

export function toSplat(path: string | null | undefined): string {
  return (path ?? '').replace(/^\/report\/?/, '').replace(/^\/+/, '')
}
