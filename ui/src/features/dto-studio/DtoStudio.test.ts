import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import MetadataAssistantPanel from '@/components/composed/metadata-assistant/MetadataAssistantPanel.vue'
import { Tabs } from '@/components/generated/shadcn/tabs'

import DtoStudio from './DtoStudio.vue'

const api = vi.hoisted(() => ({
  analyzeDtoReuse: vi.fn(),
  dtoDetail: vi.fn(),
  dtoValidationRules: vi.fn().mockResolvedValue([]),
  dtos: vi.fn(),
  models: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    analyzeDtoReuse = api.analyzeDtoReuse
    dtoDetail = api.dtoDetail
    dtoValidationRules = api.dtoValidationRules
    dtos = api.dtos
    models = api.models
  },
}))

const toolSet = {
  id: 1,
  dtoCode: 'toolSet',
  name: '工具集合',
  packageName: 'site.addzero.example',
  className: 'ToolSet',
  kind: 'STRUCTURE' as const,
  sourceModel: null,
  selectionMode: 'EXPLICIT' as const,
  excludedPaths: [],
  fields: [{
    name: 'registry',
    sourcePath: 'registry',
    description: '工具注册表',
    nullability: 'NON_NULL' as const,
    schema: null,
    kotlinType: { qualifiedName: 'example.ToolRegistry', arguments: [], nullable: false },
    validations: [],
  }],
  contributorId: ':lib:example',
  status: 1,
  version: 1,
  description: null,
}

beforeEach(() => {
  vi.clearAllMocks()
  api.dtoValidationRules.mockResolvedValue([])
  api.models.mockResolvedValue([])
})

