import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import StructuredOutputAction from './StructuredOutputAction.vue'

const api = vi.hoisted(() => ({
  generateStructuredOutput: vi.fn(),
}))

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    generateStructuredOutput = api.generateStructuredOutput
  },
}))

describe('StructuredOutputAction', () => {
  it('emits schema validated output for the current page input', async () => {
    api.generateStructuredOutput.mockResolvedValue({
      name: 'ENABLED',
      type: 'INT',
      value: '1',
      description: '开启状态。',
    })
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(StructuredOutputAction, {
        agentCode: 'constantItemCompletion',
        input: { description: '开', value: '1' },
      }),
    }))
    const wrapper = mount(host)

    await wrapper.get('[aria-label="AI 补全"]').trigger('click')
    await flushPromises()

    expect(api.generateStructuredOutput).toHaveBeenCalledWith(
      'constantItemCompletion',
      { description: '开', value: '1' },
    )
    expect(wrapper.findComponent(StructuredOutputAction).emitted('generated')?.[0]?.[0]).toEqual({
      name: 'ENABLED',
      type: 'INT',
      value: '1',
      description: '开启状态。',
    })
  })
})
