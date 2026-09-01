import { useDraggable } from '@dnd-kit/react'
import { useQuery } from '@tanstack/react-query'
import { useState, type Dispatch } from 'react'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { CatalogIconAction } from '@/components/composed/catalog-action/catalog-icon-action'
import { QueryState } from '@/components/composed/query-state/query-state'
import {
  createReportKey,
  type JsonLiteral,
  type ReportBlockKind,
  type ReportDatasetSpec,
  type ReportDocument,
  type ReportParameter,
  type ReportParameterBinding,
  type ReportSourceOption,
} from '@/features/reports/models'
import type { ReportAction } from '@/features/reports/reducer'
import { fetchReportSourceCatalog } from '@/features/reports/runner'

const PALETTE: Array<{ kind: ReportBlockKind; elementKey: string }> = [
  { kind: 'TEXT', elementKey: 'studio.report-designer.widget.add.text' },
  { kind: 'METRIC', elementKey: 'studio.report-designer.widget.add.metric' },
  { kind: 'TABLE', elementKey: 'studio.report-designer.widget.add.table' },
  { kind: 'CHART', elementKey: 'studio.report-designer.widget.add.chart' },
  { kind: 'IMAGE', elementKey: 'studio.report-designer.widget.add.image' },
]

export function DesignerToolPanel({ active, dispatch, document, editable }: Readonly<{
  active: 'components' | 'sources'
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
}>) {
  if (active === 'components') {
    const rowKey = document.rows[0]?.key
    return (
      <div className="designer-tool-list">
        {PALETTE.map((item) => (
          <PaletteItem
            disabled={!editable || (!document.datasets.length && item.kind !== 'TEXT') || !rowKey}
            dispatch={dispatch}
            item={item}
            key={item.kind}
            rowKey={rowKey}
          />
        ))}
      </div>
    )
  }
  return <SourcePanel dispatch={dispatch} document={document} editable={editable} />
}

function PaletteItem({ disabled, dispatch, item, rowKey }: Readonly<{
  disabled: boolean
  dispatch: Dispatch<ReportAction>
  item: { kind: ReportBlockKind; elementKey: string }
  rowKey?: string
}>) {
  const { isDragging, ref } = useDraggable({
    id: `palette-${item.kind}`,
    data: { sourceType: 'palette', blockKind: item.kind },
    disabled,
  })
  return (
    <CatalogAction
      className={isDragging ? 'palette-item is-dragging' : 'palette-item'}
      disabled={disabled}
      elementKey={item.elementKey}
      onClick={() => rowKey && dispatch({ type: 'addBlock', rowKey, blockKind: item.kind })}
      ref={ref}
    />
  )
}

