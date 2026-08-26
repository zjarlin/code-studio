import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'
import type { LsiLibraryFeature, LsiLibrarySpec, LowcodeDtoResourceSummary } from '@/types'

import LibraryResourceWorkspace from './LibraryResourceWorkspace.vue'

const api = vi.hoisted(() => ({
  contracts: vi.fn().mockResolvedValue([]),
  dtoDetail: vi.fn(),
  dtos: vi.fn(),
  models: vi.fn(),
  saveDto: vi.fn().mockResolvedValue(true),
  validateDto: vi.fn().mockResolvedValue({ valid: true, errors: [], warnings: [] }),
}))

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    contracts = api.contracts
    dtoDetail = api.dtoDetail
    dtos = api.dtos
    models = api.models
    saveDto = api.saveDto
    validateDto = api.validateDto
  },
}))

const feature: LsiLibraryFeature = {
  id: 2,
  libraryId: 7,
  parentId: null,
  featureCode: 'tool',
  name: '工具',
  description: null,
}

const librarySpec: LsiLibrarySpec = {
  schemaVersion: 3,
  description: null,
  contributorId: 'example',
  packagePrefix: 'example',
  scanPackage: 'example',
  kind: 'BUSINESS',
  runtimeDependencies: [],
  supportedIdentityModes: ['LOCAL'],
  applicationSelectable: true,
  dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
}

const dto: LowcodeDtoResourceSummary = {
  id: 9,
  featureId: 2,
  dtoCode: 'toolSet',
  name: '工具集合',
  packageName: 'example.tool',
  className: 'ToolSet',
  kind: 'STRUCTURE',
  visibility: 'PUBLIC',
  sourceModel: null,
  selectionMode: 'EXPLICIT',
  excludedPaths: [],
  fields: [{
    name: 'registry',
    sourcePath: 'registry',
    nullability: 'NON_NULL',
    schema: null,
    kotlinType: { qualifiedName: 'example.ToolRegistry', arguments: [], nullable: false },
    validations: [],
    annotations: [],
  }],
  annotations: [],
  superTypes: [],
  contributorId: ':lib:example',
  status: 1,
  version: 1,
  description: '工具注册表与策略集合。',
}

function mountWorkspace() {
  const host = defineComponent(() => () => h(TooltipProvider, null, {
    default: () => h(LibraryResourceWorkspace, {
      resource: 'dtos',
      features: [feature],
      librarySpec,
    }),
  }))
  return mount(host)
}

describe('LibraryResourceWorkspace DTO table', () => {
  it('gives every editable DTO column and cell an adjustment action', async () => {
    api.models.mockResolvedValue([])
    api.dtos.mockResolvedValue([dto])
    const wrapper = mountWorkspace()
    await flushPromises()

    const agentLabels = wrapper.findAll('[data-metadata-entry="agent"]').map((head) => head.text())

    expect(wrapper.findAll('[data-metadata-entry="manual"]')).toHaveLength(0)
    expect(agentLabels).toHaveLength(12)
    expect(agentLabels.join('|')).toContain('注释')
    expect(agentLabels.join('|')).toContain('功能分类')
    expect(agentLabels.join('|')).toContain('类名')
    expect(agentLabels.join('|')).toContain('Kotlin 可见性')
    expect(agentLabels.join('|')).toContain('来源实体')
    expect(agentLabels.join('|')).toContain('字段策略')
    expect(agentLabels.join('|')).toContain('业务包名')
    expect(agentLabels.join('|')).toContain('Contributor ID')
    expect(wrapper.find('[data-metadata-entry="system"]').text()).toContain('字段')
    expect(wrapper.findAll('button[aria-label^="AI 调整"]')).toHaveLength(22)
  })

  it('persists edited list columns through the complete DTO command', async () => {
    api.models.mockResolvedValue([])
    api.dtos.mockResolvedValue([dto])
    api.dtoDetail.mockResolvedValue(dto)
    const wrapper = mountWorkspace()
    await flushPromises()

    await wrapper.get('.library-dto-column-class-name input').setValue('GeneratedToolSet')
    await wrapper.get('button[aria-label="保存工具集合"]').trigger('click')
    await flushPromises()

    expect(api.saveDto).toHaveBeenCalledWith(expect.objectContaining({
      id: 9,
      featureId: '2',
      name: '工具集合',
      className: 'GeneratedToolSet',
      packageName: 'example.tool',
      contributorId: ':lib:example',
      kind: 'STRUCTURE',
      visibility: 'PUBLIC',
      selectionMode: 'EXPLICIT',
      status: 1,
      version: 1,
    }))
  })
})
