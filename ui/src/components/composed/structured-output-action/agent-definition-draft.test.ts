import { describe, expect, it } from 'vitest'

import {
  createEmptyAgent,
  formatJson,
  normalizeAgentDraft,
  parseJsonObject,
  structuredOutputFormat,
  toolCodesFromText,
  validateAgentDraft,
} from './agent-definition-draft'

describe('agent draft', () => {
  it('creates a strict structured output contract', () => {
    const draft = createEmptyAgent()
    draft.agentCode = 'taskAssistant'
    draft.name = '任务助手'
    draft.modelCode = 'generalReasoning'
    draft.instructions = '返回任务结果'

    expect(validateAgentDraft(draft)).toEqual([])
    expect(draft.structuredOutput.strict).toBe(true)
    expect(draft.structuredOutput.schema.additionalProperties).toBe(false)
    expect(structuredOutputFormat(draft.structuredOutput).type).toBe('json_schema')
  })

  it('normalizes persisted metadata and tool code input', () => {
    const draft = normalizeAgentDraft({
      agentCode: 'assistant',
      name: '助手',
      modelCode: 'default',
      instructions: '执行任务',
      structuredOutput: {
        name: 'result',
        schema: { type: 'object', properties: {}, required: [], additionalProperties: false },
      },
    })

    expect(draft.status).toBe(1)
    expect(draft.structuredOutput.strict).toBe(true)
    expect(toolCodesFromText('search,\ncalculator')).toEqual(['search', 'calculator'])
  })

  it('reports malformed JSON before persistence', () => {
    expect(() => parseJsonObject('{', '输出 Schema')).toThrow('不是合法 JSON')
    expect(() => parseJsonObject('[]', '输出 Schema')).toThrow('必须是 JSON 对象')
    expect(formatJson(parseJsonObject('{"type":"object"}', '输出 Schema'))).toContain('"type": "object"')
  })
})
