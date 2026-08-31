import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'

import { readAccessContextFingerprint } from './access-context'

const ACCESS_CONTEXT_POLL_MS = 1_000

export function AccessContextQuerySync() {
  const queryClient = useQueryClient()

  useEffect(() => {
    let disposed = false
    let reading = false
    let fingerprint: string | undefined
    const synchronize = async () => {
      if (disposed || reading) return
      reading = true
      try {
        const next = await readAccessContextFingerprint()
        if (disposed) return
        if (fingerprint === undefined) {
          fingerprint = next
          return
        }
        if (fingerprint === next) return
        fingerprint = next
        await queryClient.cancelQueries()
        if (!disposed) await queryClient.invalidateQueries()
      } catch {
        // A transient host bridge failure must not stop later context checks.
      } finally {
        reading = false
      }
    }
    const onFocus = () => void synchronize()
    void synchronize()
    const interval = window.setInterval(onFocus, ACCESS_CONTEXT_POLL_MS)
    window.addEventListener('focus', onFocus)
    return () => {
      disposed = true
      window.clearInterval(interval)
      window.removeEventListener('focus', onFocus)
    }
  }, [queryClient])

  return null
}
