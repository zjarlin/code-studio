import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AgentUiMessage, MetadataTableContext } from '@/types'

import AgentStudio from './AgentStudio.vue'

const api = vi.hoisted(() => ({
  agentConversations: vi.fn().mockResolvedValue([]),
  agentDisplayTextContext: vi.fn(),
  agentMessages: vi.fn().mockResolvedValue([]),
  agentModels: vi.fn().mockResolvedValue([{ id: 'model-1' }]),
  agentSettings: vi.fn(),
  createAgentConversation: vi.fn().mockResolvedValue(41),
}))
const agent = vi.hoisted(() => ({
  options: undefined as undefined | { contextSnapshot: () => unknown },
  chat: undefined as undefined | {
    messages: { value: AgentUiMessage[] }
    send: ReturnType<typeof vi.fn>
  },
}))

vi.mock('../../lowcode-api', () => ({
  LowcodeApi: class {
    agentConversations = api.agentConversations
    agentDisplayTextContext = api.agentDisplayTextContext
    agentMessages = api.agentMessages
    agentModels = api.agentModels
    agentSettings = api.agentSettings
    createAgentConversation = api.createAgentConversation
  },
}))

vi.mock('@/components/composed/agent-panel/use-agent-responses', async () => {
  const vue = await import('vue')
  return {
    useAgentResponses: (options: { contextSnapshot: () => unknown }) => {
      agent.options = options
      const chat = {
        messages: vue.ref<AgentUiMessage[]>([]),
        status: vue.ref('ready'),
        error: vue.ref<Error>(),
        send: vi.fn(async () => undefined),
        submitFunctionOutput: vi.fn(),
        stop: vi.fn(),
        reset: vi.fn(),
      }
      agent.chat = chat
      return chat
    },
  }
})

const AgentPanelStub = defineComponent({
  props: { messages: { type: Array, default: () => [] } },
  setup(_props, { slots }) {
    return () => h('section', [slots.header?.(), slots.empty?.(), slots.footer?.()])
  },
})
const AgentComposerStub = defineComponent({
  props: {
    notice: { type: String, default: '' },
    prompt: { type: String, default: '' },
  },
  emits: ['submit'],
  setup(props, { emit }) {
    return () => h('div', [
      h('span', { 'data-test': 'prompt' }, props.prompt),
      h('span', { 'data-test': 'notice' }, props.notice),
      h('button', { 'data-test': 'send', onClick: () => emit('submit', 'steer') }, '发送'),
    ])
  },
})

beforeEach(() => {
  vi.clearAllMocks()
  agent.options = undefined
  agent.chat = undefined
  api.agentConversations.mockResolvedValue([])
  api.agentModels.mockResolvedValue([{ id: 'model-1' }])
  api.agentSettings.mockResolvedValue({
    baseUrl: 'https://example.invalid',
    apiKeyConfigured: true,
  })
  api.createAgentConversation.mockResolvedValue(41)
})

