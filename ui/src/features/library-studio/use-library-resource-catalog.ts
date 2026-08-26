import { computed, ref, shallowRef } from 'vue'

import { LowcodeApi } from '@/lowcode-api'
import type {
  LowcodeApiContractSummary,
  LowcodeDtoResourceSummary,
  LowcodeModelDraft,
  LowcodeModelSummary,
  LsiApplicationFeature,
  LsiLibraryDefinition,
} from '@/types'

import { ConstantApi } from '../constants/constant-api'
import { normalizeModelDraft } from '../model-studio/model-draft'
import { featurePackageName, selectedLibraryFeatures } from './library-draft'
import {
  createLibraryResourceIndex,
  libraryResourceCounts,
  libraryResourceEntries,
  searchLibraryResources,
} from './library-resource-index'
import type { LibraryIndexedResource, LibraryResourceCounts } from './library-resource-index'

interface LibraryTabCounts extends LibraryResourceCounts {
  features: number
  constants: number
}

export function useLibraryResourceCatalog(api = new LowcodeApi()) {
  const constantApi = new ConstantApi()
  const libraries = ref<LsiLibraryDefinition[]>([])
  const models = ref<LowcodeModelSummary[]>([])
  const dtos = shallowRef<LowcodeDtoResourceSummary[]>([])
  const services = ref<LowcodeApiContractSummary[]>([])
  const modelDetails = shallowRef(new Map<string, LowcodeModelDraft>())
  const constantCounts = shallowRef(new Map<string, number>())

  const index = computed(() => createLibraryResourceIndex(
    libraries.value,
    models.value,
    dtos.value,
    services.value,
    [...modelDetails.value.values()],
  ))

  async function loadCatalog(): Promise<void> {
    const [libraryValues, modelValues, dtoValues, serviceValues] = await Promise.all([
      api.libraries(),
      api.models(),
      api.dtos(),
      api.contracts(),
    ])
    const features = await Promise.all(libraryValues.map((library) => api.libraryFeatures(library.id)))
    libraries.value = libraryValues
      .map((library, index) => ({ ...library, features: features[index] }))
      .sort((left, right) => left.code.localeCompare(right.code))
    models.value = modelValues
    dtos.value = dtoValues
    services.value = serviceValues
  }

  async function loadLibraryContext(
    library: LsiLibraryDefinition,
    selectedFeatureId?: number | string,
  ): Promise<void> {
    const modelCodes = new Set(
      libraryResourceEntries(index.value, library)
        .filter((entry) => entry.tab === 'models')
        .map((entry) => entry.resourceKey.replace(/^model:/, '')),
    )
    const missingModels = models.value.filter((model) =>
      modelCodes.has(model.modelCode) && !modelDetails.value.has(model.modelCode),
    )
    if (missingModels.length > 0) {
      const details = await Promise.all(missingModels.map(async (model) =>
        normalizeModelDraft(await api.detail(model.id))))
      const next = new Map(modelDetails.value)
      details.forEach((model) => next.set(model.modelCode, model))
      modelDetails.value = next
    }
    await loadConstantCount(library, selectedFeatureId)
  }

  function entriesFor(
    library: LsiLibraryDefinition,
    selectedFeatureId?: number | string,
  ): LibraryIndexedResource[] {
    return libraryResourceEntries(index.value, library, selectedFeatureId)
  }

  function countsFor(
    library: LsiLibraryDefinition,
    selectedFeatureId?: number | string,
  ): LibraryTabCounts {
    const counts = libraryResourceCounts(entriesFor(library, selectedFeatureId))
    return {
      ...counts,
      features: selectedLibraryFeatures(library.features, selectedFeatureId).length,
      constants: constantCounts.value.get(scopeKey(library, selectedFeatureId)) ?? 0,
    }
  }

  function matches(keyword: string): LibraryIndexedResource[] {
    return searchLibraryResources(index.value, keyword)
  }

  function setConstantCount(
    library: LsiLibraryDefinition,
    selectedFeatureId: number | string | undefined,
    count: number,
  ): void {
    constantCounts.value = new Map(constantCounts.value)
      .set(scopeKey(library, selectedFeatureId), count)
  }

  async function loadConstantCount(
    library: LsiLibraryDefinition,
    selectedFeatureId?: number | string,
  ): Promise<void> {
    const features = selectedLibraryFeatures(library.features, selectedFeatureId)
      .map<LsiApplicationFeature>((feature) => ({
        featureId: feature.id,
        featureCode: feature.featureCode,
        name: feature.name,
        description: feature.description,
        packageName: featurePackageName(library.spec, feature),
        contributorId: library.spec.contributorId,
        modelCodes: [],
        dtoCodes: [],
        contractCodes: [],
      }))
    const groups = (await Promise.all(features.map((feature) => constantApi.list(feature))))
      .flat()
      .filter((group, index, all) =>
        all.findIndex((candidate) => String(candidate.id) === String(group.id)) === index)
    setConstantCount(library, selectedFeatureId, groups.length)
  }

  return {
    libraries,
    loadCatalog,
    loadLibraryContext,
    entriesFor,
    countsFor,
    matches,
    setConstantCount,
  }
}

function scopeKey(library: LsiLibraryDefinition, selectedFeatureId?: number | string): string {
  return `${String(library.id)}:${selectedFeatureId ?? '*'}`
}
