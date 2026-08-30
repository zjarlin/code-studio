import { describe, expect, it } from 'vitest'

import { normalizeContractDraft } from '@/features/contracts/contract-draft'
import { normalizeDtoResource } from '@/features/dto-studio/dto-draft'
import { normalizeModelDraft } from '@/features/model-studio/model-draft'
import type { MetadataTableContext, MetadataTablePatchResult } from '@/types'

import {
  applyMetadataDisplayTextPatches,
  createMetadataDisplayTextContext,
} from './metadata-display-text'
import { needsChineseTranslation } from './metadata-display-text-targets'

describe('metadata display text patches', () => {
  it('translates allowlisted model text without changing identities or structure', () => {
    const draft = normalizeModelDraft({
      id: 9,
      featureId: 7,
      modelCode: 'catalogRecord',
      name: 'Catalog Record',
      packageName: 'example.device',
      className: 'CatalogRecord',
      tableName: 'catalog_record',
      modelType: 'ENTITY',
      status: 1,
      version: 3,
      contributorId: ':example:device',
      remark: 'Device metadata',
      entityConfig: {
        inheritedProperties: [{
          name: 'areaName',
          kotlinType: 'String',
          dbColumn: 'area_name',
          required: false,
          id: false,
          description: 'Area name',
        }],
        formulaProperties: [{
          propertyCode: 'displayPath',
          label: 'Display path',
          kotlinType: 'String',
          kind: 'KOTLIN',
          expression: 'name',
          dependencies: ['name'],
          nullable: false,
          description: 'Device display path',
        }],
      },
      routeConfig: {
        displayName: 'Device route',
        path: '/devices',
        description: 'Device API',
        properties: [{
          name: 'projectId',
          type: 'integer',
          format: 'int64',
          required: false,
          description: 'Project ID',
        }],
        queryFields: [{
          propertyName: 'projectId',
          parameterName: 'projectId',
          operator: 'EQ',
          type: 'integer',
          format: 'int64',
          required: false,
          stateCases: [],
          description: 'Project filter',
        }],
        customOperations: [{
          operationCode: 'inspect',
          name: 'Inspect device',
          description: 'Inspect one device',
          path: '/devices/{id}/inspection',
          method: 'POST',
          transport: 'HTTP',
          authenticated: true,
          callContext: false,
          parameters: [],
          requestBody: null,
          responseBody: null,
          responseEnvelope: true,
        }],
      },
      fields: [{
        id: 11,
        orderNo: 1,
        fieldCode: 'projectId',
        label: 'Project',
        kotlinType: 'Long',
        dbColumn: 'project_id',
        required: false,
        remark: 'Project reference',
      }],
      queries: [{
        id: 12,
        orderNo: 1,
        queryCode: 'byProject',
        label: 'By project',
        logic: 'AND',
        items: [{
          id: 13,
          orderNo: 1,
          fieldCode: 'projectId',
          operator: 'EQ',
          valueType: 'SINGLE',
        }],
      }],
      relations: [{
        id: 14,
        orderNo: 1,
        relationCode: 'project',
        label: 'Project',
        relationType: 'MANY_TO_ONE',
        targetModelId: 15,
        targetModelCode: 'catalogGroup',
        joinColumn: 'project_id',
        dissociateAction: 'LAX',
      }],
    })
    const context = createMetadataDisplayTextContext('model', draft, '9:0')
    expect(context.rows.some((row) => String(row.values.context).includes('路由的展示名称'))).toBe(false)
    expect(context.rows.some((row) => String(row.values.context).includes('路由属性 projectId'))).toBe(false)
    expect(context.rows.some((row) => String(row.values.context).includes('路由查询参数 projectId'))).toBe(false)
    const result = patchResult(context, [
      ['模型 catalogRecord 的展示名称', '目录记录'],
      ['字段 projectId，数据库列 project_id 的注释', '项目'],
      ['查询 byProject 的展示名称', '按项目查询'],
      ['关联 project，目标模型 catalogGroup 的注释', '项目'],
      ['路由的说明', '设备接口'],
      ['操作 inspect 的展示名称', '检查设备'],
    ])

    const application = applyMetadataDisplayTextPatches('model', draft, '9:0', result)
    const translated = normalizeModelDraft(application.draft)

    expect(application.conflicts).toEqual([])
    expect(application.applied).toHaveLength(6)
    expect(translated).toMatchObject({
      id: 9,
      featureId: 7,
      modelCode: 'catalogRecord',
      name: '目录记录',
      packageName: 'example.device',
      className: 'CatalogRecord',
      tableName: 'catalog_record',
      status: 1,
      version: 3,
      contributorId: ':example:device',
    })
    expect(translated.fields[0]).toMatchObject({
      id: 11,
      orderNo: 1,
      fieldCode: 'projectId',
      label: '项目',
      kotlinType: 'Long',
      dbColumn: 'project_id',
      remark: 'Project reference',
    })
    expect(translated.queries[0]).toMatchObject({ id: 12, queryCode: 'byProject', label: '按项目查询' })
    expect(translated.queries[0].items[0]).toMatchObject({ id: 13, fieldCode: 'projectId', operator: 'EQ' })
    expect(translated.relations[0]).toMatchObject({
      id: 14,
      relationCode: 'project',
      label: '项目',
      targetModelId: 15,
      targetModelCode: 'catalogGroup',
      joinColumn: 'project_id',
      dissociateAction: 'LAX',
    })
    expect(translated.routeConfig).toMatchObject({ path: '/devices', description: '设备接口' })
    expect(translated.routeConfig.customOperations[0]).toMatchObject({
      operationCode: 'inspect',
      name: '检查设备',
      path: '/devices/{id}/inspection',
      method: 'POST',
    })
  })

  it('keeps DTO field identities and types while translating nested schema descriptions', () => {
    const draft = normalizeDtoResource({
      id: 21,
      featureId: 4,
      dtoCode: 'deviceOutput',
      name: 'Device output',
      packageName: 'example.device',
      className: 'DeviceOutput',
      kind: 'OUTPUT',
      visibility: 'PUBLIC',
      sourceModelCode: null,
      selectionMode: 'EXPLICIT',
      excludedPaths: [],
      fields: [{
        id: 22,
        name: 'projects',
        sourcePath: 'projects',
        description: 'Related projects',
        nullability: 'NON_NULL',
        schema: {
          type: 'array',
          typeRef: null,
          format: null,
          description: 'Project list',
          properties: {},
          required: [],
          items: {
            type: 'object',
            typeRef: null,
            format: null,
            description: 'Project item',
            properties: {},
            required: [],
            items: null,
            enumValues: [],
            oneOf: [],
          },
          enumValues: [],
          oneOf: [],
        },
        kotlinType: null,
        validations: [],
        annotations: [],
        defaultValue: null,
      }],
      annotations: [],
      superTypes: [],
      contributorId: ':example:device',
      status: 1,
      version: 1,
      description: 'Device response',
    })
    const context = createMetadataDisplayTextContext('dto', draft, '21:0')
    expect(context.rows.some((row) => row.values.context === 'DTO deviceOutput 字段 projects Schema 的说明')).toBe(false)
    const result = patchResult(context, [
      ['DTO deviceOutput 的中文注释', '设备输出'],
      ['DTO deviceOutput 字段 projects 的说明', '关联项目'],
      ['DTO deviceOutput 字段 projects Schema 元素 Schema 的说明', '项目项'],
    ])

    const application = applyMetadataDisplayTextPatches('dto', draft, '21:0', result)
    const translated = normalizeDtoResource(application.draft)

    expect(application.conflicts).toEqual([])
    expect(translated.name).toBe('设备输出')
    expect(translated.fields[0]).toMatchObject({
      id: 22,
      name: 'projects',
      sourcePath: 'projects',
      description: '关联项目',
      nullability: 'NON_NULL',
      schema: {
        type: 'array',
        description: 'Project list',
        items: { type: 'object', description: '项目项' },
      },
    })
  })

  it('fills a missing standalone DTO field description without changing its schema', () => {
    const draft = normalizeDtoResource({
      id: 23,
      featureId: 4,
      dtoCode: 'devicePayload',
      name: '设备载荷',
      packageName: 'example.device',
      className: 'DevicePayload',
      kind: 'INPUT',
      visibility: 'PUBLIC',
      sourceModelCode: null,
      selectionMode: 'EXPLICIT',
      excludedPaths: [],
      fields: [{
        id: 24,
        name: 'deviceName',
        sourcePath: 'deviceName',
        description: null,
        nullability: 'NON_NULL',
        schema: {
          type: 'string',
          typeRef: null,
          format: null,
          description: '文本类型',
          properties: {},
          required: [],
          items: null,
          enumValues: [],
          oneOf: [],
        },
        kotlinType: null,
        validations: [],
        annotations: [],
        defaultValue: null,
      }],
      annotations: [],
      superTypes: [],
      contributorId: ':example:device',
      status: 1,
      version: 1,
      description: '设备载荷说明',
    })
    const context = createMetadataDisplayTextContext('dto', draft, '23:0')

    expect(context.rows).toHaveLength(1)
    expect(context.rows[0]?.values).toMatchObject({
      context: 'DTO devicePayload 字段 deviceName 的说明',
      value: '',
    })

    const result = patchResult(context, [['DTO devicePayload 字段 deviceName 的说明', '设备名称']])
    const application = applyMetadataDisplayTextPatches('dto', draft, '23:0', result)
    const translated = normalizeDtoResource(application.draft)

    expect(application.conflicts).toEqual([])
    expect(translated.fields[0]).toMatchObject({
      id: 24,
      name: 'deviceName',
      sourcePath: 'deviceName',
      description: '设备名称',
      schema: { type: 'string', description: '文本类型' },
    })
  })

  it('keeps empty projected field descriptions inheritable while translating explicit overrides', () => {
    const draft = normalizeDtoResource({
      id: 25,
      featureId: 4,
      dtoCode: 'deviceProjection',
      name: '设备投影',
      packageName: 'example.device',
      className: 'DeviceProjection',
      kind: 'OUTPUT',
      visibility: 'PUBLIC',
      sourceModelCode: 'device',
      selectionMode: 'EXPLICIT',
      excludedPaths: [],
      fields: [{
        id: 26,
        name: 'name',
        sourcePath: 'name',
        description: null,
        nullability: 'INHERIT',
        schema: null,
        kotlinType: null,
        validations: [],
        annotations: [],
        defaultValue: null,
      }, {
        id: 27,
        name: 'projectName',
        sourcePath: 'project.name',
        description: 'Project name override',
        nullability: 'INHERIT',
        schema: null,
        kotlinType: null,
        validations: [],
        annotations: [],
        defaultValue: null,
      }],
      annotations: [],
      superTypes: [],
      contributorId: ':example:device',
      status: 1,
      version: 1,
      description: '设备投影说明',
    })
    const context = createMetadataDisplayTextContext('dto', draft, '25:0')

    expect(context.rows).toHaveLength(1)
    expect(context.rows[0]?.values).toMatchObject({
      context: 'DTO deviceProjection 字段 projectName 的说明',
      value: 'Project name override',
    })
    expect(context.rows.some((row) => String(row.values.context).includes('字段 name 的说明'))).toBe(false)

    const result = patchResult(context, [['DTO deviceProjection 字段 projectName 的说明', '项目名称自定义说明']])
    const application = applyMetadataDisplayTextPatches('dto', draft, '25:0', result)
    const translated = normalizeDtoResource(application.draft)

    expect(application.conflicts).toEqual([])
    expect(translated).toMatchObject({ sourceModelCode: 'device' })
    expect(translated.fields).toEqual([
      expect.objectContaining({
        id: 26,
        name: 'name',
        sourcePath: 'name',
        description: null,
        nullability: 'INHERIT',
      }),
      expect.objectContaining({
        id: 27,
        name: 'projectName',
        sourcePath: 'project.name',
        description: '项目名称自定义说明',
        nullability: 'INHERIT',
      }),
    ])
  })

  it('keeps contract operation and transport structure while translating documentation', () => {
    const draft = normalizeContractDraft({
      id: 31,
      featureId: 5,
      contractCode: 'deviceQuery',
      name: 'Device query',
      packageName: 'example.device',
      className: 'DeviceQueryService',
      path: '/devices',
      contributorId: ':example:device',
      status: 1,
      version: 2,
      description: 'Device query contract',
      agentExposure: { operations: {} },
      operations: [{
        operationCode: 'getDevice',
        name: 'Get device',
        description: 'Read one device',
        path: '/devices/{id}',
        method: 'GET',
        transport: 'HTTP',
        implementation: 'GENERATED',
        authenticated: true,
        permission: 'device:read',
        callContext: false,
        parameters: [{
          name: 'id',
          location: 'PATH',
          required: true,
          description: 'Device ID',
          schema: {
            type: 'integer',
            typeRef: null,
            format: 'int64',
            description: null,
            properties: {},
            required: [],
            items: null,
            enumValues: [],
            oneOf: [],
          },
        }],
        requestBody: null,
        responseBody: {
          contentType: 'application/json',
          required: true,
          description: 'Device response',
          schema: {
            type: 'object',
            typeRef: null,
            format: null,
            description: 'Device detail',
            properties: {
              name: {
                type: 'string',
                typeRef: null,
                format: null,
                description: 'Device name',
                properties: {},
                required: [],
                items: null,
                enumValues: [],
                oneOf: [],
              },
            },
            required: ['name'],
            items: null,
            enumValues: [],
            oneOf: [],
          },
        },
        responseEnvelope: true,
      }],
    })
    const context = createMetadataDisplayTextContext('contract', draft, '31:0')
    expect(context.rows.some((row) => row.values.context === '契约 deviceQuery 操作 getDevice 参数 PATH:id Schema 的说明')).toBe(false)
    expect(context.rows.some((row) => row.values.context === '契约 deviceQuery 操作 getDevice 响应体 Schema 的说明')).toBe(false)
    const result = patchResult(context, [
      ['契约 deviceQuery 的展示名称', '设备查询'],
      ['操作 getDevice 的展示名称', '查询设备'],
      ['操作 getDevice 参数 PATH:id 的说明', '设备标识'],
      ['响应体 Schema 属性 name Schema 的说明', '设备名称'],
    ])

    const application = applyMetadataDisplayTextPatches('contract', draft, '31:0', result)
    const translated = normalizeContractDraft(application.draft)

    expect(application.conflicts).toEqual([])
    expect(translated).toMatchObject({
      id: 31,
      contractCode: 'deviceQuery',
      name: '设备查询',
      className: 'DeviceQueryService',
      path: '/devices',
    })
    expect(translated.operations[0]).toMatchObject({
      operationCode: 'getDevice',
      name: '查询设备',
      path: '/devices/{id}',
      method: 'GET',
      transport: 'HTTP',
      permission: 'device:read',
      parameters: [{ name: 'id', location: 'PATH', description: '设备标识' }],
      responseBody: {
        schema: {
          properties: {
            name: { type: 'string', description: '设备名称' },
          },
        },
      },
    })
  })

  it('fills a missing contract parameter description without exposing an empty operation name', () => {
    const draft = normalizeContractDraft({
      id: 32,
      featureId: 5,
      contractCode: 'deviceLookup',
      name: '设备查找',
      packageName: 'example.device',
      className: 'DeviceLookupService',
      path: '/devices',
      contributorId: ':example:device',
      status: 1,
      version: 1,
      description: '设备查找契约',
      agentExposure: { operations: {} },
      operations: [{
        operationCode: 'getDevice',
        name: '',
        description: '查询单个设备',
        path: '/devices/{id}',
        method: 'GET',
        transport: 'HTTP',
        implementation: 'GENERATED',
        authenticated: true,
        permission: 'device:read',
        callContext: false,
        parameters: [{
          name: 'id',
          location: 'PATH',
          required: true,
          description: null,
          schema: {
            type: 'integer',
            typeRef: null,
            format: 'int64',
            description: '长整数类型',
            properties: {},
            required: [],
            items: null,
            enumValues: [],
            oneOf: [],
          },
        }],
        requestBody: null,
        responseBody: null,
        responseEnvelope: true,
      }],
    })
    const context = createMetadataDisplayTextContext('contract', draft, '32:0')

    expect(context.rows).toHaveLength(1)
    expect(context.rows[0]?.values).toMatchObject({
      context: '契约 deviceLookup 操作 getDevice 参数 PATH:id 的说明',
      value: '',
    })
    expect(context.rows.some((row) => String(row.values.context).includes('操作 getDevice 的展示名称'))).toBe(false)

    const result = patchResult(context, [['操作 getDevice 参数 PATH:id 的说明', '设备标识']])
    const application = applyMetadataDisplayTextPatches('contract', draft, '32:0', result)
    const translated = normalizeContractDraft(application.draft)

    expect(application.conflicts).toEqual([])
    expect(translated.operations[0]).toMatchObject({
      operationCode: 'getDevice',
      name: '',
      path: '/devices/{id}',
      parameters: [{
        name: 'id',
        location: 'PATH',
        description: '设备标识',
        schema: { type: 'integer', format: 'int64', description: '长整数类型' },
      }],
    })
  })

  it('rejects stale or unknown targets without changing the draft', () => {
    const draft = normalizeModelDraft({
      modelCode: 'device',
      name: 'Device',
      tableName: 'device',
      fields: [],
      queries: [],
      relations: [],
    })
    const context = createMetadataDisplayTextContext('model', draft, 'device:0')
    const result = patchResult(context, [['模型 device 的展示名称', '设备']])
    result.revision = 'stale'
    result.patches.push({
      rowKey: 'unknown',
      columnKey: 'value',
      expectedValue: 'Unknown',
      edits: [{ path: null, match: 'Unknown', replacement: '未知' }],
    })

    const application = applyMetadataDisplayTextPatches('model', draft, 'device:0', result)

    expect(application.applied).toEqual([])
    expect(application.conflicts.map((conflict) => conflict.reason)).toEqual(['revision', 'revision'])
    expect(application.draft).toEqual(draft)
  })

  it('collects authored route DTO schemas while excluding compiler-derived route text', () => {
    const draft = normalizeModelDraft({
      modelCode: 'device',
      name: 'IoT API',
      tableName: 'device',
      remark: '设备元数据',
      fields: [],
      queries: [{ queryCode: 'search', label: '设备查询', logic: 'AND', items: [] }],
      relations: [],
      routeConfig: {
        displayName: 'Device route',
        description: '设备接口',
        properties: [{
          name: 'deviceId',
          type: 'integer',
          required: true,
          description: 'Device identifier',
        }],
        queryFields: [{
          propertyName: 'name',
          parameterName: 'name',
          operator: 'LIKE',
          type: 'string',
          required: false,
          stateCases: [],
          description: 'Device search term',
        }],
        customOperations: [],
        dtoSchemas: [{
          ref: { modelCode: 'device', dtoCode: 'detail' },
          className: 'DeviceDetail',
          description: 'Device schema',
          required: ['payload'],
          properties: {
            payload: {
              type: 'object',
              description: 'Device payload',
              properties: {
                name: {
                  type: 'string',
                  description: 'Device name',
                  properties: {},
                  required: [],
                  items: null,
                  enumValues: [],
                  oneOf: [],
                },
              },
              required: ['name'],
              items: {
                type: 'string',
                description: 'Device item',
                properties: {},
                required: [],
                items: null,
                enumValues: [],
                oneOf: [],
              },
              enumValues: [],
              oneOf: [{
                type: 'string',
                description: 'Device alternative',
                properties: {},
                required: [],
                items: null,
                enumValues: [],
                oneOf: [],
              }],
            },
          },
        }],
      },
    })
    draft.routeConfig.displayName = 'Device route'
    const context = createMetadataDisplayTextContext('model', draft, 'device:0')
    const contexts = context.rows.map((row) => String(row.values.context))

    expect(contexts).toEqual([
      '模型 device DTO Schema device_detail 的说明',
      '模型 device DTO Schema device_detail 属性 payload Schema 的说明',
      '模型 device DTO Schema device_detail 属性 payload Schema 属性 name Schema 的说明',
      '模型 device DTO Schema device_detail 属性 payload Schema 元素 Schema 的说明',
      '模型 device DTO Schema device_detail 属性 payload Schema 候选 1 Schema 的说明',
    ])
    expect(contexts.some((context) => context.includes('路由的展示名称'))).toBe(false)
    expect(contexts.some((context) => context.includes('路由属性 deviceId'))).toBe(false)
    expect(contexts.some((context) => context.includes('路由查询参数 name'))).toBe(false)

    const result = patchResult(context, [
      ['DTO Schema device_detail 的说明', '设备详情结构'],
      ['DTO Schema device_detail 属性 payload Schema 的说明', '设备载荷'],
      ['属性 payload Schema 属性 name Schema 的说明', '设备名称'],
      ['属性 payload Schema 元素 Schema 的说明', '设备项'],
      ['属性 payload Schema 候选 1 Schema 的说明', '设备备选项'],
    ])
    const application = applyMetadataDisplayTextPatches('model', draft, 'device:0', result)
    const translated = normalizeModelDraft(application.draft)

    expect(application.conflicts).toEqual([])
    expect(application.draft).toMatchObject({ routeConfig: { displayName: 'Device route' } })
    expect(translated.routeConfig).toMatchObject({
      properties: [{ name: 'deviceId', type: 'integer', description: 'Device identifier' }],
      queryFields: [{ propertyName: 'name', description: 'Device search term' }],
      dtoSchemas: [{
        ref: { modelCode: 'device', dtoCode: 'detail' },
        className: 'DeviceDetail',
        description: '设备详情结构',
        required: ['payload'],
        properties: {
          payload: {
            type: 'object',
            description: '设备载荷',
            required: ['name'],
            properties: { name: { type: 'string', description: '设备名称' } },
            items: { type: 'string', description: '设备项' },
            oneOf: [{ type: 'string', description: '设备备选项' }],
          },
        },
      }],
    })

    const fallbackDraft = normalizeModelDraft({
      modelCode: 'deviceFallback',
      name: '设备备用查询',
      tableName: 'device_fallback',
      remark: '设备备用查询元数据',
      fields: [],
      queries: [],
      relations: [],
      routeConfig: {
        description: '设备备用接口',
        queryFields: [{
          propertyName: 'name',
          parameterName: 'name',
          operator: 'LIKE',
          type: 'string',
          required: false,
          stateCases: [],
          description: 'Device search term',
        }],
      },
    })
    const fallbackContext = createMetadataDisplayTextContext('model', fallbackDraft, 'deviceFallback:0')

    expect(fallbackContext.rows.some((row) =>
      row.values.context === '模型 deviceFallback 路由查询参数 name 的说明')).toBe(true)
  })

  it('excludes localized text and standalone technical terms from the editable table', () => {
    expect(needsChineseTranslation('设备项目')).toBe(false)
    expect(needsChineseTranslation('IoT API ID')).toBe(false)
    expect(needsChineseTranslation('Catalog Record')).toBe(true)
    expect(needsChineseTranslation('设备 Project')).toBe(true)

    const draft = normalizeModelDraft({
      modelCode: 'device',
      name: '目录记录',
      tableName: 'device',
      fields: [{ fieldCode: 'project', label: '项目', kotlinType: 'String', dbColumn: 'project' }],
      queries: [],
      relations: [],
    })
    const context = createMetadataDisplayTextContext('model', draft, 'device:0')

    expect(context.rows.length).toBeGreaterThan(0)
    expect(context.rows.every((row) => row.values.value === '')).toBe(true)
    expect(context.rows.some((row) => String(row.values.context).includes('展示名称'))).toBe(false)
    expect(context.rows.some((row) => String(row.values.context).endsWith('的注释'))).toBe(false)
  })
})

function patchResult(
  context: MetadataTableContext,
  translations: Array<[context: string, replacement: string]>,
): MetadataTablePatchResult {
  return {
    tableId: context.tableId,
    revision: context.revision,
    patches: translations.map(([targetContext, replacement]) => {
      const row = context.rows.find((candidate) => String(candidate.values.context).includes(targetContext))
      if (!row) {
        throw new Error(`测试缺少展示文本目标: ${targetContext}`)
      }
      const value = row.values.value
      if (typeof value !== 'string') {
        throw new Error(`测试展示文本不是字符串: ${targetContext}`)
      }
      return {
        rowKey: row.rowKey,
        columnKey: 'value',
        expectedValue: value,
        edits: [{ path: null, match: value, replacement }],
      }
    }),
    questions: [],
  }
}
