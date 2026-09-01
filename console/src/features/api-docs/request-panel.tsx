import { Plus, Trash2 } from 'lucide-react'

import {
  requestContentType,
  requestFormFields,
} from '@platform/openapi-workbench'
import type {
  ApiDocument,
  ApiMultipartField,
  ApiMultipartValue,
  ApiOperation,
  ApiParameter,
  ApiParameterLocation,
  ApiSchema,
} from '@platform/openapi-workbench'

import { CatalogAction } from '@/components/composed/catalog-action/catalog-action'
import { Button } from '@/components/button'

import { newCustomHeader, type ApiRequestDraft } from './session'

interface RequestPanelProps {
  document: ApiDocument
  draft: ApiRequestDraft
  error: string
  onChange: (draft: ApiRequestDraft) => void
  onReset: () => void
  onSend: () => void
  operation: ApiOperation
  pending: boolean
}

const PARAMETER_SECTIONS: Array<{ location: ApiParameterLocation; label: string }> = [
  { location: 'path', label: '路径参数' },
  { location: 'query', label: '查询参数' },
  { location: 'header', label: '请求头' },
]

export function RequestPanel({
  document,
  draft,
  error,
  onChange,
  onReset,
  onSend,
  operation,
  pending,
}: RequestPanelProps) {
  const contentTypes = Object.keys(operation.requestBody?.content ?? {})
  const contentType = requestContentType(operation, draft.contentType)
  const formFields = requestFormFields(operation, document, contentType)

  function updateParameter(location: ApiParameterLocation, name: string, value: string): void {
    const key = location === 'path' ? 'pathValues' : location === 'query' ? 'queryValues' : 'headerValues'
    onChange({ ...draft, [key]: { ...draft[key], [name]: value } })
  }

  function updateForm(name: string, value: ApiMultipartValue): void {
    onChange({ ...draft, formValues: { ...draft.formValues, [name]: value } })
  }

  return (
    <div className="api-request-editor">
      <header className="api-operation-heading">
        <div className="api-operation-toolbar">
          <div className="api-operation-title">
            <span className={`method method-${operation.method}`}>{operation.method.toUpperCase()}</span>
            <h2>{operation.summary}</h2>
          </div>
          <div aria-label="接口操作" className="api-request-actions" role="group">
            <CatalogAction disabled={pending} elementKey="studio.api-docs.reset" onClick={onReset} />
            <CatalogAction disabled={pending} elementKey="studio.api-docs.send" onClick={onSend} variant="primary" />
          </div>
        </div>
        <code>{operation.path}</code>
        {error && <span className="form-error api-operation-error" role="alert">{error}</span>}
      </header>

      <div className="api-editor-scroll">
        {PARAMETER_SECTIONS.map(({ location, label }) => {
          const parameters = operation.parameters.filter((parameter) => parameter.in === location)
          if (!parameters.length) return null
          return (
            <RequestSection key={location} title={label}>
              {parameters.map((parameter) => (
                <ParameterField
                  key={`${location}:${parameter.name}`}
                  onChange={(value) => updateParameter(location, parameter.name, value)}
                  parameter={parameter}
                  value={parameterValue(draft, parameter)}
                />
              ))}
            </RequestSection>
          )
        })}

        <RequestSection
          action={(
            <Button
              aria-label="添加请求头"
              className="button-icon"
              onClick={() => onChange({ ...draft, customHeaders: [...draft.customHeaders, newCustomHeader()] })}
              title="添加请求头"
              variant="ghost"
            >
              <Plus />
            </Button>
          )}
          title="自定义请求头"
        >
          {!draft.customHeaders.length && <p className="api-muted">没有自定义请求头</p>}
          {draft.customHeaders.map((header) => (
            <div className="api-custom-header" key={header.id}>
              <input
                aria-label="请求头名称"
                onChange={(event) => onChange({
                  ...draft,
                  customHeaders: draft.customHeaders.map((item) => item.id === header.id
                    ? { ...item, name: event.target.value }
                    : item),
                })}
                placeholder="Header 名称"
                value={header.name}
              />
              <input
                aria-label={`${header.name || '自定义'}请求头值`}
                onChange={(event) => onChange({
                  ...draft,
                  customHeaders: draft.customHeaders.map((item) => item.id === header.id
                    ? { ...item, value: event.target.value }
                    : item),
                })}
                placeholder="值"
                value={header.value}
              />
              <Button
                aria-label="删除请求头"
                className="button-icon"
                onClick={() => onChange({
                  ...draft,
                  customHeaders: draft.customHeaders.filter((item) => item.id !== header.id),
                })}
                title="删除请求头"
                variant="ghost"
              >
                <Trash2 />
              </Button>
            </div>
          ))}
        </RequestSection>

        {operation.requestBody && (
          <RequestSection title="请求体">
            {contentTypes.length > 1 && (
              <label className="api-field">
                <span>Content-Type</span>
                <select
                  aria-label="请求内容类型"
                  onChange={(event) => onChange({ ...draft, contentType: event.target.value })}
                  value={contentType}
                >
                  {contentTypes.map((item) => <option key={item}>{item}</option>)}
                </select>
              </label>
            )}
            {contentType === 'application/octet-stream' ? (
              <label className="api-file-field">
                <span>二进制文件{operation.requestBody.required && <b>*</b>}</span>
                <input onChange={(event) => onChange({ ...draft, bodyFile: event.target.files?.[0] })} type="file" />
              </label>
            ) : formFields.length ? (
              <div className="api-form-fields">
                {formFields.map((field) => (
                  <FormField
                    field={field}
                    key={field.name}
                    onChange={(value) => updateForm(field.name, value)}
                    value={draft.formValues[field.name]}
                  />
                ))}
              </div>
            ) : (
              <label className="api-field api-body-field">
                <span>{contentType || 'Body'}{operation.requestBody.required && <b>*</b>}</span>
                <textarea
                  aria-label="请求体"
                  onChange={(event) => onChange({ ...draft, bodyText: event.target.value })}
                  spellCheck={false}
                  value={draft.bodyText}
                />
              </label>
            )}
          </RequestSection>
        )}
      </div>

    </div>
  )
}

