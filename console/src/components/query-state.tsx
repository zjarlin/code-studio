import type { ReactNode } from 'react'

export function QueryState({ children, error, pending }: Readonly<{
  children: ReactNode
  error?: Error | null
  pending: boolean
}>) {
  if (pending) return <div className="content-status" aria-busy="true">正在读取数据…</div>
  if (error) return <div className="content-status content-status-error" role="alert">{error.message}</div>
  return children
}
