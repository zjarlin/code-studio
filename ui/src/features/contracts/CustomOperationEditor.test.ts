import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CustomOperationEditor from './CustomOperationEditor.vue'
import { createCustomOperation } from '../model-studio/model-draft'

describe('CustomOperationEditor', () => {
  it('marks an operation as documentation for an existing REST route', async () => {
    const wrapper = mount(CustomOperationEditor, {
      global: { stubs: { IconButton: true } },
      props: {
        basePath: '/users',
        modelValue: [{ ...createCustomOperation(0, '/users'), callContext: true }],
      },
    })

    const implementation = wrapper.findAll('select')
      .find((select) => select.text().includes('关联既有 REST'))
    await implementation?.setValue('EXISTING_REST')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject([
      { implementation: 'EXISTING_REST', transport: 'HTTP', callContext: false },
    ])
  })

  it('binds a method body to an existing entity or matching DTO kind', async () => {
    const wrapper = mount(CustomOperationEditor, {
      global: { stubs: { IconButton: true } },
      props: {
        basePath: '/users',
        modelValue: [createCustomOperation(0, '/users')],
        typeOptions: [
          { modelCode: 'user', dtoCode: '', className: 'AccountRecord', kind: 'ENTITY' },
          { modelCode: 'user', dtoCode: 'userInput', className: 'UserInput', kind: 'INPUT' },
          { modelCode: 'user', dtoCode: 'userView', className: 'UserView', kind: 'VIEW' },
        ],
      },
    })

    const typeSelectors = wrapper.findAll('select').filter((select) => select.text().includes('AccountRecord · 实体'))
    expect(typeSelectors).toHaveLength(2)
    expect(typeSelectors[0].text()).toContain('UserInput · INPUT')
    expect(typeSelectors[0].text()).not.toContain('UserView · VIEW')
    expect(typeSelectors[1].text()).toContain('UserView · VIEW')

    await typeSelectors[0].setValue('user:')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject([
      { requestBody: { schema: { type: null, typeRef: { modelCode: 'user', dtoCode: '' } } } },
    ])
  })

  it('edits the OpenAPI response description as operation metadata', async () => {
    const wrapper = mount(CustomOperationEditor, {
      global: { stubs: { IconButton: true } },
      props: {
        basePath: '/users',
        modelValue: [createCustomOperation(0, '/users')],
      },
    })

    await wrapper.get('textarea[placeholder="响应体说明"]').setValue('返回最新用户。')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject([
      { responseBody: { description: '返回最新用户。' } },
    ])
  })

  it('emits agent exposure with a write confirmation policy', async () => {
    const operation = {
      ...createCustomOperation(0, '/users'),
      operationCode: 'createUser',
      permission: 'user:create',
    }
    const wrapper = mount(CustomOperationEditor, {
      global: { stubs: { IconButton: true } },
      props: {
        basePath: '/users',
        modelValue: [operation],
        agentExposure: { operations: {} },
      },
    })

    const agentToggle = wrapper.findAll('label.compact-switch')
      .find((label) => label.text().includes('Agent'))
      ?.find('input')
    await agentToggle?.setValue(true)

    expect(wrapper.emitted('update:agentExposure')?.at(-1)?.[0]).toEqual({
      operations: { createUser: { confirmation: 'REQUIRED' } },
    })
  })
})
