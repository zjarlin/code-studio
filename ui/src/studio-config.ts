export interface StudioConfig {
  contributorId: string
  displayName: string
  apiBaseUrl: string
  openApiPath: string
  editableContributorId: string
  capabilities: string[]
}

export type StudioWorkspace = 'library' | 'agent' | 'api'

const workspaceCapability: Record<StudioWorkspace, string> = {
  library: 'metadata',
  agent: 'agent',
  api: 'api',
}

export async function loadStudioConfig(fetcher: typeof fetch = fetch): Promise<StudioConfig> {
  const response = await fetcher('/studio/config')
  if (!response.ok) {
    throw new Error(`读取 Studio 配置失败：HTTP ${response.status}`)
  }

  const value: unknown = await response.json()
  if (!isStudioConfig(value)) {
    throw new Error('Studio 配置格式无效')
  }
  return value
}

export function availableStudioWorkspaces(config: StudioConfig): StudioWorkspace[] {
  return (Object.keys(workspaceCapability) as StudioWorkspace[])
    .filter((workspace) => config.capabilities.includes(workspaceCapability[workspace]))
}

function isStudioConfig(value: unknown): value is StudioConfig {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false
  }
  const config = value as Partial<StudioConfig>
  return typeof config.contributorId === 'string'
    && typeof config.displayName === 'string'
    && typeof config.apiBaseUrl === 'string'
    && typeof config.openApiPath === 'string'
    && typeof config.editableContributorId === 'string'
    && Array.isArray(config.capabilities)
    && config.capabilities.every((capability) => typeof capability === 'string')
}
