import type { Component } from 'vue'

import type { AgentUiMessage } from '@/types'

import AgentChartPart from './AgentChartPart.vue'
import AgentContextPart from './AgentContextPart.vue'
import AgentFunctionCallPart from './AgentFunctionCallPart.vue'
import AgentMetadataPart from './AgentMetadataPart.vue'
import AgentMetadataPatchPart from './AgentMetadataPatchPart.vue'
import AgentMetricsPart from './AgentMetricsPart.vue'
import AgentObservationPart from './AgentObservationPart.vue'
import AgentProtocolPart from './AgentProtocolPart.vue'
import AgentReportPart from './AgentReportPart.vue'
import AgentTablePart from './AgentTablePart.vue'
import AgentTextPart from './AgentTextPart.vue'
import AgentUnknownPart from './AgentUnknownPart.vue'

export type AgentMessagePart = AgentUiMessage['parts'][number]

const renderers: Record<string, Component> = {
  text: AgentTextPart,
  'step-start': AgentProtocolPart,
  'data-observation': AgentObservationPart,
  'data-metadata': AgentMetadataPart,
  'data-metadata-patch': AgentMetadataPatchPart,
  'data-configuration': AgentMetadataPart,
  'data-metrics': AgentMetricsPart,
  'data-table': AgentTablePart,
  'data-chart': AgentChartPart,
  'data-context': AgentContextPart,
  'data-function-call': AgentFunctionCallPart,
  'data-report': AgentReportPart,
}

export function resolveAgentMessageRenderer(type: string): Component {
  return renderers[type] ?? AgentUnknownPart
}

export function snapshotAgentMessagePart(part: AgentMessagePart): AgentMessagePart {
  return { ...part }
}

export function registerAgentMessageRenderer(type: string, renderer: Component): void {
  renderers[type] = renderer
}
