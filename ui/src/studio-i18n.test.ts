import { describe, expect, it } from 'vitest'

import { workspaceKeys, zhCN } from './studio-i18n'

describe('studio i18n', () => {
  it('uses the code studio brand', () => {
    expect(zhCN.brand).toBe('code studio')
  })

  it('provides Chinese copy for every workspace', () => {
    expect(Object.keys(zhCN.workspaces).sort()).toEqual([...workspaceKeys].sort())
    expect(Object.values(zhCN.workspaces).every((message) => message.label && message.description)).toBe(true)
  })

  it('provides Chinese copy for the application constants workspace', () => {
    expect(zhCN.constants.tabLabel).toBe('常量')
    expect(Object.values(zhCN.constants).every(Boolean)).toBe(true)
  })
})
