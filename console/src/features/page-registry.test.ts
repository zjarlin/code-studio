import { describe, expect, it } from 'vitest'

import { resolvePage } from './page-registry'

describe('catalog page registry', () => {
  it('discovers lazy pages from their colocated route catalog', () => {
    expect(resolvePage('studio.library')).toBeDefined()
    expect(resolvePage('studio.api-docs')).toBeDefined()
    expect(resolvePage('agent.chat')).toBeDefined()
    expect(resolvePage('agent.settings')).toBeDefined()
    expect(resolvePage('reports.library')).toBeDefined()
    expect(resolvePage('studio.report-designer')).toBeDefined()
    expect(resolvePage('studio.spreadsheet-templates')).toBeDefined()
    expect(resolvePage('not.registered')).toBeUndefined()
  })
})
