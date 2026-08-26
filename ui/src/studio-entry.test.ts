import { describe, expect, it } from 'vitest'

import { resolveStudioEntry } from './studio-entry'

describe('resolveStudioEntry', () => {
  it('defaults to the current contributor workspace', () => {
    expect(resolveStudioEntry('')).toEqual({
      apiDocumentationOnly: false,
      workspace: 'library',
    })
  })

  it('opens the isolated API documentation workspace', () => {
    expect(resolveStudioEntry('?mode=api-docs')).toEqual({
      apiDocumentationOnly: true,
      workspace: 'api',
    })
  })

  it('ignores unrelated modes', () => {
    expect(resolveStudioEntry('?mode=preview')).toEqual({
      apiDocumentationOnly: false,
      workspace: 'library',
    })
  })
})
