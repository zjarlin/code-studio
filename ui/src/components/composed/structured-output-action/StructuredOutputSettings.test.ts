import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import StructuredOutputSettings from './StructuredOutputSettings.vue'

const api = vi.hoisted(() => ({
  agents: vi.fn(),
  agentDetail: vi.fn(),
  agentModels: vi.fn(),
  validateAgent: vi.fn(),
  saveAgent: vi.fn(),
}))

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    agents = api.agents
    agentDetail = api.agentDetail
    agentModels = api.agentModels
    validateAgent = api.validateAgent
    saveAgent = api.saveAgent
  },
}))

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('StructuredOutputSettings', () => {
  it('loads and saves the page prompt and schema', async () => {
    api.agents.mockResolvedValue([{
      id: 7,
      agentCode: 'constantItemCompletion',
      name: '常量项补全',
      modelCode: 'gpt-5-mini',
      status: 1,
      version: 1,
    }])
    api.agentDetail.mockResolvedValue(agentDefinition())
    api.agentModels.mockResolvedValue([{
      id: 'gpt-5-mini',
      contextWindow: 400_000,
      contextWindowEstimated: false,
    }])
    api.validateAgent.mockResolvedValue({ valid: true, errors: [], warnings: [] })
    api.saveAgent.mockResolvedValue(true)
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(StructuredOutputSettings, { agentCode: 'constantItemCompletion' }),
    }))
    const wrapper = mount(host, { attachTo: document.body })

    await wrapper.get('[aria-label="设置 AI 补全"]').trigger('click')
    await flushPromises()

    expect(api.agentModels).toHaveBeenCalledOnce()
    const modelSelector = document.body.querySelector('[aria-label="选择对话模型"]')
    expect(modelSelector?.textContent).toContain('gpt-5-mini')
    const refreshButton = document.body.querySelector('[aria-label="刷新上游模型"]')
    await new DOMWrapper(refreshButton as HTMLButtonElement).trigger('click')
    await flushPromises()
    expect(api.agentModels).toHaveBeenCalledTimes(2)
    const textareas = document.body.querySelectorAll('textarea')
    expect(textareas).toHaveLength(2)
    await new DOMWrapper(textareas[0] as HTMLTextAreaElement).setValue('只补全一条常量，保留用户输入值。')
    const saveButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === '保存设置')
    expect(saveButton).toBeDefined()
    await new DOMWrapper(saveButton as HTMLButtonElement).trigger('click')
    await flushPromises()

    expect(api.saveAgent).toHaveBeenCalledWith(expect.objectContaining({
      agentCode: 'constantItemCompletion',
      instructions: '只补全一条常量，保留用户输入值。',
      structuredOutput: expect.objectContaining({
        name: 'constant_item_completion',
        strict: true,
      }),
    }))
  })
})

function agentDefinition() {
  return {
    id: 7,
    agentCode: 'constantItemCompletion',
    name: '常量项补全',
    modelCode: 'gpt-5-mini',
    instructions: '补全一条常量。',
    toolCodes: [],
    temperature: null,
    maxOutputTokens: 512,
    structuredOutput: {
      name: 'constant_item_completion',
      description: '补全一条常量。',
      schema: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          type: { type: 'string' },
          value: { type: 'string' },
          description: { type: 'string' },
        },
        required: ['name', 'type', 'value', 'description'],
        additionalProperties: false,
      },
      strict: true,
    },
    status: 1,
    version: 1,
    description: null,
  }
}
