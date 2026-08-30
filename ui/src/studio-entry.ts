import type { Workspace } from './studio-i18n'

export const API_DOCUMENTATION_MODE = 'api-docs'

export interface StudioEntry {
  apiDocumentationOnly: boolean
  workspace: Workspace
}

export function resolveStudioEntry(search: string): StudioEntry {
  const apiDocumentationOnly = new URLSearchParams(search).get('mode') === API_DOCUMENTATION_MODE
  return {
    apiDocumentationOnly,
    workspace: apiDocumentationOnly ? 'api' : 'library',
  }
}
