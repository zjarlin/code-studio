import type { ReactNode } from 'react'

import { Alert, AlertDescription, AlertTitle } from '@/components/generated/shadcn/alert'
import { Skeleton } from '@/components/generated/shadcn/skeleton'

export function QueryState({ children, error, pending }: Readonly<{
  children: ReactNode
  error?: Error | null
  pending: boolean
}>) {
  if (pending) {
    return (
      <div aria-busy="true" className="flex flex-col gap-2 p-4">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-8 w-3/4" />
      </div>
    )
  }
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>读取失败</AlertTitle>
        <AlertDescription>{error.message}</AlertDescription>
      </Alert>
    )
  }
  return children
}
