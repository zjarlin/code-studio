import { queryOptions } from '@tanstack/react-query'

import { loadCatalog } from './catalog'

export const catalogQueryOptions = queryOptions({
  queryKey: ['console', 'catalog'],
  queryFn: () => loadCatalog(),
  staleTime: 60_000,
})
