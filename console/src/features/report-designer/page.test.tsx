import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ReportPublicationError } from '@/features/reports/api'
import * as reportApi from '@/features/reports/api'
import { emptyReportDocument, type ReportView } from '@/features/reports/models'

import { DesignerSession } from './page'

const mocks = vi.hoisted(() => ({
  elementsByKey: new Map<string, unknown>(),
  navigate: vi.fn(),
  useBlocker: vi.fn(),
}))

vi.mock('@tanstack/react-router', () => ({
  useBlocker: mocks.useBlocker,
  useNavigate: () => mocks.navigate,
  useSearch: () => ({ reportKey: 'new', mode: 'edit' }),
}))
vi.mock('@/catalog/context', () => ({ useCatalog: () => ({ elementsByKey: mocks.elementsByKey }) }))
vi.mock('@/components/catalog-action', () => ({
  CatalogAction: ({ elementKey, ...props }: { elementKey: string } & ButtonHTMLAttributes<HTMLButtonElement>) =>
    <button data-element-key={elementKey} {...props}>{elementKey}</button>,
}))
vi.mock('@/components/catalog-icon-action', () => ({
  CatalogIconAction: ({ elementKey, ...props }: { elementKey: string } & ButtonHTMLAttributes<HTMLButtonElement>) =>
    <button data-element-key={elementKey} {...props}>{elementKey}</button>,
}))
vi.mock('@/components/query-state', () => ({ QueryState: ({ children }: { children: ReactNode }) => children }))
vi.mock('./preview', () => ({ DesignerPreview: () => null }))
vi.mock('./workbench', () => ({
  DesignerWorkbench: ({ editable, persisted, reportKey, setReportKey }: {
    editable: boolean
    persisted: boolean
    reportKey: string
    setReportKey: (value: string) => void
  }) => (
    <label>
      报表标识
      <input
        aria-label="报表标识"
        data-editable={String(editable)}
        data-persisted={String(persisted)}
        onChange={(event) => setReportKey(event.target.value)}
        value={reportKey}
      />
    </label>
  ),
}))

beforeEach(() => {
  mocks.elementsByKey.clear()
  mocks.navigate.mockReset()
  mocks.useBlocker.mockReset()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('DesignerSession permissions and publication recovery', () => {
  it('keeps create permission active while a new report key is being entered and blocks dirty navigation', () => {
    mocks.elementsByKey.set('studio.report-designer.create', {})
    const view = renderSession(newReport())

    fireEvent.change(view.getByLabelText('报表标识'), { target: { value: 'sales' } })

    expect(view.getByLabelText('报表标识')).toHaveAttribute('data-editable', 'true')
    expect(view.getByText('studio.report-designer.create')).toBeEnabled()
    expect(mocks.useBlocker.mock.calls.at(-1)?.[0].disabled).toBe(false)
  })

  it('adopts a saved revision and keeps the publication error visible for retry', async () => {
    mocks.elementsByKey.set('studio.report-designer.create', {})
    const initial = newReport()
    initial.document.rows = [{ key: 'row', blocks: [{ key: 'title', kind: 'TEXT', text: '销售', columnSpan: 12 }] }]
    const saved: ReportView = { reportKey: 'sales', revision: 1, document: initial.document, publishedRevision: null }
    vi.spyOn(reportApi, 'saveAndPublishReport').mockRejectedValue(
      new ReportPublicationError(saved, new Error('暂不可用'), true),
    )
    const view = renderSession(initial)
    fireEvent.change(view.getByLabelText('报表标识'), { target: { value: 'sales' } })

    fireEvent.click(view.getByText('studio.report-designer.publish'))

    await waitFor(() => expect(view.getByRole('alert')).toHaveTextContent('草稿已保存'))
    expect(view.getByLabelText('报表标识')).toHaveAttribute('data-persisted', 'true')
    expect(view.getByLabelText('报表标识')).toHaveValue('sales')
  })
})

function newReport(): ReportView {
  return { reportKey: '', revision: 0, document: emptyReportDocument(), publishedRevision: null }
}

function renderSession(initial: ReportView) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <DesignerSession initial={initial} mode="edit" reportOptions={[]} routeName="报表设计器" />
    </QueryClientProvider>,
  )
}
