import type {
  FieldCommand,
  QueryCommand,
  QueryCommandLogic,
  QueryConditionCommand,
  QueryConditionCommandOperator,
  QueryConditionCommandValueType,
} from '@generated/openapi/models'
import {
  QueryCommandLogic as QueryLogics,
  QueryConditionCommandOperator as QueryOperators,
  QueryConditionCommandValueType as QueryValueTypes,
} from '@generated/openapi/models'

import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { Input } from '@platform/ui/components/generated/shadcn/input'

export function QueryEditor({ editable, fields, queries, onChange }: Readonly<{
  editable: boolean
  fields: FieldCommand[]
  queries: QueryCommand[]
  onChange: (queries: QueryCommand[]) => void
}>) {
  const fieldCode = fields[0]?.fieldCode ?? ''

  function updateQuery(index: number, patch: Partial<QueryCommand>) {
    onChange(normalizeQueries(queries.map((query, queryIndex) => queryIndex === index ? { ...query, ...patch } : query)))
  }

  function updateCondition(queryIndex: number, conditionIndex: number, patch: Partial<QueryConditionCommand>) {
    const query = queries[queryIndex]
    if (!query) return
    updateQuery(queryIndex, {
      items: (query.items ?? []).map((condition, index) => index === conditionIndex ? { ...condition, ...patch } : condition),
    })
  }

  return (
    <section className="query-editor">
      <header className="resource-section-heading">
        <strong>查询</strong>
        {editable && <CatalogIconAction elementKey="studio.library.query.create" onClick={() => onChange(normalizeQueries([...queries, createQuery(queries.length, fieldCode)]))} />}
      </header>
      {queries.map((query, queryIndex) => (
        <section className="query-definition" key={query.id ?? `${query.queryCode}:${queryIndex}`}>
          <div className="resource-table" role="table" aria-label="查询定义">
            <div className="resource-row resource-row-head query-row" role="row"><span>代码</span><span>名称</span><span>组合逻辑</span><span>条件</span><span>操作</span></div>
            <div className="resource-row query-row" role="row">
              <Input aria-label="查询代码" disabled={!editable} onChange={(event) => updateQuery(queryIndex, { queryCode: event.target.value })} required value={query.queryCode} />
              <Input aria-label="查询名称" disabled={!editable} onChange={(event) => updateQuery(queryIndex, { label: event.target.value })} required value={query.label} />
              <select aria-label="查询组合逻辑" disabled={!editable} onChange={(event) => updateQuery(queryIndex, { logic: event.target.value as QueryCommandLogic })} value={query.logic ?? QueryLogics.AND}>
                {Object.values(QueryLogics).map((logic) => <option key={logic} value={logic}>{logic === QueryLogics.AND ? '全部满足' : '任一匹配'}</option>)}
              </select>
              <span>{query.items?.length ?? 0} 个条件</span>
              {editable && <CatalogIconAction elementKey="studio.library.query.delete" onClick={() => onChange(normalizeQueries(queries.filter((_, index) => index !== queryIndex)))} variant="destructive" />}
            </div>
          </div>
          <details className="query-conditions" open>
            <summary>查询条件</summary>
            <div className="resource-section-heading">
              <span>按字段、操作符和参数名生成查询参数。</span>
              {editable && <CatalogIconAction elementKey="studio.library.query.condition.create" onClick={() => updateQuery(queryIndex, { items: [...(query.items ?? []), createCondition(query.items?.length ?? 0, fieldCode)] })} />}
            </div>
            <div className="resource-table" role="table" aria-label={`${query.label} 查询条件`}>
              <div className="resource-row resource-row-head query-condition-row" role="row"><span>字段</span><span>操作符</span><span>值类型</span><span>参数名</span><span>操作</span></div>
              {(query.items ?? []).map((condition, conditionIndex) => (
                <div className="resource-row query-condition-row" key={condition.id ?? `${condition.fieldCode}:${conditionIndex}`} role="row">
                  <select aria-label="查询字段" disabled={!editable} onChange={(event) => updateCondition(queryIndex, conditionIndex, { fieldCode: event.target.value })} value={condition.fieldCode}>
                    {fields.map((field) => <option key={field.fieldCode} value={field.fieldCode}>{field.label} ({field.fieldCode})</option>)}
                    {!fields.some((field) => field.fieldCode === condition.fieldCode) && <option value={condition.fieldCode}>{condition.fieldCode || '未选择字段'}</option>}
                  </select>
                  <select aria-label="查询操作符" disabled={!editable} onChange={(event) => updateCondition(queryIndex, conditionIndex, { operator: event.target.value as QueryConditionCommandOperator })} value={condition.operator}>
                    {Object.values(QueryOperators).map((operator) => <option key={operator} value={operator}>{operator}</option>)}
                  </select>
                  <select aria-label="查询值类型" disabled={!editable} onChange={(event) => updateCondition(queryIndex, conditionIndex, { valueType: event.target.value as QueryConditionCommandValueType })} value={condition.valueType}>
                    {Object.values(QueryValueTypes).map((valueType) => <option key={valueType} value={valueType}>{valueType}</option>)}
                  </select>
                  <Input aria-label="查询参数名" disabled={!editable} onChange={(event) => updateCondition(queryIndex, conditionIndex, { paramName: event.target.value || null })} value={condition.paramName ?? ''} />
                  {editable && <CatalogIconAction elementKey="studio.library.query.condition.delete" onClick={() => updateQuery(queryIndex, { items: (query.items ?? []).filter((_, index) => index !== conditionIndex) })} variant="destructive" />}
                </div>
              ))}
              {!(query.items ?? []).length && <div className="resource-table-empty">尚未配置查询条件</div>}
            </div>
          </details>
        </section>
      ))}
      {!queries.length && <div className="resource-table-empty">尚未配置查询</div>}
    </section>
  )
}

function createQuery(index: number, fieldCode: string): QueryCommand {
  return {
    orderNo: index + 1,
    queryCode: `query${index + 1}`,
    label: `查询 ${index + 1}`,
    logic: QueryLogics.AND,
    items: [createCondition(0, fieldCode)],
  }
}

function createCondition(index: number, fieldCode: string): QueryConditionCommand {
  return {
    orderNo: index + 1,
    fieldCode,
    operator: QueryOperators.EQ,
    valueType: QueryValueTypes.SINGLE,
    paramName: fieldCode || null,
  }
}

function normalizeQueries(queries: QueryCommand[]): QueryCommand[] {
  return queries.map((query, queryIndex) => ({
    ...query,
    orderNo: queryIndex + 1,
    items: (query.items ?? []).map((condition, conditionIndex) => ({ ...condition, orderNo: conditionIndex + 1 })),
  }))
}
