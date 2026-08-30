import type {
  ConventionFileSummary,
  LowcodeDtoResourceSummary,
  LowcodeModelDraft,
  LowcodeModelSummary,
  LsiLibraryDefinition,
  LsiLibraryFeature,
} from '@/types'
import { featurePackageName } from './library-draft'

export type LibraryDataTab = 'models' | 'queries' | 'dtos' | 'services'

export interface LibraryIndexedResource {
  libraryId: number | string
  featureId: number | string
  featureCode: string
  featurePackageName: string
  tab: LibraryDataTab
  resourceKey: string
  label: string
  detail: string
  searchText: string
}

export interface LibraryResourceCounts {
  models: number
  queries: number
  dtos: number
  services: number
}

export function createLibraryResourceIndex(
  libraries: LsiLibraryDefinition[],
  models: LowcodeModelSummary[],
  dtos: LowcodeDtoResourceSummary[],
  services: ConventionFileSummary[],
  modelDetails: LowcodeModelDraft[] = [],
): LibraryIndexedResource[] {
  const entries: LibraryIndexedResource[] = []
  for (const library of libraries) {
    models.forEach((model) => appendModel(entries, library, model))
    dtos.forEach((dto) => appendDto(entries, library, dto, models))
    services.forEach((service) => appendService(entries, library, service))
    modelDetails.forEach((model) => appendQueries(entries, library, model))
  }
  return entries.sort((left, right) =>
    `${String(left.libraryId)}:${left.tab}:${left.detail}`.localeCompare(
      `${String(right.libraryId)}:${right.tab}:${right.detail}`,
    ))
}

export function libraryResourceEntries(
  entries: LibraryIndexedResource[],
  library: LsiLibraryDefinition,
  selectedFeatureId?: number | string,
): LibraryIndexedResource[] {
  return entries.filter((entry) =>
    String(entry.libraryId) === String(library.id)
    && (selectedFeatureId == null || String(entry.featureId) === String(selectedFeatureId)),
  )
}

export function libraryResourceCounts(entries: LibraryIndexedResource[]): LibraryResourceCounts {
  const keys = { models: new Set<string>(), queries: new Set<string>(), dtos: new Set<string>(), services: new Set<string>() }
  entries.forEach((entry) => keys[entry.tab].add(entry.resourceKey))
  return { models: keys.models.size, queries: keys.queries.size, dtos: keys.dtos.size, services: keys.services.size }
}

export function searchLibraryResources(entries: LibraryIndexedResource[], keyword: string): LibraryIndexedResource[] {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) return []
  const queryTokens = searchTokens(keyword)
  return entries.filter((entry) => {
    if (entry.searchText.toLowerCase().includes(normalized)) return true
    if (queryTokens.length < 2) return false
    const entryTokens = searchTokens(entry.searchText)
    return queryTokens.every((queryToken) => entryTokens.some((entryToken) =>
      entryToken === queryToken || entryToken.startsWith(queryToken)))
  })
}

export function resourceBelongsToFeatureScope(
  featureId: number | string | undefined,
  features: LsiLibraryFeature[],
  selectedFeatureId?: number | string,
): boolean {
  if (featureId == null || !features.some((feature) => String(feature.id) === String(featureId))) return false
  return selectedFeatureId == null || String(featureId) === String(selectedFeatureId)
}

function appendModel(entries: LibraryIndexedResource[], library: LsiLibraryDefinition, model: LowcodeModelSummary): void {
  const feature = resourceFeature(library, model.featureId)
  if (!feature) return
  append(entries, library, feature, 'models', `model:${model.modelCode}`, model.name, model.className ?? '实体模型', [
    model.modelCode, model.name, model.remark, model.packageName, model.className,
    model.routeConfig?.path, ...(model.routeConfig?.aliasPaths ?? []),
    ...(model.fields ?? []).flatMap((field) => [field.fieldCode, field.label, field.kotlinType]),
    ...(model.relations ?? []).flatMap((relation) => [relation.relationCode, relation.label, relation.relationType]),
  ])
  model.routeConfig?.customOperations?.forEach((operation) => append(
    entries, library, feature, 'models', `model:${model.modelCode}`, operation.name,
    `${operation.method} ${operation.path}`,
    [operation.operationCode, operation.name, operation.description, operation.path, operation.method, model.modelCode],
  ))
}

