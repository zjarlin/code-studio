import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/generated/shadcn/tooltip'

import ConventionFileWorkspace from './ConventionFileWorkspace.vue'

vi.mock('@/lowcode-api', () => ({
  LowcodeApi: class {
    conventionFiles = vi.fn().mockResolvedValue([{
      id: 1,
      featureId: 11,
      fileCode: 'cleanup',
      name: '数据清理任务',
      className: 'CleanupJob',
      kind: 'SCHEDULED_JOB',
      status: 1,
      packageName: 'com.example.maintenance.job',
      contributorId: 'example',
    }])
  },
}))

describe('ConventionFileWorkspace', () => {
  it('only exposes convention file identity instead of method signatures', async () => {
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(ConventionFileWorkspace, {
        features: [{ id: 11, libraryId: 1, parentId: null, featureCode: 'maintenance', name: '维护' }],
        readOnly: true,
        librarySpec: {
          schemaVersion: 3,
          contributorId: 'example',
          packagePrefix: 'com.example',
          scanPackage: 'com.example',
          kind: 'BUSINESS',
          runtimeDependencies: [],
          supportedIdentityModes: ['LOCAL'],
          applicationSelectable: true,
          dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
        },
      }),
    }))
    const wrapper = mount(host)
    await flushPromises()

    expect(wrapper.text()).toContain('Service / 定时任务')
    expect(wrapper.findAll('input').map((input) => input.element.value)).toContain('CleanupJob')
    expect(wrapper.findAll('input').every((input) => input.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.findAll('button').find((button) => button.text().includes('定时任务'))?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('入参')
    expect(wrapper.text()).not.toContain('出参')
  })

  it('creates a scheduled job row directly', async () => {
    const host = defineComponent(() => () => h(TooltipProvider, null, {
      default: () => h(ConventionFileWorkspace, {
        createKind: 'SCHEDULED_JOB',
        createRequest: 1,
        features: [{ id: 11, libraryId: 1, parentId: null, featureCode: 'maintenance', name: '维护' }],
        librarySpec: {
          schemaVersion: 3,
          contributorId: 'example',
          packagePrefix: 'com.example',
          scanPackage: 'com.example',
          kind: 'BUSINESS',
          runtimeDependencies: [],
          supportedIdentityModes: ['LOCAL'],
          applicationSelectable: true,
          dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
        },
      }),
    }))
    const wrapper = mount(host)
    await flushPromises()

    const createdRow = wrapper.findAll('tbody tr')[0]
    expect(createdRow.text()).toContain('定时任务')
    expect(createdRow.get('code').text()).toBe('com.example.maintenance.job')
  })
})
