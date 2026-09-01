import {
  addLibrary,
  addConventionFile,
  addDto,
  addModel,
  createLibraryFeature,
  deleteLibraries,
  deleteConventionFiles,
  deleteConstants,
  deleteDtos,
  deleteLibraryFeature,
  deleteModels,
  getConsoleConfig,
  listConstants,
  listConventionFiles,
  listLibraries,
  listLibraryFeatures,
  listDtos,
  listModels,
  previewLibrary,
  saveConstant,
  updateLibrary,
  updateConventionFile,
  updateDto,
  updateModel,
  validateConventionFile,
  validateDto,
  updateLibraryFeature,
  validateConstant,
  validateLibrary,
  validateLibraryFeature,
  validateModel,
} from '@generated/openapi/client'
import {
  type ConstantCommand,
  type ConstantView,
  type ConventionFileCommand,
  type ConventionFileView,
  type DtoCommand,
  type ModelCommand,
  type LibraryFeatureCommand,
  type LibraryFeatureView,
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
  return (page.list ?? []).filter(isPresent)
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

export async function persistLibrary(command: LibraryCommand): Promise<void> {
  const validation = requireApiData(await validateLibrary(command), 'Library 校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || 'Library 校验失败')
  if (command.id == null) {
    requireApiData(await addLibrary(command), 'Library 创建响应缺少 data')
  } else {
    requireApiData(await updateLibrary(command), 'Library 保存响应缺少 data')
  }
}

export async function removeLibrary(id: number): Promise<void> {
  requireApiData(await deleteLibraries([id]), 'Library 删除响应缺少 data')
}

export async function fetchLibraryPreview(libraryId: number, featureId?: number) {
  return requireApiData(
    await previewLibrary({ id: libraryId, featureId }),
    '生成预览响应缺少 data',
  )
}

export async function fetchLibraryFeatures(libraryId: number): Promise<Array<NonNullable<LibraryFeatureView>>> {
  const page = requireApiData(
    await listLibraryFeatures({ libraryId, pageNo: 1, pageSize: 1000 }),
    '功能目录响应缺少 data',
  )
  return (page.list ?? []).filter(isPresent)
}

export async function persistLibraryFeature(command: LibraryFeatureCommand): Promise<NonNullable<LibraryFeatureView>> {
  const validation = requireApiData(
    await validateLibraryFeature(command),
    '功能目录校验响应缺少 data',
  )
  if (!validation.valid) throw new Error(validation.errors?.join('；') || '功能目录校验失败')
  const result = command.id == null
    ? await createLibraryFeature(command)
    : await updateLibraryFeature(command)
  return requirePresent(requireApiData(result, '功能目录保存响应缺少 data'), '功能目录保存响应缺少 data')
}

export async function removeLibraryFeature(id: number): Promise<void> {
  requireApiData(await deleteLibraryFeature({ id }), '功能目录删除响应缺少 data')
}

export async function fetchConstants(featureId: number): Promise<Array<NonNullable<ConstantView>>> {
  const constants = requireApiData(await listConstants({ featureId }), '常量列表响应缺少 data')
  return constants.filter(isPresent)
}

export async function persistConstant(command: ConstantCommand): Promise<NonNullable<ConstantView>> {
  const validation = requireApiData(await validateConstant(command), '常量校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || '常量校验失败')
  return requirePresent(requireApiData(await saveConstant(command), '常量保存响应缺少 data'), '常量保存响应缺少 data')
}

export async function removeConstant(id: number): Promise<void> {
  requireApiData(await deleteConstants([id]), '常量删除响应缺少 data')
}

export async function fetchModels(featureId: number): Promise<ModelCommand[]> {
  const page = requirePresent(
    requireApiData(
      await listModels({ pageNumber: 1, pageSize: 1000, condition: { featureId } }),
      '模型列表响应缺少 data',
    ),
    '模型列表响应缺少 data',
  )
  return page.rows ?? []
}

export async function persistModel(command: ModelCommand): Promise<void> {
  const validation = requireApiData(await validateModel(command), '模型校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || '模型校验失败')
  if (command.id == null) {
    requireApiData(await addModel(command), '模型创建响应缺少 data')
  } else {
    requireApiData(await updateModel(command), '模型保存响应缺少 data')
  }
}

export async function removeModel(id: number): Promise<void> {
  requireApiData(await deleteModels([id]), '模型删除响应缺少 data')
}

export async function fetchDtos(featureId: number): Promise<DtoCommand[]> {
  const dtos = requireApiData(await listDtos(), 'DTO 列表响应缺少 data')
  return dtos.filter((dto) => dto.featureId === featureId)
}

export async function persistDto(command: DtoCommand): Promise<void> {
  const validation = requireApiData(await validateDto(command), 'DTO 校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || 'DTO 校验失败')
  if (command.id == null) {
    requireApiData(await addDto(command), 'DTO 创建响应缺少 data')
  } else {
    requireApiData(await updateDto(command), 'DTO 保存响应缺少 data')
  }
}

export async function removeDto(id: number): Promise<void> {
  requireApiData(await deleteDtos([id]), 'DTO 删除响应缺少 data')
}

export async function fetchConventionFiles(featureId: number): Promise<Array<NonNullable<ConventionFileView>>> {
  const files = requireApiData(await listConventionFiles(), '约定文件列表响应缺少 data')
  return files.filter(isPresent).filter((file) => file.featureId === featureId)
}

export async function persistConventionFile(command: ConventionFileCommand): Promise<void> {
  const validation = requireApiData(await validateConventionFile(command), '约定文件校验响应缺少 data')
  if (!validation.valid) throw new Error(validation.errors?.join('；') || '约定文件校验失败')
  if (command.id == null) {
    requireApiData(await addConventionFile(command), '约定文件创建响应缺少 data')
  } else {
    requireApiData(await updateConventionFile(command), '约定文件保存响应缺少 data')
  }
}

export async function removeConventionFile(id: number): Promise<void> {
  requireApiData(await deleteConventionFiles([id]), '约定文件删除响应缺少 data')
}

function isPresent<T>(value: T | null): value is NonNullable<T> {
  return value !== null
}

function requirePresent<T>(value: T | null | undefined, message: string): NonNullable<T> {
  if (value === null || value === undefined) throw new Error(message)
  return value
}
