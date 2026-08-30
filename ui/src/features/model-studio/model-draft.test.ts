import { describe, expect, it } from 'vitest'

import {
  applyAgentModelDraft,
  applyFieldCode,
  applyModelFeatureLocation,
  applyModelTableName,
  applyQueryLogic,
  applyQueryOperator,
  applyRelationCode,
  applyRelationKind,
  createField,
  createCustomOperation,
  createQuery,
  createRelation,
  diffModelDraft,
  moveItem,
  normalizeModelDraft,
  queryableModelProperties,
  validateModelDraft,
} from './model-draft'

describe('model draft', () => {
  it('derives generated location from the owning feature', () => {
    const draft = normalizeModelDraft({
      featureId: 1,
      packageName: '',
      tableName: 'catalog_product_category',
      contributorId: null,
      routeConfig: { packageName: '' },
    })

    const located = applyModelFeatureLocation(draft, {
      featureId: 21,
      packageName: 'com.example.application.catalog.product',
      contributorId: 'example.catalog',
    })

    expect(located).toMatchObject({
      featureId: 21,
      packageName: 'com.example.application.catalog.product',
      contributorId: 'example.catalog',
      routeConfig: { packageName: 'com.example.application.catalog.product' },
    })
  })

  it('does not require generated location fields in a model command', () => {
    const draft = normalizeModelDraft({
      modelCode: 'catalogProductCategory',
      name: '产品分类',
      tableName: 'catalog_product_category',
      routeConfig: { path: '/catalog/product-category' },
    })

    expect(validateModelDraft(draft)).toEqual([])
  })

  it('defaults write policies to writable and preserves explicit restrictions', () => {
    expect(createField(0)).toMatchObject({ createWritable: true, updateWritable: true })
    expect(createRelation(0)).toMatchObject({ createWritable: true, updateWritable: true })

    const draft = normalizeModelDraft({
      fields: [{ fieldCode: 'code', createWritable: false, updateWritable: true }],
      relations: [{ relationCode: 'owner', createWritable: true, updateWritable: false }],
    })

    expect(draft.fields[0]).toMatchObject({ createWritable: false, updateWritable: true })
    expect(draft.relations[0]).toMatchObject({ createWritable: true, updateWritable: false })
  })

  it('reports recently changed fields and sections after an assistant result is applied', () => {
    const previous = normalizeModelDraft({
      fields: [
        { fieldCode: 'userName', label: 'userName', dbColumn: 'user_name', kotlinType: 'String' },
        { fieldCode: 'status', label: '状态', dbColumn: 'status', kotlinType: 'Int' },
      ],
    })
    const current = normalizeModelDraft({
      ...previous,
      fields: previous.fields.map((field) => field.fieldCode === 'userName'
        ? { ...field, label: '用户名' }
        : field),
    })

    expect(diffModelDraft(previous, current)).toEqual({
      sections: ['fields'],
      fieldKeys: ['code:userName'],
    })
  })

  it('normalizes an OpenAPI-shaped empty model', () => {
    const draft = normalizeModelDraft({ modelType: 'ENTITY', status: 1, version: 1 })

    expect(draft.modelCode).toBe('')
    expect(draft.routeConfig.enabledOperations).toHaveLength(9)
    expect(draft.routeConfig.agentExposure).toEqual({ operations: {} })
    expect(draft.routeConfig.defaultOrders).toEqual([])
    expect(draft.fields).toEqual([])
    expect(draft.queries).toEqual([])
    expect(draft.relations).toEqual([])
    expect(draft.entityConfig.sourceMode).toBe('GENERATED')
    expect(draft.entityConfig.sourceQualifiedName).toBeNull()
    expect(draft.entityConfig.baseModels).toEqual(['BASE_ENTITY'])
    expect(draft.entityConfig.inheritedRelationCodes).toEqual([])
    expect(draft.entityConfig.formulaProperties).toEqual([])
    expect(draft.entityConfig.transientProperties).toEqual([])
    expect(draft.entityConfig.inheritanceRoot).toBeNull()
    expect(draft.entityConfig.inheritanceSubtype).toBeNull()
  })

  it('preserves ordered default page sorting', () => {
    const draft = normalizeModelDraft({
      routeConfig: {
        defaultOrders: [
          { propertyName: 'sortOrder', direction: 'ASC' },
          { propertyName: 'id', direction: 'DESC' },
        ],
      },
    })

    expect(draft.routeConfig.defaultOrders).toEqual([
      { propertyName: 'sortOrder', direction: 'ASC' },
      { propertyName: 'id', direction: 'DESC' },
    ])
  })

  it('normalizes agent exposure and custom operation permission', () => {
    const draft = normalizeModelDraft({
      routeConfig: {
        agentExposure: {
          operations: {
            PAGE: { confirmation: 'AUTO' },
            DELETE: { confirmation: 'UNKNOWN' },
          },
        },
        customOperations: [{
          operationCode: 'approve',
          permission: 'example:approve',
        }],
      },
    })

    expect(draft.routeConfig.agentExposure.operations).toEqual({
      PAGE: { confirmation: 'AUTO' },
      DELETE: { confirmation: 'REQUIRED' },
    })
    expect(draft.routeConfig.customOperations[0].permission).toBe('example:approve')
  })

  it('normalizes Jimmer table inheritance metadata', () => {
    const root = normalizeModelDraft({
      entityConfig: {
        inheritanceRoot: {
          strategy: 'JOINED',
          discriminatorField: 'workOrderType',
          joinedTableDissociateAction: 'LAX',
        },
      },
    })
    const subtype = normalizeModelDraft({
      entityConfig: {
        baseMode: 'INHERITED',
        inheritanceSubtype: {
          parentModelCode: 'workOrder',
          discriminatorValue: 'MAINTENANCE',
        },
      },
    })

    expect(root.entityConfig.inheritanceRoot).toMatchObject({
      strategy: 'JOINED',
      discriminatorField: 'workOrderType',
      instantiability: 'ABSTRACT',
      joinedTableDissociateAction: 'LAX',
    })
    expect(subtype.entityConfig.inheritanceSubtype).toMatchObject({
      parentModelCode: 'workOrder',
      discriminatorValue: 'MAINTENANCE',
      instantiability: 'AUTO',
    })
  })

  it('normalizes entity type references', () => {
    const draft = normalizeModelDraft({
      routeConfig: {
        customOperations: [{
          operationCode: 'save',
          name: '保存',
          path: '/save',
          requestBody: { schema: { typeRef: { modelCode: 'user', dtoCode: '' } } },
        }],
      },
    })

    expect(draft.routeConfig.customOperations[0].requestBody?.schema.typeRef).toEqual({
      modelCode: 'user',
      dtoCode: '',
    })
    expect(draft.routeConfig.customOperations[0].implementation).toBe('GENERATED')
  })

  it('normalizes named route DTO schemas and their nested API schemas', () => {
    const draft = normalizeModelDraft({
      routeConfig: {
        dtoSchemas: [{
          ref: { modelCode: 'device', dtoCode: 'detail' },
          className: 'DeviceDetail',
          description: '设备详情',
          required: ['payload'],
          properties: {
            payload: {
              type: 'array',
              description: '设备载荷',
              items: { type: 'string', description: '载荷项' },
            },
          },
        }],
      },
    })

    expect(draft.routeConfig.dtoSchemas).toEqual([expect.objectContaining({
      ref: { modelCode: 'device', dtoCode: 'detail' },
      className: 'DeviceDetail',
      description: '设备详情',
      required: ['payload'],
      properties: {
        payload: expect.objectContaining({
          type: 'array',
          description: '设备载荷',
          properties: {},
          required: [],
          items: expect.objectContaining({ type: 'string', description: '载荷项' }),
          enumValues: [],
          oneOf: [],
        }),
      },
    })])
  })

  it('preserves existing REST operation ownership', () => {
    const draft = normalizeModelDraft({
      routeConfig: {
        customOperations: [{
          operationCode: 'existingSave',
          name: '既有保存接口',
          path: '/save',
          implementation: 'EXISTING_REST',
        }],
      },
    })

    expect(draft.routeConfig.customOperations[0].implementation).toBe('EXISTING_REST')
  })

  it('normalizes explicit enum storage and rejects unknown strategies', () => {
    const draft = normalizeModelDraft({
      fields: [
        { fieldCode: 'status', enumStorage: 'ORDINAL' },
        { fieldCode: 'legacyStatus', enumStorage: 'UNKNOWN' },
      ],
    })

    expect(draft.fields[0].enumStorage).toBe('ORDINAL')
    expect(draft.fields[1].enumStorage).toBeNull()
    expect(createField(0).enumStorage).toBeNull()
  })

  it('normalizes composable base models and keeps custom inheritance independent', () => {
    const composed = normalizeModelDraft({
      entityConfig: { baseModels: ['BASE_ENTITY', 'TENANT', 'VERSION', 'UNKNOWN'] },
    })
    const inherited = normalizeModelDraft({
      entityConfig: {
        baseMode: 'INHERITED',
        superTypes: ['example.Record'],
        inheritedRelationCodes: ['parent', 'children'],
      },
    })

    expect(composed.entityConfig.baseModels).toEqual(['BASE_ENTITY', 'TENANT', 'VERSION'])
    expect(inherited.entityConfig.baseModels).toEqual([])
    expect(inherited.entityConfig.superTypes).toEqual(['example.Record'])
    expect(inherited.entityConfig.inheritedRelationCodes).toEqual(['parent', 'children'])
  })

  it('normalizes complex transient resolver properties', () => {
    const draft = normalizeModelDraft({
      entityConfig: {
        transientProperties: [{
          propertyCode: 'latest_result',
          label: '最新结果',
          kotlinType: 'example.Result',
          kind: 'RESOLVER',
          resolverValueType: 'Long?',
          nullable: true,
        }],
      },
    })

    expect(draft.entityConfig.transientProperties).toEqual([
      expect.objectContaining({
        propertyCode: 'latest_result',
        kind: 'RESOLVER',
        resolverValueType: 'Long?',
        nullable: true,
      }),
    ])
  })

  it('normalizes Jimmer formula properties as entity metadata', () => {
    const draft = normalizeModelDraft({
      entityConfig: {
        formulaProperties: [{
          propertyCode: 'display_name',
          label: '显示名称',
          kotlinType: 'String',
          kind: 'KOTLIN',
          expression: 'firstName + lastName',
          dependencies: ['firstName', 'lastName'],
          nullable: false,
        }],
      },
    })

    expect(draft.entityConfig.formulaProperties).toEqual([
      expect.objectContaining({
        propertyCode: 'display_name',
        kind: 'KOTLIN',
        dependencies: ['firstName', 'lastName'],
        nullable: false,
      }),
    ])
  })

  it('exposes every generated property kind to query metadata', () => {
    const draft = normalizeModelDraft({
      fields: [{ fieldCode: 'name', label: '名称', kotlinType: 'String', required: false }],
      entityConfig: {
        formulaProperties: [{
          propertyCode: 'leafFlag',
          label: '是否叶子节点',
          kotlinType: 'Boolean',
          kind: 'KOTLIN',
          expression: 'children.isEmpty()',
          dependencies: ['children'],
          nullable: false,
        }],
        transientProperties: [{
          propertyCode: 'summary',
          label: '摘要',
          kotlinType: 'String',
          kind: 'DRAFT',
          nullable: true,
        }],
      },
      relations: [{
        relationCode: 'parent',
        label: '上级地区',
        relationType: 'MANY_TO_ONE',
        required: false,
      }],
    })

    const properties = Object.fromEntries(queryableModelProperties(draft).map((property) => [property.code, property]))

    expect(properties.id).toMatchObject({ kotlinType: 'Long', required: true })
    expect(properties.name).toMatchObject({ kotlinType: 'String', required: false })
    expect(properties.leafFlag).toMatchObject({ kotlinType: 'Boolean', required: true })
    expect(properties.summary).toMatchObject({ kotlinType: 'String', required: false })
    expect(properties.parentId).toMatchObject({ kotlinType: 'Long', required: false })
  })

  it('resolves the complete parent property chain for inheritance subtypes', () => {
    const root = normalizeModelDraft({
      modelCode: 'workOrder',
      contributorId: ':example:work-order',
      fields: [{ fieldCode: 'title', label: 'Title', kotlinType: 'String', required: true }],
      entityConfig: {
        inheritanceRoot: {
          strategy: 'JOINED',
          discriminatorField: 'workOrderType',
          instantiability: 'ABSTRACT',
          discriminatorValue: null,
          joinedTableDissociateAction: 'DELETE',
        },
        formulaProperties: [{
          propertyCode: 'titleLength',
          label: 'Title length',
          kotlinType: 'Int',
          kind: 'KOTLIN',
          expression: 'title.length',
          dependencies: ['title'],
          nullable: false,
        }],
      },
      relations: [{
        relationCode: 'owner',
        label: 'Owner',
        relationType: 'MANY_TO_ONE',
        required: false,
      }],
    })
    const subtype = normalizeModelDraft({
      modelCode: 'maintenanceWorkOrder',
      contributorId: ':example:work-order',
      fields: [{ fieldCode: 'maintenancePlan', label: 'Plan', kotlinType: 'String', required: false }],
      entityConfig: {
        baseMode: 'INHERITED',
        baseModels: [],
        inheritanceSubtype: {
          parentModelCode: 'workOrder',
          discriminatorValue: 'MAINTENANCE',
          instantiability: 'AUTO',
        },
      },
    })

    const properties = Object.fromEntries(queryableModelProperties(subtype, [{ ...root, id: 1 }])
      .map((property) => [property.code, property]))

    expect(properties.id).toMatchObject({ kotlinType: 'Long', required: true })
    expect(properties.title).toMatchObject({ kotlinType: 'String', required: true })
    expect(properties.titleLength).toMatchObject({ kotlinType: 'Int', required: true })
    expect(properties.ownerId).toMatchObject({ kotlinType: 'Long', required: false })
    expect(properties.maintenancePlan).toMatchObject({ kotlinType: 'String', required: false })
  })

  it('keeps route presentation settings while entity metadata owns the source', () => {
    const draft = normalizeModelDraft({
      modelCode: 'accountRecord',
      name: '系统用户',
      packageName: 'com.example.application.identity',
      className: 'AccountRecord',
      tableName: 'account_records',
      contributorId: ':example:domain',
      routeConfig: {
        path: '/system/user',
        enabledOperations: ['GET', 'UPDATE'],
        properties: [{ name: 'username', type: 'string', required: true }],
        queryFields: [],
        tree: {},
        excel: {},
      },
    })

    expect(draft.className).toBe('AccountRecords')
    expect(draft.routeConfig.qualifiedName).toBe('com.example.application.identity.generated.AccountRecords')
    expect(draft.routeConfig.properties[0]).toMatchObject({ name: 'username', required: true })
    expect(draft.routeConfig.tree).toBeNull()
    expect(draft.routeConfig.excel).toBeNull()
    expect(validateModelDraft(draft)).toEqual([])
  })

  it('derives the Kotlin classifier strictly from the database table name', () => {
    const draft = normalizeModelDraft({ modelCode: '', className: '', tableName: '' })
    const named = { ...draft, name: '维修工单' }
    const derived = applyModelTableName(named, 'biz_work_order')

    expect(named).toMatchObject({ name: '维修工单', modelCode: '', className: '', tableName: '' })
    expect(derived.modelCode).toBe('bizWorkOrder')
    expect(derived.className).toBe('BizWorkOrder')
    expect(derived.tableName).toBe('biz_work_order')
    expect(derived.routeConfig.path).toBe('/biz-work-order')

    const field = applyFieldCode(createField(0), 'createdTime')
    expect(field.dbColumn).toBe('created_time')
  })

  it('preserves a customized hidden model code when the database table changes', () => {
    const draft = applyModelTableName(normalizeModelDraft({
      modelCode: 'maintenance',
      tableName: 'maintenance_order',
    }), 'maintenance_work_order')

    expect(draft.className).toBe('MaintenanceWorkOrder')
    expect(draft.modelCode).toBe('maintenance')
  })

  it('preserves the hidden identity of a persisted model when its table changes', () => {
    const draft = applyModelTableName(normalizeModelDraft({
      id: 9,
      modelCode: 'maintenanceOrder',
      tableName: 'maintenance_order',
    }), 'maintenance_work_order')

    expect(draft.className).toBe('MaintenanceWorkOrder')
    expect(draft.modelCode).toBe('maintenanceOrder')
  })

  it('keeps a customized model code while the Kotlin name follows the database table', () => {
    const draft = normalizeModelDraft({
      name: '维修工单',
      modelCode: 'custom_order',
      className: 'CustomOrder',
      tableName: 'biz_work_order',
    })

    expect({ ...draft, name: '维修任务' }).toMatchObject({
      name: '维修任务',
      modelCode: 'custom_order',
      className: 'BizWorkOrder',
      tableName: 'biz_work_order',
    })
  })

  it('maps agent metadata into a generator draft while preserving host settings', () => {
    const current = normalizeModelDraft({
      id: 9,
      modelCode: 'oldModel',
      name: '原模型',
      packageName: 'example.old',
      className: 'OldModel',
      tableName: 'old_model',
      status: 0,
      version: 4,
      contributorId: ':example:domain',
      routeConfig: {
        path: '/old-model',
        enabledOperations: ['GET'],
      },
      fields: [{
        id: 91,
        fieldCode: 'name',
        label: '原名称',
        kotlinType: 'String',
        dbColumn: 'name',
      }],
    })

    const draft = applyAgentModelDraft(current, {
      modelCode: 'workOrder',
      name: '工单',
      packageName: 'example.work',
      className: 'WorkOrder',
      tableName: 'work_order',
      modelType: 'ENTITY',
      contributorId: null,
      fields: [{
        fieldCode: 'name',
        label: '名称',
        kotlinType: 'String',
        dbColumn: 'name',
        required: true,
        listVisible: true,
        formVisible: true,
        formControl: 'input',
        dictCode: null,
        defaultValue: null,
        remark: null,
      }],
      queries: [{
        queryCode: 'byName',
        label: '按名称查询',
        logic: 'AND',
        conditions: [{ fieldCode: 'name', operator: 'LIKE', valueType: 'SINGLE', paramName: null }],
      }],
      relations: [],
    })

    expect(draft).toMatchObject({
      id: 9,
      modelCode: 'workOrder',
      status: 0,
      version: 4,
      contributorId: ':example:domain',
    })
    expect(draft.routeConfig).toMatchObject({ path: '/old-model' })
    expect(draft.fields[0]).toMatchObject({ id: 91, orderNo: 1, fieldCode: 'name' })
    expect(draft.queries[0]).toMatchObject({ orderNo: 1, queryCode: 'byName' })
    expect(draft.queries[0].items[0]).toMatchObject({ orderNo: 1, fieldCode: 'name', operator: 'LIKE' })
  })

  it('preserves existing child identities and unsupported relation properties during assistant edits', () => {
    const current = normalizeModelDraft({
      fields: [{
        id: 11,
        fieldCode: 'userName',
        label: 'userName',
        kotlinType: 'String',
        dbColumn: 'user_name',
        createWritable: false,
        updateWritable: false,
      }],
      queries: [{
        id: 12,
        orderNo: 1,
        queryCode: 'byUserName',
        label: '按用户名查询',
        logic: 'AND',
        items: [{
          id: 13,
          orderNo: 1,
          fieldCode: 'userName',
          operator: 'LIKE',
          valueType: 'SINGLE',
        }],
      }],
      relations: [{
        id: 14,
        orderNo: 1,
        relationCode: 'department',
        label: '部门',
        relationType: 'MANY_TO_ONE',
        targetModelId: 15,
        targetModelCode: 'department',
        joinColumn: 'department_id',
        dissociateAction: 'LAX',
        createWritable: false,
        updateWritable: false,
      }],
    })

    const draft = applyAgentModelDraft(current, {
      fields: [{
        orderNo: 1,
        fieldCode: 'userName',
        label: '用户名',
        kotlinType: 'String',
        dbColumn: 'user_name',
      }],
      queries: [{
        orderNo: 1,
        queryCode: 'byUserName',
        label: '按用户名查询',
        logic: 'AND',
        items: [{
          orderNo: 1,
          fieldCode: 'userName',
          operator: 'LIKE',
          valueType: 'SINGLE',
        }],
      }],
      relations: [{
        orderNo: 1,
        relationCode: 'department',
        label: '部门',
        relationType: 'MANY_TO_ONE',
        targetModelCode: 'department',
        joinColumn: 'department_id',
      }],
    })

    expect(draft.fields[0]).toMatchObject({
      id: 11,
      label: '用户名',
      createWritable: false,
      updateWritable: false,
    })
    expect(draft.queries[0]).toMatchObject({ id: 12 })
    expect(draft.queries[0].items[0]).toMatchObject({ id: 13 })
    expect(draft.relations[0]).toMatchObject({
      id: 14,
      targetModelId: 15,
      dissociateAction: 'LAX',
      createWritable: false,
      updateWritable: false,
    })
  })

  it('normalizes custom contracts and validates protocol invariants', () => {
    const operation = {
      ...createCustomOperation(0, '/work-order'),
      operationCode: 'watchProgress',
      name: '监听进度',
      transport: 'SSE' as const,
      method: 'GET' as const,
      responseEnvelope: false,
      responseBody: {
        contentType: 'text/event-stream',
        required: true,
        schema: { type: 'string' },
      },
    }
    const draft = normalizeModelDraft({
      modelCode: 'workOrder',
      name: '工单',
      packageName: 'site.addzero.work',
      className: 'WorkOrder',
      tableName: 'work_order',
      contributorId: ':example:domain',
      routeConfig: {
        path: '/work-order',
        customOperations: [operation],
      },
    })

    expect(draft.routeConfig.customOperations[0]).toMatchObject({
      operationCode: 'watchProgress',
      transport: 'SSE',
      responseEnvelope: false,
    })
    expect(draft.routeConfig.customOperations[0].responseBody?.schema).toMatchObject({
      type: 'string',
      properties: {},
      oneOf: [],
    })
    expect(validateModelDraft(draft)).toEqual([])
  })

  it('keeps order numbers contiguous while moving items', () => {
    const fields = [createField(0), createField(1), createField(2)].map((field, index) => ({
      ...field,
      fieldCode: `field${index}`,
    }))

    const reordered = moveItem(fields, 2, 0)

    expect(reordered.map((field) => field.fieldCode)).toEqual(['field2', 'field0', 'field1'])
    expect(reordered.map((field) => field.orderNo)).toEqual([1, 2, 3])
  })

  it('applies query rules for OR, collections, and temporal ranges', () => {
    const query = createQuery(0, 'name')
    const orQuery = applyQueryLogic(query, 'OR')
    const inCondition = applyQueryOperator(query.items[0], 'IN', 'Long')
    const notInCondition = applyQueryOperator(query.items[0], 'NOT_IN', 'Long')
    const betweenCondition = applyQueryOperator(query.items[0], 'BETWEEN', 'LocalDateTime')
    const timeRangeCondition = applyQueryOperator(query.items[0], 'TIME_RANGE', 'LocalDateTime')
    const zeroStateCondition = applyQueryOperator(query.items[0], 'ZERO_STATE', 'Int')

    expect(orQuery.items[0]).toMatchObject({ operator: 'LIKE', valueType: 'SINGLE', paramName: 'keyword' })
    expect(inCondition.valueType).toBe('MULTIPLE')
    expect(notInCondition.valueType).toBe('MULTIPLE')
    expect(betweenCondition.valueType).toBe('DATETIME_RANGE')
    expect(timeRangeCondition.valueType).toBe('DATETIME_RANGE')
    expect(zeroStateCondition.valueType).toBe('SINGLE')
  })

  it('clears incompatible relation mapping when kind changes', () => {
    const relation = {
      ...createRelation(0),
      relationCode: 'owner',
      mappedBy: 'orders',
      joinTable: 'work_order_owner',
    }
    const manyToOne = applyRelationKind(relation, 'MANY_TO_ONE')
    const oneToMany = applyRelationKind(manyToOne, 'ONE_TO_MANY')
    const renamed = applyRelationCode(oneToMany, 'workOrders')

    expect(manyToOne.joinColumn).toBe('owner_id')
    expect(manyToOne.mappedBy).toBeNull()
    expect(manyToOne.joinTable).toBeNull()
    expect(oneToMany.joinColumn).toBeNull()
    expect(renamed.joinColumn).toBeNull()
  })

  it('reports incomplete nested configuration before calling the backend', () => {
    const draft = normalizeModelDraft({
      modelCode: 'workOrder',
      name: '工单',
      packageName: 'site.addzero.work',
      className: 'WorkOrder',
      tableName: 'work_order',
      contributorId: ':example:domain',
      fields: [createField(0)],
      queries: [createQuery(0)],
      relations: [createRelation(0)],
    })

    expect(validateModelDraft(draft)).toEqual([
      '字段 1 名称不能为空',
      '字段 1 属性名不能为空',
      '字段 1 数据库列名不能为空',
      '查询 1 名称不能为空',
      '查询 1 方法名不能为空',
      '查询 1 至少需要一个条件',
      '关联 1 名称不能为空',
      '关联 1 属性名不能为空',
      '关联 1 需要目标模型',
      '关联 1 JoinColumn不能为空',
    ])
  })
})
