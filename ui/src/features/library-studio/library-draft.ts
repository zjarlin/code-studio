import type { LibraryDefinitionDraft, LsiLibraryDefinition, LsiLibraryFeature, LsiLibrarySpec } from '@/types'
import { toPinyinSnakeIdentifier } from '@/lib/identifier'

export function createLibraryDraft(): LibraryDefinitionDraft {
  return {
    code: '', displayName: '', version: 1, status: 1,
    spec: {
      schemaVersion: 3,
      description: null,
      contributorId: '',
      packagePrefix: 'com.example.application',
      scanPackage: 'com.example.application',
      kind: 'BUSINESS',
      runtimeDependencies: [],
      supportedIdentityModes: ['EXTERNAL_JWT', 'LOCAL'],
      applicationSelectable: true,
      dataScope: { tenantScoped: false, userScoped: false, departmentScoped: false },
    },
  }
}

export function libraryViewToDraft(view: LsiLibraryDefinition): LibraryDefinitionDraft {
  return normalizeLibraryDraft({
    id: view.id,
    code: view.code,
    displayName: view.displayName,
    version: view.version,
    status: view.status,
    spec: view.spec,
  })
}

export function normalizeLibraryDraft(draft: LibraryDefinitionDraft): LibraryDefinitionDraft {
  return {
    id: draft.id,
    code: draft.code.trim(),
    displayName: draft.displayName.trim(),
    version: draft.version,
    status: draft.status,
    spec: {
      ...draft.spec,
      schemaVersion: 3,
      description: draft.spec.description?.trim() || null,
      contributorId: draft.spec.contributorId.trim(),
      packagePrefix: draft.spec.packagePrefix.trim().replace(/\.$/, ''),
      scanPackage: draft.spec.scanPackage.trim().replace(/\.$/, ''),
      runtimeDependencies: draft.spec.runtimeDependencies.map((value) => value.trim()).filter(Boolean),
      supportedIdentityModes: [...draft.spec.supportedIdentityModes].sort(),
      dataScope: { ...draft.spec.dataScope },
    },
  }
}

export function applyLibraryDisplayName(
  draft: LibraryDefinitionDraft,
  displayName: string,
): LibraryDefinitionDraft {
  const previousDefault = toPinyinSnakeIdentifier(draft.displayName).replaceAll('_', '-')
  const identityFollowsName = draft.id == null && (!draft.code || draft.code === previousDefault)
  return {
    ...draft,
    displayName,
    code: identityFollowsName
      ? toPinyinSnakeIdentifier(displayName).replaceAll('_', '-')
      : draft.code,
  }
}

export function featureCodeFromName(name: string, parentCode = ''): string {
  const segment = toPinyinSnakeIdentifier(name) || 'feature'
  return parentCode ? `${parentCode}.${segment}` : segment
}

export function createLibraryFeature(
  libraryId: number | string,
  features: LsiLibraryFeature[],
): Omit<LsiLibraryFeature, 'id'> {
  const index = features.length + 1
  const name = `功能 ${index}`
  return {
    libraryId,
    parentId: null,
    featureCode: featureCodeFromName(name),
    name,
    description: null,
  }
}

export function featurePackageName(spec: Pick<LsiLibrarySpec, 'packagePrefix'>, feature: LsiLibraryFeature): string {
  return `${spec.packagePrefix.replace(/\.$/, '')}.${feature.featureCode}`
}

export function selectedLibraryFeatures(
  features: LsiLibraryFeature[],
  featureId?: number | string,
): LsiLibraryFeature[] {
  if (featureId == null) return features
  return features.filter((feature) => String(feature.id) === String(featureId))
}
