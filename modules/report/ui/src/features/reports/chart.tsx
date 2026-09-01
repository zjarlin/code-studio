import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { DatasetComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { useEffect, useRef } from 'react'

import type { ReportChartKind } from './models'

echarts.use([BarChart, LineChart, PieChart, DatasetComponent, GridComponent, LegendComponent, TooltipComponent, SVGRenderer])

export function ReportChart({ categories, kind, values }: Readonly<{
  categories: string[]
  kind: ReportChartKind
  values: number[]
}>) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return undefined
    const chart = echarts.init(container, undefined, { renderer: 'svg' })
    const source = categories.map((category, index) => ({ category, value: values[index] ?? 0 }))
    const series = kind === 'PIE'
      ? [{ type: 'pie' as const, radius: ['42%', '72%'], encode: { itemName: 'category', value: 'value' } }]
      : [{ type: kind.toLowerCase() as 'bar' | 'line', encode: { x: 'category', y: 'value' }, smooth: kind === 'LINE' }]
    chart.setOption({
      animation: false,
      color: ['#1769e0', '#23835a', '#a56600', '#c23b42'],
      dataset: { source },
      grid: kind === 'PIE' ? undefined : { top: 18, right: 12, bottom: 28, left: 44 },
      legend: kind === 'PIE' ? { bottom: 0, type: 'scroll' } : undefined,
      tooltip: { trigger: kind === 'PIE' ? 'item' : 'axis' },
      xAxis: kind === 'PIE' ? undefined : { type: 'category', axisLabel: { color: '#687084' } },
      yAxis: kind === 'PIE' ? undefined : { type: 'value', axisLabel: { color: '#687084' } },
      series,
    })
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(container)
    return () => {
      observer.disconnect()
      chart.dispose()
    }
  }, [categories, kind, values])

  return <div className="report-chart" ref={containerRef} role="img" aria-label="报表图表" />
}