describe('AgentStudio workspace display text translation', () => {
  it('loads the dedicated table and sends it through table scope', async () => {
    const context = displayTextContext([{
      rowKey: 'model/1/name',
      values: { targetKey: 'model/1/name', context: '模型 device 的展示名称', value: 'Device' },
    }])
    api.agentDisplayTextContext.mockResolvedValue(context)
    const wrapper = createWrapper()
    await flushPromises()

    const suggestion = wrapper.findAll('button')
      .find((button) => button.text().includes('中文化全部未翻译元数据'))
    expect(suggestion).toBeDefined()
    await suggestion?.trigger('click')
    await flushPromises()

    expect(api.agentDisplayTextContext).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-test="prompt"]').text()).toContain('检查并中文化全部未翻译')
    expect(wrapper.get('[data-test="notice"]').text()).toContain('已加载 1 项')

    let snapshot: unknown
    agent.chat?.send.mockImplementationOnce(async () => {
      snapshot = agent.options?.contextSnapshot()
    })
    await wrapper.get('[data-test="send"]').trigger('click')
    await flushPromises()

    expect(snapshot).toMatchObject({
      scene: 'agent.workspace.display-text',
      state: {
        scope: 'table',
        operation: {
          type: 'translate',
          targetLanguage: '中文',
          instruction: expect.stringContaining('补全空说明'),
        },
        table: context,
      },
    })
    expect(snapshot).not.toHaveProperty('draft')
  })

  it('reports an empty candidate table without entering display text mode', async () => {
    api.agentDisplayTextContext.mockResolvedValue(displayTextContext([]))
    const wrapper = createWrapper()
    await flushPromises()

    const suggestion = wrapper.findAll('button')
      .find((button) => button.text().includes('中文化全部未翻译元数据'))
    await suggestion?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="notice"]').text()).toContain('无需处理')
    expect(wrapper.get('[data-test="prompt"]').text()).toBe('')
    expect(wrapper.find('[role="radio"][aria-checked="true"]').text()).toContain('智能识别')
    expect(agent.chat?.send).not.toHaveBeenCalled()
  })

  it('clears the dedicated prompt before returning to a generic workspace mode', async () => {
    api.agentDisplayTextContext.mockResolvedValue(displayTextContext([{
      rowKey: 'model/1/name',
      values: { targetKey: 'model/1/name', context: '模型 device 的展示名称', value: 'Device' },
    }]))
    const wrapper = createWrapper()
    await flushPromises()

    const suggestion = wrapper.findAll('button')
      .find((button) => button.text().includes('中文化全部未翻译元数据'))
    await suggestion?.trigger('click')
    await flushPromises()
    const configurationMode = wrapper.findAll('[role="radio"]')
      .find((button) => button.text().includes('配置模式'))
    await configurationMode?.trigger('click')

    expect(wrapper.get('[data-test="prompt"]').text()).toBe('')
    expect(agent.options?.contextSnapshot()).toEqual({
      scene: 'agent.workspace',
      state: { scope: 'workspace' },
    })
    expect(agent.chat?.send).not.toHaveBeenCalled()
  })

  it('keeps the existing API key gate after loading display text context', async () => {
    api.agentSettings.mockResolvedValue({ baseUrl: 'https://example.invalid', apiKeyConfigured: false })
    api.agentModels.mockResolvedValue([])
    api.agentDisplayTextContext.mockResolvedValue(displayTextContext([{
      rowKey: 'model/1/name',
      values: { targetKey: 'model/1/name', context: '模型 device 的展示名称', value: 'Device' },
    }]))
    const wrapper = createWrapper()
    await flushPromises()

    const suggestion = wrapper.findAll('button')
      .find((button) => button.text().includes('中文化全部未翻译元数据'))
    await suggestion?.trigger('click')
    await flushPromises()
    await wrapper.get('[data-test="send"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="notice"]').text()).toContain('请先保存 API Key')
    expect(agent.chat?.send).not.toHaveBeenCalled()
  })
})

function createWrapper() {
  return mount(AgentStudio, {
    global: {
      stubs: {
        AgentComposer: AgentComposerStub,
        AgentPanel: AgentPanelStub,
        Dialog: true,
        IconButton: true,
        SearchInput: true,
      },
    },
  })
}

function displayTextContext(rows: MetadataTableContext['rows']): MetadataTableContext {
  return {
    tableId: 'metadata.display-text:workspace:1',
    revision: 'revision-1',
    targetColumnKey: 'value',
    rowIdentityKey: 'targetKey',
    context: { metadataScope: 'workspace' },
    operations: ['translate'],
    columns: [
      { key: 'targetKey', label: '目标', kind: 'scalar', agentEditable: false, context: true },
      { key: 'context', label: '语义上下文', kind: 'scalar', agentEditable: false, context: true },
      { key: 'value', label: '展示文本', kind: 'scalar', agentEditable: true, context: false },
    ],
    rows,
    selection: { rowKeys: rows.map((row) => row.rowKey), filteredRowCount: rows.length },
  }
}
