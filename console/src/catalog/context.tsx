import { useQuery } from '@tanstack/react-query'
import { createContext, type ReactNode, useContext } from 'react'

import { catalogQueryOptions } from './query'
import type { CatalogIndex } from './types'

const CatalogContext = createContext<CatalogIndex | null>(null)

export function CatalogProvider({ children }: Readonly<{ children: ReactNode }>) {
  const catalog = useQuery(catalogQueryOptions)

  if (catalog.isPending) {
    return <div className="app-status" aria-busy="true">正在读取界面目录…</div>
  }
  if (catalog.isError) {
    return <div className="app-status app-status-error" role="alert">{catalog.error.message}</div>
  }
  return <CatalogContext.Provider value={catalog.data}>{children}</CatalogContext.Provider>
}

export function useCatalog(): CatalogIndex {
  const catalog = useContext(CatalogContext)
  if (!catalog) throw new Error('useCatalog 必须在 CatalogProvider 内调用')
  return catalog
}
