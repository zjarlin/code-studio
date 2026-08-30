import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ContractStudio from './contracts/ContractStudio.vue'
import DtoStudio from './dto-studio/DtoStudio.vue'

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    contracts = vi.fn().mockResolvedValue([])
    dtos = vi.fn().mockResolvedValue([])
    models = vi.fn().mockResolvedValue([])
  },
}))

function fieldInput(wrapper: ReturnType<typeof shallowMount>, labelText: string) {
  return wrapper.findAll('label')
    .find((label) => label.text().includes(labelText))
    ?.get('input')
}

describe('resource identity fields', () => {
  it('hides the DTO code and derives it from the Kotlin class name', async () => {
    const wrapper = shallowMount(DtoStudio)
    await flushPromises()

    expect(wrapper.text()).not.toContain('唯一标识')

    await fieldInput(wrapper, '类名')?.setValue('MaintenanceStatisticsOutput')

    expect(wrapper.get('.workspace-title span').text()).toBe('MaintenanceStatisticsOutput')
  })

  it('hides the Service code and derives it from the Kotlin interface', async () => {
    const wrapper = shallowMount(ContractStudio)
    await flushPromises()

    expect(wrapper.text()).not.toContain('唯一标识')

    await fieldInput(wrapper, '领域 Service 接口')?.setValue('OrderSummaryService')

    expect(wrapper.get('.workspace-title span').text()).toBe('OrderSummaryService')
    expect(fieldInput(wrapper, '基础路径')?.element.value).toBe('/order-summary')
  })
})
