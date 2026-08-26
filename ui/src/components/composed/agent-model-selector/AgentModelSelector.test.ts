import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'
import type { AgentProviderModel } from '@/types'

import AgentModelSelector from './AgentModelSelector.vue'

function mountSelector(options: {
  modelValue: string
  models: AgentProviderModel[]
  loading?: boolean
}, onUpdate?: (value: string) => void) {
  const host = defineComponent(() => () => h(TooltipProvider, null, {
    default: () => h(AgentModelSelector, {
      ...options,
      'onUpdate:modelValue': onUpdate,
    }),
  }))
  return mount(host)
}

describe('AgentModelSelector', () => {
  it('使用组件库 Select 展示当前模型', () => {
    const wrapper = mountSelector({
      modelValue: 'gpt-example',
      models: [{
        id: 'gpt-example',
        contextWindow: 128_000,
        contextWindowEstimated: false,
      }],
    })

    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.get('[data-slot="select-trigger"]').attributes('aria-label')).toBe('选择对话模型')
    expect(wrapper.get('[data-slot="select-value"]').text()).toBe('gpt-example')
  })

  it('加载模型时禁用选择器并展示加载状态', () => {
    const wrapper = mountSelector({
      loading: true,
      modelValue: '',
      models: [],
    })

    const trigger = wrapper.get('[data-slot="select-trigger"]')
    expect(trigger.attributes('disabled')).toBeDefined()
    expect(trigger.text()).toContain('正在读取模型')
  })

  it('已保存模型不可用时选择首个上游模型', async () => {
    const updates: string[] = []
    mountSelector({
      modelValue: 'retired-model',
      models: [{
        id: 'current-model',
        contextWindow: 128_000,
        contextWindowEstimated: false,
      }],
    }, value => updates.push(value))

    await nextTick()

    expect(updates).toEqual(['current-model'])
  })
})