function SourcePanel({ dispatch, document, editable }: Readonly<{
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
}>) {
  const catalog = useQuery({ queryKey: ['console', 'report-source-catalog'], queryFn: fetchReportSourceCatalog })
  const [modelCode, setModelCode] = useState('')
  const [operationId, setOperationId] = useState('')
  const model = catalog.data?.models.find((candidate) => candidate.modelCode === modelCode)
  const operation = catalog.data?.operations.find((candidate) => candidate.operationId === operationId)
  const addDataset = (source: ReportSourceOption, identity: Pick<ReportDatasetSpec, 'source' | 'modelCode' | 'operationId'>) => {
    dispatch({
      type: 'addDataset',
      dataset: {
        key: createReportKey('dataset'),
        name: source.name,
        fields: source.fields,
        parameterBindings: {},
        ...identity,
      },
    })
  }

  return (
    <div className="designer-source-panel">
      <section>
        <h3>已选数据集</h3>
        <div className="source-list">
          {document.datasets.map((dataset) => (
            <DatasetEditor
              catalog={catalog.data}
              dataset={dataset}
              dispatch={dispatch}
              document={document}
              editable={editable}
              key={dataset.key}
            />
          ))}
          {!document.datasets.length ? <div className="panel-empty">暂无数据集</div> : null}
        </div>
      </section>
      <ParameterEditor dispatch={dispatch} document={document} editable={editable} />
      <QueryState error={catalog.error} pending={catalog.isPending}>
        <section>
          <h3>模型</h3>
          <select aria-label="模型" disabled={!editable} onChange={(event) => setModelCode(event.target.value)} value={modelCode}>
            <option value="">选择模型</option>
            {catalog.data?.models.map((candidate) => <option key={candidate.modelCode} value={candidate.modelCode}>{candidate.name}</option>)}
          </select>
          <CatalogAction
            disabled={!editable || !model}
            elementKey="studio.report-designer.source.add-model"
            onClick={() => model && addDataset(model, { source: 'MODEL', modelCode: model.modelCode, operationId: null })}
          />
        </section>
        <section>
          <h3>OpenAPI GET</h3>
          <select aria-label="OpenAPI GET" disabled={!editable} onChange={(event) => setOperationId(event.target.value)} value={operationId}>
            <option value="">选择操作</option>
            {catalog.data?.operations.map((candidate) => <option key={candidate.operationId} value={candidate.operationId}>{candidate.name}</option>)}
          </select>
          <CatalogAction
            disabled={!editable || !operation}
            elementKey="studio.report-designer.source.add-openapi"
            onClick={() => operation && addDataset(operation, { source: 'OPENAPI', modelCode: null, operationId: operation.operationId })}
          />
        </section>
      </QueryState>
    </div>
  )
}

function DatasetEditor({ catalog, dataset, dispatch, document, editable }: Readonly<{
  catalog?: Awaited<ReturnType<typeof fetchReportSourceCatalog>>
  dataset: ReportDatasetSpec
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
}>) {
  const source = dataset.source === 'MODEL'
    ? catalog?.models.find(({ modelCode }) => modelCode === dataset.modelCode)
    : catalog?.operations.find(({ operationId }) => operationId === dataset.operationId)
  const replace = (next: ReportDatasetSpec) => dispatch({ type: 'replaceDataset', dataset: next })
  return (
    <div className="source-row source-row-expanded">
      <div className="source-row-heading">
        <span><strong>{dataset.name}</strong><small>{dataset.source === 'MODEL' ? dataset.modelCode : dataset.operationId}</small></span>
        <CatalogIconAction disabled={!editable} elementKey="studio.report-designer.source.remove" onClick={() => dispatch({ type: 'removeDataset', datasetKey: dataset.key })} />
      </div>
      {source?.parameters.map((parameter) => (
        <DatasetBinding
          binding={dataset.parameterBindings[parameter.name]}
          disabled={!editable}
          key={parameter.name}
          label={`${parameter.name}${parameter.required ? ' *' : ''}`}
          onChange={(binding) => {
            const parameterBindings = { ...dataset.parameterBindings }
            if (binding) parameterBindings[parameter.name] = binding
            else delete parameterBindings[parameter.name]
            replace({ ...dataset, parameterBindings })
          }}
          parameters={document.parameters}
        />
      ))}
      <small>{dataset.fields.length ? `${dataset.fields.length} 个已识别字段` : '响应字段将在运行时校验'}</small>
    </div>
  )
}

function DatasetBinding({ binding, disabled, label, onChange, parameters }: Readonly<{
  binding?: ReportParameterBinding
  disabled: boolean
  label: string
  onChange: (binding?: ReportParameterBinding) => void
  parameters: ReportParameter[]
}>) {
  const value = binding?.kind === 'PARAMETER' ? `parameter:${binding.parameterKey}` : binding ? 'literal' : ''
  return (
    <label className="source-binding">{label}
      <select
        disabled={disabled}
        onChange={(event) => {
          const next = event.target.value
          if (!next) onChange(undefined)
          else if (next === 'literal') onChange({ kind: 'LITERAL', parameterKey: null, literal: '' })
          else onChange({ kind: 'PARAMETER', parameterKey: next.slice('parameter:'.length), literal: null })
        }}
        value={value}
      >
        <option value="">不绑定</option>
        {parameters.map((parameter) => <option key={parameter.key} value={`parameter:${parameter.key}`}>参数：{parameter.label}</option>)}
        <option value="literal">JSON 字面量</option>
      </select>
      {binding?.kind === 'LITERAL' ? (
        <input
          aria-label={`${label} 字面量`}
          disabled={disabled}
          onBlur={(event) => onChange({ ...binding, literal: parseLiteral(event.target.value) })}
          defaultValue={formatLiteral(binding.literal)}
        />
      ) : null}
    </label>
  )
}

