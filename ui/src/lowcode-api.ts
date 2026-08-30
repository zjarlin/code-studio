import type {
  AgentDefinitionDraft,
  AgentMetadataResult,
  AgentDefinitionSummary,
  AgentConversationSummary,
  AgentProviderSettingsCommand,
  AgentProviderSettingsView,
  AgentProviderModel,
  AgentStructuredOutputDraft,
  MetadataConfigurationApplyResult,
  MetadataTableContext,
  MetadataTablePatchResult,
  AgentUiMessage,
  CommonResult,
  ConventionFileCommand,
  ConventionFileSummary,
  JsonObject,
  LowcodeApiContractDraft,
  LowcodeApiContractSummary,
  LowcodeContractPreview,
  LowcodeDtoPreview,
  LowcodeDtoResourceDraft,
  LowcodeDtoResourceSummary,
  LowcodeDtoReuseAnalysis,
  LsiValidationRuleMetadata,
  LowcodeModelSummary,
  LowcodePreview,
  LowcodeValidationResult,
  PageResult,
  LibraryDefinitionCommand,
  LibraryDefinitionPreview,
  LsiLibraryDefinition,
  LsiLibraryFeature,
} from './types'

let accessTokenProvider: () => string = () => ''
const MODEL_CATALOG_PAGE_SIZE = 20

export function configureLowcodeApiAccessToken(provider: () => string): void {
  accessTokenProvider = provider
}

export class LowcodeApi {
  async models(condition: JsonObject = {}): Promise<LowcodeModelSummary[]> {
    const models: LowcodeModelSummary[] = []
    let pageNumber = 1
    let totalPageCount = 1
    while (pageNumber <= totalPageCount) {
      const page = await this.modelPage(pageNumber, MODEL_CATALOG_PAGE_SIZE, condition)
      models.push(...page.rows)
      totalPageCount = page.totalPageCount
      pageNumber += 1
    }
    return models
  }

  async modelPage(
    pageNumber: number,
    pageSize: number,
    condition: JsonObject = {},
  ): Promise<PageResult<LowcodeModelSummary>> {
    const page = await this.request<{
      rows: LowcodeModelSummary[]
      totalRowCount: number | string
      totalPageCount: number | string
    }>('/studio/api/lowcode/model/page', {
      method: 'POST',
      body: JSON.stringify({ pageNumber, pageSize, condition }),
    })
    return {
      rows: page.rows,
      totalRowCount: Number(page.totalRowCount),
      totalPageCount: Number(page.totalPageCount),
    }
  }

  async detail(id: number | string): Promise<JsonObject> {
    return this.request(`/studio/api/lowcode/model/detail?id=${encodeURIComponent(id)}`)
  }

  async validate(command: JsonObject): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/model/validate', {
      method: 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async save(command: JsonObject): Promise<number | string | boolean> {
    const id = command.id
    return this.request(id ? '/studio/api/lowcode/model/update' : '/studio/api/lowcode/model/add', {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async delete(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/model', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async preview(id: number | string): Promise<LowcodePreview> {
    return this.request(`/studio/api/lowcode/model/preview?id=${encodeURIComponent(id)}`)
  }

  async download(id: number | string): Promise<void> {
    return this.downloadArchive(`/studio/api/lowcode/model/download?id=${encodeURIComponent(id)}`, `lowcode-model-${id}.zip`)
  }

  async contracts(): Promise<LowcodeApiContractSummary[]> {
    return this.request('/studio/api/lowcode/contract/list', {
      method: 'POST',
      body: '{}',
    })
  }

  async conventionFiles(): Promise<ConventionFileSummary[]> {
    return this.request('/studio/api/lowcode/convention-file/list', {
      method: 'POST',
      body: '{}',
    })
  }

  async validateConventionFile(command: ConventionFileCommand): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/convention-file/validate', {
      method: 'POST',
      body: JSON.stringify(command),
    })
  }

  async saveConventionFile(command: ConventionFileCommand): Promise<number | string | boolean> {
    return this.request(command.id
      ? '/studio/api/lowcode/convention-file/update'
      : '/studio/api/lowcode/convention-file/add', {
      method: command.id ? 'PUT' : 'POST',
      body: JSON.stringify(command),
    })
  }

  async deleteConventionFile(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/convention-file', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async dtos(): Promise<LowcodeDtoResourceSummary[]> {
    return this.request('/studio/api/lowcode/dto/list', { method: 'POST', body: '{}' })
  }

  async dtoDetail(id: number | string): Promise<LowcodeDtoResourceSummary> {
    return this.request(`/studio/api/lowcode/dto/detail?id=${encodeURIComponent(id)}`)
  }

  async validateDto(command: LowcodeDtoResourceDraft): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/dto/validate', {
      method: 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async dtoValidationRules(): Promise<LsiValidationRuleMetadata[]> {
    return this.request('/studio/api/lowcode/dto/validation-rules')
  }

