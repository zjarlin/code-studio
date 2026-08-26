import type {
  AgentDefinitionDraft,
  AgentStructuredOutputDraft,
  JsonObject,
  JsonValue,
} from '@/types'

const AGENT_CODE_PATTERN = /^[a-z][A-Za-z0-9_]{0,127}$/
const OUTPUT_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_-]{0,63}$/

export function createEmptyAgent(): AgentDefinitionDraft {
  return {
    agentCode: '',
    name: '',
    modelCode: '',
    instructions: '',
    toolCodes: [],
    temperature: null,
    maxOutputTokens: null,
    structuredOutput: {
      name: 'agent_result',
      description: null,
      schema: defaultOutputSchema(),
      strict: true,
    },
    status: 1,
    version: 1,
    description: null,
  }
}

export function normalizeAgentDraft(value: JsonObject): AgentDefinitionDraft {
  const output = objectValue(value.structuredOutput)
  return {
    ...value,
    id: value.id as number | string | undefined,
    agentCode: stringValue(value.agentCode),
    name: stringValue(value.name),
    modelCode: stringValue(value.modelCode),
    instructions: stringValue(value.instructions),
    toolCodes: stringArray(value.toolCodes),
    temperature: optionalNumber(value.temperature),
    maxOutputTokens: optionalNumber(value.maxOutputTokens),
    structuredOutput: {
      name: stringValue(output.name) || 'agent_result',
      description: optionalString(output.description),
      schema: objectValue(output.schema, defaultOutputSchema()),
      strict: output.strict !== false,
    },
    status: numberValue(value.status, 1),
    version: numberValue(value.version, 1),
    description: optionalString(value.description),
  }
}

export function validateAgentDraft(draft: AgentDefinitionDraft): string[] {
  const errors: string[] = []
  if (!AGENT_CODE_PATTERN.test(draft.agentCode.trim())) {
    errors.push('智能体内部标识必须以小写字母开头，且只能包含英文、数字和下划线')
  }
  required(draft.name, '智能体名称', errors)
  required(draft.modelCode, '模型', errors)
  required(draft.instructions, '系统指令', errors)
  if (!OUTPUT_NAME_PATTERN.test(draft.structuredOutput.name.trim())) {
    errors.push('输出名称必须是 1 到 64 位英文、数字、下划线或连字符')
  }
  if (draft.structuredOutput.schema.type !== 'object') {
    errors.push('输出 Schema 根节点 type 必须是 object')
  }
  if (draft.version <= 0) {
    errors.push('定义版本必须大于 0')
  }
  if (draft.temperature != null && (draft.temperature < 0 || draft.temperature > 2)) {
    errors.push('采样温度必须在 0 到 2 之间')
  }
  if (draft.maxOutputTokens != null && draft.maxOutputTokens <= 0) {
    errors.push('最大输出令牌数必须大于 0')
  }
  return errors
}

export function parseJsonObject(value: string, label: string): JsonObject {
  const parsed = parseJsonValue(value, label)
  if (!isObject(parsed)) {
    throw new Error(`${label}必须是 JSON 对象`)
  }
  return parsed
}

export function parseJsonValue(value: string, label: string): JsonValue {
  try {
    return JSON.parse(value) as JsonValue
  } catch (error) {
    const message = error instanceof Error ? error.message : '格式错误'
    throw new Error(`${label}不是合法 JSON：${message}`)
  }
}

export function formatJson(value: JsonValue): string {
  return JSON.stringify(value, null, 2)
}

export function structuredOutputFormat(output: AgentStructuredOutputDraft): JsonObject {
  return {
    type: 'json_schema',
    name: output.name,
    description: output.description ?? undefined,
    schema: output.schema,
    strict: true,
  }
}

export function toolCodesFromText(value: string): string[] {
  return value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean)
}

function defaultOutputSchema(): JsonObject {
  return {
    type: 'object',
    properties: {
      result: {
        type: 'string',
        description: '智能体处理结果',
      },
    },
    required: ['result'],
    additionalProperties: false,
  }
}

function required(value: string, label: string, errors: string[]): void {
  if (!value.trim()) {
    errors.push(`${label}不能为空`)
  }
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function optionalString(value: unknown): string | null {
  const result = stringValue(value).trim()
  return result || null
}

function numberValue(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function optionalNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function objectValue(value: unknown, fallback: JsonObject = {}): JsonObject {
  return isObject(value) ? value : fallback
}

function isObject(value: unknown): value is JsonObject {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
