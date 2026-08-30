import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import type { LowcodeApiSchemaDraft } from '@/types'
import { createFieldSchema } from './dto-draft'
import ApiSchemaEditor from './ApiSchemaEditor.vue'

function mountEditor() {
  const Host = defineComponent({
    components: { ApiSchemaEditor },
    setup() {
      const schema = ref<LowcodeApiSchemaDraft>(createFieldSchema('string'))
      const typeOptions = [
        { modelCode: 'device', dtoCode: '', className: 'Device', kind: 'ENTITY' as const },
        { modelCode: null, dtoCode: 'statusCount', className: 'StatusCountView', kind: 'VIEW' as const },
      ]
      return { schema, typeOptions }
    },
    template: '<ApiSchemaEditor v-model="schema" :type-options="typeOptions" />',
  })
  return mount(Host, { global: { stubs: { IconButton: true } } })
}

describe('ApiSchemaEditor', () => {
  it('builds nested object and list schemas through structured controls', async () => {
    const wrapper = mountEditor()

    await wrapper.get('[data-schema-kind]').setValue('object')
    await wrapper.get('button').trigger('click')
    const kindSelectors = wrapper.findAll('[data-schema-kind]')
    await kindSelectors[1].setValue('array')

    const schema = (wrapper.vm as unknown as { schema: LowcodeApiSchemaDraft }).schema
    expect(schema.type).toBe('object')
    expect(schema.properties.field1).toMatchObject({
      type: 'array',
      items: { type: 'string' },
    })
    expect(schema.required).toEqual([])
  })

  it('stores named DTO references without a model code', async () => {
    const wrapper = mountEditor()

    await wrapper.get('[data-schema-kind]').setValue('reference')
    await wrapper.get('[data-schema-reference]').setValue('dto:statusCount')

    const schema = (wrapper.vm as unknown as { schema: LowcodeApiSchemaDraft }).schema
    expect(schema.typeRef).toEqual({ modelCode: null, dtoCode: 'statusCount' })
    expect(schema.type).toBeNull()
  })
})
