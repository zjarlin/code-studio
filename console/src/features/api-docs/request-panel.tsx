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
import { Button } from '@platform/ui/components/generated/shadcn/button'
import { Checkbox } from '@platform/ui/components/generated/shadcn/checkbox'
import { Field, FieldLabel } from '@platform/ui/components/generated/shadcn/field'
import { Input } from '@platform/ui/components/generated/shadcn/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@platform/ui/components/generated/shadcn/select'
import { Textarea } from '@platform/ui/components/generated/shadcn/textarea'

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
              onClick={() => onChange({ ...draft, customHeaders: [...draft.customHeaders, newCustomHeader()] })}
              size="icon-sm"
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
              <Input
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
              <Input
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
                onClick={() => onChange({
                  ...draft,
                  customHeaders: draft.customHeaders.filter((item) => item.id !== header.id),
                })}
                size="icon-sm"
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
              <Field className="api-field">
                <FieldLabel>Content-Type</FieldLabel>
                <Select onValueChange={(value) => onChange({ ...draft, contentType: value })} value={contentType}>
                  <SelectTrigger aria-label="请求内容类型"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup>
                    {contentTypes.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}
                  </SelectGroup></SelectContent>
                </Select>
              </Field>
            )}
            {contentType === 'application/octet-stream' ? (
              <Field className="api-file-field">
                <FieldLabel>二进制文件{operation.requestBody.required && <b>*</b>}</FieldLabel>
                <Input onChange={(event) => onChange({ ...draft, bodyFile: event.target.files?.[0] })} type="file" />
              </Field>
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
              <Field className="api-field api-body-field">
                <FieldLabel>{contentType || 'Body'}{operation.requestBody.required && <b>*</b>}</FieldLabel>
                <Textarea
                  aria-label="请求体"
                  onChange={(event) => onChange({ ...draft, bodyText: event.target.value })}
                  spellCheck={false}
                  value={draft.bodyText}
                />
              </Field>
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
    <Field className="api-field">
      <FieldLabel><code>{parameter.name}</code>{parameter.required && <b>*</b>}<small>{parameter.description}</small></FieldLabel>
      {options.length ? (
        <Select onValueChange={onChange} value={value}>
          <SelectTrigger><SelectValue placeholder="请选择" /></SelectTrigger>
          <SelectContent><SelectGroup>
            {options.map((option) => <SelectItem key={String(option)} value={String(option)}>{String(option)}</SelectItem>)}
          </SelectGroup></SelectContent>
        </Select>
      ) : (
        <Input
          onChange={(event) => onChange(event.target.value)}
          type={numericSchema(parameter.schema) ? 'number' : 'text'}
          value={value}
        />
      )}
    </Field>
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
      <Field className="api-file-field">
        <FieldLabel><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></FieldLabel>
        <Input
          multiple={multiple}
          onChange={(event) => {
            const files = [...(event.target.files ?? [])]
            onChange(multiple ? files : files[0])
          }}
          type="file"
        />
      </Field>
    )
  }
  if (schemaTypes(field.schema).includes('boolean')) {
    return (
      <Field className="api-checkbox-field" orientation="horizontal">
        <Checkbox checked={value === true} onCheckedChange={(checked) => onChange(checked === true)} />
        <FieldLabel><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></FieldLabel>
      </Field>
    )
  }
  return (
    <Field className="api-field">
      <FieldLabel><code>{field.name}</code>{field.required && <b>*</b>}<small>{field.schema.description}</small></FieldLabel>
      <Input
        onChange={(event) => onChange(multiple ? event.target.value.split(',').map((item) => item.trim()) : event.target.value)}
        placeholder={multiple ? '多个值以逗号分隔' : undefined}
        type={numericSchema(field.schema) ? 'number' : 'text'}
        value={Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').join(', ') : String(value ?? '')}
      />
    </Field>
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
