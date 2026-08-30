import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { normalizeModelDraft } from '@/features/model-studio/model-draft'
import type { AgentUiMessage, MetadataTableContext } from '@/types'

import MetadataAssistantPanel from './MetadataAssistantPanel.vue'

const agent = vi.hoisted(() => ({
  options: undefined as undefined | { contextSnapshot: () => unknown },
  chat: undefined as undefined | {
    messages: { value: AgentUiMessage[] }
    send: ReturnType<typeof vi.fn>
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

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    agentSettings = vi.fn(async () => ({ baseUrl: 'https://example.invalid', apiKeyConfigured: true }))
    agentModels = vi.fn(async () => [{ id: 'model-1', contextWindow: 1000, contextWindowEstimated: false }])
  },
}))

vi.mock('@/lib/error', () => ({ reportError: (_error: unknown, fallback: string) => fallback }))
vi.mock('@/lib/uuid', () => ({ createUuid: () => 'queue-id' }))

const AgentPanelStub = defineComponent({
  setup(_props, { slots }) {
    return () => h('section', [slots.header?.(), slots.empty?.(), slots.footer?.()])
  },
})
const AgentComposerStub = defineComponent({
  emits: ['submit'],
  setup(_props, { emit }) {
    return () => h('button', {
      'data-test': 'send',
      onClick: () => emit('submit', 'steer'),
    }, '发送')
  },
})

beforeEach(() => {
  agent.options = undefined
  agent.chat = undefined
})

describe('MetadataAssistantPanel display text translation', () => {
  it('sends only the allowlisted virtual table and applies a checked patch', async () => {
    const draft = normalizeModelDraft({
      id: 9,
      modelCode: 'device',
      name: 'Device',
      tableName: 'device',
      fields: [{
        id: 11,
        orderNo: 1,
        fieldCode: 'projectId',
        label: 'Project',
        kotlinType: 'Long',
        dbColumn: 'project_id',
        required: false,
      }],
      queries: [],
      relations: [],
    })
    const wrapper = mount(MetadataAssistantPanel, {
      props: {
        scope: 'model',
        draft,
        draftIdentity: '9:0',
      },
      global: {
        stubs: {
          AgentComposer: AgentComposerStub,
          AgentPanel: AgentPanelStub,
        },
      },
    })
    await flushPromises()

    const action = wrapper.findAll('button').find((button) => button.text().includes('中文化未翻译项'))
    expect(action).toBeDefined()
    await action?.trigger('click')
    let snapshot: unknown
    agent.chat?.send.mockImplementationOnce(async () => {
      snapshot = agent.options?.contextSnapshot()
    })
    await wrapper.get('[data-test="send"]').trigger('click')
    await flushPromises()

    expect(snapshot).toMatchObject({
      scene: 'metadata.model.display-text',
      state: {
        scope: 'table',
        operation: {
          type: 'translate',
          targetLanguage: '中文',
          instruction: expect.stringContaining('补全空说明'),
        },
        table: {
          targetColumnKey: 'value',
          columns: [
            expect.objectContaining({ key: 'targetKey', agentEditable: false }),
            expect.objectContaining({ key: 'context', agentEditable: false }),
            expect.objectContaining({ key: 'value', agentEditable: true }),
          ],
        },
      },
    })
    expect(snapshot).not.toHaveProperty('draft')
    const table = (snapshot as { state: { table: MetadataTableContext } }).state.table
    const fieldRow = table.rows.find((row) => row.values.value === 'Project')
    expect(fieldRow?.values).toMatchObject({ value: 'Project' })
    expect(table.columns.filter((column) => column.agentEditable).map((column) => column.key)).toEqual(['value'])

    const result = {
      tableId: table.tableId,
      revision: table.revision,
      patches: [{
        rowKey: fieldRow?.rowKey ?? '',
        columnKey: 'value',
        expectedValue: 'Project',
        edits: [{ path: null, match: 'Project', replacement: '项目' }],
      }],
      questions: [],
    }
    if (agent.chat) {
      agent.chat.messages.value = [{
        id: 'assistant-1',
        role: 'assistant',
        parts: [{ type: 'data-metadata-patch', data: result }],
      }]
    }
    await nextTick()

    const apply = wrapper.findAll('button').find((button) => button.text().includes('应用翻译'))
    expect(apply).toBeDefined()
    await apply?.trigger('click')

    const translated = wrapper.emitted('applyDisplayText')?.[0]?.[0] as typeof draft
    expect(translated).toMatchObject({ id: 9, modelCode: 'device', tableName: 'device' })
    expect(translated.fields).toHaveLength(1)
    expect(translated.fields[0]).toMatchObject({
      id: 11,
      fieldCode: 'projectId',
      label: '项目',
      kotlinType: 'Long',
      dbColumn: 'project_id',
    })
  })
})
