import { REPORT_GRID_COLUMNS, type ReportBlockSpec, type ReportDocument } from './models'
import { isJsonPointer } from './runner'

const REPORT_KEY = /^[a-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*$/

export function validateReportKey(reportKey: string): string | undefined {
  return REPORT_KEY.test(reportKey) ? undefined : '报表标识必须以小写字母开头，只能包含字母、数字和连字符'
}

export function validateReportForPublish(document: ReportDocument): string[] {
  const errors: string[] = []
  if (!document.name.trim()) errors.push('报表名称不能为空')
  if (![8, 12, 20].includes(document.page.marginMm)) errors.push('A4 页边距只能是 8、12 或 20 mm')
  if (!document.rows.length) errors.push('发布报表至少需要一行')
  document.rows.forEach((row) => {
    if (!row.blocks.length) errors.push(`网格行 ${row.key} 不能为空`)
    if (row.blocks.reduce((sum, block) => sum + block.columnSpan, 0) > REPORT_GRID_COLUMNS) {
      errors.push(`网格行 ${row.key} 超过 12 列`)
    }
    if (row.blocks.some(({ kind }) => kind === 'TABLE') && (row.blocks.length !== 1 || row.blocks[0]?.columnSpan !== 12)) {
      errors.push(`网格行 ${row.key} 的表格必须独占一行`)
    }
  })
  const blocks = document.rows.flatMap((row) => row.blocks)
  const referencedDatasets = new Set(blocks.map(datasetKeyOf).filter(Boolean))
  document.datasets.forEach((dataset) => {
    if (!referencedDatasets.has(dataset.key)) errors.push(`数据集 ${dataset.name} 未被组件使用`)
    dataset.fields.forEach((field) => {
      if (!isJsonPointer(field.pointer)) errors.push(`数据集字段 ${field.label} 不是有效 JSON Pointer`)
    })
  })
  const referencedParameters = new Set(document.datasets.flatMap((dataset) =>
    Object.values(dataset.parameterBindings).flatMap((binding) => binding.kind === 'PARAMETER' ? [binding.parameterKey] : []),
  ))
  document.parameters.forEach((parameter) => {
    if (!referencedParameters.has(parameter.key)) errors.push(`参数 ${parameter.label} 未被数据集使用`)
    if (parameter.type === 'ENUM' && !parameter.options.length) errors.push(`枚举参数 ${parameter.label} 没有选项`)
  })
  blocks.forEach((block) => validateBlock(block, errors))
  return [...new Set(errors)]
}

function validateBlock(block: ReportBlockSpec, errors: string[]): void {
  if (block.kind === 'TEXT' && !block.text.trim()) errors.push(`文本 ${block.key} 不能为空`)
  if (block.kind === 'METRIC' && !isJsonPointer(block.valuePointer)) errors.push(`指标 ${block.key} 指针无效`)
  if (block.kind === 'TABLE') {
    if (!block.columns.length) errors.push(`表格 ${block.key} 至少需要一列`)
    block.columns.forEach((column) => {
      if (!isJsonPointer(column.valuePointer)) errors.push(`表格列 ${column.label} 指针无效`)
    })
  }
  if (block.kind === 'CHART' && (!isJsonPointer(block.categoryPointer) || !isJsonPointer(block.valuePointer))) {
    errors.push(`图表 ${block.key} 指针无效`)
  }
  if (block.kind === 'IMAGE') {
    if (!block.alt.trim()) errors.push(`图片 ${block.key} 必须填写替代文本`)
    if (!isJsonPointer(block.sourcePointer)) errors.push(`图片 ${block.key} 指针无效`)
  }
}

function datasetKeyOf(block: ReportBlockSpec): string | undefined {
  return block.kind === 'TEXT' ? undefined : block.datasetKey
}
