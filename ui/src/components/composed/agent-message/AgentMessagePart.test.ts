import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, shallowRef } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { AgentUiMessage } from '@/types'

import AgentMessagePart from './AgentMessagePart.vue'
import { snapshotAgentMessagePart } from './agent-message-renderers'

describe('AgentMessagePart', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('renders in-place part updates after a shallow message replacement', async () => {
    const message = shallowRef<AgentUiMessage>({
      id: 'assistant-message',
      role: 'assistant',
      parts: [
        { type: 'step-start' },
        {
          type: 'data-observation',
          id: 'observation-model',
          data: { phase: 'model', state: 'running', label: '正在请求模型' },
        },
        { type: 'text', text: '', state: 'streaming' },
      ],
    })
    const harness = defineComponent(() => () => h(
      'div',
      message.value.parts.map((part, index) => h(AgentMessagePart, {
        key: `${part.type}-${index}`,
        part: snapshotAgentMessagePart(part),
      })),
    ))
    const wrapper = mount(harness)

    expect(wrapper.get('[data-state="running"]').text()).toBe('正在请求模型')
    expect(wrapper.find('.agent-message-text').exists()).toBe(false)

    const observation = message.value.parts[1]
    const text = message.value.parts[2]
    if (observation?.type === 'data-observation') {
      observation.data = { phase: 'model', state: 'completed', label: '模型响应完成' }
    }
    if (text?.type === 'text') {
      text.text = '回复已生成'
      text.state = 'done'
    }
    message.value = { ...message.value }
    await nextTick()

    expect(wrapper.get('[data-state="completed"]').text()).toBe('模型响应完成')
    expect(wrapper.get('.agent-message-text').text()).toBe('回复已生成')
    expect(wrapper.text()).not.toContain('step-start')
  })

  it('blocks unresolved configuration and applies a confirmed change set', async () => {
    const pending = mount(AgentMessagePart, {
      props: {
        part: {
          type: 'data-configuration',
          data: {
            summary: '需要补充信息',
            models: [],
            contracts: [],
            agents: [],
            questions: ['请补充Contributor ID'],
          },
        },
      },
    })

    expect(pending.get('button').attributes('disabled')).toBeDefined()

    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
        code: 0,
        msg: '',
        data: {
          modelIds: { exampleTag: '1' },
          dtoIds: {},
          contractIds: {},
          agentIds: {},
        },
      })))
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const applicable = mount(AgentMessagePart, {
      props: {
        part: {
          type: 'data-configuration',
          data: {
            summary: '新增通用标签',
            models: [{ name: '通用标签', modelCode: 'exampleTag', fields: [] }],
            contracts: [],
            agents: [],
            questions: [],
          },
        },
      },
    })

    await applicable.get('button').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/studio/api/lowcode/agent/configuration/apply',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(applicable.text()).toContain('配置已应用：1 模型、0 DTO、0 契约、0 Agent')
  })

  it('applies workspace display text through the dedicated patch endpoint', async () => {
    const result = {
      summary: '中文化展示文本',
      tableId: 'metadata.display-text:workspace',
      revision: 'revision-1',
      patches: [{
        rowKey: 'model/1/name',
        columnKey: 'value',
        expectedValue: 'Device',
        edits: [{ path: null, match: 'Device', replacement: '设备' }],
      }],
      questions: [],
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: '', data: 1 })))
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const ordinary = mount(AgentMessagePart, {
      props: {
        part: {
          type: 'data-metadata-patch',
          data: { ...result, tableId: 'metadata.display-text:model:1' },
        },
      },
    })
    const prefixed = mount(AgentMessagePart, {
      props: {
        part: {
          type: 'data-metadata-patch',
          data: { ...result, tableId: 'metadata.display-text:workspace:spoofed' },
        },
      },
    })
    const wrapper = mount(AgentMessagePart, {
      props: { part: { type: 'data-metadata-patch', data: result } },
    })

    expect(ordinary.find('button').exists()).toBe(false)
    expect(prefixed.find('button').exists()).toBe(false)
    expect(wrapper.get('button').text()).toContain('应用中文化')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/studio/api/lowcode/agent/display-text/apply',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(result) }),
    )
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/configuration/apply'))).toBe(false)
    expect(wrapper.text()).toContain('已应用 1 项展示文本')
  })

  it('blocks workspace display text patches with questions or a stale apply error', async () => {
    const base = {
      summary: '待中文化',
      tableId: 'metadata.display-text:workspace',
      revision: 'revision-1',
      patches: [{
        rowKey: 'model/1/name',
        columnKey: 'value',
        expectedValue: 'Device',
        edits: [{ path: null, match: 'Device', replacement: '设备' }],
      }],
    }
    const pending = mount(AgentMessagePart, {
      props: {
        part: {
          type: 'data-metadata-patch',
          data: { ...base, questions: ['无法确认业务含义'] },
        },
      },
    })

    expect(pending.get('button').attributes('disabled')).toBeDefined()
    expect(pending.text()).toContain('无法确认业务含义')

    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 409,
      msg: '元数据已变化，请重新检查',
      data: null,
    }), { status: 409 }))
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const stale = mount(AgentMessagePart, {
      props: {
        part: { type: 'data-metadata-patch', data: { ...base, questions: [] } },
      },
    })

    await stale.get('button').trigger('click')
    await flushPromises()

    expect(stale.text()).toContain('元数据已变化，请重新检查')
    expect(stale.get('button').attributes('disabled')).toBeDefined()
  })
})