function ParameterEditor({ dispatch, document, editable }: Readonly<{
  dispatch: Dispatch<ReportAction>
  document: ReportDocument
  editable: boolean
}>) {
  return (
    <section>
      <div className="source-section-heading">
        <h3>报表参数</h3>
        <CatalogIconAction
          disabled={!editable}
          elementKey="studio.report-designer.parameter.add"
          onClick={() => dispatch({
            type: 'addParameter',
            parameter: {
              key: createReportKey('parameter'),
              label: '新参数',
              type: 'TEXT',
              required: false,
              defaultValue: null,
              options: [],
            },
          })}
        />
      </div>
      {document.parameters.map((parameter) => (
        <div className="parameter-editor" key={parameter.key}>
          <input disabled={!editable} onChange={(event) => dispatch({ type: 'replaceParameter', parameter: { ...parameter, label: event.target.value } })} value={parameter.label} />
          <select
            disabled={!editable}
            onChange={(event) => {
              const type = event.target.value as ReportParameter['type']
              dispatch({
                type: 'replaceParameter',
                parameter: { ...parameter, type, options: type === 'ENUM' ? [{ value: 'option', label: '选项' }] : [] },
              })
            }}
            value={parameter.type}
          >
            <option value="TEXT">文本</option><option value="NUMBER">数字</option><option value="BOOLEAN">布尔</option>
            <option value="DATE">日期</option><option value="DATETIME">日期时间</option><option value="ENUM">枚举</option>
          </select>
          <input disabled={!editable} onChange={(event) => dispatch({ type: 'replaceParameter', parameter: { ...parameter, defaultValue: event.target.value || null } })} placeholder="默认值" value={parameter.defaultValue ?? ''} />
          <label className="parameter-required">
            <input
              checked={parameter.required}
              disabled={!editable}
              onChange={(event) => dispatch({ type: 'replaceParameter', parameter: { ...parameter, required: event.target.checked } })}
              type="checkbox"
            />
            必填
          </label>
          {parameter.type === 'ENUM' ? (
            <textarea
              aria-label={`${parameter.label} 枚举选项`}
              disabled={!editable}
              onBlur={(event) => dispatch({ type: 'replaceParameter', parameter: { ...parameter, options: parseOptions(event.target.value) } })}
              defaultValue={parameter.options.map((option) => `${option.value} | ${option.label}`).join('\n')}
            />
          ) : null}
          <CatalogIconAction disabled={!editable} elementKey="studio.report-designer.parameter.remove" onClick={() => dispatch({ type: 'removeParameter', parameterKey: parameter.key })} />
        </div>
      ))}
      {!document.parameters.length ? <div className="panel-empty">暂无参数</div> : null}
    </section>
  )
}

function parseLiteral(value: string): JsonLiteral {
  try {
    const parsed: unknown = JSON.parse(value)
    return parsed === null || ['string', 'number', 'boolean'].includes(typeof parsed) ? parsed as JsonLiteral : value
  } catch {
    return value
  }
}

function formatLiteral(value: JsonLiteral): string {
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function parseOptions(value: string): ReportParameter['options'] {
  return value.split('\n').flatMap((line) => {
    const [optionValue, label] = line.split('|').map((part) => part.trim())
    return optionValue && label ? [{ value: optionValue, label }] : []
  })
}
