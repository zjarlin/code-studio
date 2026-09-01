import {
  addLibrary,
  getConsoleConfig,
  listLibraries,
  validateLibrary,
} from '@generated/openapi/client'
import {
  LibrarySpecKind,
  LibrarySpecSupportedIdentityModesItem,
  type LibraryCommand,
} from '@generated/openapi/models'

import { requireApiData } from '@/lib/http'

export interface CreateLibraryInput {
  code: string
  displayName: string
  packagePrefix: string
  contributorId: string
}

export async function fetchLibraries() {
  const page = requireApiData(await listLibraries({ pageNo: 1, pageSize: 1000 }), 'Library 列表响应缺少 data')
  return page.list
}

export async function fetchStudioConfig() {
  return requireApiData(await getConsoleConfig(), 'Studio 配置响应缺少 data')
}

export async function createLibrary(input: CreateLibraryInput): Promise<void> {
  const command: LibraryCommand = {
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
      kind: LibrarySpecKind.BUSINESS,
      runtimeDependencies: [],
      supportedIdentityModes: [
        LibrarySpecSupportedIdentityModesItem.EXTERNAL_JWT,
        LibrarySpecSupportedIdentityModesItem.LOCAL,
      ],
      applicationSelectable: true,
      dataScope: {
        tenantScoped: false,
        userScoped: false,
        departmentScoped: false,
      },
    },
  }
  const validation = requireApiData(await validateLibrary(command), 'Library 校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || 'Library 校验失败')
  requireApiData(await addLibrary(command), 'Library 创建响应缺少 data')
}
