import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ApiOperationTree from './ApiOperationTree.vue'
import type { ApiOperation } from '../types'

const operation: ApiOperation = {
  id: 'get:/users/simple-list',
  method: 'get',
  path: '/users/simple-list',
  addresses: [
    { path: '/users/simple-list' },
    { path: '/users/list' },
  ],
  summary: '获取用户列表',
  tags: ['Users'],
  parameters: [],
  responses: {},
  lowcodeContract: false,
  transport: 'HTTP',
  metadataIssues: [],
}

describe('ApiOperationTree', () => {
  it('renders aliases as multiple addresses inside one operation item', async () => {
    const wrapper = mount(ApiOperationTree, {
      props: {
        groups: [{ name: 'Users', operations: [operation] }],
        selectedId: operation.id,
      },
    })

    expect(wrapper.findAll('.api-operation-item')).toHaveLength(1)
    expect(wrapper.findAll('.api-operation-address-preview > span').map((item) => item.text())).toEqual([
      '/users/simple-list',
      '/users/list',
    ])
    await wrapper.get('.api-operation-item').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([operation])
  })
})
