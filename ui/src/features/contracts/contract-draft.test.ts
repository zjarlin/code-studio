import { describe, expect, it } from 'vitest'

import { createCustomOperation } from '../model-studio/model-draft'
import {
  applyAgentContractDraft,
  applyContractClassName,
  applyContractCode,
  createEmptyContract,
  normalizeContractDraft,
  validateContractDraft,
  updateContractOperations,
} from './contract-draft'

describe('contract draft', () => {
  it('creates an entity-independent contract owner by convention', () => {
    const draft = applyContractCode(createEmptyContract(), 'identityAccess')

    expect(draft.className).toBe('IdentityAccessService')
    expect(draft.path).toBe('/identity-access')
  })

  it('derives the hidden Service code from its Kotlin interface', () => {
    const draft = applyContractClassName(createEmptyContract(), 'OrderSummaryService')

    expect(draft.contractCode).toBe('orderSummary')
    expect(draft.path).toBe('/order-summary')
  })

  it('normalizes operations and validates protocol rules', () => {
    const operation = createCustomOperation(0, '/identity')
    const draft = normalizeContractDraft({
      contractCode: 'identity',
      name: '身份认证',
      packageName: 'com.example.application.identity',
      className: 'IdentityService',
      path: '/identity',
      contributorId: ':example:domain',
      operations: [{ ...operation, transport: 'SSE', responseEnvelope: true }],
    })

    expect(draft.operations[0].transport).toBe('SSE')
    expect(draft.operations[0].responseEnvelope).toBe(false)
    expect(validateContractDraft(draft)).toEqual([])
  })

  it('keeps an entity reference with an empty dto code', () => {
    const operation = createCustomOperation(0, '/identity')
    const draft = normalizeContractDraft({
      operations: [{
        ...operation,
        requestBody: { schema: { typeRef: { modelCode: 'user', dtoCode: '' } } },
      }],
    })

    expect(draft.operations[0].requestBody?.schema.typeRef).toEqual({ modelCode: 'user', dtoCode: '' })
  })

  it('keeps an independent dto reference without a model code', () => {
    const operation = createCustomOperation(0, '/stats')
    const draft = normalizeContractDraft({
      operations: [{
        ...operation,
        responseBody: { schema: { typeRef: { modelCode: null, dtoCode: 'deviceStatusCount' } } },
      }],
    })

    expect(draft.operations[0].responseBody?.schema.typeRef).toEqual({
      modelCode: null,
      dtoCode: 'deviceStatusCount',
    })
  })

  it('requires at least one operation', () => {
    const draft = applyContractCode(createEmptyContract(), 'identity')
    draft.name = '身份认证'

    expect(validateContractDraft(draft)).toContain('领域 Service 至少配置一个操作')
  })

  it('maps an agent contract into the generator draft without replacing persisted identity', () => {
    const current = {
      ...createEmptyContract(),
      id: 9,
      status: 0,
      version: 3,
      contributorId: ':example',
      agentExposure: { operations: { existing: { confirmation: 'REQUIRED' as const } } },
    }
    const draft = applyAgentContractDraft(current, {
      contractCode: 'orderQuery',
      name: '订单查询',
      packageName: 'example.contracts',
      className: 'OrderQueryService',
      path: '/orders',
      contributorId: null,
      description: '订单读取接口',
      operations: [{
        operationCode: 'getOrder',
        name: '查询订单',
        method: 'GET',
        path: '/orders/{id}',
        description: null,
        transport: 'HTTP',
        authenticated: true,
        callContext: true,
        parameters: [{
          name: 'id',
          location: 'PATH',
          required: true,
          description: '订单 ID',
          type: 'integer',
          format: 'int64',
        }],
        requestBody: null,
        responseBody: {
          contentType: 'application/json',
          required: true,
          description: '返回订单详情。',
          fields: [{
            name: 'id',
            type: 'integer',
            format: 'int64',
            arrayItemType: null,
            required: true,
            description: '订单 ID',
            enumValues: [],
          }],
        },
        responseEnvelope: true,
      }],
    })

    expect(draft.id).toBe(9)
    expect(draft.status).toBe(0)
    expect(draft.version).toBe(3)
    expect(draft.contributorId).toBe(':example')
    expect(draft.agentExposure).toEqual(current.agentExposure)
    expect(draft.operations[0].callContext).toBe(true)
    expect(draft.operations[0].parameters[0].schema).toMatchObject({ type: 'integer', format: 'int64' })
    expect(draft.operations[0].responseBody?.description).toBe('返回订单详情。')
    expect(draft.operations[0].responseBody?.schema.required).toEqual(['id'])
  })

  it('validates exposed operations and their permission boundary', () => {
    const operation = {
      ...createCustomOperation(0, '/identity'),
      operationCode: 'approve',
      permission: null,
    }
    const draft = {
      ...applyContractCode(createEmptyContract(), 'identity'),
      name: '身份认证',
      contributorId: ':example:domain',
      operations: [operation],
      agentExposure: { operations: { approve: { confirmation: 'REQUIRED' as const } } },
    }

    expect(validateContractDraft(draft)).toContain('Agent 暴露操作 approve 必须配置权限标识')
    expect(validateContractDraft({
      ...draft,
      operations: [{ ...operation, permission: 'identity:approve' }],
    })).not.toContain('Agent 暴露操作 approve 必须配置权限标识')
  })

  it('migrates exposure when an operation code changes and removes unsupported exposure', () => {
    const operation = { ...createCustomOperation(0, '/identity'), operationCode: 'approve' }
    const draft = {
      ...createEmptyContract(),
      operations: [operation],
      agentExposure: { operations: { approve: { confirmation: 'REQUIRED' as const } } },
    }

    const renamed = updateContractOperations(draft, [{ ...operation, operationCode: 'confirm' }])
    expect(renamed.agentExposure.operations).toEqual({
      confirm: { confirmation: 'REQUIRED' },
    })

    const external = updateContractOperations(renamed, [{
      ...renamed.operations[0],
      implementation: 'EXISTING_REST',
    }])
    expect(external.agentExposure.operations).toEqual({})
  })

  it('removes only the deleted operation exposure', () => {
    const first = { ...createCustomOperation(0, '/identity'), operationCode: 'first' }
    const second = { ...createCustomOperation(1, '/identity'), operationCode: 'second' }
    const draft = {
      ...createEmptyContract(),
      operations: [first, second],
      agentExposure: {
        operations: {
          first: { confirmation: 'REQUIRED' as const },
          second: { confirmation: 'AUTO' as const },
        },
      },
    }

    expect(updateContractOperations(draft, [second]).agentExposure.operations).toEqual({
      second: { confirmation: 'AUTO' },
    })
  })

  it('only allows call context on authenticated operations', () => {
    const operation = {
      ...createCustomOperation(0, '/identity'),
      authenticated: false,
      callContext: true,
    }
    const draft = normalizeContractDraft({
      contractCode: 'identity',
      name: '身份认证',
      packageName: 'com.example.application.identity',
      className: 'IdentityService',
      path: '/identity',
      contributorId: ':example:domain',
      operations: [operation],
    })

    expect(validateContractDraft(draft)).toContain('Controller 操作 1 只有认证操作才能传递调用上下文')
  })
})
