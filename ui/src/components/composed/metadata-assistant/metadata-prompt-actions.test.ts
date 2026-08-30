import { describe, expect, it } from 'vitest'

import {
  CONTRACT_METADATA_PROMPT_ACTIONS,
  DTO_METADATA_PROMPT_ACTIONS,
  isMetadataDisplayTextTranslationRequest,
  MODEL_METADATA_PROMPT_ACTIONS,
} from './metadata-prompt-actions'

describe('model metadata prompt actions', () => {
  it('configures a display-text-only translation prompt', () => {
    const action = MODEL_METADATA_PROMPT_ACTIONS.find((candidate) => candidate.label === '中文化未翻译项')

    expect(action).toBeDefined()
    expect(action?.mode).toBe('display-text-translation')
    expect(action?.prompt).toContain('名称、注释和说明')
    expect(action?.prompt).toContain('补全空说明或备注')
    expect(action?.prompt).toContain('不得修改 ID、编码、字段、查询、关联、路由')
  })

  it('recognizes explicit display-text translation requests conservatively', () => {
    expect(isMetadataDisplayTextTranslationRequest('检查所有元数据没翻译的要翻译')).toBe(true)
    expect(isMetadataDisplayTextTranslationRequest('把展示名称改成中文')).toBe(true)
    expect(isMetadataDisplayTextTranslationRequest('翻译这段日志')).toBe(false)
    expect(isMetadataDisplayTextTranslationRequest('新增一个状态字段')).toBe(false)
  })
})

describe('DTO metadata prompt actions', () => {
  it('offers display-only translation without changing DTO field contracts', () => {
    const action = DTO_METADATA_PROMPT_ACTIONS.find((candidate) => candidate.label === '中文化未翻译项')

    expect(action?.mode).toBe('display-text-translation')
    expect(action?.prompt).toContain('字段说明和嵌套 Schema 说明')
    expect(action?.prompt).toContain('补全空说明')
    expect(action?.prompt).toContain('不得修改 DTO ID、dtoCode、字段 name、sourcePath、类型')
  })

  it('offers whole-field generation while keeping the manual DTO name and ownership context', () => {
    const action = DTO_METADATA_PROMPT_ACTIONS.find((candidate) => candidate.label === '生成 DTO 结构')

    expect(action?.prompt).toContain('fields')
    expect(action?.prompt).toContain('STRUCTURE')
    expect(action?.prompt).toContain('不要修改 name、featureId、packageName 或 contributorId')
  })
})

describe('contract metadata prompt actions', () => {
  it('offers display-only translation without changing operation contracts', () => {
    const action = CONTRACT_METADATA_PROMPT_ACTIONS.find((candidate) => candidate.label === '中文化未翻译项')

    expect(action?.mode).toBe('display-text-translation')
    expect(action?.prompt).toContain('补全空说明')
    expect(action?.prompt).toContain('operationCode、path、method、transport')
  })

  it('offers documentation generation without changing the API contract', () => {
    const action = CONTRACT_METADATA_PROMPT_ACTIONS.find((candidate) => candidate.label === '生成接口文档')

    expect(action?.prompt).toContain('responseBody.description')
    expect(action?.prompt).toContain('保留契约身份')
    expect(action?.prompt).toContain('不要新增、删除或重排操作')
  })
})
