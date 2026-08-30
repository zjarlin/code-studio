import { describe, expect, it } from 'vitest'

import {
  databaseIdentifierToPascalCase,
  toPinyinSnakeIdentifier,
  toResourceCodeFromClassName,
} from './identifier'

describe('toPinyinSnakeIdentifier', () => {
  it('transliterates Chinese names with snake case separators', () => {
    expect(toPinyinSnakeIdentifier('物联网')).toBe('wu_lian_wang')
    expect(toPinyinSnakeIdentifier('设备管理')).toBe('she_bei_guan_li')
  })

  it('normalizes existing Latin names without changing their meaning', () => {
    expect(toPinyinSnakeIdentifier('Device Management')).toBe('device_management')
    expect(toPinyinSnakeIdentifier('deviceProfile')).toBe('device_profile')
  })
})

describe('toResourceCodeFromClassName', () => {
  it('derives hidden resource codes from Kotlin class names', () => {
    expect(toResourceCodeFromClassName('AccountRecord')).toBe('accountRecord')
    expect(toResourceCodeFromClassName('MaintenanceStatisticsView', 'View')).toBe('maintenanceStatistics')
    expect(toResourceCodeFromClassName('OrderSummaryService', 'Service')).toBe('orderSummary')
  })
})

describe('databaseIdentifierToPascalCase', () => {
  it('derives Kotlin classifier names strictly from database identifiers', () => {
    expect(databaseIdentifierToPascalCase('work_order_item')).toBe('WorkOrderItem')
    expect(databaseIdentifierToPascalCase('ORDER-ITEM.PROFILE')).toBe('OrderItemProfile')
    expect(databaseIdentifierToPascalCase('orderItemProfile')).toBe('OrderItemProfile')
    expect(databaseIdentifierToPascalCase('  audit_log_2  ')).toBe('AuditLog2')
  })
})