function appendDto(
  entries: LibraryIndexedResource[],
  library: LsiLibraryDefinition,
  dto: LowcodeDtoResourceSummary,
  models: LowcodeModelSummary[],
): void {
  const feature = resourceFeature(library, dto.featureId)
  if (!feature) return
  append(entries, library, feature, 'dtos', `dto:${dto.dtoCode}`, dto.name, dto.className,
    [dto.dtoCode, dto.name, dto.description, dto.packageName, dto.className, ...dtoReferenceSearchValues(dto, models)])
}

function appendService(entries: LibraryIndexedResource[], library: LsiLibraryDefinition, service: ConventionFileSummary): void {
  const feature = resourceFeature(library, service.featureId)
  if (!feature) return
  const kindLabel = service.kind === 'SERVICE' ? 'Service' : '定时任务'
  append(
    entries,
    library,
    feature,
    'services',
    `convention-file:${service.id}`,
    service.name,
    `${kindLabel} · ${service.className}`,
    [service.fileCode, service.name, service.description, service.packageName, service.className, kindLabel],
  )
}

function appendQueries(entries: LibraryIndexedResource[], library: LsiLibraryDefinition, model: LowcodeModelDraft): void {
  const feature = resourceFeature(library, model.featureId)
  if (!feature) return
  model.queries.forEach((query) => append(
    entries, library, feature, 'queries', `query:${model.modelCode}:${query.queryCode}`, query.label,
    `${model.name} · ${query.queryCode}`, [model.modelCode, model.name, query.queryCode, query.label],
  ))
}

function resourceFeature(library: LsiLibraryDefinition, featureId: number | string): LsiLibraryFeature | undefined {
  return library.features.find((feature) => String(feature.id) === String(featureId))
}

function dtoReferenceSearchValues(
  dto: LowcodeDtoResourceSummary,
  models: LowcodeModelSummary[],
): string[] {
  return models.flatMap((model) => {
    if (String(model.featureId) !== String(dto.featureId)) return []
    const referencingFields = (model.fields ?? []).filter((field) =>
      referencesClassifier(field.kotlinType, dto.className))
    if (referencingFields.length === 0) return []
    return [
      model.modelCode,
      model.name,
      model.className ?? '',
      ...referencingFields.flatMap((field) => [field.fieldCode, field.label, field.kotlinType]),
    ]
  })
}

function referencesClassifier(kotlinType: string, className: string): boolean {
  return kotlinType.split(/[^\p{L}\p{N}_.]+/u)
    .some((classifier) => classifier === className || classifier.endsWith(`.${className}`))
}

function searchTokens(value: string): string[] {
  return value
    .replace(/([a-z\d])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean)
    .map(normalizeSearchToken)
}

function normalizeSearchToken(value: string): string {
  return value.length > 3 && value.endsWith('s') && !value.endsWith('ss')
    ? value.slice(0, -1)
    : value
}

function append(
  entries: LibraryIndexedResource[],
  library: LsiLibraryDefinition,
  feature: LsiLibraryFeature,
  tab: LibraryDataTab,
  resourceKey: string,
  label: string,
  detail: string,
  searchValues: Array<string | null | undefined>,
): void {
  const packageName = featurePackageName(library.spec, feature)
  const contextValues = [
    library.code,
    library.displayName,
    library.spec.description,
    library.spec.contributorId,
    library.spec.scanPackage,
    feature.featureCode,
    feature.name,
    feature.description,
    packageName,
  ]
  entries.push({
    libraryId: library.id,
    featureId: feature.id,
    featureCode: feature.featureCode,
    featurePackageName: packageName,
    tab,
    resourceKey,
    label,
    detail,
    searchText: [...contextValues, ...searchValues].filter(Boolean).join(' '),
  })
}
