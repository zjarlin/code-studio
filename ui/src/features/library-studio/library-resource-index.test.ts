import { describe, expect, it } from 'vitest'

import type { ConventionFileSummary, LowcodeDtoResourceSummary, LowcodeModelSummary, LsiLibraryDefinition } from '@/types'

import {
  createLibraryResourceIndex,
  libraryResourceCounts,
  searchLibraryResources,
} from './library-resource-index'

const library: LsiLibraryDefinition = {
  id: 9,
  code: 'example-foundation',
  displayName: '系统基础',
  version: 1,
  status: 1,
  features: [{ id: 91, libraryId: 9, parentId: null, featureCode: 'system', name: '系统基础' }],
  spec: {
    schemaVersion: 3,
    contributorId: 'example-foundation',
    packagePrefix: 'com.example.application',
    scanPackage: 'com.example.application.foundation',
    kind: 'BUILT_IN',
    runtimeDependencies: [],
    supportedIdentityModes: ['LOCAL'],
    applicationSelectable: true,
    dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
  },
}

const model: LowcodeModelSummary = {
  id: 3,
  featureId: 91,
  modelCode: 'systemConfig',
  modelType: 'ENTITY',
  name: '系统配置',
  packageName: 'com.example.application.foundation.config',
  className: 'SystemConfig',
  status: 1,
  contributorId: 'example-foundation',
  version: 1,
  routeConfig: {
    className: 'SystemConfig',
    qualifiedName: 'com.example.application.foundation.generated.entity.SystemConfig',
    path: '/infra/config',
    aliasPaths: [],
    fetchPaths: [],
    excludePaths: [],
    enabledOperations: ['PAGE'],
    customOperations: [{
      operationCode: 'getValueByKey',
      name: '按键读取配置值',
      path: '/infra/config/get-value-by-key',
      method: 'GET',
      transport: 'HTTP',
    }],
  },
}

const catalogLibrary: LsiLibraryDefinition = {
  ...library,
  id: 5,
  code: 'catalog',
  displayName: '物联网',
  features: [{ id: 51, libraryId: 5, parentId: null, featureCode: 'thing', name: '物模型' }],
  spec: {
    ...library.spec,
    contributorId: 'catalog',
    packagePrefix: 'com.example.application.catalog',
    scanPackage: 'com.example.application.catalog',
  },
}

const thingModel: LowcodeModelSummary = {
  ...model,
  id: 31,
  featureId: 51,
  modelCode: 'catalogSchemaModel',
  name: '产品目录模型',
  className: 'CatalogSchemaModel',
  fields: [
    {
      fieldCode: 'propertySpec',
      label: '属性配置',
      kotlinType: 'com.example.application.catalog.thing.generated.dto.CatalogSchemaPropertySpec',
      required: false,
    },
    {
      fieldCode: 'eventSpec',
      label: '事件配置',
      kotlinType: 'com.example.application.catalog.thing.generated.dto.CatalogSchemaEventSpec',
      required: false,
    },
  ],
}

const thingDtos: LowcodeDtoResourceSummary[] = [
  ['catalogSchemaPropertySpec', '物模型属性配置', 'CatalogSchemaPropertySpec'],
  ['catalogSchemaEventSpec', '物模型事件配置', 'CatalogSchemaEventSpec'],
].map(([dtoCode, name, className], index) => ({
  id: 41 + index,
  featureId: 51,
  dtoCode,
  name,
  packageName: 'com.example.application.catalog.thing',
  className,
  kind: 'STRUCTURE',
  selectionMode: 'EXPLICIT',
  excludedPaths: [],
  fields: [],
  status: 1,
  version: 1,
}))

const conventionFile: ConventionFileSummary = {
  id: 61,
  featureId: 51,
  fileCode: 'catalogSync',
  name: '目录同步任务',
  className: 'CatalogSyncJob',
  kind: 'SCHEDULED_JOB',
  status: 1,
  packageName: 'com.example.application.catalog.thing.job',
  contributorId: 'catalog',
}

describe('library resource index', () => {
  it('uses feature ids and indexes existing REST paths', () => {
    const entries = createLibraryResourceIndex([library], [model], [], [])

    expect(libraryResourceCounts(entries).models).toBe(1)
    expect(searchLibraryResources(entries, '/infra/config/get-value-by-key')).toEqual([
      expect.objectContaining({
        libraryId: 9,
        featureId: 91,
        featureCode: 'system',
        tab: 'models',
        label: '按键读取配置值',
        detail: 'GET /infra/config/get-value-by-key',
      }),
    ])
  })

  it('finds generated DTOs through the model fields that reference them', () => {
    const entries = createLibraryResourceIndex([catalogLibrary], [thingModel], thingDtos, [])
    const matches = searchLibraryResources(entries, 'CatalogSchemaModelSpecs')

    expect(matches).toEqual(expect.arrayContaining([
      expect.objectContaining({ libraryId: 5, tab: 'models', resourceKey: 'model:catalogSchemaModel' }),
      expect.objectContaining({ libraryId: 5, tab: 'dtos', resourceKey: 'dto:catalogSchemaPropertySpec' }),
      expect.objectContaining({ libraryId: 5, tab: 'dtos', resourceKey: 'dto:catalogSchemaEventSpec' }),
    ]))
  })

  it('indexes Service and scheduled job convention files without operations', () => {
    const entries = createLibraryResourceIndex([catalogLibrary], [], [], [conventionFile])

    expect(libraryResourceCounts(entries).services).toBe(1)
    expect(searchLibraryResources(entries, 'CatalogSyncJob')).toEqual([
      expect.objectContaining({
        tab: 'services',
        resourceKey: 'convention-file:61',
        detail: '定时任务 · CatalogSyncJob',
      }),
    ])
  })
})
