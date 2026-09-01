import { describe, expect, it } from 'vitest'

import { emptyReportDocument } from './models'
import { validateReportForPublish, validateReportKey } from './validation'

describe('report validation', () => {
  it('validates stable report keys', () => {
    expect(validateReportKey('sales-summary')).toBeUndefined()
    expect(validateReportKey('Sales Summary')).toContain('小写字母')
  })

  it('reports empty rows before publish', () => {
    expect(validateReportForPublish(emptyReportDocument('销售汇总')).some((error) => error.includes('不能为空'))).toBe(true)
  })
})
