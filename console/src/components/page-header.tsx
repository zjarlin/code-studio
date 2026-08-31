import type { ReactNode } from 'react'

import type { CatalogEntry } from '@/catalog/types'

export function PageHeader({ actions, route }: Readonly<{ actions?: ReactNode; route: CatalogEntry }>) {
  return (
    <header className="page-header">
      <div className="page-heading">
        <h1>{route.name}</h1>
        {route.description && <p>{route.description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}
