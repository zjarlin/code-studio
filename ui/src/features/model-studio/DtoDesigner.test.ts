import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, ref } from 'vue'

import DtoDesigner from './DtoDesigner.vue'
import ModelDesigner from './ModelDesigner.vue'
import { createEntityConfig, normalizeModelDraft } from './model-draft'
import type { LowcodeModelDraft } from '../../types'

describe('DtoDesigner', () => {
  it('emits selected base models from the checkbox control', async () => {
    const wrapper = mount(DtoDesigner, {
      global: { stubs: { IconButton: true } },
      props: {
        entityConfig: createEntityConfig(),
        fields: [],
        models: [],
        relations: [],
      },
    })

    await wrapper.get('[data-base-model="TENANT"]').trigger('click')

    expect(wrapper.emitted('adopt-base-model')?.at(-1)?.[0]).toMatchObject({
      baseModels: ['BASE_ENTITY', 'TENANT'],
    })
  })

  it('propagates base model selection through the model designer', async () => {
    const Host = defineComponent({
      components: { ModelDesigner },
      setup() {
        const model = ref<LowcodeModelDraft>(normalizeModelDraft({}))
        return { model }
      },
      template: '<ModelDesigner v-model="model" initial-section="model" :models="[]" />',
    })
    const wrapper = mount(Host)

    await wrapper.get('[data-base-model="TENANT"]').trigger('click')

    expect(wrapper.get('[data-base-model="TENANT"]').attributes('data-state')).toBe('checked')
  })

  it('moves a matching local field into the selected base model', async () => {
    const model = ref<LowcodeModelDraft>(normalizeModelDraft({
      fields: [
        { fieldCode: 'deleted', label: '逻辑删除标记', kotlinType: 'Int', dbColumn: 'deleted' },
        { fieldCode: 'deviceKey', label: '设备标识', kotlinType: 'String', dbColumn: 'device_key' },
      ],
    }))
    const Host = defineComponent({
      components: { ModelDesigner },
      setup() {
        return { model }
      },
      template: '<ModelDesigner v-model="model" initial-section="model" :models="[]" />',
    })
    const wrapper = mount(Host)

    await wrapper.get('[data-base-model="DELETED"]').trigger('click')

    const selected = model.value as unknown as {
      entityConfig: { baseModels: string[] }
      fields: Array<{ fieldCode: string }>
    }
    const baseModels = selected.entityConfig.baseModels
    const fieldCodes = selected.fields.map((field) => field.fieldCode)
    expect(baseModels).toEqual(['BASE_ENTITY', 'DELETED'])
    expect(fieldCodes).toEqual(['deviceKey'])
  })

  it('keeps entity inheritance settings available when the host owns dto resources', () => {
    const wrapper = mount(ModelDesigner, {
      props: {
        modelValue: normalizeModelDraft({}),
        models: [],
      },
    })

    expect(wrapper.text()).toContain('实体高级设置')
    expect(wrapper.text()).toContain('表继承角色')
    expect(wrapper.text()).not.toContain('添加 DTO')
  })

  it('switches a model to a Jimmer inheritance subtype', async () => {
    const wrapper = mount(DtoDesigner, {
      global: { stubs: { IconButton: true } },
      props: {
        modelCode: 'maintenanceWorkOrder',
        contributorId: ':example:work-order',
        entityConfig: createEntityConfig(),
        fields: [],
        models: [{
          id: 1,
          featureId: 1,
          modelCode: 'workOrder',
          modelType: 'ENTITY',
          name: 'Work order',
          status: 1,
          contributorId: ':example:work-order',
          version: 1,
          entityConfig: {
            ...createEntityConfig(),
            inheritanceRoot: {
              strategy: 'JOINED',
              discriminatorField: 'workOrderType',
              instantiability: 'ABSTRACT',
              discriminatorValue: null,
              joinedTableDissociateAction: 'DELETE',
            },
          },
        }],
        relations: [],
      },
    })

    const roleSelect = wrapper.findAll('select').find((select) => select.text().includes('继承根模型'))
    await roleSelect?.setValue('SUBTYPE')

    expect(wrapper.emitted('update:entityConfig')?.at(-1)?.[0]).toMatchObject({
      baseMode: 'INHERITED',
      baseModels: [],
      inheritanceRoot: null,
      inheritanceSubtype: {
        parentModelCode: 'workOrder',
        discriminatorValue: '',
      },
    })
  })

})
