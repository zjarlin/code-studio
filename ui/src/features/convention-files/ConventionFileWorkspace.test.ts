import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

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
    const wrapper = shallowMount(ConventionFileWorkspace, {
      props: {
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
          dataScope: {},
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Service / 定时任务')
    expect(wrapper.text()).toContain('CleanupJob')
    expect(wrapper.text()).not.toContain('入参')
    expect(wrapper.text()).not.toContain('出参')
  })
})
