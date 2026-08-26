import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import LibraryQueryTable from './LibraryQueryTable.vue'

const model = {
  id: 7,
  featureId: 17,
  modelCode: 'inspectionTask',
  name: '巡检任务',
  packageName: 'example.inspection.task',
  className: 'InspectionTask',
  tableName: 'inspection_task',
  modelType: 'ENTITY',
  status: 1,
  version: 1,
  contributorId: 'example.catalog',
  fields: [{ orderNo: 1, fieldCode: 'status', label: '状态', kotlinType: 'String', dbColumn: 'status', required: false }],
  relations: [],
  queries: [{ id: 71, orderNo: 1, queryCode: 'byStatus', label: '按状态', logic: 'AND', items: [{ id: 711, orderNo: 1, fieldCode: 'status', operator: 'EQ', valueType: 'SINGLE' }] }],
}

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    models = vi.fn().mockResolvedValue([model])
    detail = vi.fn().mockResolvedValue(model)
  },
}))

afterEach(() => {
  document.body.innerHTML = ''
})

describe('LibraryQueryTable', () => {
  it('opens nested query conditions as a table dialog', async () => {
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(LibraryQueryTable, {
        features: [{ id: 17, libraryId: 3, parentId: null, featureCode: 'inspection.task', name: '巡检任务' }],
        selectedFeatureId: 17,
      }),
    }))
    const wrapper = mount(host, { attachTo: document.body })
    await flushPromises()

    const conditionButton = wrapper.findAll('button').find((button) => button.text().includes('1 个条件'))
    expect(conditionButton).toBeDefined()
    await conditionButton!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.library-table-workspace').attributes('data-active-query')).toBe('query:71')
    expect(document.body.textContent).toContain('条件集合使用表格配置')
    expect(document.body.textContent).toContain('状态 · status')
    for (const label of ['字段', '操作符', '参数名']) {
      expect(document.body.querySelector(`[aria-label="AI 调整${label}列"]`), label).not.toBeNull()
      expect(document.body.querySelector(`[aria-label="AI 调整${label}单元格"]`), label).not.toBeNull()
    }
  })
})
