import { describe, expect, it } from 'vitest'

import {
  applyAgentDtoDraft,
  applyDtoClassName,
  applyDtoKind,
  createDtoResource,
  createDtoResourceField,
  normalizeDtoResource,
  validateDtoResource,
} from './dto-draft'

describe('DTO draft identity', () => {
  it('derives the hidden DTO code from the Kotlin class name', () => {
    const draft = applyDtoClassName(createDtoResource(), 'MaintenanceStatisticsOutput')

    expect(draft.dtoCode).toBe('maintenanceStatistics')
  })

  it('preserves a persisted custom code when the class name changes', () => {
    const draft = applyDtoClassName({
      ...createDtoResource(),
      dtoCode: 'legacy_statistics',
      className: 'LegacyStatisticsOutput',
    }, 'MaintenanceStatisticsOutput')

    expect(draft.dtoCode).toBe('legacy_statistics')
  })

  it('normalizes legacy VIEW metadata to OUTPUT without renaming its class', () => {
    const draft = normalizeDtoResource({
      ...createDtoResource(),
      id: 1,
      dtoCode: 'newsView',
      name: '新闻',
      className: 'NewsView',
      kind: 'VIEW',
    })

    expect(draft.kind).toBe('OUTPUT')
    expect(draft.className).toBe('NewsView')
  })

  it('creates structured Kotlin fields without an API schema', () => {
    const field = createDtoResourceField('STRUCTURE', true)

    expect(field.schema).toBeNull()
    expect(field.description).toBeNull()
    expect(field.kotlinType).toEqual({ qualifiedName: 'kotlin.String', arguments: [], nullable: false })
  })

  it('normalizes a field description independently from its API schema', () => {
    const draft = normalizeDtoResource({
      ...createDtoResource(),
      fields: [{
        name: 'deviceName',
        sourcePath: 'deviceName',
        description: '设备名称',
        nullability: 'NON_NULL',
        schema: { type: 'string', description: 'String type' },
        kotlinType: null,
        validations: [],
        annotations: [],
      }],
    })

    expect(draft.fields[0].description).toBe('设备名称')
    expect(draft.fields[0].schema?.description).toBe('String type')
  })

  it('preserves recursive Kotlin types and validates their classifier', () => {
    const draft = normalizeDtoResource({
      ...createDtoResource(),
      dtoCode: 'toolSet',
      name: '工具集合',
      packageName: 'site.addzero.example',
      className: 'ToolSet',
      kind: 'STRUCTURE',
      contributorId: ':lib:example',
      fields: [{
        name: 'policies',
        sourcePath: 'policies',
        nullability: 'NON_NULL',
        schema: null,
        kotlinType: {
          qualifiedName: 'kotlin.collections.Map',
          arguments: [
            { qualifiedName: 'kotlin.String', arguments: [], nullable: false },
            { qualifiedName: 'example.Policy', arguments: [], nullable: false },
          ],
          nullable: false,
        },
        validations: [],
      }],
    })

    expect(draft.fields[0].kotlinType?.arguments).toHaveLength(2)
    expect(validateDtoResource(draft)).toEqual([])
    expect(validateDtoResource({
      ...draft,
      fields: [{ ...draft.fields[0], kotlinType: { qualifiedName: '', arguments: [], nullable: false } }],
    })).toContain('字段 policies 缺少 Kotlin 全限定类型')
  })

  it('merges assistant fields by stable source path while preserving DTO identity and manual name', () => {
    const current = normalizeDtoResource({
      ...createDtoResource(),
      id: 9,
      featureId: 3,
      dtoCode: 'toolSet',
      name: '工具集合',
      packageName: 'example.tool',
      className: 'ToolSet',
      kind: 'STRUCTURE',
      contributorId: ':lib:example',
      fields: [{
        id: 91,
        name: 'registry',
        sourcePath: 'registry',
        nullability: 'NON_NULL',
        schema: null,
        kotlinType: { qualifiedName: 'example.LegacyRegistry', arguments: [], nullable: false },
        validations: [],
        annotations: [],
        defaultValue: { kind: 'EMPTY_INSTANCE' },
      }],
    })

    const generated = applyAgentDtoDraft(current, {
      dtoCode: 'generatedToolSet',
      name: '不应覆盖人工名称',
      packageName: 'example.generated',
      className: 'GeneratedToolSet',
      kind: 'STRUCTURE',
      visibility: 'INTERNAL',
      sourceModelCode: null,
      selectionMode: 'EXPLICIT',
      excludedPaths: [],
      fields: [{
        name: 'registry',
        sourcePath: 'registry',
        nullability: 'NON_NULL',
        schema: null,
        kotlinType: { qualifiedName: 'example.ToolRegistry', arguments: [], nullable: false },
        validations: [],
      }, {
        name: 'policies',
        sourcePath: 'policies',
        nullability: 'NON_NULL',
        schema: null,
        kotlinType: { qualifiedName: 'kotlin.collections.List', arguments: [], nullable: false },
        validations: [],
      }],
      contributorId: ':lib:generated',
      status: 1,
      version: 2,
      description: '生成说明',
    })

    expect(generated).toMatchObject({
      id: 9,
      featureId: 3,
      name: '工具集合',
      dtoCode: 'generatedToolSet',
      className: 'GeneratedToolSet',
      visibility: 'INTERNAL',
    })
    expect(generated.fields[0]).toMatchObject({
      id: 91,
      defaultValue: { kind: 'EMPTY_INSTANCE' },
      kotlinType: { qualifiedName: 'example.ToolRegistry' },
    })
    expect(generated.fields[1].name).toBe('policies')
  })

  it('normalizes field representations when the DTO kind changes', () => {
    const output = normalizeDtoResource({
      ...createDtoResource(),
      fields: [createDtoResourceField('OUTPUT', true)],
    })

    const structure = applyDtoKind(output, 'STRUCTURE')
    const restored = applyDtoKind(structure, 'OUTPUT')

    expect(structure.fields[0]).toMatchObject({ schema: null, kotlinType: { qualifiedName: 'kotlin.String' } })
    expect(restored.fields[0]).toMatchObject({ kotlinType: null, schema: { type: 'string' } })
  })
})
