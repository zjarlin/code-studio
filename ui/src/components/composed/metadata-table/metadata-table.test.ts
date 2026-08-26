import { describe, expect, it } from 'vitest'

import {
  applyMetadataPatches,
  createLiteralReplacementPatches,
  createTableRevision,
  normalizeMetadataPatchResult,
} from './metadata-table'
import type { MetadataTableDescriptor } from './metadata-table'

interface Row {
  code: string
  label: string
  packageName: string
  status: string
}

interface StructuredRow {
  code: string
  schema: Record<string, unknown>
  rules: unknown[]
}

const rows: Row[] = [
  { code: 'task', label: '巡检任务。巡检任务。', packageName: 'example.inspection.task', status: 'enabled' },
  { code: 'plan', label: '巡检计划。', packageName: 'example.inspection.plan', status: 'disabled' },
]

function descriptor(revision = 'r1'): MetadataTableDescriptor<Row> {
  return {
    tableId: 'library.features',
    revision,
    rowIdentityKey: 'code',
    rowKey: (row) => row.code,
    columns: [
      { key: 'code', label: '编码', kind: 'scalar', context: true },
      { key: 'label', label: '名称', kind: 'scalar', editable: true, context: false },
      { key: 'packageName', label: '包名', kind: 'scalar', context: true },
      {
        key: 'status', label: '状态', kind: 'enum', editable: true, context: false,
        options: [{ label: '启用', value: 'enabled' }, { label: '停用', value: 'disabled' }],
      },
    ],
    operations: ['translate', 'replace', 'fill', 'custom'],
  }
}