  async analyzeDtoReuse(command: LowcodeDtoResourceDraft): Promise<LowcodeDtoReuseAnalysis> {
    return this.request('/studio/api/lowcode/dto/reuse-analysis', {
      method: 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async saveDto(command: LowcodeDtoResourceDraft): Promise<number | string | boolean> {
    return this.request(command.id ? '/studio/api/lowcode/dto/update' : '/studio/api/lowcode/dto/add', {
      method: command.id ? 'PUT' : 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async deleteDto(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/dto', { method: 'DELETE', body: JSON.stringify([id]) })
  }

  async previewDto(id: number | string): Promise<LowcodeDtoPreview> {
    return this.request(`/studio/api/lowcode/dto/preview?id=${encodeURIComponent(id)}`)
  }

  async downloadDto(id: number | string): Promise<void> {
    return this.downloadArchive(`/studio/api/lowcode/dto/download?id=${encodeURIComponent(id)}`, `lowcode-dto-${id}.zip`)
  }

  async contractDetail(id: number | string): Promise<JsonObject> {
    return this.request(`/studio/api/lowcode/contract/detail?id=${encodeURIComponent(id)}`)
  }

  async validateContract(command: LowcodeApiContractDraft): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/contract/validate', {
      method: 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async saveContract(command: LowcodeApiContractDraft): Promise<number | string | boolean> {
    return this.request(command.id ? '/studio/api/lowcode/contract/update' : '/studio/api/lowcode/contract/add', {
      method: command.id ? 'PUT' : 'POST',
      body: JSON.stringify(featureOwnedPayload(command)),
    })
  }

  async deleteContract(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/contract', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async previewContract(id: number | string): Promise<LowcodeContractPreview> {
    return this.request(`/studio/api/lowcode/contract/preview?id=${encodeURIComponent(id)}`)
  }

  async downloadContract(id: number | string): Promise<void> {
    return this.downloadArchive(`/studio/api/lowcode/contract/download?id=${encodeURIComponent(id)}`, `lowcode-contract-${id}.zip`)
  }

  async agents(): Promise<AgentDefinitionSummary[]> {
    return this.request('/studio/api/lowcode/agent/list', {
      method: 'POST',
      body: '{}',
    })
  }

  async agentDetail(id: number | string): Promise<JsonObject> {
    return this.request(`/studio/api/lowcode/agent/detail?id=${encodeURIComponent(id)}`)
  }

  async validateAgent(command: AgentDefinitionDraft): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/agent/validate', {
      method: 'POST',
      body: JSON.stringify(command),
    })
  }

  async saveAgent(command: AgentDefinitionDraft): Promise<number | string | boolean> {
    return this.request(command.id ? '/studio/api/lowcode/agent/update' : '/studio/api/lowcode/agent/add', {
      method: command.id ? 'PUT' : 'POST',
      body: JSON.stringify(command),
    })
  }

  async deleteAgent(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/agent', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async validateAgentOutput(
    structuredOutput: AgentStructuredOutputDraft,
    output: unknown,
  ): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/agent/validate-output', {
      method: 'POST',
      body: JSON.stringify({ structuredOutput, output }),
    })
  }

  async generateStructuredOutput(agentCode: string, input: JsonObject): Promise<JsonObject> {
    return this.request('/studio/api/agent/structured-output', {
      method: 'POST',
      body: JSON.stringify({ agentCode, input }),
    })
  }

  async agentSettings(): Promise<AgentProviderSettingsView> {
    return this.request('/studio/api/agent/settings')
  }

  async updateAgentSettings(command: AgentProviderSettingsCommand): Promise<AgentProviderSettingsView> {
    return this.request('/studio/api/agent/settings', {
      method: 'PUT',
      body: JSON.stringify(command),
    })
  }

  async agentModels(): Promise<AgentProviderModel[]> {
    return this.request('/studio/api/agent/models')
  }

  async agentConversations(): Promise<AgentConversationSummary[]> {
    return this.request('/studio/api/agent/conversations')
  }

  async createAgentConversation(title: string | undefined, modelId: string): Promise<number | string> {
    return this.request('/studio/api/agent/conversations', {
      method: 'POST',
      body: JSON.stringify({ title, modelId }),
    })
  }

  async updateAgentConversationModel(conversationId: number | string, modelId: string): Promise<boolean> {
    return this.request('/studio/api/agent/conversations/model', {
      method: 'PUT',
      body: JSON.stringify({ conversationId, modelId }),
    })
  }

