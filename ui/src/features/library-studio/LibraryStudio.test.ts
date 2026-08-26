import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'
import type { LowcodeModelDraft, LowcodeModelSummary, LsiLibraryDefinition } from '@/types'

import LibraryStudio from './LibraryStudio.vue'

const library: LsiLibraryDefinition = {
  id: 7,
  code: 'example-foundation',
  displayName: '系统基础',
  version: 1,
  status: 1,
  features: [{ id: 71, libraryId: 7, parentId: null, featureCode: 'system', name: '系统基础' }],
  spec: {
    schemaVersion: 3,
    description: '字典和平台基础数据',
    contributorId: 'example-foundation',
    packagePrefix: 'com.example.application',
    scanPackage: 'com.example.application.foundation',
    kind: 'BUILT_IN',
    runtimeDependencies: [],
    supportedIdentityModes: ['EXTERNAL_JWT', 'LOCAL'],
    applicationSelectable: false,
    dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
  },
}
const model: LowcodeModelSummary = {
  id: 13,
  featureId: 71,
  modelCode: 'systemConfig',
  modelType: 'ENTITY',
  name: '系统配置',
  packageName: 'com.example.application.foundation.config',
  className: 'SystemConfig',
  tableName: 'system_config',
  status: 1,
  contributorId: 'example-foundation',
  version: 1,
  routeConfig: {
    className: 'SystemConfig',
    qualifiedName: 'com.example.application.foundation.generated.entity.SystemConfig',
    path: '/infra/config',
    aliasPaths: [],
    fetchPaths: [],
    excludePaths: [],
    enabledOperations: ['PAGE'],
    customOperations: [{
      operationCode: 'getValueByKey',
      name: '按键读取配置值',
      path: '/infra/config/get-value-by-key',
      method: 'GET',
      transport: 'HTTP',
    }],
  },
}
const modelDetail: LowcodeModelDraft = {
  ...model,
  packageName: 'com.example.application.foundation.config',
  className: 'SystemConfig',
  tableName: 'system_config',
  modelType: 'ENTITY',
  entityConfig: {
    sourceMode: 'GENERATED',
    baseMode: 'DEFAULT',
    baseModels: [],
    superTypes: [],
    relationOrderings: {},
    inheritedProperties: [],
    inheritedRelationCodes: [],
    formulaProperties: [],
    transientProperties: [],
  },
  routeConfig: {
    packageName: model.packageName!,
    qualifiedName: model.routeConfig!.qualifiedName,
    className: model.routeConfig!.className,
    path: model.routeConfig!.path,
    aliasPaths: [],
    fetchPaths: [],
    excludePaths: [],
    enabledOperations: ['PAGE'],
    properties: [],
    queryFields: [],
    defaultOrders: [],
    customOperations: [],
    dtoSchemas: [],
    agentExposure: { operations: {} },
  },
  fields: [],
  queries: [{ id: 21, orderNo: 1, queryCode: 'byKey', label: '按键查询', logic: 'AND', items: [] }],
  relations: [],
}

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    libraries = vi.fn().mockResolvedValue([library])
    libraryFeatures = vi.fn().mockResolvedValue(library.features)
    models = vi.fn().mockResolvedValue([model])
    dtos = vi.fn().mockResolvedValue([])
    contracts = vi.fn().mockResolvedValue([])
    detail = vi.fn().mockResolvedValue(modelDetail)
  },
}))

vi.mock('../constants/constant-api', () => ({
  ConstantApi: class {
    list = vi.fn().mockResolvedValue([])
  },
}))

function mountStudio() {
  const host = defineComponent(() => () => h(TooltipProvider, null, { default: () => h(LibraryStudio) }))
  return mount(host, { global: { stubs: { LibraryResourceWorkspace: true, LibraryQueryTable: true } } })
}

describe('LibraryStudio', () => {
  it('hides empty resource tabs and keeps populated workspaces isolated', async () => {
    const wrapper = mountStudio()
    await flushPromises()

    expect(wrapper.text()).toContain('系统基础')
    expect(wrapper.text()).toContain('内置')
    await wrapper.get('.model-item').trigger('click')
    await flushPromises()
    expect(wrapper.get('.model-item').attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('.application-workspace').text()).not.toContain('唯一标识')
    expect(wrapper.findAll('label').map(label => label.text())).toContain('Contributor ID')

    await wrapper.get('.application-feature-item').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('label').map(label => label.text())).not.toContain('Contributor ID')
    const modelTab = wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '模型')
    expect(modelTab).toBeDefined()
    await modelTab!.trigger('mousedown', { button: 0, ctrlKey: false })
    await flushPromises()
    expect(wrapper.find('library-resource-workspace-stub').exists()).toBe(true)
    expect(wrapper.find('.application-editor').exists()).toBe(false)

    const queryTab = wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '查询')
    expect(queryTab).toBeDefined()
    await queryTab!.trigger('mousedown', { button: 0, ctrlKey: false })
    await flushPromises()
    expect(wrapper.find('library-query-table-stub').exists()).toBe(true)
    expect(wrapper.find('library-resource-workspace-stub').exists()).toBe(false)

    const tabLabels = wrapper.findAll('[role="tab"]').map((tab) => tab.text())
    expect(tabLabels).not.toContain('DTO')
    expect(tabLabels).not.toContain('Service')
    expect(tabLabels).not.toContain('常量')
  })

  it('exposes resource creation through a labeled polymorphic menu', async () => {
    const wrapper = mountStudio()
    await flushPromises()

    const createButton = wrapper.get('[aria-label="新增资源"]')
    expect(createButton.text()).toContain('新增')
    await createButton.trigger('click')
    await flushPromises()

    const menuItems = document.body.querySelectorAll('[role="menuitem"]')
    const labels = Array.from(menuItems).map((item) => item.textContent?.trim())
    expect(labels).toContain('常量')
  })

  it('finds an operation by its full HTTP path and opens its owning tab', async () => {
    const wrapper = mountStudio()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('/infra/config/get-value-by-key')
    expect(wrapper.get('.library-search-result').text()).toContain('GET /infra/config/get-value-by-key')

    await wrapper.get('.library-search-result').trigger('click')
    await flushPromises()

    expect(wrapper.find('library-resource-workspace-stub').exists()).toBe(true)
    expect(wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '模型')?.attributes('data-state')).toBe('active')
  })
})
