import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AccessContextQuerySync } from '@/lib/access-context-sync'

import type { ReportDocument } from './models'
import { ReportRenderer } from './renderer'

afterEach(() => {
  cleanup()
  delete window.adminHostBridge
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('ReportRenderer authenticated images', () => {
  it('drops and reloads a cached image when the host access context changes', async () => {
    let accessToken = 'first'
    window.adminHostBridge = { getAccessContext: () => ({ accessToken }) }
    const fetcher = vi.fn<typeof fetch>().mockImplementation(async () =>
      new Response(new Blob(['image']), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetcher)
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL')
      .mockReturnValueOnce('blob:first')
      .mockReturnValueOnce('blob:second')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)

    const queryClient = new QueryClient()
    const view = render(
      <QueryClientProvider client={queryClient}>
        <AccessContextQuerySync>
          <ReportRenderer document={imageDocument} results={{ images: [{ path: '/files/logo' }] }} />
        </AccessContextQuerySync>
      </QueryClientProvider>,
    )
    await waitFor(() => expect(view.getByAltText('Logo')).toHaveAttribute('src', 'blob:first'))

    accessToken = 'second'
    window.dispatchEvent(new Event('focus'))

    await waitFor(() => expect(view.getByAltText('Logo')).toHaveAttribute('src', 'blob:second'))
    expect(createObjectUrl).toHaveBeenCalledTimes(2)
    const secondHeaders = new Headers(fetcher.mock.calls[1]?.[1]?.headers)
    expect(secondHeaders.get('Authorization')).toBe('Bearer second')
  })

  it('recovers when an image source changes after a failed request', async () => {
    const fetcher = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(null, { status: 404 }))
      .mockResolvedValueOnce(new Response(new Blob(['image']), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:recovered')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const queryClient = new QueryClient()
    const renderTree = (source: string) => (
      <QueryClientProvider client={queryClient}>
        <AccessContextQuerySync>
          <ReportRenderer document={imageDocument} results={{ images: [{ path: source }] }} />
        </AccessContextQuerySync>
      </QueryClientProvider>
    )
    const view = render(renderTree('/files/missing'))
    await waitFor(() => expect(view.getByRole('alert')).toHaveTextContent('HTTP 404'))

    view.rerender(renderTree('/files/logo'))

    await waitFor(() => expect(view.getByAltText('Logo')).toHaveAttribute('src', 'blob:recovered'))
  })
})

const imageDocument: ReportDocument = {
  version: 1,
  name: '图片报表',
  description: null,
  page: { orientation: 'PORTRAIT', marginMm: 12 },
  parameters: [],
  datasets: [{
    key: 'images',
    name: '图片',
    source: 'OPENAPI',
    modelCode: null,
    operationId: 'getImages',
    parameterBindings: {},
    fields: [{ key: 'path', label: '路径', pointer: '/path' }],
  }],
  rows: [{
    key: 'images',
    blocks: [{
      key: 'logo',
      kind: 'IMAGE',
      datasetKey: 'images',
      sourcePointer: '/path',
      alt: 'Logo',
      columnSpan: 12,
    }],
  }],
}
