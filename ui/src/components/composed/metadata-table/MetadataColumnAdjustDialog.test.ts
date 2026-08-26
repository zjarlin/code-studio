import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import MetadataColumnAdjustDialog from './MetadataColumnAdjustDialog.vue'
import { createTableRevision } from './metadata-table'
import type { MetadataTableDescriptor } from './metadata-table'

const api = vi.hoisted(() => ({ generateStructuredOutput: vi.fn() }))

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    generateStructuredOutput = api.generateStructuredOutput
  },
}))

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('MetadataColumnAdjustDialog', () => {
  it('creates local fragment patches for the common remove-full-stops adjustment', async () => {
    const rows: Record<string, unknown>[] = [
      { code: 'device-auth', description: '设备认证方式。' },
      { code: 'product-name', description: '产品名称。用于设备展示。' },
      { code: 'status', description: '产品状态' },
    ]
    const base: Omit<MetadataTableDescriptor<Record<string, unknown>>, 'revision'> = {
      tableId: 'metadata.fields',
      rowIdentityKey: 'code',
      rowKey: (row) => String(row.code),
      columns: [
        { key: 'code', label: '编码', kind: 'scalar', context: true },
        { key: 'description', label: '备注', kind: 'scalar', editable: true, context: true },
      ],
      operations: ['translate', 'replace', 'fill', 'custom'],
    }
    const descriptor = { ...base, revision: createTableRevision(base, rows) }
    const wrapper = mount(MetadataColumnAdjustDialog, {
      attachTo: document.body,
      props: { columnKey: 'description', descriptor, rows },
      global: { stubs: { StructuredOutputSettings: true } },
    })

    await wrapper.get('[aria-label="AI 调整备注列"]').trigger('click')
    const presetButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('去掉中文句号'))
    await new DOMWrapper(presetButton as HTMLButtonElement).trigger('click')

    expect(api.generateStructuredOutput).not.toHaveBeenCalled()
    const applyButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('应用到草稿'))
    await new DOMWrapper(applyButton as HTMLButtonElement).trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({
      rows: [
        { code: 'device-auth', description: '设备认证方式' },
        { code: 'product-name', description: '产品名称用于设备展示' },
        { code: 'status', description: '产品状态' },
      ],
      applied: [
        {
          rowKey: 'device-auth',
          edits: [{ path: null, match: '。', replacement: '' }],
        },
        {
          rowKey: 'product-name',
          edits: [
            { path: null, match: '。', replacement: '' },
            { path: null, match: '。', replacement: '' },
          ],
        },
      ],
      conflicts: [],
    })
  })

  it('uses the configured structured output service and emits checked patches', async () => {
    const rows: Record<string, unknown>[] = [{ code: 'enabled', label: '' }]
    const base: Omit<MetadataTableDescriptor<Record<string, unknown>>, 'revision'> = {
      tableId: 'metadata.example',
      rowIdentityKey: 'code',
      rowKey: (row) => String(row.code),
      columns: [
        { key: 'code', label: '编码', kind: 'scalar', context: true },
        {
          key: 'label',
          label: '名称',
          kind: 'enum',
          editable: true,
          context: true,
          options: [{ label: '启用', value: '启用' }],
        },
      ],
      operations: ['fill', 'custom'],
    }
    const descriptor = { ...base, revision: createTableRevision(base, rows) }
    api.generateStructuredOutput.mockResolvedValue({
      tableId: descriptor.tableId,
      revision: descriptor.revision,
      summary: '补全名称',
      patches: [{ rowKey: 'enabled', columnKey: 'label', expectedValue: '', edits: [{ match: '', replacement: '启用' }] }],
      questions: [],
    })
    const wrapper = mount(MetadataColumnAdjustDialog, {
      attachTo: document.body,
      props: { columnKey: 'label', descriptor, rows },
      global: { stubs: { StructuredOutputSettings: true } },
    })

    await wrapper.get('[aria-label="AI 调整名称列"]').trigger('click')
    expect(document.body.textContent).not.toContain('翻译')
    expect(document.body.textContent).not.toContain('替换')
    const customButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('自定义'))
    await new DOMWrapper(customButton as HTMLButtonElement).trigger('click')
    const instructionInput = document.body.querySelector('.metadata-adjust-fields input')
    await new DOMWrapper(instructionInput as HTMLInputElement).setValue('去掉整列句号')
    const generateButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('生成 Patch'))
    await new DOMWrapper(generateButton as HTMLButtonElement).trigger('click')
    await flushPromises()

    expect(api.generateStructuredOutput).toHaveBeenCalledWith(
      'metadataColumnCompletion',
      expect.objectContaining({
        operation: {
          type: 'custom',
          instruction: '去掉整列句号',
        },
        table: expect.objectContaining({
          tableId: 'metadata.example',
          targetColumnKey: 'label',
          columns: expect.arrayContaining([
            expect.objectContaining({
              key: 'label',
              agentEditable: true,
              context: false,
              options: [{ label: '启用', value: '启用' }],
            }),
          ]),
          rows: [{ rowKey: 'enabled', values: { code: 'enabled', label: '' } }],
        }),
      }),
    )
    const applyButton = Array.from(document.body.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('应用到草稿'))
    await new DOMWrapper(applyButton as HTMLButtonElement).trigger('click')

    expect(wrapper.emitted('apply')?.[0]?.[0]).toMatchObject({
      rows: [{ code: 'enabled', label: '启用' }],
      conflicts: [],
    })
  })
})
