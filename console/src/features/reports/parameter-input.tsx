import type { ReportParameter } from './models'

export function ReportParameterInput({ onChange, parameter, value }: Readonly<{
  onChange: (value: string) => void
  parameter: ReportParameter
  value: string
}>) {
  if (parameter.type === 'ENUM') {
    return (
      <label>{parameter.label}<select onChange={(event) => onChange(event.target.value)} required={parameter.required} value={value}>
        <option disabled={parameter.required} value="">{parameter.required ? '请选择' : '全部'}</option>
        {parameter.options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select></label>
    )
  }
  if (parameter.type === 'BOOLEAN') {
    return (
      <label>{parameter.label}<select onChange={(event) => onChange(event.target.value)} required={parameter.required} value={value}>
        <option disabled={parameter.required} value="">{parameter.required ? '请选择' : '全部'}</option>
        <option value="true">是</option>
        <option value="false">否</option>
      </select></label>
    )
  }
  const inputType = parameter.type === 'DATE'
    ? 'date'
    : parameter.type === 'DATETIME' ? 'datetime-local' : parameter.type === 'NUMBER' ? 'number' : 'text'
  return (
    <label>{parameter.label}<input onChange={(event) => onChange(event.target.value)} required={parameter.required} type={inputType} value={value} /></label>
  )
}
