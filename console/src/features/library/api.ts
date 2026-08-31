import { requestData } from '@/lib/http'

export interface StudioConfig {
  editableContributorId: string
}

export interface LibraryDefinition {
  id: number | string
  code: string
  displayName: string
  version: number
  status: number
  spec: {
    description?: string | null
    contributorId: string
    packagePrefix: string
    scanPackage: string
    kind: string
  }
}

interface LibraryPage {
  list: LibraryDefinition[]
  total: number | string
}

export interface CreateLibraryInput {
  code: string
  displayName: string
  packagePrefix: string
  contributorId: string
}

export async function fetchLibraries(): Promise<LibraryDefinition[]> {
  const page = await requestData<LibraryPage>('/studio/api/lowcode/library/page?pageNo=1&pageSize=1000')
  return page.list
}

export function fetchStudioConfig(): Promise<StudioConfig> {
  return requestData('/studio/config')
}

export async function createLibrary(input: CreateLibraryInput): Promise<void> {
  const command = {
    code: input.code.trim(),
    displayName: input.displayName.trim(),
    version: 1,
    status: 1,
    spec: {
      schemaVersion: 3,
      description: null,
      contributorId: input.contributorId,
      packagePrefix: input.packagePrefix.trim(),
      scanPackage: input.packagePrefix.trim(),
      kind: 'BUSINESS',
      runtimeDependencies: [],
      supportedIdentityModes: ['EXTERNAL_JWT', 'LOCAL'],
      applicationSelectable: true,
      dataScope: {
        tenantScoped: false,
        userScoped: false,
        departmentScoped: false,
      },
    },
  }
  const validation = await requestData<{ valid: boolean; errors: string[] }>(
    '/studio/api/lowcode/library/validate',
    { method: 'POST', body: JSON.stringify(command) },
  )
  if (!validation.valid) throw new Error(validation.errors.join('；') || 'Library 校验失败')
  await requestData('/studio/api/lowcode/library/add', {
    method: 'POST',
    body: JSON.stringify(command),
  })
}