  async deleteAgentConversation(id: number | string): Promise<boolean> {
    return this.request('/studio/api/agent/conversations', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async agentMessages(id: number | string): Promise<AgentUiMessage[]> {
    return this.request(`/studio/api/agent/messages?id=${encodeURIComponent(id)}`)
  }

  async applyAgentMetadata(metadata: AgentMetadataResult): Promise<MetadataConfigurationApplyResult> {
    return this.request('/studio/api/lowcode/agent/configuration/apply', {
      method: 'POST',
      body: JSON.stringify({ metadata }),
    })
  }

  async agentDisplayTextContext(): Promise<MetadataTableContext> {
    return this.request('/studio/api/lowcode/agent/display-text/context')
  }

  async applyAgentDisplayText(result: MetadataTablePatchResult): Promise<number> {
    return this.request('/studio/api/lowcode/agent/display-text/apply', {
      method: 'POST',
      body: JSON.stringify(result),
    })
  }

  async libraries(): Promise<LsiLibraryDefinition[]> {
    const page = await this.request<{
      list: LsiLibraryDefinition[]
      total: number | string
    }>('/studio/api/lowcode/library/page?pageNo=1&pageSize=1000')
    return page.list.map((library) => ({ ...library, features: [] }))
  }

  async libraryFeatures(libraryId: number | string): Promise<LsiLibraryFeature[]> {
    const page = await this.request<{
      list: LsiLibraryFeature[]
      total: number | string
    }>(`/studio/api/lowcode/library-feature/page?pageNo=1&pageSize=1000&libraryId=${encodeURIComponent(libraryId)}`)
    return page.list.sort((left, right) => left.featureCode.localeCompare(right.featureCode))
  }

  async saveLibraryFeature(feature: Omit<LsiLibraryFeature, 'id'> & { id?: number | string }): Promise<LsiLibraryFeature> {
    if (feature.id == null) {
      return this.request('/studio/api/lowcode/library-feature/create', { method: 'POST', body: JSON.stringify(feature) })
    }
    await this.request<number>('/studio/api/lowcode/library-feature/update', { method: 'PUT', body: JSON.stringify(feature) })
    return feature as LsiLibraryFeature
  }

  async deleteLibraryFeature(id: number | string): Promise<boolean> {
    return this.request(`/studio/api/lowcode/library-feature/delete?id=${encodeURIComponent(id)}`, {
      method: 'DELETE',
    })
  }

  async validateLibrary(command: LibraryDefinitionCommand): Promise<LowcodeValidationResult> {
    return this.request('/studio/api/lowcode/library/validate', {
      method: 'POST',
      body: JSON.stringify(command),
    })
  }

  async saveLibrary(command: LibraryDefinitionCommand): Promise<number | string | boolean> {
    return this.request(command.id ? '/studio/api/lowcode/library/update' : '/studio/api/lowcode/library/add', {
      method: command.id ? 'PUT' : 'POST',
      body: JSON.stringify(command),
    })
  }

  async deleteLibrary(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/library/delete', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  async previewLibrary(id: number | string, featureId?: number | string): Promise<LibraryDefinitionPreview> {
    const filter = featureId == null ? '' : `&featureId=${encodeURIComponent(featureId)}`
    return this.request(`/studio/api/lowcode/library/preview?id=${encodeURIComponent(id)}${filter}`)
  }

  private async downloadArchive(path: string, fileName: string): Promise<void> {
    const response = await fetch(path, {
      headers: this.headers(false),
    })
    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      const result = (await response.json()) as CommonResult<never>
      throw new ApiError(result.code, result.msg)
    }
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`)
    }
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    let response: Response
    try {
      response = await fetch(path, {
        ...init,
        headers: {
          ...this.headers(init.body !== undefined),
          ...init.headers,
        },
      })
    } catch (cause) {
      throw new Error('无法连接平台服务，请确认后端已启动或代理地址配置正确', { cause })
    }
    const payload = await response.text()
    if (!payload.trim()) {
      throw new Error(httpResponseError(response, '平台服务返回空响应'))
    }
    let result: CommonResult<T>
    try {
      result = JSON.parse(payload) as CommonResult<T>
    } catch (cause) {
      throw new Error(httpResponseError(response, '平台服务返回了无效的 JSON'), { cause })
    }
    if (result.code !== 0) {
      throw new ApiError(result.code, result.msg)
    }
    if (!response.ok) {
      throw new Error(httpResponseError(response, '平台服务请求失败'))
    }
    return result.data
  }

  private headers(json: boolean): Record<string, string> {
    const headers: Record<string, string> = {}
    const accessToken = accessTokenProvider().trim()
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`
    }
    if (json) {
      headers['Content-Type'] = 'application/json'
    }
    return headers
  }
}

function featureOwnedPayload(command: JsonObject): JsonObject {
  const payload = { ...command }
  delete payload.packageName
  delete payload.contributorId
  delete payload.featurePackageName
  const route = payload.routeConfig
  if (route && !Array.isArray(route) && typeof route === 'object') {
    const normalizedRoute = { ...route }
    delete normalizedRoute.packageName
    delete normalizedRoute.qualifiedName
    delete normalizedRoute.featurePackageName
    payload.routeConfig = normalizedRoute
  }
  return payload
}

function httpResponseError(response: Response, fallback: string): string {
  if (response.status >= 500) {
    return `平台服务暂不可用（HTTP ${response.status}），请确认后端已启动或代理地址配置正确`
  }
  return `${fallback}（HTTP ${response.status}）`
}

export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message || `请求失败：${code}`)
  }
}
