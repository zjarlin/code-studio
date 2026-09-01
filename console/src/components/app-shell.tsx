import { Link, Outlet, useLocation } from '@tanstack/react-router'

import { resolveCatalogRoute } from '@/catalog/catalog'
import { useCatalog } from '@/catalog/context'
import type { CatalogEntry } from '@/catalog/types'

import { CatalogIcon } from './composed/catalog-icon/catalog-icon'

export function AppShell() {
  const catalog = useCatalog()
  const pathname = useLocation({ select: (location) => location.pathname })
  const activeRoute = resolveCatalogRoute(catalog, pathname)
  const activeSceneKey = activeRoute?.parentKey ?? catalog.scenes[0]?.routeKey
  const sideRoutes = activeSceneKey ? catalog.routesByScene.get(activeSceneKey) ?? [] : []

  return (
    <div className="admin-shell">
      <header className="topbar">
        <a className="brand" href="/console/" aria-label="Code Studio Console">
          <span className="brand-mark">C</span>
          <strong>Code Studio</strong>
        </a>
        <nav className="scene-nav" aria-label="场景">
          {catalog.scenes.map((scene) => (
            <CatalogLink
              active={scene.routeKey === activeSceneKey}
              className="scene-link"
              entry={scene}
              key={scene.routeKey}
            />
          ))}
        </nav>
      </header>
      <aside className="sidebar">
        <nav aria-label="当前场景导航">
          {sideRoutes.map((route) => (
            <CatalogLink
              active={route.routeKey === activeRoute?.routeKey}
              className="side-link"
              entry={route}
              key={route.routeKey}
            />
          ))}
        </nav>
      </aside>
      <main className="page"><Outlet /></main>
    </div>
  )
}

function CatalogLink({ active, className, entry }: Readonly<{
  active: boolean
  className: string
  entry: CatalogEntry
}>) {
  return (
    <Link
      aria-current={active ? 'page' : undefined}
      className={className}
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
  return (path ?? '').replace(/^\/console\/?/, '').replace(/^\/+/, '')
}
