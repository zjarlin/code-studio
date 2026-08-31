import { createFileRoute, Navigate } from '@tanstack/react-router'

import { useCatalog } from '@/catalog/context'
import { toSplat } from '@/components/app-shell'

export const Route = createFileRoute('/')({
  component: CatalogRedirect,
})

function CatalogRedirect() {
  const firstRoute = useCatalog().routes[0]
  if (!firstRoute) return <div className="content-status">当前账号没有可用界面</div>
  return <Navigate params={{ _splat: toSplat(firstRoute.path) }} replace to="/$" />
}
