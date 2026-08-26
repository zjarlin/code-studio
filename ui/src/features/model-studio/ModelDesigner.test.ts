import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type {
  LowcodeModelDraft,
  LowcodeModelDesignerSection,
  LowcodeModelRecentChanges,
  LowcodeModelSummary,
  LowcodeRelationKind,
} from '../../types'
import ModelDesigner from './ModelDesigner.vue'
import { normalizeModelDraft } from './model-draft'

const targetModels: LowcodeModelSummary[] = [
  {
    id: 2,
    featureId: 1,
    modelCode: 'exampleDepartment',
    modelType: 'ENTITY',
    name: 'Example Department',
    className: 'ExampleDepartment',
    status: 1,
    version: 1,
  },
]

const relationKinds: LowcodeRelationKind[] = [
  'ONE_TO_ONE',
  'MANY_TO_ONE',
  'ONE_TO_MANY',
  'MANY_TO_MANY',
]

function modelWithRelations() {
  return normalizeModelDraft({
    tableName: 'example_owner',
    fields: [{ fieldCode: 'username', label: '用户名', kotlinType: 'String' }],
    relations: relationKinds.map((relationType, index) => ({
      relationCode: index === 3 ? 'departments' : `relation${index + 1}`,
      label: index === 3 ? 'Example Departments' : `关联${index + 1}`,
      relationType,
      targetModelId: 2,
      targetModelCode: 'exampleDepartment',
      joinTable: relationType === 'MANY_TO_MANY' ? 'example_owner_department' : null,
      joinTableJoinColumn: relationType === 'MANY_TO_MANY' ? 'owner_id' : null,
      joinTableInverseColumn: relationType === 'MANY_TO_MANY' ? 'department_id' : null,
    })),
  })
}

function mountDesigner(
  recentChanges: LowcodeModelRecentChanges = { sections: [], fieldKeys: [] },
  initialSection: LowcodeModelDesignerSection = 'fields',
) {
  return mount(ModelDesigner, {
    props: {
      initialSection,
      modelValue: modelWithRelations(),
      models: targetModels,
      recentChanges,
    },
    global: {
      stubs: {
        IconButton: true,
      },
    },
  })
}

