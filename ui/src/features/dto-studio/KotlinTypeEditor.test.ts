import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import KotlinTypeEditor from './KotlinTypeEditor.vue'

describe('KotlinTypeEditor', () => {
  it('adds a structured generic argument', async () => {
    const wrapper = mount(KotlinTypeEditor, {
      props: {
        modelValue: { qualifiedName: 'kotlin.collections.List', arguments: [], nullable: false },
      },
    })

    await wrapper.get('button[aria-label="添加泛型参数"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual({
      qualifiedName: 'kotlin.collections.List',
      arguments: [{ qualifiedName: 'kotlin.String', arguments: [], nullable: false }],
      nullable: false,
    })
  })
})
