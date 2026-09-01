import {
  deleteSpreadsheetTemplate as deleteSpreadsheetTemplateRequest,
  fillSpreadsheetTemplate as fillSpreadsheetTemplateRequest,
  getSpreadsheetTemplate as getSpreadsheetTemplateRequest,
  importSpreadsheetTemplate as importSpreadsheetTemplateRequest,
  listSpreadsheetTemplates,
  updateSpreadsheetTemplate as updateSpreadsheetTemplateRequest,
} from '@generated/openapi/client'

import { requireApiData } from '@/lib/http'

import type {
  PageResult,
  SpreadsheetTemplateDocument,
  SpreadsheetTemplateFillCommand,
  SpreadsheetTemplateListItemView,
  SpreadsheetTemplateView,
} from './models'

export async function fetchSpreadsheetTemplates(): Promise<PageResult<SpreadsheetTemplateListItemView>> {
  return requireApiData(await listSpreadsheetTemplates({ pageNo: 1, pageSize: 200 })) as PageResult<SpreadsheetTemplateListItemView>
}

export async function fetchSpreadsheetTemplate(templateKey: string): Promise<SpreadsheetTemplateView> {
  return requireApiData(await getSpreadsheetTemplateRequest(templateKey)) as SpreadsheetTemplateView
}

export async function importSpreadsheetTemplate(input: Readonly<{
  templateKey: string
  name: string
  file: File
}>): Promise<SpreadsheetTemplateView> {
  return requireApiData(await importSpreadsheetTemplateRequest(input)) as SpreadsheetTemplateView
}

export async function updateSpreadsheetTemplate(
  templateKey: string,
  expectedRevision: number,
  document: SpreadsheetTemplateDocument,
): Promise<SpreadsheetTemplateView> {
  const { name, description, variables, bindings, ledgers, edits } = document
  const result = await updateSpreadsheetTemplateRequest(templateKey, {
    expectedRevision,
    draft: { name, description, variables, bindings, ledgers, edits },
  })
  return requireApiData(result) as SpreadsheetTemplateView
}

export async function deleteSpreadsheetTemplate(templateKey: string): Promise<boolean> {
  return requireApiData(await deleteSpreadsheetTemplateRequest(templateKey))
}

export function fillSpreadsheetTemplate(
  templateKey: string,
  command: SpreadsheetTemplateFillCommand,
): Promise<Blob> {
  return fillSpreadsheetTemplateRequest(templateKey, command)
}