describe('ModelDesigner relation metadata', () => {
  it('provides column and cell adjustment actions for every editable field', () => {
    const wrapper = mountDesigner()
    const labels = [
      '注释', '属性名', 'Kotlin 类型', '数据库列', '表单控件', '字典', '枚举存储', '默认值', '备注',
      '必填', '新增可写', '修改可写', '自然键', 'JSON', '列表', '表单',
    ]

    for (const label of labels) {
      expect(wrapper.find(`[aria-label="AI 调整${label}列"]`).exists(), label).toBe(true)
      expect(wrapper.find(`[aria-label="AI 调整${label}单元格"]`).exists(), label).toBe(true)
    }
  })

  it('provides column and eligible cell actions for every editable relation field', () => {
    const wrapper = mountDesigner({ sections: [], fieldKeys: [] }, 'relations')
    const labels = [
      '注释', '属性名', '类型', '目标模型', 'JoinColumn / mappedBy', 'JoinTable / 本端列', '目标端列',
      '删除行为', '必填', '新增可写', '修改可写', '列表', '表单',
    ]

    for (const label of labels) {
      expect(wrapper.find(`[aria-label="AI 调整${label}列"]`).exists(), label).toBe(true)
      expect(wrapper.find(`[aria-label="AI 调整${label}单元格"]`).exists(), label).toBe(true)
    }
  })

  it('shows a readonly Kotlin file name derived from the database table', async () => {
    const wrapper = mountDesigner({ sections: [], fieldKeys: [] }, 'model')
    const kotlinFileField = wrapper.findAll('label')
      .find((label) => label.text().includes('Kotlin 文件名'))
    const tableNameField = wrapper.findAll('label')
      .find((label) => label.text().includes('数据库表名'))

    expect(wrapper.text()).not.toContain('唯一标识')
    expect(kotlinFileField?.get('input').attributes()).toHaveProperty('readonly')
    expect(kotlinFileField?.get('input').element.value).toBe('ExampleOwner.kt')

    await tableNameField?.get('input').setValue('maintenance_work_order')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      className: 'MaintenanceWorkOrder',
      modelCode: 'maintenanceWorkOrder',
      tableName: 'maintenance_work_order',
    })
  })

  it('labels comment columns consistently and highlights the rows most recently changed by AI', () => {
    const wrapper = mountDesigner({ sections: ['fields'], fieldKeys: ['code:username'] })
    const fieldsTab = wrapper.findAll('[data-slot="tabs-trigger"]')
      .find((tab) => tab.text().includes('属性'))

    expect(wrapper.text()).toContain('注释')
    expect(wrapper.text()).not.toContain('名称（KDoc）')
    expect(fieldsTab?.attributes('data-recently-changed')).toBe('true')
    expect(wrapper.get('tr[data-recently-changed="true"]').text()).toContain('AI')
  })

  it('edits create and update write policies independently', async () => {
    const wrapper = mountDesigner()
    const createWritable = wrapper.get('[aria-label="字段新增可写"]')
    const updateWritable = wrapper.get('[aria-label="字段修改可写"]')

    expect(createWritable.attributes('data-state')).toBe('checked')
    expect(updateWritable.attributes('data-state')).toBe('checked')

    await createWritable.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({
      fields: [{ createWritable: false, updateWritable: true }],
    })

    const relationWrapper = mountDesigner({ sections: [], fieldKeys: [] }, 'relations')
    expect(relationWrapper.get('[aria-label="关联新增可写"]').attributes('data-state')).toBe('checked')
    expect(relationWrapper.get('[aria-label="关联修改可写"]').attributes('data-state')).toBe('checked')
  })

  it('keeps each relation in one editable relation table', () => {
    const wrapper = mountDesigner()

    const tabs = wrapper.findAll('[data-slot="tabs-trigger"]')
    const fieldsTab = tabs.find((tab) => tab.text().includes('属性'))
    const relationsTab = tabs.find((tab) => tab.text().includes('关联'))

    expect(wrapper.text()).toContain('属性')
    expect(wrapper.text()).toContain('数据字段')
    expect(wrapper.text()).not.toContain('关联对象')
    expect(fieldsTab?.text()).toContain('1')
    expect(relationsTab?.text()).toContain('4')

    const relationWrapper = mountDesigner({ sections: [], fieldKeys: [] }, 'relations')
    const relationCodeInputs = relationWrapper.findAll('input[placeholder="propertyName"]')

    expect(relationWrapper.text()).not.toContain('关联对象')
    expect(relationCodeInputs).toHaveLength(relationKinds.length)
    expect(relationCodeInputs.map((input) => (input.element as HTMLInputElement).value)).toContain('departments')
  })

  it('selects relation targets and resolves legacy code-only values', async () => {
    const draft = modelWithRelations()
    draft.relations[0].targetModelId = null
    const wrapper = mount(ModelDesigner, {
      props: {
        initialSection: 'relations',
        modelValue: draft,
        models: targetModels,
      },
      global: {
        stubs: {
          IconButton: true,
        },
      },
    })
    const targetSelects = wrapper.findAll('select').filter((select) =>
      select.text().includes('Example Department · ExampleDepartment'),
    )

    expect(targetSelects).toHaveLength(relationKinds.length)
    expect((targetSelects[0].element as HTMLSelectElement).value).toBe('2')
    expect(wrapper.find('input[placeholder="modelCode"]').exists()).toBe(false)

    await targetSelects[0].setValue('2')

    const updated = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as LowcodeModelDraft | undefined
    expect(updated?.relations[0]).toMatchObject({
      targetModelId: 2,
      targetModelCode: 'exampleDepartment',
    })
  })

  it('assigns a distinct semantic class to every relation kind', () => {
    const wrapper = mountDesigner({ sections: [], fieldKeys: [] }, 'relations')

    for (const relationType of relationKinds) {
      const badge = wrapper.get(`[data-relation-kind="${relationType}"]`)
      expect(badge.classes()).toContain(`relation-kind-${relationType.toLowerCase().replaceAll('_', '-')}`)
    }
  })
})
