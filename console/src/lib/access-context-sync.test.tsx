import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AccessContextQuerySync } from './access-context-sync'

afterEach(() => {
  cleanup()
  delete window.adminHostBridge
})

describe('AccessContextQuerySync', () => {
  it('cancels and invalidates cached queries after the host login context changes', async () => {
    let accessToken = 'first'
    window.adminHostBridge = { getAccessContext: vi.fn(() => ({ accessToken })) }
    const queryClient = new QueryClient()
    const cancel = vi.spyOn(queryClient, 'cancelQueries')
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    render(<QueryClientProvider client={queryClient}><AccessContextQuerySync /></QueryClientProvider>)
    await waitFor(() => expect(window.adminHostBridge?.getAccessContext).toHaveBeenCalledOnce())

    accessToken = 'second'
    window.dispatchEvent(new Event('focus'))

    await waitFor(() => expect(cancel).toHaveBeenCalledOnce())
    expect(invalidate).toHaveBeenCalledOnce()
  })
})