describe('DtoStudio reuse analysis', () => {
  it('applies assistant-generated fields while preserving the manual DTO comment', async () => {
    api.dtos.mockResolvedValue([toolSet])
    api.dtoDetail.mockResolvedValue(toolSet)
    const wrapper = shallowMount(DtoStudio, {
      props: { initialDtoCode: 'toolSet' },
    })
    await flushPromises()

    const assistant = wrapper.findComponent(MetadataAssistantPanel)
    expect(assistant.props('scope')).toBe('dto')
    assistant.vm.$emit('apply', {
      ...toolSet,
      name: '不应覆盖人工注释',
      className: 'GeneratedToolSet',
      fields: [{
        ...toolSet.fields[0],
        kotlinType: { qualifiedName: 'example.GeneratedRegistry', arguments: [], nullable: false },
      }, {
        name: 'policies',
        sourcePath: 'policies',
        nullability: 'NON_NULL',
        schema: null,
        kotlinType: { qualifiedName: 'kotlin.collections.List', arguments: [], nullable: false },
        validations: [],
      }],
    })
    await wrapper.vm.$nextTick()

    const appliedDraft = wrapper.findComponent(MetadataAssistantPanel).props('draft')
    expect(appliedDraft).toMatchObject({
      name: '工具集合',
      className: 'GeneratedToolSet',
      fields: [
        { kotlinType: { qualifiedName: 'example.GeneratedRegistry' } },
        { name: 'policies' },
      ],
    })
  })

  it('shows advisory candidates for the current structure draft', async () => {
    api.dtos.mockResolvedValue([toolSet])
    api.dtoDetail.mockResolvedValue(toolSet)
    api.analyzeDtoReuse.mockResolvedValue({
      draftQualifiedName: 'site.addzero.example.generated.dto.ToolSet',
      snapshotGeneratedAtEpochMillis: 1_787_210_540_478,
      metadataStale: false,
      sourceFingerprint: 'source',
      currentMetadataFingerprint: 'metadata',
      candidates: [{
        leftQualifiedName: 'site.addzero.example.generated.dto.ToolSet',
        rightQualifiedName: 'example.ExistingToolSet',
        relation: 'EXACT',
        sharedProperties: ['registry', 'policies'],
        leftCoverage: 1,
        rightCoverage: 1,
        jaccard: 1,
        constructorOrderCompatible: true,
        defaultValuesCompatible: true,
      }],
      reusableFragments: [],
      fieldCorrelations: [],
      structures: [{
        qualifiedName: 'example.ExistingToolSet',
        properties: [],
        origins: ['SOURCE'],
      }],
    })
    const wrapper = mount(DtoStudio, {
      props: { initialDtoCode: 'toolSet' },
      global: { stubs: { IconButton: true } },
    })
    await flushPromises()

    expect(wrapper.find('[aria-label="AI 调整Kotlin 类型列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整Kotlin 类型单元格"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整字段说明列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整字段说明单元格"]').exists()).toBe(true)
    expect((wrapper.get('input[placeholder="字段说明"]').element as HTMLInputElement).value).toBe('工具注册表')

    wrapper.getComponent(Tabs).vm.$emit('update:modelValue', 'reuse')
    await flushPromises()

    expect(api.analyzeDtoReuse).toHaveBeenCalledWith(expect.objectContaining({ dtoCode: 'toolSet' }))
    expect(wrapper.text()).toContain('example.ExistingToolSet')
    expect(wrapper.text()).toContain('快照有效')
  })

  it('edits parameterized validation rules on projected fields', async () => {
    const model = {
      id: 2,
      modelCode: 'news',
      modelType: 'ENTITY',
      name: '新闻',
      className: 'News',
      status: 1,
      contributorId: ':lib:example',
      version: 1,
      fields: [{ fieldCode: 'title', label: '标题', kotlinType: 'String', required: true }],
    }
    const input = {
      id: 3,
      dtoCode: 'newsInput',
      name: '新闻输入',
      packageName: 'site.addzero.example',
      className: 'NewsInput',
      kind: 'INPUT' as const,
      sourceModel: model,
      selectionMode: 'EXPLICIT' as const,
      excludedPaths: [],
      fields: [{
        name: 'title',
        sourcePath: 'title',
        nullability: 'INHERIT' as const,
        schema: null,
        kotlinType: null,
        validations: [{ code: 'maxLength', message: null, parameters: { value: '180' } }],
      }],
      contributorId: ':lib:example',
      status: 1,
      version: 1,
      description: null,
    }
    api.dtos.mockResolvedValue([input])
    api.dtoDetail.mockResolvedValue(input)
    api.models.mockResolvedValue([model])
    api.dtoValidationRules.mockResolvedValue([{
      code: 'maxLength',
      name: '最大长度',
      description: '文本长度不能超过配置值。',
      predicate: 'MAX_LENGTH',
      supportedValueKinds: ['TEXT'],
      defaultMessage: '字段长度不能超过 {value}',
      parameters: [{
        code: 'value',
        name: '最大长度',
        description: '允许的最大字符数。',
        kind: 'INTEGER',
        required: true,
        minimum: 1,
        maximum: null,
      }],
    }])

    const wrapper = mount(DtoStudio, {
      props: { initialDtoCode: 'newsInput' },
      global: { stubs: { IconButton: true } },
    })
    await flushPromises()

    expect(wrapper.find('[aria-label="AI 调整来源属性列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整来源属性单元格"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整校验列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整校验单元格"]').exists()).toBe(true)
    expect((wrapper.get('input[placeholder="最大长度"]').element as HTMLInputElement).value).toBe('180')
    expect(wrapper.text()).toContain('最大长度')
  })

  it('offers local adjustment for independent API schema cells', async () => {
    const independent = {
      ...toolSet,
      dtoCode: 'toolPayload',
      name: '工具载荷',
      className: 'ToolPayload',
      kind: 'INPUT' as const,
      fields: [{
        name: 'registry',
        sourcePath: 'registry',
        description: 'Registry field',
        nullability: 'NON_NULL' as const,
        schema: { type: 'string' as const, format: null, description: 'String schema' },
        kotlinType: null,
        validations: [],
      }],
    }
    api.dtos.mockResolvedValue([independent])
    api.dtoDetail.mockResolvedValue(independent)

    const wrapper = mount(DtoStudio, {
      props: { initialDtoCode: 'toolPayload' },
      global: { stubs: { IconButton: true } },
    })
    await flushPromises()

    expect(wrapper.find('[aria-label="AI 调整API 类型列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整API 类型单元格"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整校验列"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="AI 调整校验单元格"]').exists()).toBe(true)

    await wrapper.get('input[placeholder="字段说明"]').setValue('工具注册信息')
    const edited = wrapper.findComponent(MetadataAssistantPanel).props('draft') as typeof independent
    expect(edited.fields[0].description).toBe('工具注册信息')
    expect(edited.fields[0].schema?.description).toBe('String schema')
  })
})
