import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import ConstantWorkspace from './ConstantWorkspace.vue'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ConstantWorkspace', () => {
  it('loads constants from the selected feature and shows their comments', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(result([{
      id: '1',
      featureId: 9,
      groupCode: 'messageStatus',
      featurePackageName: 'example.message',
      contributorId: ':example:message',
      objectName: 'MessageConstants',
      description: '消息常量。',
      constants: [{ id: '2', name: 'ENABLED_STATUS', type: 'INT', value: '1', description: '已启用状态。' }],
    }])))
    const feature = {
      featureId: 9,
      featureCode: 'message',
      name: '消息通知',
      packageName: 'example.message',
      contributorId: ':example:message',
      modelCodes: [],
      dtoCodes: [],
      contractCodes: [],
    }
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(ConstantWorkspace, { feature }),
    }))
    const wrapper = mount(host)

    await flushPromises()

    expect(wrapper.get('[aria-label="消息通知常量"]').text()).toContain('MessageConstants')
    expect(wrapper.get('[aria-label="消息通知常量"]').text()).not.toContain('messageStatus')
    expect(wrapper.get<HTMLInputElement>('[aria-label="常量名"]').element.value).toBe('ENABLED_STATUS')
    expect(wrapper.get<HTMLInputElement>('[aria-label="常量注释"]').element.value).toBe('已启用状态。')
  })

  it('localizes network failures', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch')))
    const feature = {
      featureId: 9,
      featureCode: 'message',
      name: '消息通知',
      packageName: 'example.message',
      contributorId: ':example:message',
      modelCodes: [],
      dtoCodes: [],
      contractCodes: [],
    }
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(ConstantWorkspace, { feature }),
    }))
    const wrapper = mount(host)

    await flushPromises()

    expect(wrapper.get('.constant-message').text()).toContain('常量服务连接失败')
    expect(wrapper.get('.constant-message').text()).not.toContain('Failed to fetch')
  })

  it('completes a constant from a short comment and value', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input) => {
      if (String(input) === '/studio/api/lowcode/constant/list') {
        return result([])
      }
      if (String(input) === '/studio/api/agent/structured-output') {
        return result({
          name: 'ENABLED',
          type: 'INT',
          value: '999',
          description: '开启状态。',
        })
      }
      throw new Error(`Unexpected request: ${String(input)}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    const feature = {
      featureId: 9,
      featureCode: 'message',
      name: '消息通知',
      packageName: 'example.message',
      contributorId: ':example:message',
      modelCodes: [],
      dtoCodes: [],
      contractCodes: [],
    }
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(ConstantWorkspace, { feature }),
    }))
    const wrapper = mount(host)
    await flushPromises()

    await wrapper.get('[aria-label="常量值"]').setValue('1')
    await wrapper.get('[aria-label="常量注释"]').setValue('开')
    await wrapper.get('[aria-label="AI 补全常量"]').trigger('click')
    await flushPromises()

    expect(wrapper.get<HTMLInputElement>('[aria-label="常量名"]').element.value).toBe('ENABLED')
    expect(wrapper.get<HTMLSelectElement>('[aria-label="常量类型"]').element.value).toBe('INT')
    expect(wrapper.get<HTMLInputElement>('[aria-label="常量值"]').element.value).toBe('1')
    expect(wrapper.get<HTMLInputElement>('[aria-label="常量注释"]').element.value).toBe('开启状态。')
    expect(wrapper.get('.constant-message').text()).toContain('常量项已由 AI 补全')

    const request = fetchMock.mock.calls.find(([input]) => String(input) === '/studio/api/agent/structured-output')
    const payload = JSON.parse(String(request?.[1]?.body))
    expect(payload.agentCode).toBe('constantItemCompletion')
    expect(payload.input.constant).toMatchObject({ type: 'STRING', value: '1', description: '开' })
    expect(payload.input.feature).toMatchObject({ code: 'message', name: '消息通知' })
  })
})

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 0, msg: '', data }), { status: 200 })
}
