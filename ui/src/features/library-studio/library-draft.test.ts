import { describe, expect, it } from 'vitest'

import {
  applyLibraryDisplayName,
  createLibraryDraft,
  featureCodeFromName,
  featurePackageName,
  normalizeLibraryDraft,
  selectedLibraryFeatures,
} from './library-draft'

describe('library draft', () => {
  it('derives hidden identities from names without renaming persisted resources', () => {
    const created = applyLibraryDisplayName(createLibraryDraft(), '设备能力')
    const persisted = applyLibraryDisplayName({ ...created, id: 7 }, '设备中心')

    expect(created.code).toBe('she-bei-neng-li')
    expect(persisted.code).toBe('she-bei-neng-li')
    expect(featureCodeFromName('巡检任务', 'inspection')).toBe('inspection.xun_jian_ren_wu')
  })

  it('normalizes root package metadata without embedded features', () => {
    const draft = createLibraryDraft()
    draft.code = ' example '
    draft.spec.packagePrefix = ' com.example.application.sample. '

    const normalized = normalizeLibraryDraft(draft)

    expect(normalized.code).toBe('example')
    expect(normalized.spec.packagePrefix).toBe('com.example.application.sample')
    expect(normalized.spec).not.toHaveProperty('features')
  })

  it('computes package and selects exact feature ownership', () => {
    const features = [
      { id: 1, libraryId: 9, parentId: null, featureCode: 'inspection', name: '巡检' },
      { id: 2, libraryId: 9, parentId: 1, featureCode: 'inspection.task', name: '任务' },
    ]
    expect(featurePackageName({ packagePrefix: 'example' }, features[1])).toBe('example.inspection.task')
    expect(selectedLibraryFeatures(features)).toHaveLength(2)
    expect(selectedLibraryFeatures(features, 1).map(({ id }) => id)).toEqual([1])
  })
})