function RequestSection({ action, children, title }: Readonly<{ action?: React.ReactNode; children: React.ReactNode; title: string }>) {
  return (
    <section className="api-request-section">
      <header><h3>{title}</h3>{action}</header>
      <div>{children}</div>
    </section>
  )
}

function ParameterField({ onChange, parameter, value }: Readonly<{
  onChange: (value: string) => void
  parameter: ApiParameter
  value: string
}>) {
  const options = parameter.schema?.enum ?? []
  return (
    <label className="api-field">
      <span><code>{parameter.name}</code>{parameter.required && <b>*</b>}<small>{parameter.description}</small></span>
      {options.length ? (
        <select onChange={(event) => onChange(event.target.value)} value={value}>
          <option value="">请选择</option>
          {options.map((option) => <option key={String(option)} value={String(option)}>{String(option)}</option>)}
        </select>
      ) : (
        <input
          onChange={(event) => onChange(event.target.value)}
          type={numericSchema(parameter.schema) ? 'number' : 'text'}
          value={value}
        />
      )}
    </label>
  )
}

function FormField({ field, onChange, value }: Readonly<{
  field: ApiMultipartField
  onChange: (value: ApiMultipartValue) => void
  value: ApiMultipartValue
}>) {
  const binary = field.schema.format === 'binary' || field.schema.items?.format === 'binary'
  const multiple = schemaTypes(field.schema).includes('array')
  if (binary) {
    return (
      <label className="api-file-field">
        <span><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></span>
        <input
          multiple={multiple}
          onChange={(event) => {
            const files = [...(event.target.files ?? [])]
            onChange(multiple ? files : files[0])
          }}
          type="file"
        />
      </label>
    )
  }
  if (schemaTypes(field.schema).includes('boolean')) {
    return (
      <label className="api-checkbox-field">
        <input checked={value === true} onChange={(event) => onChange(event.target.checked)} type="checkbox" />
        <span><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></span>
      </label>
    )
  }
  return (
    <label className="api-field">
      <span><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></span>
      <input
        onChange={(event) => onChange(multiple ? event.target.value.split(',').map((item) => item.trim()) : event.target.value)}
        placeholder={multiple ? '多个值以逗号分隔' : undefined}
        type={numericSchema(field.schema) ? 'number' : 'text'}
        value={Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').join(', ') : String(value ?? '')}
      />
    </label>
  )
}

function parameterValue(draft: ApiRequestDraft, parameter: ApiParameter): string {
  if (parameter.in === 'path') return draft.pathValues[parameter.name] ?? ''
  if (parameter.in === 'query') return draft.queryValues[parameter.name] ?? ''
  return draft.headerValues[parameter.name] ?? ''
}

function numericSchema(schema: ApiSchema | undefined): boolean {
  return schemaTypes(schema).some((type) => type === 'integer' || type === 'number')
}

function schemaTypes(schema: ApiSchema | undefined): string[] {
  if (!schema?.type) return []
  return Array.isArray(schema.type) ? schema.type : [schema.type]
}
