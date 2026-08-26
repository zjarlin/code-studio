import type { CommonResult, LsiApplicationFeature } from '@/types'

export type ConstantValueType = 'BOOLEAN' | 'INT' | 'LONG' | 'STRING'

export interface ConstantItem {
  id?: number | string
  name: string
  type: ConstantValueType
  value: string
  description: string
}

export interface ConstantGroup {
  id?: number | string
  featureId: number | string
  groupCode: string
  featurePackageName: string
  contributorId: string
  objectName: string
  description: string
  constants: ConstantItem[]
}

export class ConstantApi {
  async list(feature: Pick<LsiApplicationFeature, 'featureId'>): Promise<ConstantGroup[]> {
    return this.request('/studio/api/lowcode/constant/list', {
      method: 'POST',
      body: JSON.stringify({
        featureId: feature.featureId,
      }),
    })
  }

  async save(group: ConstantGroup): Promise<ConstantGroup> {
    const { featurePackageName: _packageName, contributorId: _contributorId, ...command } = group
    return this.request('/studio/api/lowcode/constant/save', {
      method: 'POST',
      body: JSON.stringify(command),
    })
  }

  async delete(id: number | string): Promise<boolean> {
    return this.request('/studio/api/lowcode/constant', {
      method: 'DELETE',
      body: JSON.stringify([id]),
    })
  }

  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const response = await fetch(path, {
      ...init,
      headers: { 'Content-Type': 'application/json' },
    })
    const result = (await response.json()) as CommonResult<T>
    if (result.code !== 0) {
      throw new ConstantApiError(result.code, result.msg)
    }
    return result.data
  }
}

export class ConstantApiError extends Error {
  constructor(readonly code: number, message: string) {
    super(message || `常量元数据请求失败：${code}`)
  }
}
