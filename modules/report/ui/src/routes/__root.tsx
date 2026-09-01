import { createRootRouteWithContext, HeadContent, Scripts } from '@tanstack/react-router'
import type { ReactNode } from 'react'

import { CatalogProvider } from '@/catalog/context'
import { AppShell } from '@/components/composed/app-shell/app-shell'
import type { RouterContext } from '@/router'

import '../styles.css'

export const Route = createRootRouteWithContext<RouterContext>()({
  head: () => ({
    meta: [
      { charSet: 'utf-8' },
      { name: 'viewport', content: 'width=device-width, initial-scale=1' },
      { title: 'Report Studio' },
    ],
  }),
  component: RootComponent,
  notFoundComponent: () => <div className="content-status">页面不存在</div>,
})

function RootComponent() {
  return (
    <RootDocument>
      <CatalogProvider><AppShell /></CatalogProvider>
    </RootDocument>
  )
}

function RootDocument({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="zh-CN">
      <head><HeadContent /></head>
      <body>{children}<Scripts /></body>
    </html>
  )
}