describe('metadata table patches', () => {
  it('creates deterministic literal replacement patches without a model call', () => {
    const result = createLiteralReplacementPatches(descriptor(), rows, 'label', '巡检任务', '点检任务')

    expect(result.patches[0]).toMatchObject({
      expectedValue: '巡检任务。巡检任务。',
      edits: [
        { path: null, match: '巡检任务', replacement: '点检任务' },
        { path: null, match: '巡检任务', replacement: '点检任务' },
      ],
    })
    expect(applyMetadataPatches(descriptor(), rows, result).rows[0].label).toBe('点检任务。点检任务。')
  })

  it('rejects an empty literal search before scanning rows', () => {
    expect(() => createLiteralReplacementPatches(descriptor(), rows, 'label', '', '补全'))
      .toThrow('查找内容不能为空')
  })

  it('applies only editable scalar cells and reports old value or revision conflicts', () => {
    const table = descriptor('r1')
    const result = createLiteralReplacementPatches(table, rows, 'label', '巡检', '点检')
    result.patches.push({
      rowKey: 'task', columnKey: 'packageName', expectedValue: 'example.inspection.task',
      edits: [{ path: null, match: 'inspection', replacement: 'blocked' }],
    })
    const changed = rows.map((row) => row.code === 'plan' ? { ...row, label: '人工修改' } : row)
    const applied = applyMetadataPatches(table, changed, result)

    expect(applied.rows[0].label).toBe('点检任务。点检任务。')
    expect(applied.rows[1].label).toBe('人工修改')
    expect(applied.conflicts.map(({ reason }) => reason)).toEqual(['value', 'column'])
    expect(applyMetadataPatches(descriptor('r2'), rows, result).conflicts.every(({ reason }) => reason === 'revision')).toBe(true)
  })

  it('derives a stable revision from declared cells', () => {
    const table = descriptor('')
    expect(createTableRevision(table, rows)).toBe(createTableRevision(table, rows.map((row) => ({ ...row }))))
    expect(createTableRevision(table, rows)).not.toBe(createTableRevision(table, [{ ...rows[0], label: '其他' }, rows[1]]))
  })

  it('normalizes missing optional scalar values to JSON null', () => {
    const table = descriptor() as unknown as MetadataTableDescriptor<Record<string, unknown>>
    const optionalRows: Record<string, unknown>[] = [
      { code: 'task', label: undefined, packageName: 'example.task', status: 'enabled' },
    ]
    const result = normalizeMetadataPatchResult({
      tableId: 'library.features',
      revision: createTableRevision(table, optionalRows),
      patches: [{
        rowKey: 'task', columnKey: 'label', expectedValue: null,
        edits: [{ match: null, replacement: '任务' }],
      }],
      questions: [],
    })

    expect(applyMetadataPatches(
      { ...table, revision: result.revision },
      optionalRows,
      result,
    ).rows[0].label).toBe('任务')
  })

  it('supports a filtered row scope and partial patch selection', () => {
    const table = descriptor()
    const result = createLiteralReplacementPatches(table, [rows[1]], 'label', '巡检', '点检')
    const application = applyMetadataPatches(table, rows, result, result.patches.slice(0, 1))

    expect(application.applied.map(({ rowKey }) => rowKey)).toEqual(['plan'])
    expect(application.rows.map(({ label }) => label)).toEqual(['巡检任务。巡检任务。', '点检计划。'])
  })

  it('normalizes structured output before revision and old value checks', () => {
    const result = normalizeMetadataPatchResult({
      tableId: 'library.features',
      revision: 'r1',
      summary: '补全名称',
      patches: [{
        rowKey: 'task', columnKey: 'label', expectedValue: '巡检任务。巡检任务。',
        edits: [{ match: '巡检任务', replacement: '点检任务' }],
      }],
      questions: [],
    })

    expect(applyMetadataPatches(descriptor('r1'), rows, result).rows[0].label).toBe('点检任务。巡检任务。')
    expect(() => normalizeMetadataPatchResult({
      tableId: 'library.features',
      revision: 'r1',
      patches: [{ rowKey: 'task', columnKey: 'label', expectedValue: [], edits: [{ match: '巡检', replacement: '点检' }] }],
      questions: [],
    })).toThrow('只能修改标量值')
  })

  it('rejects unmatched fragments and enum values outside declared options', () => {
    const table = descriptor()
    const textResult = normalizeMetadataPatchResult({
      tableId: table.tableId,
      revision: table.revision,
      patches: [{
        rowKey: 'task', columnKey: 'label', expectedValue: rows[0].label,
        edits: [{ match: '不存在', replacement: '替换' }],
      }],
      questions: [],
    })
    const enumResult = normalizeMetadataPatchResult({
      tableId: table.tableId,
      revision: table.revision,
      patches: [{
        rowKey: 'task', columnKey: 'status', expectedValue: 'enabled',
        edits: [{ match: 'enabled', replacement: 'unknown' }],
      }],
      questions: [],
    })

    expect(applyMetadataPatches(table, rows, textResult).conflicts[0].reason).toBe('match')
    expect(applyMetadataPatches(table, rows, enumResult).conflicts[0].reason).toBe('replacement')
  })

  it('keeps only fragments that belong to the patched row when a model repeats cross-row edits', () => {
    const table = descriptor()
    const result = normalizeMetadataPatchResult({
      tableId: table.tableId,
      revision: table.revision,
      patches: rows.map((row) => ({
        rowKey: row.code,
        columnKey: 'label',
        expectedValue: null,
        edits: rows.map((candidate) => ({
          path: null,
          match: candidate.label,
          replacement: candidate.label.replaceAll('。', ''),
        })),
      })),
      questions: [],
    })

    const application = applyMetadataPatches(table, rows, result)

    expect(application.conflicts).toEqual([])
    expect(application.rows.map(({ label }) => label)).toEqual(['巡检任务巡检任务', '巡检计划'])
  })

  it('applies JSON Pointer edits only to existing structured scalar leaves', () => {
    const structuredRows: StructuredRow[] = [{
      code: 'task',
      schema: {
        description: '任务说明。',
        nested: { type: 'string', required: true },
        untouched: '保留',
      },
      rules: [{ code: 'maxLength', message: '不能超过 20 个字符。', parameters: { max: '20' } }],
    }]
    const base: Omit<MetadataTableDescriptor<StructuredRow>, 'revision'> = {
      tableId: 'dto.fields',
      rowIdentityKey: 'code',
      rowKey: (row) => row.code,
      columns: [
        { key: 'code', label: '编码', kind: 'scalar', context: true },
        { key: 'schema', label: 'Schema', kind: 'object', editable: true, context: true },
        { key: 'rules', label: '校验', kind: 'collection', editable: true, context: true },
      ],
      operations: ['custom'],
    }
    const table = { ...base, revision: createTableRevision(base, structuredRows) }
    const schemaResult = normalizeMetadataPatchResult({
      tableId: table.tableId,
      revision: table.revision,
      patches: [{
        rowKey: 'task', columnKey: 'schema', expectedValue: null,
        edits: [
          { path: '/description', match: '。', replacement: '' },
          { path: '/nested/required', match: true, replacement: false },
        ],
      }],
      questions: [],
    })
    const ruleResult = normalizeMetadataPatchResult({
      tableId: table.tableId,
      revision: table.revision,
      patches: [{
        rowKey: 'task', columnKey: 'rules', expectedValue: null,
        edits: [{ path: '/0/message', match: '。', replacement: '' }],
      }],
      questions: [],
    })

    const schema = applyMetadataPatches(table, structuredRows, schemaResult).rows[0].schema
    const rules = applyMetadataPatches(table, structuredRows, ruleResult).rows[0].rules
    expect(schema).toEqual({
      description: '任务说明',
      nested: { type: 'string', required: false },
      untouched: '保留',
    })
    expect(rules).toEqual([{ code: 'maxLength', message: '不能超过 20 个字符', parameters: { max: '20' } }])
    expect(structuredRows[0].schema).toMatchObject({ description: '任务说明。' })
  })

  it('rejects root, missing, and prototype JSON Pointer targets', () => {
    const structuredRows: StructuredRow[] = [{ code: 'task', schema: { description: '说明' }, rules: [] }]
    const base: Omit<MetadataTableDescriptor<StructuredRow>, 'revision'> = {
      tableId: 'dto.fields',
      rowIdentityKey: 'code',
      rowKey: (row) => row.code,
      columns: [{ key: 'schema', label: 'Schema', kind: 'object', editable: true, context: true }],
      operations: ['custom'],
    }
    const table = { ...base, revision: createTableRevision(base, structuredRows) }

    for (const path of ['', '/missing', '/__proto__/polluted']) {
      const result = normalizeMetadataPatchResult({
        tableId: table.tableId,
        revision: table.revision,
        patches: [{
          rowKey: 'task', columnKey: 'schema', expectedValue: null,
          edits: [{ path, match: '说明', replacement: '其他' }],
        }],
        questions: [],
      })
      expect(applyMetadataPatches(table, structuredRows, result).conflicts[0].reason).toBe('path')
    }
  })
})
