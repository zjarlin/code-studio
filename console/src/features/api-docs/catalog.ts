import {
  collectApiOperations,
  loadStudioOpenApi,
  resolveStudioApiBaseUrl,
} from '@platform/openapi-workbench'
import type { ApiDocument, ApiOperation } from '@platform/openapi-workbench'
import { getConsoleConfig } from '@generated/openapi/client'
import type { StudioClientConfig } from '@generated/openapi/models'

import { authenticatedFetch } from '@/lib/access-context'
import { requireApiData } from '@/lib/http'

export type StudioApiConfig = StudioClientConfig

export interface ApiCatalog {
  baseUrl: string
  config: StudioApiConfig
  document: ApiDocument
  operations: ApiOperation[]
}

export async function fetchApiCatalog(): Promise<ApiCatalog> {
  const config = requireApiData(await getConsoleConfig(), '管理端配置缺少 data')
  const browserOrigin = window.location.origin
  const baseUrl = resolveStudioApiBaseUrl(config.apiBaseUrl, browserOrigin)
  const document = await loadStudioOpenApi(config, {
    attempts: 1,
    browserOrigin,
    fetcher: authenticatedFetch,
  })

  return {
    baseUrl,
    config,
    document,
    operations: collectApiOperations(document),
  }
}
