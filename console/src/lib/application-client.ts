import { getConsoleConfig } from '@generated/openapi/client'

import { requireApiData, type ApiRequestOptions } from './http'

let baseUrlRequest: Promise<string> | undefined

export async function applicationRequestOptions(): Promise<ApiRequestOptions> {
  baseUrlRequest ??= getConsoleConfig()
    .then((result) => requireApiData(result, '管理端配置缺少 data').apiBaseUrl)
    .catch((error: unknown) => {
      baseUrlRequest = undefined
      throw error
    })
  return { baseUrl: await baseUrlRequest }
}
